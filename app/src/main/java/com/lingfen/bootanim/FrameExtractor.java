package com.lingfen.bootanim;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * 视频抽帧 → 目标画布尺寸的 JPEG 帧序列。
 *
 * 关键点（都是踩过的坑）：
 * 1) 必须先读 METADATA_KEY_VIDEO_ROTATION，部分视频像素是竖的但带 90° 旋转元数据，
 *    不处理会出现人物横躺（实测 iPhone 导出的 mov 常见）。
 * 2) getFrameAtTime 用微秒；用 OPTION_CLOSEST 保证取到最接近目标时间的那帧。
 * 3) 缩放用等比 + 居中裁/垫，绝不拉伸变形。
 * 4) JPEG 质量 88（规范推荐 85–92），比 PNG 体积小一个数量级。
 */
public class FrameExtractor {

    public interface Progress {
        void onProgress(int done, int total);
        void onStatus(String msg);
    }

    public static class Result {
        public int frameCount;
        public int width, height;
        public long totalBytes;
        public String workDir;
    }

    private static final int JPEG_QUALITY = 88;

    /**
     * @param srcUri     源视频
     * @param workDir    工作目录（会清空重建 frames/）
     * @param targetW    目标宽（如 1920）
     * @param targetH    目标高（如 1080）
     * @param fps        目标帧率（desc.txt 用同一值）
     * @param maxSeconds 0 = 全片；>0 则只取前 N 秒
     */
    public static Result extract(android.content.Context ctx, Uri srcUri, File workDir,
                                 int targetW, int targetH, int fps,
                                 double maxSeconds, Progress cb) throws Exception {
        return extract(ctx, srcUri, workDir, targetW, targetH, fps, maxSeconds,
                JPEG_QUALITY, cb);
    }

    /** 同上，但可指定 JPEG 质量（85–92 之间符合规范，低于 85 用于压体积） */
    public static Result extract(android.content.Context ctx, Uri srcUri, File workDir,
                                 int targetW, int targetH, int fps,
                                 double maxSeconds, int quality, Progress cb) throws Exception {

        File framesDir = new File(workDir, "frames");
        deleteRecursive(framesDir);
        if (!framesDir.mkdirs()) throw new Exception("无法创建工作目录: " + framesDir);

        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        Result r = new Result();
        r.workDir = workDir.getAbsolutePath();
        try {
            mmr.setDataSource(ctx, srcUri);

            long durationMs = parseLong(mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            if (durationMs <= 0) throw new Exception("无法读取视频时长，文件可能损坏");

            int rotation = (int) parseLong(mmr.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);

            double useMs = durationMs;
            if (maxSeconds > 0 && durationMs > maxSeconds * 1000.0) {
                useMs = maxSeconds * 1000.0;
            }

            int total = (int) Math.round(useMs / 1000.0 * fps);
            if (total < 1) total = 1;
            // 与 BootPackager 完全一致的帧号宽度：保证按文件名排序 == 按时间排序
            int digits = String.valueOf(total + Math.max(1, fps)).length();
            if (digits < 3) digits = 3;
            r.frameCount = total;
            r.width = targetW;
            r.height = targetH;

            if (cb != null) cb.onStatus("共 " + total + " 帧，开始抽帧…");

            long stepUs = (long) (1_000_000.0 / fps);
            long emitted = 0;

            for (int i = 0; i < total; i++) {
                long tUs = i * stepUs;
                Bitmap raw = mmr.getFrameAtTime(tUs,
                        MediaMetadataRetriever.OPTION_CLOSEST);
                if (raw == null) {
                    // 个别机型在超范围时返回 null，用上一帧兜底（避免帧数缺口）
                    if (i == 0) throw new Exception("首帧解码失败，可能是编码格式不支持");
                    raw = mmr.getFrameAtTime(Math.max(0, tUs - stepUs),
                            MediaMetadataRetriever.OPTION_CLOSEST);
                    if (raw == null) continue;
                }

                Bitmap oriented = applyRotation(raw, rotation);
                if (oriented != raw && !raw.isRecycled()) raw.recycle();

                // fitToCanvas 内部接管并回收 oriented（若它不等于返回值）
                Bitmap fitted = fitToCanvas(oriented, targetW, targetH);

                File out = new File(framesDir, String.format("%0" + digits + "d.jpg", i + 1));
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    fitted.compress(Bitmap.CompressFormat.JPEG, quality, fos);
                }
                if (!fitted.isRecycled()) fitted.recycle();

                emitted++;
                if (cb != null && (i % 5 == 0 || i == total - 1)) {
                    cb.onProgress(i + 1, total);
                }
            }

            r.frameCount = (int) emitted;
            long totalBytes = 0;
            File[] files = framesDir.listFiles();
            if (files != null) for (File f : files) totalBytes += f.length();
            r.totalBytes = totalBytes;

            if (r.frameCount <= 0) throw new Exception("没有抽出任何帧");

        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
        return r;
    }

    /** 应用旋转元数据（0/90/180/270）。 */
    private static Bitmap applyRotation(Bitmap src, int rotation) {
        if (rotation % 360 == 0) return src;
        Matrix m = new Matrix();
        m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
        if (out == null) return src;
        if (out != src && !src.isRecycled()) src.recycle();
        return out;
    }

    /**
     * 等比缩放 + 居中裁/垫到目标画布。
     * 策略：先按"填满"缩放(cover)，再居中裁剪超出的部分；
     * 若源比目标还小，则等比放大到刚好覆盖（不做 letterbox，避免黑边）。
     */
    /**
     * 等比缩放（cover）+ 居中裁/垫到目标画布。
     * 所有权契约：返回的 Bitmap 由调用方负责 recycle；入参 src 在本函数内一定被回收
     * （除非它本身就是返回值）。
     */
    private static Bitmap fitToCanvas(Bitmap src, int tw, int th) {
        int sw = src.getWidth(), sh = src.getHeight();
        if (sw == tw && sh == th) return src;   // src 即结果，调用方 recycle 它即可

        float scale = Math.max((float) tw / sw, (float) th / sh);
        int dw = Math.max(1, Math.round(sw * scale));
        int dh = Math.max(1, Math.round(sh * scale));

        Bitmap scaled = Bitmap.createScaledBitmap(src, dw, dh, true);
        if (scaled != src && !src.isRecycled()) src.recycle();

        if (dw == tw && dh == th) return scaled;

        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.BLACK);           // 不透明背景，规范要求不依赖 alpha
        int left = (tw - dw) / 2;
        int top = (th - dh) / 2;
        c.drawBitmap(scaled, left, top, new Paint(Paint.FILTER_BITMAP_FLAG));
        if (!scaled.isRecycled()) scaled.recycle();
        return out;
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    public static void deleteRecursive(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
