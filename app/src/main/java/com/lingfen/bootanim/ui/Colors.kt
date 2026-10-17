package com.lingfen.bootanim.ui

import androidx.compose.ui.graphics.Color

/** 与 HTML 原型 :root 完全一致的配色 */
object C {
    val bg      = Color(0xFF080A0F)
    val card    = Color(0xFF141821)
    val card2   = Color(0xFF1B202B)
    val stroke  = Color(0xFF232A38)
    val stroke2 = Color(0xFF2C3446)

    val accent  = Color(0xFF4D8DFF)
    val soft    = Color(0x214D8DFF)   // rgba(77,141,255,.13)

    val t1      = Color(0xFFF1F5FC)
    val t2      = Color(0xFFA4AEC0)
    val t3      = Color(0xFF687384)

    val ok      = Color(0xFF33D69F)
    val warn    = Color(0xFFFFB020)
    val bad     = Color(0xFFFF5A5F)

    // 悬浮进度卡（不透明，避免内容穿透）
    val float   = Color(0xFF181D27)
    // 取消按钮深红
    val dangerBg     = Color(0xD9781920)   // rgba(120,25,32,.85) → alpha 0xD9
    val dangerBorder = Color(0xFF7A1F26)
    val dangerText   = Color(0xFFFF9AA0)
}
