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

private val ITEM_W = 50.dp

/**
 * 通用横向波轮选择器
 *
 * 帧率、画质、时长共用同一个组件：
 *   帧率：min=25,  max=60,       step=1
 *   画质：min=50,  max=100,      step=1
 *   时长：min=6.0, max=源时长,   step=0.5
 *
 * 初始定位用 rememberLazyListState(initialFirstVisibleItemIndex)，
 * 不依赖事后 scrollToItem（首帧布局未完成时会静默失败，表现为停在最小值）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelPicker(
    value: Double,
    min: Double,
    max: Double,
    step: Double,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    format: (Double) -> String = { v ->
        if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()
    },
) {
    if (max <= min) {
        Box(modifier.fillMaxWidth().height(62.dp), contentAlignment = Alignment.Center) {
            Text("—", color = C.t3, fontSize = 13.sp)
        }
        return
    }

    val count = ((max - min) / step).roundToInt() + 1
    val effValue = value.coerceIn(min, max)
    val targetIndex = ((effValue - min) / step).roundToInt().coerceIn(0, count - 1)

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val haptic = LocalHapticFeedback.current
    val fling = rememberSnapFlingBehavior(lazyListState = listState)

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

    var lastApplied by remember { mutableIntStateOf(targetIndex) }

    LaunchedEffect(centerIndex) {
        if (centerIndex != lastApplied) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            lastApplied = centerIndex
            onChange(min + centerIndex * step)
        }
    }

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
                    val v = min + i * step
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
                            text = format(v),
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
