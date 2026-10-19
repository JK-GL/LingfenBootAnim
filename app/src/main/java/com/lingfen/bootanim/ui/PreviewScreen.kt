package com.lingfen.bootanim.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingfen.bootanim.core.ZipPlayer
import java.io.File

/**
 * ZIP 播放预览（全屏）
 * 严格按 desc.txt 时序播放，模拟车机开机动画
 */
@Composable
fun PreviewScreen(
    zipFile: File,
    onClose: () -> Unit,
) {
    var player by remember { mutableStateOf<ZipPlayer?>(null) }
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var elapsed by remember { mutableStateOf(0.0) }
    var total by remember { mutableStateOf(0.0) }
    var ended by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf("") }

    DisposableEffect(zipFile) {
        val p = ZipPlayer(zipFile)
        val ok = try { p.load() } catch (e: Exception) { false }
        if (!ok) {
            error = "解析失败：ZIP 内找不到有效的 desc.txt"
        } else {
            // 一加载完成立即赋总秒数，无需等到点击播放才显示
            total = p.totalSeconds
            elapsed = 0.0
            info = buildString {
                append("总计 %.2fs".format(p.totalSeconds))
                append(" · ${p.width}×${p.height}")
                append(" · ${p.fps}fps")
                append(" · %.2f Mb".format(zipFile.length() / 1048576.0))
            }
            p.onFrame = { frame = it }
            p.onProgress = { e, t -> elapsed = e; total = t }
            p.onEnd = { ended = true }
            p.prefetchFirst { frame = it }
        }
        player = p
        onDispose { p.release() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.13f))
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("✕", color = Color.White, fontSize = 16.sp)
                }
                Spacer(Modifier.width(12.dp))
                Text("开机动画预览", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(C.ok)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text("按 desc.txt 时序", color = Color(0xFF0B0E14), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                when {
                    error != null -> Text(error!!, color = C.bad, fontSize = 13.sp)
                    frame != null -> Image(
                        bitmap = frame!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        contentScale = ContentScale.Fit,
                    )
                    else -> Text("加载中…", color = C.t3, fontSize = 13.sp)
                }
            }

            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(C.accent)
                            .clickable {
                                val p = player ?: return@clickable
                                if (ended) { ended = false; p.restart() }
                                p.play()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (ended) "重播" else "播放",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF242B39))
                    ) {
                        val frac = if (total > 0) (elapsed / total).toFloat().coerceIn(0f, 1f) else 0f
                        Box(
                            Modifier
                                .fillMaxWidth(frac)
                                .fillMaxHeight()
                                .background(C.accent)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "%.1f / %.1fs".format(elapsed, total),
                        color = Color(0xFF8A94A6),
                        fontSize = 11.sp,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(info, color = Color(0xFF5F6878), fontSize = 10.5.sp, textAlign = TextAlign.Start)
            }
        }
    }
}
