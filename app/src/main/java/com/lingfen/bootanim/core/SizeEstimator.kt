package com.lingfen.bootanim.core

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * 体积试压：真实抽帧测量单帧 JPEG 字节数
 *
 * 之前在原型里用固定系数估算，误差高达 4 倍（640×360 源实测 47KB/帧，
 * 而按 200KB/帧估算是 54.6MB vs 实际 12.9MB）。这里改成真测。
 */
object SizeEstimator {

    /**
     * @return 估算总字节数（含 part0 / part2）
     */
    fun estimate(
        ctx: Context,
        uri: Uri,
        durationSec: Double,
        outW: Int,
        outH: Int,
        frames: Int,
        tailFrames: Int,
        quality: Int = 88,
    ): Long {
        val perFrame = sampleFrameBytes(ctx, uri, durationSec, outW, outH, quality)
        val total = perFrame * (frames + 1 + tailFrames)
        return total
    }

    /** 抽 5 帧取平均（含缩放 + JPEG 压缩，与真实打包一致） */
    private fun sampleFrameBytes(
        ctx: Context,
        uri: Uri,
        durationSec: Double,
        outW: Int,
        outH: Int,
        quality: Int,
    ): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(ctx, uri)
            val durMs = (durationSec * 1000).toLong()
            val points = doubleArrayOf(0.08, 0.28, 0.5, 0.72, 0.92)
            val sizes = ArrayList<Long>(points.size)

            for (p in points) {
                val tUs = (durMs * p * 1000).toLong().coerceAtLeast(0)
                val bmp = mmr.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: continue
                val scaled = scaleToCanvas(bmp, outW, outH)
                if (scaled !== bmp) bmp.recycle()

                val bos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
                sizes.add(bos.size().toLong())
                scaled.recycle()
            }

            if (sizes.isEmpty()) fallbackBytes(outW, outH)
            else sizes.sum() / sizes.size
        } catch (e: Exception) {
            fallbackBytes(outW, outH)
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    /** 等比缩放 + 黑底填充（与 FrameExtractor 逻辑一致） */
    private fun scaleToCanvas(src: Bitmap, w: Int, h: Int): Bitmap {
        if (src.width == w && src.height == h) return src
        val scale = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val dw = max(1, (src.width * scale).toInt())
        val dh = max(1, (src.height * scale).toInt())
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(
            src,
            null,
            android.graphics.Rect((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }

    /** 兜底：无法解码时按分辨率给保守值 */
    private fun fallbackBytes(w: Int, h: Int): Long {
        val px = w.toLong() * h
        val base = 160L * 1024 * px / (1920L * 1080)
        return base
    }
}
