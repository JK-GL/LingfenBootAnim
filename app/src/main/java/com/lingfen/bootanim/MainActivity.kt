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
                    onCancel = { state.cancelled = true },
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
            // 规范：车机开机窗口 6.0~6.5 秒，默认停在黄金时长 6.0 秒（若不足 6 秒则为原片时长）
            state.targetSeconds = if (state.videoDuration >= 6.0) 6.0 else state.videoDuration
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

    // ── 生成流程 ──────────────────────────────
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

        Thread {
            val workDir = File(cacheDir, "work_${System.currentTimeMillis()}")
            workDir.mkdirs()
            try {
                // ① 分析视频
                ui { state.stepIndex = 0; state.percent = 3; state.stepDetail = "%.1fs".format(state.videoDuration) }
                Thread.sleep(180)
                if (state.cancelled) return@Thread

                // ② 抽取帧：使用用户选定的真实画质（70–100，默认 85）
                val quality = state.quality
                state.usedQuality = quality
                ui { state.stepIndex = 1; state.percent = 5; state.stepDetail = "抽帧 (q$quality)" }

                FrameExtractor.extract(this, uri, workDir, w, h, fps, target, quality,
                    object : FrameExtractor.Progress {
                        override fun onProgress(done: Int, total: Int) {
                            if (state.cancelled) return
                            ui {
                                state.percent = 5 + (done.toFloat() / total * 75).toInt()
                                state.stepDetail = "$done/$total"
                            }
                        }
                        override fun onStatus(msg: String?) {}
                    })
                if (state.cancelled) return@Thread

                // ③ 起始定格
                ui { state.stepIndex = 2; state.percent = 81 }
                Thread.sleep(120)

                // ④ 打包主体
                ui { state.stepIndex = 3; state.percent = 83 }
                val outZip = File(cacheDir, outNameOf(state))
                BootPackager.packageFrames(workDir, outZip, w, h, fps,
                    object : BootPackager.Progress {
                        override fun onProgress(done: Int, total: Int) {
                            if (state.cancelled) return
                            ui {
                                state.percent = 83 + (done.toFloat() / total * 14).toInt()
                                state.stepDetail = "$done/$total"
                            }
                        }
                        override fun onStatus(msg: String?) {}
                    })
                if (state.cancelled) return@Thread

                // ⑤ 收尾定格
                ui { state.stepIndex = 4; state.percent = 97 }
                Thread.sleep(120)

                // ⑥ 校验 + 保存
                ui { state.stepIndex = 5; state.percent = 99 }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    StoragePublisher.publishModern(this, outZip, outZip.name)
                } else {
                    StoragePublisher.publishLegacy(outZip, outZip.name)
                }

                ui {
                    state.percent = 100
                    state.generating = false
                    state.lastZip = outZip
                    state.lastZipBytes = outZip.length()
                    // 不自动打开预览，由用户在结果卡点击
                }

            } catch (e: Exception) {
                ui {
                    state.generating = false
                    state.percent = 0
                    state.error = e.message ?: "生成失败"
                }
            } finally {
                FrameExtractor.deleteRecursive(workDir)
            }
        }.start()
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
