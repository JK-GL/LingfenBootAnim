package com.lingfen.bootanim

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.lingfen.bootanim.core.Quality
import com.lingfen.bootanim.core.SizeEstimator
import com.lingfen.bootanim.ui.*
import java.io.File

class MainActivity : ComponentActivity() {

    private val state = AppState()

    /** 预览目标（ZIP 文件） */
    private var previewFile by mutableStateOf<File?>(null)

    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> if (uri != null) loadVideo(uri) }

    private val pickZip = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val file = copyToCache(uri)
            if (file != null && isValidZip(file)) {
                previewFile = file
            } else {
                file?.delete()
                showZipErrorDialog()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val target = previewFile
            if (target != null) {
                PreviewScreen(zipFile = target) { previewFile = null }
            } else {
                MainScreen(
                    st = state,
                    onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                    onGenerate = { startGenerate() },
                    onCancel = { cancelGenerate() },
                    onPickZip = {
                        pickZip.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                    },
                    onOpenLast = { previewFile = state.lastZip },
                )
            }
        }
    }

    // ── 读取视频元数据 ──────────────────────────────
    private fun loadVideo(uri: Uri) {
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(this, uri)
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val rot = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0

            // 旋转 90/270 时宽高互换
            val realW = if (rot == 90 || rot == 270) h else w
            val realH = if (rot == 90 || rot == 270) w else h

            state.videoUri = uri
            state.videoName = queryName(uri)
            state.videoW = realW
            state.videoH = realH
            state.videoDuration = durMs / 1000.0
            // 默认光标停留在原视频完整时长，向左滑动时受最低 6.0 秒下限门禁约束
            state.targetSeconds = state.videoDuration
            state.estimatedBytes = 0L
        } catch (_: Exception) {
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun queryName(uri: Uri): String {
        var name = "video.mp4"
        try {
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) c.getString(i)?.let { name = it }
            }
        } catch (_: Exception) {}
        return name
    }

    private fun copyToCache(uri: Uri): File? = try {
        val f = File(cacheDir, "pv_${System.currentTimeMillis()}.zip")
        contentResolver.openInputStream(uri)?.use { input ->
            f.outputStream().use { out -> input.copyTo(out) }
        }
        f
    } catch (_: Exception) { null }

    /** 预检选择的文件是否为合规的开机动画 zip 文件 */
    private fun isValidZip(file: File): Boolean = try {
        java.util.zip.ZipFile(file).use { zf ->
            zf.getEntry("desc.txt") != null
        }
    } catch (_: Exception) { false }

    /** 弹窗提示用户选择合规的 zip 文件 */
    private fun showZipErrorDialog() {
        android.app.AlertDialog.Builder(this)
            .setTitle("提示")
            .setMessage("请选择正确的 zip 文件")
            .setPositiveButton("确定", null)
            .show()
    }

    // ── 生成与取消流程 ──────────────────────────
    private var generateThread: Thread? = null

    /** 用户点击「取消生成」：UI 瞬关 + 线程中断 + 状态清空 */
    private fun cancelGenerate() {
        state.cancelled = true
        state.generating = false
        state.percent = 0
        state.stepDetail = ""
        state.stepIndex = 0
        try {
            generateThread?.interrupt()
        } catch (_: Exception) {}
        generateThread = null
        android.widget.Toast.makeText(this, "已取消生成", android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun startGenerate() {
        val uri = state.videoUri ?: return
        val w = state.outW
        val h = state.outH
        val fps = state.outFps
        val target = state.targetSeconds

        state.cancelled = false
        state.generating = true
        state.stepIndex = 0
        state.percent = 0
        state.stepDetail = ""
        state.warnings = emptyList()

        val thread = Thread {
            val workDir = File(cacheDir, "work_${System.currentTimeMillis()}")
            workDir.mkdirs()
            try {
                // ① 分析视频
                ui { state.stepIndex = 0; state.percent = 3; state.stepDetail = "%.1fs".format(state.videoDuration) }
                Thread.sleep(180)
                if (state.cancelled || Thread.currentThread().isInterrupted) return@Thread

                // ② 抽取帧：使用用户选定的真实画质（50–100，默认 85）与帧率（25–60，默认 25）
                val quality = state.quality
                state.usedQuality = quality
                ui { state.stepIndex = 1; state.percent = 5; state.stepDetail = "抽帧 (q$quality)" }

                var lastProgressTime = 0L
                FrameExtractor.extract(this, uri, workDir, w, h, fps, target, quality,
                    object : FrameExtractor.Progress {
                        override fun onProgress(done: Int, total: Int) {
                            if (state.cancelled || Thread.currentThread().isInterrupted) return
                            val now = System.currentTimeMillis()
                            // 100ms 节流门控：防止过度轰炸主线程导致触摸事件被丢弃
                            if (now - lastProgressTime >= 100 || done == total) {
                                lastProgressTime = now
                                ui {
                                    if (!state.cancelled && state.generating) {
                                        state.percent = 5 + (done.toFloat() / total * 75).toInt()
                                        state.stepDetail = "$done/$total"
                                    }
                                }
                            }
                        }
                        override fun onStatus(msg: String?) {}
                    },
                    { state.cancelled || Thread.currentThread().isInterrupted }
                )
                if (state.cancelled || Thread.currentThread().isInterrupted) return@Thread

                // ③ 起始定格
                ui { state.stepIndex = 2; state.percent = 81 }
                Thread.sleep(120)
                if (state.cancelled || Thread.currentThread().isInterrupted) return@Thread

                // ④ 打包主体
                ui { state.stepIndex = 3; state.percent = 83 }
                val outZip = File(cacheDir, outNameOf(state))
                val packResult = BootPackager.packageFrames(workDir, outZip, w, h, fps,
                    object : BootPackager.Progress {
                        override fun onProgress(done: Int, total: Int) {
                            if (state.cancelled || Thread.currentThread().isInterrupted) return
                            ui {
                                if (!state.cancelled && state.generating) {
                                    state.percent = 83 + (done.toFloat() / total * 14).toInt()
                                    state.stepDetail = "$done/$total"
                                }
                            }
                        }
                        override fun onStatus(msg: String?) {}
                    })
                if (state.cancelled || Thread.currentThread().isInterrupted) return@Thread

                // ⑤ 收尾定格
                ui { state.stepIndex = 4; state.percent = 97 }
                Thread.sleep(120)
                if (state.cancelled || Thread.currentThread().isInterrupted) return@Thread

                // ⑥ 校验 + 保存
                ui { state.stepIndex = 5; state.percent = 99 }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    StoragePublisher.publishModern(this, outZip, outZip.name)
                } else {
                    StoragePublisher.publishLegacy(outZip, outZip.name)
                }

                if (!state.cancelled) {
                    ui {
                        state.percent = 100
                        state.generating = false
                        state.lastZip = outZip
                        state.lastZipBytes = outZip.length()
                        state.warnings = packResult.warnings.toList()
                    }
                }

            } catch (e: InterruptedException) {
                // 用户主动取消，静默退出
            } catch (e: Exception) {
                if (!state.cancelled) {
                    ui {
                        state.generating = false
                        state.percent = 0
                        state.error = e.message ?: "生成失败"
                    }
                }
            } finally {
                // 异步清理临时目录，彻底解除对任何主线程与当前流程的阻塞
                Thread {
                    try { FrameExtractor.deleteRecursive(workDir) } catch (_: Exception) {}
                }.start()
                if (state.cancelled) {
                    ui {
                        state.generating = false
                        state.percent = 0
                        state.stepDetail = ""
                    }
                }
            }
        }
        generateThread = thread
        thread.start()
    }

    private fun ui(block: () -> Unit) = runOnUiThread(block)

    private fun outNameOf(st: AppState): String {
        var n = st.videoName.substringBeforeLast('.', st.videoName)
        n = n.replace(Regex("[^\\w\\u4e00-\\u9fa5\\-]"), "_").trim()
        if (n.isEmpty()) n = "bootanimation"
        if (n.length > 44) n = n.take(44)
        return "$n.zip"
    }
}
