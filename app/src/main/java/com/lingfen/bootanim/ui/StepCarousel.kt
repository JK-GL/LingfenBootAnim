package com.lingfen.bootanim.ui

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 六个生成步骤 */
val STEP_NAMES = listOf("分析视频", "抽取帧", "起始定格", "打包主体", "收尾定格", "校验")

/**
 * 三段轮播步骤卡
 * 上一步模糊淡出 / 当前步高亮 / 下一步模糊淡入
 * 底部：左上 1/6，右上百分比，下方进度条
 */
@Composable
fun StepCarousel(
    stepIndex: Int,          // 0..5
    percent: Int,            // 0..100
    detail: String,          // 当前步右侧的小字（如 "38s"）
    modifier: Modifier = Modifier,
) {
    val total = STEP_NAMES.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(C.float)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        // 三段
        StepRow(
            text = STEP_NAMES.getOrNull(stepIndex - 1),
            state = RowState.PREV
        )
        StepRow(
            text = STEP_NAMES.getOrNull(stepIndex),
            state = RowState.NOW,
            detail = detail
        )
        StepRow(
            text = STEP_NAMES.getOrNull(stepIndex + 1),
            state = RowState.NEXT
        )

        Spacer(Modifier.height(10.dp))

        // 计数 / 百分比
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stepIndex + 1}/$total", color = C.t2, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("$percent%", color = C.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(6.dp))

        // 进度条
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF242B39))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent / 100f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF4D8DFF), Color(0xFF6FA8FF)))
                    )
            )
        }
    }
}

private enum class RowState { PREV, NOW, NEXT }

/** 步骤状态图标：空心圈 / 转圈 / 绿勾 / 红叉 */
@Composable
private fun StepIcon(state: RowState, done: Boolean = false, err: Boolean = false) {
    val color = when {
        err -> C.bad
        done -> C.ok
        state == RowState.NOW -> C.accent
        else -> C.t3
    }
    val infinite = rememberInfiniteTransition(label = "spin")
    val angle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(750, easing = LinearEasing)),
        label = "angle"
    )

    Canvas(Modifier.fillMaxSize()) {
        val r = size.minDimension / 2f - 1.5f
        val c = center
        when {
            done -> {
                drawCircle(color, r + 1.5f)
                // 勾
                val p = Path().apply {
                    moveTo(c.x - r * 0.45f, c.y + r * 0.02f)
                    lineTo(c.x - r * 0.1f, c.y + r * 0.38f)
                    lineTo(c.x + r * 0.48f, c.y - r * 0.36f)
                }
                drawPath(p, Color(0xFF0B0E14), style = Stroke(1.8f, cap = StrokeCap.Round))
            }
            err -> {
                drawCircle(color, r + 1.5f)
                val d = r * 0.42f
                drawLine(Color.White, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), 1.8f, StrokeCap.Round)
                drawLine(Color.White, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), 1.8f, StrokeCap.Round)
            }
            state == RowState.NOW -> {
                // 转圈：留一个缺口
                drawArc(
                    color = color,
                    startAngle = angle,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(1.6f, cap = StrokeCap.Round)
                )
            }
            else -> {
                drawCircle(color, r, style = Stroke(1.5f))
            }
        }
    }
}


@Composable
private fun StepRow(text: String?, state: RowState, detail: String = "") {
    val targetAlpha = when (state) {
        RowState.PREV -> 0.45f
        RowState.NOW -> 1f
        RowState.NEXT -> 0.32f
    }
    val targetScale = when (state) {
        RowState.PREV -> 0.94f
        RowState.NOW -> 1f
        RowState.NEXT -> 0.92f
    }
    val targetBlur = when (state) {
        RowState.PREV -> 1.1f
        RowState.NOW -> 0f
        RowState.NEXT -> 1.7f
    }

    val alpha by animateFloatAsState(targetAlpha, tween(280), label = "a")
    val scale by animateFloatAsState(targetScale, tween(280), label = "s")

    val height = if (state == RowState.NOW) 30.dp else 25.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetBlur > 0f) {
                    renderEffect = android.graphics.RenderEffect
                        .createBlurEffect(targetBlur, targetBlur, android.graphics.Shader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 状态圆点：转圈 / 打勾 / 叉 / 空心
        Box(Modifier.size(15.dp)) {
            StepIcon(state = state)
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = text ?: "",
            color = if (state == RowState.NOW) C.t1 else C.t2,
            fontSize = if (state == RowState.NOW) 15.5.sp else 13.5.sp,
            fontWeight = if (state == RowState.NOW) FontWeight.Bold else FontWeight.Normal,
        )

        Spacer(Modifier.weight(1f))

        if (detail.isNotEmpty() && state == RowState.NOW) {
            Text(detail, color = C.accent, fontSize = 12.5.sp)
        }
    }
}
