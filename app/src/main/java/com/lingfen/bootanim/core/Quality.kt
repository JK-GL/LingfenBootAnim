package com.lingfen.bootanim.core

/**
 * JPEG 质量决策：按 q88 预估体积分档，保证不会产出超大包。
 *
 * 规范推荐 85–92；体积过大时才降到 85 以下，并在界面显示实际使用的质量。
 */
object Quality {

    private const val Mb = 1024L * 1024L

    /** 根据 q88 预估体积选择质量 */
    fun pick(est88: Long): Int = when {
        est88 <= 30 * Mb -> 88
        est88 <= 60 * Mb -> 84
        est88 <= 100 * Mb -> 78
        else -> 72
    }

    /** 相对 q88 的体积系数（用于界面预估校正） */
    fun factor(q: Int): Double = when {
        q >= 88 -> 1.0
        q >= 84 -> 0.78
        q >= 78 -> 0.58
        else -> 0.45
    }

    /** 按目标质量校正预估体积 */
    fun adjust(est88: Long, q: Int): Long = (est88 * factor(q)).toLong()
}
