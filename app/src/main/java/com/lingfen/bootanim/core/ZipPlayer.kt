package com.lingfen.bootanim.core

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import java.io.File
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

    private val zip = ZipFile(zipFile)
    private val timeline = ArrayList<Frame>()
    private val cache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
    private val handler = Handler(Looper.getMainLooper())

    private var playing = false
    private var baseTime = 0L
    private var elapsed = 0L
    private var curIndex = -1

    var onFrame: ((Bitmap) -> Unit)? = null
    var onProgress: ((Double, Double) -> Unit)? = null   // (elapsedSec, totalSec)
    var onEnd: (() -> Unit)? = null

    /** 解析 desc.txt 并构建时间轴 */
    fun load(): Boolean {
        val descEntry = zip.getEntry("desc.txt") ?: return false
        val desc = zip.getInputStream(descEntry).bufferedReader().use { it.readText() }
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
            val frames = zip.entries().toList()
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
                pt.frames.forEach { push(it, 1.0 / fps) }
                push(pt.frames.last(), 2.0)   // p 类型：保持 2 秒
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
        cache.values.forEach { it.recycle() }
        cache.clear()
        try { zip.close() } catch (_: Exception) {}
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
            handler.postDelayed(this, 33)
        }
    }

    private fun indexAt(sec: Double): Int {
        for (i in timeline.indices.reversed()) {
            if (sec >= timeline[i].t0) return i
        }
        return 0
    }

    /** 按需解码 + LRU 8 帧 */
    private fun decodeAsync(idx: Int, cb: (Bitmap?) -> Unit) {
        val f = timeline.getOrNull(idx) ?: return cb(null)
        cache[f.path]?.let { return cb(it) }

        Thread {
            val bmp = try {
                val entry = zip.getEntry(f.path) ?: return@Thread cb(null)
                zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { null }

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
        }.start()
    }

    /** 预取首帧 */
    fun prefetchFirst(cb: (Bitmap?) -> Unit) {
        if (timeline.isEmpty()) return cb(null)
        decodeAsync(0, cb)
    }
}
