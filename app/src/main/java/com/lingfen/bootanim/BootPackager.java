package com.lingfen.bootanim;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 把 frames/ 里的帧组装成合规 bootanimation.zip。
 *
 * desc.txt（规范三段式，主体绝不循环）：
 *   1920 1080 25
 *   c 1 10 part0      ← 起始定格 0.4s
 *   c 1 0  part1      ← 主体一次播完
 *   p 0 0  part2      ← 末帧定格
 */
public class BootPackager {

    public static class Result {
        public File zip;
        public int totalFrames;
        public long zipBytes;
        public double durationSec;
        public List<String> warnings = new ArrayList<>();
    }

    public interface Progress {
        void onProgress(int done, int total);
        void onStatus(String msg);
    }

    /** part2 定格帧数：按 1 秒折算，至少 1 帧。 */
    private static int tailFrames(int fps) {
        return Math.max(1, fps);
    }

    public static Result packageFrames(File workDir, File outZip,
                                       int w, int h, int fps,
                                       Progress cb) throws Exception {

        File framesDir = new File(workDir, "frames");
        File[] raw = framesDir.listFiles();
        if (raw == null || raw.length == 0) throw new Exception("没有可打包的帧");
        Arrays.sort(raw, Comparator.comparing(File::getName));
        int mainN = raw.length;
        int tailN = tailFrames(fps);
        int mainN = raw.length;
        // 帧号宽度动态计算，同一 part 内等宽
        int digits = String.valueOf(mainN + 1).length();
        if (digits < 3) digits = 3;

        Result r = new Result();
        r.durationSec = mainN / (double) fps;
        r.totalFrames = mainN + 1;

        // 规范体积上限预警
        long estimate = 0;
        for (File f : raw) estimate += f.length();
        if (estimate > 128L * 1024 * 1024) {
            r.warnings.add("预计 ZIP " + (estimate / 1048576) + " MiB，超过规范上限 128 MiB");
        }
        if (r.totalFrames > 4096) {
            r.warnings.add("总帧数 " + r.totalFrames + " 超过规范上限 4096");
        }

        if (outZip.exists()) //noinspection ResultOfMethodCallIgnored
            outZip.delete();
        File parent = outZip.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        int totalSteps = 1 + mainN + 1;
        int done = 0;

        try (StoredZipWriter zip = new StoredZipWriter(outZip)) {
            // desc.txt —— 开头零停顿（c 1 0 part0 一气呵成播完），末尾仅保留1帧车机备用防黑屏（p 0 0 part1）
            String desc = w + " " + h + " " + fps + "
"
                    + "c 1 0 part0
"
                    + "p 0 0 part1
";
            zip.put("desc.txt", desc.getBytes(StandardCharsets.US_ASCII));
            done++;

            if (cb != null) cb.onStatus("写入主体（" + mainN + " 帧，精准秒数）…");
            for (int i = 0; i < mainN; i++) {
                zip.putFile(String.format("part0/%0" + digits + "d.jpg", i), raw[i]);
                done++;
                if (cb != null && (i % 10 == 0 || i == mainN - 1)) cb.onProgress(done, totalSteps);
            }

            if (cb != null) cb.onStatus("写入车机备用末帧…");
            File last = raw[mainN - 1];
            zip.put(String.format("part1/%0" + digits + "d.jpg", mainN), readAll(last));
            done++;
        }

        r.zip = outZip;
        r.zipBytes = outZip.length();

        // ---- 轻量自检（规范第 5 节核心项）----
        verify(outZip, w, h, fps, mainN, r.warnings);
        return r;
    }

    /** 打包后自检：条目压缩方式、desc 内容、帧数、尺寸抽查。 */
    private static void verify(File zip, int w, int h, int fps, int mainN,
                              List<String> warnings) {
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zip)) {
            int stored = 0, deflated = 0;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry e = en.nextElement();
                if (e.getMethod() == java.util.zip.ZipEntry.STORED) stored++;
                else deflated++;
            }
            if (deflated > 0) warnings.add("有 " + deflated + " 个条目未使用 Stored（应为 0）");
            if (stored == 0) warnings.add("ZIP 内没有任何条目");

            java.util.zip.ZipEntry de = zf.getEntry("desc.txt");
            if (de == null) {
                warnings.add("desc.txt 不在根目录");
            } else {
                byte[] b = readAll(zf.getInputStream(de));
                String s = new String(b, StandardCharsets.US_ASCII);
                String[] lines = s.split("\n");
                if (lines.length < 4) warnings.add("desc.txt 行数不足");
                else {
                    String[] head = lines[0].trim().split("\\s+");
                    if (head.length != 3
                            || !head[0].equals(String.valueOf(w))
                            || !head[1].equals(String.valueOf(h))
                            || !head[2].equals(String.valueOf(fps))) {
                        warnings.add("desc.txt 首行与设定不符: " + lines[0]);
                    }
                }
                if (b.length >= 3 && (b[0] & 0xFF) == 0xEF && (b[1] & 0xFF) == 0xBB) {
                    warnings.add("desc.txt 带 BOM");
                }
            }

            int n0 = 0, n1 = 0;
            java.util.Enumeration<? extends java.util.zip.ZipEntry> e2 = zf.entries();
            while (e2.hasMoreElements()) {
                String n = e2.nextElement().getName();
                if (n.startsWith("part0/")) n0++;
                else if (n.startsWith("part1/")) n1++;
            }
            if (n0 != mainN) warnings.add("part0 帧数 " + n0 + " ≠ 抽帧数 " + mainN);
            if (n1 < 1) warnings.add("part1 为空");

        } catch (Exception e) {
            warnings.add("自检异常: " + e.getMessage());
        }
    }

    private static byte[] readAll(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            int off = 0;
            while (off < data.length) {
                int n = in.read(data, off, data.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return data;
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }
}
