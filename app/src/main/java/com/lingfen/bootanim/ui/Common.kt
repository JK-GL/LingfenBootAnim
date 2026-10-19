package com.lingfen.bootanim.ui

import androidx.compose.runtime.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 卡片容器 */
@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.card)
            .border(1.dp, C.stroke, RoundedCornerShape(18.dp))
            .padding(16.dp),
        content = content
    )
}

/** 小标题 */
@Composable
fun SectionTitle(text: String, hint: String? = null, onHint: () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text.uppercase(),
            color = C.t3,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
        )
        if (hint != null) {
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null
                    ) { onHint() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(17.dp)
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, C.stroke2, RoundedCornerShape(50))
                        .background(C.card2),
                    contentAlignment = Alignment.Center
                ) {
                    Text("?", color = C.t2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** 信息行 */
@Composable
fun InfoRow(
    key: String,
    value: String,
    valueColor: Color = C.t1,
    valueSize: Int = 14,
    bold: Boolean = false,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(key, color = C.t3, fontSize = 14.sp)
            Spacer(Modifier.weight(1f))
            Text(
                text = value,
                color = valueColor,
                fontSize = valueSize.sp,
                fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
        if (showDivider) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.045f))
            )
        }
    }
}

/** 主按钮 */
@Composable
fun PrimaryButton(
    text: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (enabled) C.accent else C.card2)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else C.t3,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 深红取消按钮 */
@Composable
fun DangerButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(C.dangerBg)
            .border(1.dp, C.dangerBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = C.dangerText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 分段控件（画布尺寸） */
@Composable
fun SegmentedControl(
    options: List<Pair<String, String>>,   // label, subLabel
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(C.card2)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, (label, sub) ->
            val on = i == selectedIndex
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) C.accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    color = if (on) Color.White else C.t2,
                    fontSize = 14.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                )
                Text(
                    sub,
                    color = if (on) Color.White.copy(alpha = 0.72f) else C.t3,
                    fontSize = 9.5.sp,
                )
            }
        }
    }
}
