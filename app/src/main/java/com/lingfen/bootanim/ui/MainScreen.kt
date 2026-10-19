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
    var helpDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val showHelp = { title: String, content: String -> helpDialog = title to content }

    // 当视频/尺寸/时长/画质/帧率改变时，异步试压测体积
    // 快速体积响应：选了波轮 0 毫秒秒变，绝不卡死等待
    LaunchedEffect(st.videoUri, st.canvasIndex, st.targetSeconds, st.quality, st.outFps) {
        val uri = st.videoUri ?: return@LaunchedEffect
        if (st.videoDuration <= 0) return@LaunchedEffect
        val frames = st.mainFrames
        if (frames <= 0) return@LaunchedEffect

        // 先给实时公式估算值（立刻响应）
        val baseKb = if (st.outW == 1920) 165.0 else 75.0
        val qFactor = 1.0 + (st.quality - 85) * 0.035
        val fastTotal = ((frames + 1) * baseKb * qFactor * 1024).toLong()
        st.estimatedBytes = fastTotal
        st.estimating = true

        // 拖拽防抖：停手 220ms 后才做真实试压。
        // 否则画质 51 档、帧率 35 档每滑一格都会触发一次 5 帧解码，CPU 被占满导致界面发涩。
        kotlinx.coroutines.delay(220)

        // 后台异步精准修正（单帧字节数有缓存，只有换视频/画布/画质才会真解码）
        val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                SizeEstimator.estimate(
                    ctx, uri, st.videoDuration,
                    st.outW, st.outH, frames, 1, st.quality
                )
            } catch (_: Exception) { fastTotal }
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

            // ② 制作参数（四合一合并卡片：画布 + 帧率 25~60 + 画质 50~100 + 时长波轮）
            CardBox {
                // 画布
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("画布", "必须等于车机屏幕分辨率，填错会被车机拒绝") { showHelp("画布说明", "画布分辨率必须严格与车机屏幕物理分辨率对齐。\n\n• 填错分辨率车机将无法正常渲染甚至黑屏拒绝；\n• 现已提供宝骏/五菱主流 1920×1080 与 1024×600 等规格，按实际车型选择即可。") }
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

                // 帧率（4 档真实可用值，默认 30；只有能整除 50/60Hz 的值才保留）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("帧率", "车机屏只有 50Hz 和 60Hz 两种，帧率必须整除刷新率才不抖，所以只保留 4 个可用档位。") { showHelp("帧率说明", "播放帧率，只保留能整除车机屏幕刷新率的 4 个档位。\n\n• 25 fps：适配 50Hz 屏，帧数最少、体积最小。\n• 30 fps：适配 60Hz 屏，最推荐的平衡档，比 25 只多 20% 帧数。\n• 50 fps：适配 50Hz 屏的高帧档，画面最顺但体积翻倍。\n• 60 fps：适配 60Hz 屏的最高档，对车机解码能力要求最高，低端车机可能解不动反而更卡。\n\n帧率越高，帧数 = 时长 × 帧率 同比增加，ZIP 体积也同比增加。\n注意：帧率不能无中生有，源片只有 25fps 时输出 60fps 不会更顺，只会更大。") }
                    Text(
                        "${st.outFps} fps · ${st.fpsHint}",
                        color = C.ok,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(8.dp))
                SegmentedControl(
                    options = listOf(
                        "25" to "50Hz",
                        "30" to "60Hz 推荐",
                        "50" to "50Hz 高帧",
                        "60" to "60Hz 高帧",
                    ),
                    selectedIndex = st.fpsIndex,
                    onSelect = { st.fpsIndex = it },
                )

                Spacer(Modifier.height(11.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.stroke))
                Spacer(Modifier.height(11.dp))

                // 画质（50–100 连续调节，默认 85，右侧提示推荐 85–92）
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionTitle("画质", "JPEG 压缩质量（50–100）。推荐 85–92，默认 85；调低可显著缩小体积，调高画面更细腻。") { showHelp("画质说明", "JPEG 压缩质量（50–100）。\n\n• 推荐范围：85–92\n• 默认基准：85\n• 作用：调低可大幅缩小 ZIP 体积，调高画面更细腻。车机对体积敏感时建议设为 75~85。") }
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
                    min = 50.0,
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
                    SectionTitle("时长", "播放时长。全片均匀重采样加速，不剪断视频；缩短时长可大幅降低帧数与文件体积。") { showHelp("时长说明", "播放动画时长设置。\n\n• 默认停留在原视频时长；\n• 向左滑动可加快播放速度并大幅压减帧数与体积；\n• 车机开机窗口约为 6.5 秒，最低下限保护为 6.0 秒，避免开机过早放完定格；\n• 实际落盘时长 = 帧数 ÷ 帧率，可能与你拖到的值有半帧以内误差。") }
                    Text(
                        if (st.hasVideo)
                            "%.1f 秒 · %.0f 帧 · 原 %.1f 秒".format(st.targetSeconds, st.mainFrames.toDouble(), st.videoDuration)
                        else "— 秒 · 原 — 秒",
                        color = C.t2,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(4.dp))

                if (st.hasVideo) {
                    val minSec = st.minSeconds
                    val maxSec = maxOf(minSec, st.videoDuration)
                    WheelPicker(
                        value = st.targetSeconds.coerceIn(minSec, maxSec),
                        min = minSec,
                        max = maxSec,
                        step = 0.5,
                        onChange = { st.targetSeconds = it },
                        format = { if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it) }
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
                        Text("选择视频后开始", color = C.t3, fontSize = 13.sp)
                    }
                }

                // 实时规范预警：帧数 / 体积任一越线当场提示，不等打包完才发现
                if (st.hasVideo) {
                    val overFrames = st.mainFrames > 4096
                    val overBytes = st.estimatedBytes > 128L * 1024 * 1024
                    if (overFrames || overBytes) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            buildString {
                                if (overFrames) append("⚠ ${st.mainFrames} 帧超过车机规范上限 4096")
                                if (overFrames && overBytes) append("；")
                                if (overBytes) append("⚠ 约 %.0f Mb 超过规范上限 128 MiB".format(st.estimatedBytes / 1048576.0))
                                append("，请缩短时长或降低帧率/画质")
                            },
                            color = C.bad,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
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
                InfoRow("帧率", if (st.hasVideo) "${st.outFps} fps · ${st.fpsHint}" else "—")
                InfoRow("主体帧数", if (st.hasVideo) "${st.mainFrames} 帧 · 实际 %.2f 秒".format(st.actualSeconds) else "—")
                val sizeText = when {
                    !st.hasVideo -> "—"
                    st.estimating -> "重新计算中… 约 %.1f Mb".format(st.estimatedBytes / 1048576.0)
                    else -> "约 %.1f Mb".format(st.estimatedBytes / 1048576.0)
                }
                InfoRow(
                    "预计体积",
                    sizeText,
                    valueColor = if (st.estimatedBytes > 128L * 1024 * 1024) C.warn else (if (st.estimating) C.accent else C.t1),
                    bold = true,
                )
                val pathText = if (st.hasVideo) {
                    val base = st.videoName.substringBeforeLast('.').ifEmpty { "bootanimation" }
                    "下载/LingfenBoot/$base.zip"
                } else {
                    "下载/LingfenBoot/"
                }
                InfoRow("保存位置", pathText, C.t2, 13, showDivider = false)

                if (st.lastZip != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(C.ok.copy(alpha = 0.12f))
                            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            Modifier.weight(1f).clickable { onOpenLast() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("✓", color = C.ok, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "已生成 %.1f Mb · %d 帧 · 点击预览".format(
                                    st.lastZipBytes / 1048576.0,
                                    st.mainFrames + 1,
                                ),
                                color = C.ok,
                                fontSize = 12.sp,
                            )
                        }
                        // 右侧靠边关闭按钮
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .clickable { st.lastZip = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = C.ok.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // 打包自检 / 规范预警（128 MiB、4096 帧、desc 校验等）
                    if (st.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        st.warnings.forEach { w ->
                            Text("⚠ $w", color = C.bad, fontSize = 11.sp, lineHeight = 15.sp)
                        }
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
                    enabled = st.hasVideo,
                ) { onGenerate() }
            }
        }

        // 统一说明弹窗
        helpDialog?.let { (title, content) ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { helpDialog = null },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { helpDialog = null }) {
                        androidx.compose.material3.Text("我知道了", color = C.accent, fontWeight = FontWeight.Bold)
                    }
                },
                title = {
                    androidx.compose.material3.Text(title, color = C.t1, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    androidx.compose.material3.Text(content, color = C.t2, fontSize = 14.sp, lineHeight = 21.sp)
                },
                containerColor = C.card,
                shape = RoundedCornerShape(18.dp)
            )
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
        st.videoDuration < 6.0 -> "源视频较短 · 开机末尾保持定格"
        over in -0.55..0.05 -> "开机黄金窗口 (6.0–6.5s)"
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


