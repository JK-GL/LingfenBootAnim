package com.lingfen.bootanim;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private TextView tvFileName, tvEstimate, tvStatus, tvPickLabel;
    private ProgressBar progress;
    private Button btnGo;
    private Uri srcUri;
    private String srcName;
    private int srcW, srcH, srcRotation;
    private long srcDurationMs;
    private String srcFps;

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<Intent> picker =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() != Activity.RESULT_OK || res.getData() == null) return;
                Uri u = res.getData().getData();
                if (u == null) return;
                srcUri = u;
                srcName = queryName(u);
                tvFileName.setText(srcName + "   （读取中…）");
                tvPickLabel.setText("重新选择");
                pool.execute(() -> {
                    probe(u);
                    ui.post(this::refreshEstimate);
                });
            });

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        tvFileName = findViewById(R.id.tvFileName);
        tvEstimate = findViewById(R.id.tvEstimate);
        tvStatus = findViewById(R.id.tvStatus);
        tvPickLabel = findViewById(R.id.tvPickLabel);
        progress = findViewById(R.id.progress);
        btnGo = findViewById(R.id.btnGo);

        findViewById(R.id.cardPick).setOnClickListener(v -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("video/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            picker.launch(i);
        });

        RadioGroup rgCanvas = findViewById(R.id.rgCanvas);
        RadioGroup rgFps = findViewById(R.id.rgFps);
        RadioGroup rgDur = findViewById(R.id.rgDuration);
        RadioGroup.OnCheckedChangeListener l = (g, id) -> refreshEstimate();
        rgCanvas.setOnCheckedChangeListener(l);
        rgFps.setOnCheckedChangeListener(l);
        rgDur.setOnCheckedChangeListener(l);

        btnGo.setOnClickListener(v -> startBuild());
    }

    private String queryName(Uri u) {
        try (android.database.Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "video.mp4";
    }

    private void probe(Uri u) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(this, u);
            srcDurationMs = parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 0);
            srcRotation = (int) parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION), 0);
            srcW = (int) parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
            srcH = (int) parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
            String fr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
            srcFps = fr == null ? "?" : String.format("%.1f", parseDouble(fr, 0));
            if (srcRotation == 90 || srcRotation == 270) {
                int t = srcW; srcW = srcH; srcH = t;   // 显示尺寸
            }
        } catch (Exception e) {
            ui.post(() -> Toast.makeText(this, "读取失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
        } finally {
            try { mmr.release(); } catch (Exception ignored) {}
        }
    }

    private int[] canvasSize() {
        int id = ((RadioGroup) findViewById(R.id.rgCanvas)).getCheckedRadioButtonId();
        if (id == R.id.rb720) return new int[]{1280, 720};
        if (id == R.id.rbPortrait) return new int[]{1080, 1920};
        return new int[]{1920, 1080};
    }

    private int fps() {
        int id = ((RadioGroup) findViewById(R.id.rgFps)).getCheckedRadioButtonId();
        if (id == R.id.rb30) return 30;
        if (id == R.id.rb15) return 15;
        return 25;
    }

    private double maxSeconds() {
        int id = ((RadioGroup) findViewById(R.id.rgDuration)).getCheckedRadioButtonId();
        if (id == R.id.rb15s) return 15;
        if (id == R.id.rb10s) return 10;
        return 0;
    }

    private void refreshEstimate() {
        if (srcUri == null || srcDurationMs <= 0) return;
        int[] cs = canvasSize();
        int f = fps();
        double dur = srcDurationMs / 1000.0;
        double use = maxSeconds() > 0 ? Math.min(dur, maxSeconds()) : dur;
        int frames = (int) Math.round(use * f);

        tvFileName.setText(srcName + "   " + srcW + "×" + srcH
                + (srcRotation % 180 != 0 ? " (旋转" + srcRotation + "°)" : "")
                + " · " + String.format("%.1f", dur) + "s · " + srcFps + "fps");

        double px = cs[0] * (double) cs[1];
        double kbPerFrame = 45.0 * (px / (1920.0 * 1080.0));
        double estMb = frames * kbPerFrame / 1024.0;

        StringBuilder sb = new StringBuilder();
        sb.append("输出画布：").append(cs[0]).append(" × ").append(cs[1]).append("\n");
        sb.append("帧率：").append(f).append(" fps\n");
        sb.append("主体帧数：").append(frames).append(" 帧 → 时长 ")
          .append(String.format("%.2f", frames / (double) f)).append(" 秒\n");
        sb.append("预计体积：约 ").append(String.format("%.1f", estMb)).append(" MB");
        if (estMb > 128) sb.append("   ⚠ 超过 128 MiB 上限");
        sb.append("\n输出：下载/LingfenBoot/").append(baseName()).append(".zip");
        tvEstimate.setText(sb.toString());
        btnGo.setEnabled(true);
    }

    private String baseName() {
        String base = (srcName == null || srcName.trim().isEmpty())
                ? "bootanimation" : srcName;
        int dot = base.lastIndexOf('.');
        String n = dot > 0 ? base.substring(0, dot) : base;
        n = n.replaceAll("[^\\w\\u4e00-\\u9fa5\\-]", "_").trim();
        if (n.isEmpty()) n = "bootanimation";
        return n;
    }

    private void startBuild() {
        if (srcUri == null) return;
        // Android 9（API 28）写公共目录需要运行时存储权限；API 29+ 零权限
        if (StoragePublisher.needLegacyPermission()
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                   != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
            return;
        }
        doBuild();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == 1001) {
            if (res.length > 0 && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                doBuild();
            } else {
                Toast.makeText(this, "需要存储权限才能把 zip 存到「下载」目录", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void doBuild() {
        if (srcUri == null) return;
        btnGo.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        tvStatus.setText("准备中…");

        final int[] cs = canvasSize();
        final int f = fps();
        final double mx = maxSeconds();
        final String outName = baseName();

        pool.execute(() -> {
            File work = new File(getCacheDir(), "build");
            // 先在 app 私有目录生成，再经 MediaStore 发布到「下载」，全程零权限
            File outZip = new File(work, outName + ".zip");

            try {
                // ---- 1. 抽帧 ----
                FrameExtractor.Result fr = FrameExtractor.extract(
                        this, srcUri, work, cs[0], cs[1], f, mx,
                        new FrameExtractor.Progress() {
                            public void onProgress(int d, int t) {
                                int pct = (int) (d * 55.0 / Math.max(1, t));
                                ui.post(() -> { progress.setProgress(pct);
                                    tvStatus.setText("抽帧 " + d + " / " + t); });
                            }
                            public void onStatus(String m) { ui.post(() -> tvStatus.setText(m)); }
                        });

                ui.post(() -> { progress.setProgress(58); tvStatus.setText("打包中…"); });

                // ---- 2. 打包（复用 BootPackager，desc/part/Stored 全在里面） ----
                BootPackager.Result pr = BootPackager.packageFrames(
                        work, outZip, cs[0], cs[1], f,
                        new BootPackager.Progress() {
                            public void onProgress(int d, int t) {
                                int pct = 58 + (int) (d * 38.0 / Math.max(1, t));
                                ui.post(() -> { progress.setProgress(pct);
                                    tvStatus.setText("打包 " + d + " / " + t); });
                            }
                            public void onStatus(String m) { ui.post(() -> tvStatus.setText(m)); }
                        });

                final StringBuilder warn = new StringBuilder();
                for (String w : pr.warnings) warn.append("\n⚠ ").append(w);

                // ---- 3. 发布到「下载」----
                ui.post(() -> tvStatus.setText("保存到下载目录…"));
                StoragePublisher.Result pub = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                        ? StoragePublisher.publishModern(this, outZip, outZip.getName())
                        : StoragePublisher.publishLegacy(outZip, outZip.getName());

                ui.post(() -> {
                    progress.setProgress(100);
                    tvStatus.setText("完成");
                    btnGo.setEnabled(true);
                    String msg = "已生成 " + outZip.getName() + "\n"
                            + pr.totalFrames + " 帧 · "
                            + String.format("%.2f", pr.zipBytes / 1048576.0) + " MB · "
                            + String.format("%.2f", pr.durationSec) + " 秒";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    tvEstimate.setText(tvEstimate.getText()
                            + "\n\n✅ 已保存：" + pub.display
                            + "\n" + pr.totalFrames + " 帧（含定格）/ 主体 "
                            + String.format("%.2f", pr.durationSec) + " 秒 / "
                            + String.format("%.2f", pr.zipBytes / 1048576.0) + " MB"
                            + warn);
                });

            } catch (Exception ex) {
                ui.post(() -> {
                    btnGo.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    tvStatus.setText("失败");
                    Toast.makeText(this, "生成失败: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static long parseLong(String s, long def) {
        if (s == null) return def;
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    private static double parseDouble(String s, double def) {
        if (s == null) return def;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return def; }
    }
}
