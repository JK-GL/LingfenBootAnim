package com.lingfen.bootanim.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingfen.bootanim.core.SizeEstimator
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    st: AppState,
    onPickVideo: () -> Unit,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    onOpenLast: () -> Unit,
    onPickZip: () -> Unit,
) {
    val scroll = rememberScrollState()
    val ctx = LocalContext.current

    // 当视频/尺寸/时长/画质改变时，异步试压测体积
    LaunchedEffect(st.videoUri, st.canvasIndex, st.targetSeconds, st.quality) {
        val uri = st.videoUri ?: return@LaunchedEffect
        if (st.videoDuration <= 0) return@LaunchedEffect
        st.estimating = true
        val frames = st.mainFrames
        if (frames <= 0) { st.estimating = false; return@LaunchedEffect }
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            SizeEstimator.estimate(
                ctx, uri, st.videoDuration,
                st.outW, st.outH, frames, st.tailFrames, st.quality
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
                "菱粉桌面开机动画制作",
                color = C.t1,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Spacer(Modifier.height(14.dp))

            // ① 选视频
            VideoCard(st = st, onPick = onPickVideo)
            Spacer(Modifier.height(12.dp))

            // ② 制作参数（三合一合并卡片：画布 + 画质 70~100 + 时长波轮）
            CardBox {
                // 画布
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("画布", "必须等于车机屏幕分辨率，填错会被车机拒绝") {}
                }
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf("1920×1080" to "1080p 车机", "1280×720" to "720p 车机"),
                    selectedIndex = st.canvasIndex,
                    onSelect = { st.canvasIndex = it },
                )

                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.stroke))
                Spacer(Modifier.height(11.dp))

                // 画质（70–100 连续调节，默认 85，右侧提示推荐 85–92）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("画质", "JPEG 压缩质量（70–100）。推荐 85–92，默认 85；调低可显著缩小体积，调高画面更细腻。") {}
                    Text(
                        "JPEG 质量 ${st.quality} · 推荐 85–92",
                        color = C.t2,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))
                WheelPicker(
                    value = st.quality.toDouble(),
                    min = 70.0,
                    max = 100.0,
                    step = 1.0,
                    onChange = { st.quality = it.roundToInt() },
                    format = { it.toInt().toString() }
                )

                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.stroke))
                Spacer(Modifier.height(11.dp))

                // 时长
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("时长", "播放时长。固定 25fps 标准；全片均匀重采样加速，不剪断视频，缩短时长可大幅降低文件体积。") {}
                    Text(
                        if (st.hasVideo)
                            "%.1f 秒 · 原 %.1f 秒".format(st.targetSeconds, st.videoDuration)
                        else "— 秒 · 原 — 秒",
                        color = C.t2,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))

                if (st.hasVideo) {
                    WheelPicker(
                        value = st.targetSeconds,
                        min = 1.0,
                        max = maxOf(1.0, st.videoDuration),
                        step = 0.5,
                        onChange = { st.targetSeconds = it },
                        format = { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                        Text("选择视频后开始", color = C.t3, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
                SpeedBar(st)
            }
            Spacer(Modifier.height(12.dp))

            // ③ 结果预览
            CardBox {
                SectionTitle("结果预览") {}
                Spacer(Modifier.height(6.dp))
                InfoRow("画布", "${st.outW} × ${st.outH}")
                InfoRow("帧率", if (st.hasVideo) "${st.outFps} fps (标准)" else "—")
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
                InfoRow("保存路径", "下载/LingfenBoot/${outName(st)}", C.t2, 12, showDivider = false)

                if (st.lastZip != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.ok.copy(alpha = 0.12f))
                            .clickable { onOpenLast() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✓", color = C.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "已生成 %.1f MB · %d 帧 · 点击预览".format(
                                st.lastZipBytes / 1048576.0,
                                st.mainFrames + 1 + st.tailFrames,
                            ),
                            color = C.ok,
                            fontSize = 12.sp,
                        )
                    }
                }

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

@Composable
fun SpeedBar(st: AppState) {
    val speed = st.speed
    val over = st.targetSeconds - 6.5

    val bg = C.soft
    val bd = Color(0x334D8DFF)

    val stateText = when {
        !st.hasVideo -> "选择视频后开始"
        over > 0.05 -> "超出开机窗口 %.1fs (正常播放)".format(over)
        else -> "开机窗口内 · 完美播放"
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, bd, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (st.hasVideo) "%.2f×".format(speed) else "1.00×",
            color = C.accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        Text(
            stateText,
            color = if (over > 0.05) C.t2 else C.ok,
            fontSize = 11.5.sp,
        )
    }
}

private fun outName(st: AppState): String {
    val base = st.videoName.substringBeforeLast('.')
    val sec = if (st.targetSeconds <= 0.0) "6.0" else "%.1f".format(st.targetSeconds)
    val res = if (st.canvasIndex == 0) "1080p" else "720p"
    return "${base}_${res}_${sec}s_bootanimation.zip"
}
