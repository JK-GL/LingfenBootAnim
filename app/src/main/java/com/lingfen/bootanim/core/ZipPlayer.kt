package com.lingfen.bootanim.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.zip.ZipFile
import kotlin.math.max

/**
 * bootanimation.zip 播放器
 *
 * 严格按 desc.txt 语义播放：
 *   c <count> <pause> <folder>  → 播放 count 轮，每轮所有帧，轮末停 pause 帧
 *   p <count> <pause> <folder>  → 播完保持末帧
 *
 * 关键：不能一次性解码全部帧（500 帧 1080p 会 OOM），
 * 用 LRU 缓存 8 帧，播放时按需解码 + 预解码下一帧。
 * 预览解码用 inSampleSize=2 降到 1/4 像素量，才能跟上 60fps 的帧间隔。
 */
class ZipPlayer(private val zipFile: File) {

    data class Part(
        val type: Char,
        val count: Int,
        val pause: Int,
        val folder: String,
        val frames: List<String>,
    )

    data class Frame(val path: String, val t0: Double, val t1: Double)

    var width = 1920; private set
    var height = 1080; private set
    var fps = 25; private set
    var totalSeconds = 0.0; private set

    private var zip: ZipFile? = null
    private val timeline = ArrayList<Frame>()
    private val cache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
    private val handler = Handler(Looper.getMainLooper())

    /** 串行解码线程：避免 60fps 下每帧 new Thread 造成线程堆积 */
    private val decodeExec = Executors.newSingleThreadExecutor()

    private var playing = false
    private var baseTime = 0L
    private var elapsed = 0L
    private var curIndex = -1

    var onFrame: ((Bitmap) -> Unit)? = null
    var onProgress: ((Double, Double) -> Unit)? = null   // (elapsedSec, totalSec)
    var onEnd: (() -> Unit)? = null

    /** 解析 desc.txt 并构建时间轴 */
    fun load(): Boolean {
        val z = try { ZipFile(zipFile) } catch (_: Exception) { return false }
        zip = z
        val descEntry = z.getEntry("desc.txt") ?: return false
        val desc = z.getInputStream(descEntry).bufferedReader().use { it.readText() }
            .removePrefix("\uFEFF")

        val lines = desc.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return false

        val head = lines[0].split(Regex("\\s+"))
        if (head.size < 3) return false
        width = head[0].toIntOrNull() ?: return false
        height = head[1].toIntOrNull() ?: return false
        fps = head[2].toIntOrNull() ?: return false

        val parts = ArrayList<Part>()
        for (i in 1 until lines.size) {
            val sp = lines[i].split(Regex("\\s+"))
            if (sp.size < 4) continue
            val type = sp[0].firstOrNull() ?: continue
            val count = sp[1].toIntOrNull() ?: 0
            val pause = sp[2].toIntOrNull() ?: 0
            val folder = sp[3]
            val frames = z.entries().toList()
                .map { it.name }
                .filter { it.startsWith("$folder/") && !it.endsWith("/") }
                .sorted()
            if (frames.isEmpty()) continue
            parts.add(Part(type, count, pause, folder, frames))
        }
        if (parts.isEmpty()) return false

        // 构建时间轴
        timeline.clear()
        var t = 0.0
        fun push(path: String, dur: Double) {
            timeline.add(Frame(path, t, t + dur))
            t += dur
        }

        for (pt in parts) {
            if (pt.type == 'c') {
                val loops = if (pt.count > 0) pt.count else 1
                repeat(loops) {
                    pt.frames.forEach { push(it, 1.0 / fps) }
                    if (pt.pause > 0) push(pt.frames.last(), pt.pause.toDouble() / fps)
                }
            } else {
                // 车机保活定格段（p 类型）：如果前面已有主体动画（c 类型），预览不重复播放末帧定格
                // 确保用户选 5.0 秒，时间轴就精确为 5.0 秒，放完立即利落结束！
                if (parts.none { it.type == 'c' }) {
                    pt.frames.forEach { push(it, 1.0 / fps) }
                }
            }
        }
        totalSeconds = t
        return true
    }

    fun play() {
        if (timeline.isEmpty()) return
        if (playing) { pause(); return }
        playing = true
        baseTime = System.currentTimeMillis() - elapsed
        handler.post(tick)
    }

    fun pause() {
        playing = false
        handler.removeCallbacks(tick)
    }

    fun restart() {
        pause()
        elapsed = 0
        curIndex = -1
        onProgress?.invoke(0.0, totalSeconds)
    }

    fun release() {
        pause()
        try { (decodeExec as? ThreadPoolExecutor)?.queue?.clear() } catch (_: Exception) {}
        decodeExec.shutdownNow()
        synchronized(cache) {
            cache.values.forEach { it.recycle() }
            cache.clear()
        }
        try { zip?.close() } catch (_: Exception) {}
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            elapsed = System.currentTimeMillis() - baseTime
            val sec = elapsed / 1000.0
            onProgress?.invoke(minOf(sec, totalSeconds), totalSeconds)

            val idx = indexAt(sec)
            if (idx != curIndex) {
                curIndex = idx
                decodeAsync(idx) { bmp ->
                    if (bmp != null && idx == curIndex) onFrame?.invoke(bmp)
                }
            }

            if (sec >= totalSeconds) {
                playing = false
                onEnd?.invoke()
                return
            }
            // 轮询间隔随帧率自适应：60fps 帧间隔 16.7ms，必须用 8ms 轮询才能翻出每一帧。
            // 原来写死 33ms，等于把 60fps 的包压成 30fps 预览，会误导判断。
            val pollMs = max(8L, (1000.0 / fps / 2.0).toLong())
            handler.postDelayed(this, pollMs)
        }
    }

    private fun indexAt(sec: Double): Int {
        for (i in timeline.indices.reversed()) {
            if (sec >= timeline[i].t0) return i
        }
        return 0
    }

    /** 按需解码 + LRU 8 帧；串行执行，并丢弃过期解码请求 */
    private fun decodeAsync(idx: Int, cb: (Bitmap?) -> Unit) {
        val f = timeline.getOrNull(idx) ?: return cb(null)
        synchronized(cache) { cache[f.path] }?.let { return cb(it) }

        // 丢掉队列里尚未开始的旧请求：60fps 下若解码追不上，不丢会无限堆积
        (decodeExec as? ThreadPoolExecutor)?.queue?.clear()

        decodeExec.execute {
            val entry = try { zip?.getEntry(f.path) } catch (_: Exception) { null }
            val bmp: Bitmap? = if (entry == null) null else try {
                zip?.getInputStream(entry).use { ins ->
                    val opts = BitmapFactory.Options().apply {
                        // 预览只需屏幕可见精度，降采样 4 倍像素量，解码快 3~4 倍、内存省 75%
                        inSampleSize = 2
                    }
                    BitmapFactory.decodeStream(ins, null, opts)
                }
            } catch (_: Exception) { null }

            if (bmp != null) {
                synchronized(cache) {
                    cache[f.path] = bmp
                    while (cache.size > 8) {
                        val eldest = cache.keys.first()
                        if (eldest == f.path) break
                        cache.remove(eldest)?.recycle()
                    }
                }
            }
            handler.post { cb(bmp) }
        }
    }

    /** 预取首帧 */
    fun prefetchFirst(cb: (Bitmap?) -> Unit) {
        if (timeline.isEmpty()) return cb(null)
        decodeAsync(0, cb)
    }
}
