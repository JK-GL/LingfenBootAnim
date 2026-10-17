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
 * 初始定位靠 rememberLazyListState(initialFirstVisibleItemIndex) 直接落位，
 * 不依赖事后 scrollToItem —— 后者在首帧布局未完成时会静默失败，表现为停在 0。
 *
 * 用户滚动 → onChange 回传；外部值变化 → 主动滚动。
 * 用 lastApplied 区分两者，避免滚动回弹打架。
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

    // 目标值兜底：有视频但值为 0 时，默认落在源时长
    val effValue = if (value <= 0.0) maxSeconds else value.coerceIn(0.0, maxSeconds)
    val targetIndex = (effValue / STEP).roundToInt().coerceIn(0, count - 1)

    // 首次组合直接定位到目标项（最可靠）
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val haptic = LocalHapticFeedback.current
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // 中心项
    val centerIndex by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf targetIndex
            val vpCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - vpCenter)
            }?.index ?: targetIndex
        }
    }

    // 记录「已经对齐到哪个索引」，用于区分用户滚动与外部变更
    var lastApplied by remember { mutableIntStateOf(targetIndex) }

    // 用户滚动 → 震动 + 回传
    LaunchedEffect(centerIndex) {
        if (centerIndex != lastApplied) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastApplied = centerIndex          // 这是用户滚出来的，不要再滚回去
            onChange(centerIndex * STEP)
        }
    }

    // 外部值变化（换视频 / 代码设定）→ 主动滚动
    LaunchedEffect(targetIndex, count) {
        if (targetIndex != lastApplied) {
            listState.scrollToItem(targetIndex)
            lastApplied = targetIndex
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

            // 中心指针
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

            // 两侧渐隐
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
