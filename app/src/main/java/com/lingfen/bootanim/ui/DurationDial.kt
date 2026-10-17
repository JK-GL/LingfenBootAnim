package com.lingfen.bootanim.ui

import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

private const val STEP = 0.5
private val ITEM_W = 50.dp

/**
 * 横向刻度带（时长选择）
 *
 * 关键实现细节：
 * 1) 用 contentPadding = (视口宽 - 单格宽)/2，让首尾项也能滚到正中
 * 2) 中心项判定基于视口中心，与 scroll 对齐方式一致
 * 3) 跨格触发触觉反馈
 * 4) 两侧模糊：API31+ RenderEffect，低版本降级 alpha
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DurationDial(
    value: Double,
    maxSeconds: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = (maxSeconds / STEP).roundToInt() + 1
    if (count < 2) {
        Box(modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
            Text("—", color = C.t3, fontSize = 13.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // 中心项索引
    val centerIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf 0
            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - vpCenter)
            }?.index ?: 0
        }
    }

    // 跨格震动 + 回传
    var lastHaptic by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centerIndex) {
        if (centerIndex != lastHaptic) {
            if (lastHaptic >= 0) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            lastHaptic = centerIndex
            onChange(centerIndex * STEP)
        }
    }

    // 首次进入 / 换视频：直接定位到目标值（不做动画，避免与布局竞争）
    var initialized by remember(maxSeconds) { mutableStateOf(false) }
    LaunchedEffect(value, maxSeconds, count) {
        val target = (value / STEP).roundToInt().coerceIn(0, count - 1)
        if (!initialized) {
            // 等一帧让 LazyRow 完成测量，否则 scrollToItem 可能无效
            withFrameNanos { }
            listState.scrollToItem(target)
            lastHaptic = target
            initialized = true
        } else if (target != centerIndex) {
            listState.animateScrollToItem(target)
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val sidePad = (maxWidth - ITEM_W) / 2

        Box(Modifier.fillMaxWidth().height(62.dp)) {
            LazyRow(
                state = listState,
                flingBehavior = fling,
                contentPadding = PaddingValues(horizontal = sidePad),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(count) { i ->
                    val v = i * STEP
                    val dist = abs(i - centerIndex)
                    val isCenter = dist == 0
                    val isNear = dist == 1

                    val fontSize = when {
                        isCenter -> 26.sp
                        isNear -> 18.sp
                        else -> 15.sp
                    }
                    val color = when {
                        isCenter -> C.t1
                        isNear -> Color(0xFF8892A6)
                        else -> Color(0xFF4A5468)
                    }
                    val weight = if (isCenter) FontWeight.Bold else FontWeight.Medium
                    val blur = when {
                        isCenter -> 0f
                        isNear -> 1.5f
                        else -> 3f
                    }

                    Column(
                        modifier = Modifier
                            .width(ITEM_W)
                            .fillMaxHeight()
                            .graphicsLayer {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blur > 0f) {
                                    renderEffect = android.graphics.RenderEffect
                                        .createBlurEffect(
                                            blur, blur,
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                        .asComposeRenderEffect()
                                }
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blur > 0f) {
                                    alpha = if (isNear) 0.55f else 0.3f
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (v % 1.0 == 0.0) v.toInt().toString() else v.toString(),
                            color = color,
                            fontSize = fontSize,
                            fontWeight = weight,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(7.dp))
                        Box(
                            Modifier
                                .size(if (isCenter) 5.dp else 3.dp)
                                .background(if (isCenter) C.accent else Color(0xFF3A4255), CircleShape)
                        )
                    }
                }
            }

            // 中心指针线
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(1.dp)
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, C.accent.copy(alpha = 0.5f), Color.Transparent)
                        )
                    )
            )

            // 左右渐隐（遮罩效果）
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(
                        Brush.horizontalGradient(listOf(C.card, Color.Transparent))
                    )
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().background(
                        Brush.horizontalGradient(listOf(Color.Transparent, C.card))
                    )
                )
            }
        }
    }
}
