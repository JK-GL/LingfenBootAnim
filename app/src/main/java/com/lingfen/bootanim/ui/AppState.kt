package com.lingfen.bootanim.ui

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.max
import kotlin.math.roundToInt

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
    var quality: Int by mutableStateOf(85)             // 70~100 连续画质波轮，默认 85（推荐 85–92）

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

    // 生成结果（不自动弹预览，只记录）
    var lastZip: java.io.File? by mutableStateOf(null)
    var lastZipBytes: Long by mutableStateOf(0L)
    var usedQuality: Int by mutableStateOf(85)

    // 预览
    var previewZip: java.io.File? by mutableStateOf(null)

    val outW: Int get() = if (canvasIndex == 0) 1920 else 1280
    val outH: Int get() = if (canvasIndex == 0) 1080 else 720

    /** 等效倍速 */
    val speed: Double get() = if (targetSeconds <= 0.0) 1.0 else videoDuration / targetSeconds

    /** 严格遵循规范：帧率恒定为 25 fps 标准 */
    val outFps: Int get() = 25

    /** 主体帧数 = 目标播放时长 × 25 fps（时长缩短，帧数减少，体积真减！） */
    val mainFrames: Int get() = max(1, (targetSeconds * outFps).roundToInt())

    /** 尾帧数（1 秒定格 = 25 帧） */
    val tailFrames: Int get() = outFps

    /** 最低合规时长：车机开机窗口 6.0~6.5 秒 */
    val minSeconds: Double get() = if (videoDuration >= 6.0) 6.0 else videoDuration

    val hasVideo: Boolean get() = videoUri != null

    fun reset() {
        targetSeconds = if (videoDuration >= 6.0) 6.0 else videoDuration
        quality = 85
        estimatedBytes = 0L
    }
}
