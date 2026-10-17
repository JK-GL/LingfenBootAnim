package com.lingfen.bootanim.ui

import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingfen.bootanim.core.SizeEstimator
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    st: AppState,
    onPickVideo: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onPickZip: () -> Unit,
) {
    val ctx = LocalContext.current
    val scroll = rememberScrollState()

    // 选视频后异步试压
    LaunchedEffect(st.videoUri, st.canvasIndex) {
        val uri = st.videoUri ?: return@LaunchedEffect
        if (st.videoDuration <= 0) return@LaunchedEffect
        st.estimating = true
        val frames = st.mainFrames
        if (frames <= 0) { st.estimating = false; return@LaunchedEffect }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SizeEstimator.estimate(
                ctx, uri, st.videoDuration,
                st.outW, st.outH, frames, st.tailFrames
            )
        }
        st.estimatedBytes = bytes
        st.estimating = false
    }

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = if (st.generating) 260.dp else 100.dp)
        ) {
            Text(
                "菱粉开机动画",
                color = C.t1,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(14.dp))

            // ① 选视频
            VideoCard(st = st, onPick = onPickVideo)
            Spacer(Modifier.height(12.dp))

            // ② 画布
            CardBox {
                SectionTitle("画布尺寸", "必须等于车机屏幕分辨率，否则上传会被拒绝") {
                    // 提示走 Toast，简化处理
                }
                Spacer(Modifier.height(12.dp))
                SegmentedControl(
                    options = listOf("1920×1080" to "1080p 车机", "1280×720" to "720p 车机"),
                    selectedIndex = st.canvasIndex,
                    onSelect = { st.canvasIndex = it },
                )
            }
            Spacer(Modifier.height(12.dp))

            // ③ 时长
            CardBox {
                SectionTitle("输出时长", "拖动决定动画播放多长，帧率自动换算") {}
                Spacer(Modifier.height(6.dp))

                if (st.hasVideo) {
                    DurationDial(
                        value = st.targetSeconds,
                        maxSeconds = st.videoDuration,
                        onChange = { st.targetSeconds = it },
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                        Text("选择视频后开始", color = C.t3, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 时长 / 原时长
                Text(
                    if (st.hasVideo)
                        "%.1f 秒 · 原 %.1f 秒".format(st.targetSeconds, st.videoDuration)
                    else "— 秒 · 原 — 秒",
                    color = C.t3,
                    fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                SpeedBar(st)
            }
            Spacer(Modifier.height(12.dp))

            // ④ 结果预览
            CardBox {
                SectionTitle("结果预览") {}
                Spacer(Modifier.height(6.dp))
                InfoRow("输出画布", "${st.outW} × ${st.outH}")
                InfoRow("帧率", if (st.hasVideo) "${st.outFps} fps" else "—")
                InfoRow("主体帧数", if (st.hasVideo) "${st.mainFrames} 帧" else "—")
                InfoRow(
                    "预计体积",
                    when {
                        !st.hasVideo -> "—"
                        st.estimating -> "测量中…"
                        else -> "约 %.1f MB".format(st.estimatedBytes / 1048576.0)
                    },
                    valueColor = if (st.estimatedBytes > 128L * 1024 * 1024) C.warn else C.t1,
                    bold = true,
                )
                InfoRow("输出", "下载/LingfenBoot/${outName(st)}", C.t2, 12, showDivider = false)

                Spacer(Modifier.height(14.dp))

                // 选择 ZIP 预览
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(C.accent.copy(alpha = 0.08f))
                        .border(1.dp, C.stroke2, RoundedCornerShape(12.dp))
                        .clickable { onPickZip() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "选择 ZIP 预览车机效果",
                        color = C.accent,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // 底部按钮 / 悬浮进度卡
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
        ) {
            if (st.generating) {
                StepCarousel(
                    stepIndex = st.stepIndex,
                    percent = st.percent,
                    detail = st.stepDetail,
                )
                Spacer(Modifier.height(10.dp))
                DangerButton("取消生成") { onCancel() }
            } else {
                PrimaryButton(
                    "生成 bootanimation.zip",
                    enabled = st.hasVideo && !st.estimating,
                ) { onGenerate() }
            }
        }
    }
}

fun outName(st: AppState): String {
    var n = st.videoName.substringBeforeLast('.', st.videoName)
    n = n.replace(Regex("[^\\w\\u4e00-\\u9fa5\\-]"), "_").trim()
    if (n.isEmpty()) n = "bootanimation"
    if (n.length > 44) n = n.take(44)
    return "$n.zip"
}

/** 速度状态条 */
@Composable
fun SpeedBar(st: AppState) {
    val speed = st.speed
    val drop = st.dropRate
    val over = st.targetSeconds - 6.5

    // 只有严重掉帧（>=5%）才黄
    val warn = drop >= 0.05 && speed > 2.4
    val bg = if (warn) Color(0x1CFFB020) else C.soft
    val bd = if (warn) Color(0x47FFB020) else Color(0x334D8DFF)

    val stateText = when {
        !st.hasVideo -> "选择视频后开始"
        warn -> "封顶 60fps · 掉帧 ${(drop * 100).roundToInt()}%"
        over > 0 -> "超窗 %.1fs".format(over)
        else -> "无掉帧"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (st.hasVideo) "%.2f×".format(speed) else "—",
            color = if (warn) C.warn else C.accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stateText,
            color = if (warn) C.warn else C.ok,
            fontSize = 11.5.sp,
        )
    }
}
