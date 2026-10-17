package com.lingfen.bootanim.ui

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 视频选择卡
 * 未选：虚线框 + 加号（绝对居中）+ 底部「选择视频」
 * 已选：就地播放预览 + 文件名/分辨率/时长
 */
@Composable
fun VideoCard(st: AppState, onPick: () -> Unit) {
    val ctx = LocalContext.current

    CardBox {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(15.dp))
                .background(C.card2)
                .border(
                    width = 1.5.dp,
                    color = if (st.hasVideo) Color.Transparent else C.stroke2,
                    shape = RoundedCornerShape(15.dp)
                )
                .clickable { if (!st.hasVideo) onPick() }
        ) {
            if (st.hasVideo) {
                val uri = st.videoUri
                if (uri != null) {
                    AndroidView(
                        factory = { c ->
                            VideoView(c).apply {
                                setVideoURI(uri)
                                setOnPreparedListener { mp ->
                                    mp.isLooping = true
                                    mp.setVolume(0f, 0f)
                                    seekTo(50)
                                    start()
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else {
                // 加号：绝对居中
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(C.soft),
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = C.accent, fontSize = 26.sp, fontWeight = FontWeight.Light)
                }
                // 底部提示
                Text(
                    "选择视频",
                    color = C.t2,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }

        if (st.hasVideo) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    st.videoName,
                    color = C.t2,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(3.dp).clip(CircleShape).background(C.stroke2))
                Spacer(Modifier.width(8.dp))
                Text(
                    "${st.videoW}×${st.videoH} · %.1fs".format(st.videoDuration),
                    color = C.t3,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "换一个",
                    color = C.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onPick() }
                )
            }
        }
    }
}
