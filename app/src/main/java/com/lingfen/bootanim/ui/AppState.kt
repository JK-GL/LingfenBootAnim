package com.lingfen.bootanim.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.roundToInt

/** 界面全部状态 */
class AppState {
    // 视频
    var videoUri: Uri? by mutableStateOf(null)
    var videoName: String by mutableStateOf("")
    var videoW: Int by mutableStateOf(0)
    var videoH: Int by mutableStateOf(0)
    var videoDuration: Double by mutableStateOf(0.0)

    // 参数
    var canvasIndex: Int by mutableStateOf(0)          // 0=1080p 1=720p
    var targetSeconds: Double by mutableStateOf(0.0)
    var baseFps: Int = 25

    // 估算
    var estimatedBytes: Long by mutableStateOf(0L)
    var estimating: Boolean by mutableStateOf(false)

    // 生成
    var generating: Boolean by mutableStateOf(false)
    var stepIndex: Int by mutableStateOf(0)
    var percent: Int by mutableStateOf(0)
    var stepDetail: String by mutableStateOf("")

    var cancelled: Boolean = false
    var error: String? by mutableStateOf(null)

    // 预览
    var previewZip: java.io.File? by mutableStateOf(null)

    val outW: Int get() = if (canvasIndex == 0) 1920 else 1280
    val outH: Int get() = if (canvasIndex == 0) 1080 else 720

    /** 等效倍速 */
    val speed: Double get() = if (targetSeconds <= 0.0) 1.0 else videoDuration / targetSeconds

    /** 推导帧率（25 × speed，封顶 60） */
    val outFps: Int get() = max(1, minOf(60, (baseFps * speed).roundToInt()))

    /** 主体帧数 */
    val mainFrames: Int get() = (targetSeconds * outFps).roundToInt()

    /** 尾帧数（1 秒定格） */
    val tailFrames: Int get() = outFps

    /** 掉帧比例 */
    val dropRate: Double
        get() {
            if (videoDuration <= 0.0) return 0.0
            val full = videoDuration * baseFps
            return max(0.0, (full - mainFrames) / full)
        }

    val hasVideo: Boolean get() = videoUri != null

    fun reset() {
        targetSeconds = videoDuration
        estimatedBytes = 0L
    }
}
