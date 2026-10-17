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
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 横向刻度带（时长选择）
 *
 * 交互与视觉对齐 HTML 原型：
 *  - 每格 0.5 秒
 *  - 中心项放大高亮，相邻项中等，远处最小
 *  - 左右边缘渐隐（原型用 mask-image，这里用 graphicsLayer + 遮罩）
 *  - 每跨一格触发一次触觉反馈
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DurationDial(
    value: Double,
    maxSeconds: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val step = 0.5
    val itemWidth = 50.dp
    val count = (maxSeconds / step).roundToInt() + 1
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    val haptic = LocalHapticFeedback.current
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

    // 当前中心项索引
    val centerIndex by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull {
                abs((it.offset + it.size / 2) - viewportCenter)
            }?.index ?: 0
        }
    }

    // 跨格震动
    var lastHapticIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(centerIndex) {
        if (centerIndex != lastHapticIndex) {
            if (lastHapticIndex >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastHapticIndex = centerIndex
            onChange(centerIndex * step)
        }
    }

    // 外部值变化时滚动到对应位置
    LaunchedEffect(value, maxSeconds) {
        val target = (value / step).roundToInt()
        if (target != centerIndex && target in 0 until count) {
            listState.animateScrollToItem(target)
        }
    }

    Box(modifier = modifier) {
        LazyRow(
            state = listState,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = 0.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
        ) {
            items(count) { i ->
                val v = i * step
                val dist = abs(i - centerIndex)
                val isCenter = dist == 0
                val isNear = dist == 1

                // 尺寸与颜色（与原型一致：26/18/15 sp）
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

                // 远处模糊（API31+ 真模糊，低版本用 alpha 降级）
                val blurRadius = when {
                    isCenter -> 0f
                    isNear -> 1.5f
                    else -> 3f
                }

                Column(
                    modifier = Modifier
                        .width(itemWidth)
                        .fillMaxHeight()
                        .graphicsLayer {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0f) {
                                renderEffect = RenderEffect
                                    .createBlurEffect(blurRadius, blurRadius, Shader.TileMode.CLAMP)
                                    .asComposeRenderEffect()
                            }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurRadius > 0f) {
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
                        modifier = Modifier
                            .size(if (isCenter) 5.dp else 3.dp)
                            .background(
                                if (isCenter) C.accent else Color(0xFF3A4255),
                                CircleShape
                            )
                    )
                }
            }
        }

        // 中心指针线
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, C.accent.copy(alpha = 0.5f), Color.Transparent)
                    )
                )
                .alpha(0.5f)
        )

        // 左右渐隐遮罩（模拟 CSS mask-image）
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(C.card, Color.Transparent)
                        )
                    )
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, C.card)
                        )
                    )
            )
        }
    }
}
