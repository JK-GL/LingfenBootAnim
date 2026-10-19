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
    var quality: Int by mutableStateOf(85)             // 50~100 连续画质波轮，默认 85（推荐 85–92）
    var outFps: Int by mutableStateOf(25)              // 25~60 连续帧率波轮，默认 25

    // 估算
    var estimatedBytes: Long by mutableStateOf(0L)
    var estimating: Boolean by mutableStateOf(false)

    // 生成
    var generating: Boolean by mutableStateOf(false)
    var stepIndex: Int by mutableStateOf(0)
    var percent: Int by mutableStateOf(0)
    var stepDetail: String by mutableStateOf("")

    @Volatile var cancelled: Boolean = false
    var error: String? by mutableStateOf(null)

    // 生成结果（不自动弹预览，只记录）
    var lastZip: java.io.File? by mutableStateOf(null)
    var lastZipBytes: Long by mutableStateOf(0L)
    var usedQuality: Int by mutableStateOf(85)

    /** 打包自检/规范预警（128 MiB、4096 帧、desc 校验等），生成后展示给用户 */
    var warnings: List<String> by mutableStateOf(emptyList())

    // 预览
    var previewZip: java.io.File? by mutableStateOf(null)

    val outW: Int get() = if (canvasIndex == 0) 1920 else 1280
    val outH: Int get() = if (canvasIndex == 0) 1080 else 720

    /** 等效倍速 */
    val speed: Double get() = if (targetSeconds <= 0.0) 1.0 else videoDuration / targetSeconds

    /** 帧率 25~60 可选；决定帧间隔，需与车机屏幕刷新率整除才不抖 */
    val mainFrames: Int get() = max(1, (targetSeconds * outFps).roundToInt())

    /** 车机备用末帧：打包时只写 1 帧，用于播完防黑屏 */
    val tailFrames: Int get() = 1

    /** 真实落盘时长 = 帧数 ÷ 帧率（与波轮目标值可能有 ±半帧误差，界面以此为准） */
    val actualSeconds: Double get() = mainFrames / outFps.toDouble()

    /** 帧率与常见车机刷新率的整除关系提示 */
    val fpsHint: String get() = when {
        outFps <= 0 -> ""
        60 % outFps == 0 && 50 % outFps == 0 -> "60Hz/50Hz 均整除"
        60 % outFps == 0 -> "适配 60Hz 屏"
        50 % outFps == 0 -> "适配 50Hz 屏"
        else -> "与 60/50Hz 不整除，可能轻微抖动"
    }

    /** 最低合规时长：车机开机窗口 6.0~6.5 秒 */
    val minSeconds: Double get() = if (videoDuration >= 6.0) 6.0 else videoDuration

    val hasVideo: Boolean get() = videoUri != null

    fun reset() {
        targetSeconds = videoDuration
        quality = 85
        estimatedBytes = 0L
    }
}
