package com.lingfen.bootanim;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

/**
 * 把生成的 zip 保存到「下载/LingfenBoot/」。
 *
 * 版本差异（这是真机必崩点）：
 *  - API 29+：MediaStore.Downloads 存在，用 RELATIVE_PATH，零权限。
 *  - API 28 ：MediaStore.Downloads **不存在**（Q 才加），必须走 MediaStore.Files
 *              + DATA 列，且需要 WRITE_EXTERNAL_STORAGE 运行时权限。
 */
public class StoragePublisher {

    public static final String SUB_DIR = "LingfenBoot";

    public static class Result {
        public Uri uri;
        public String display;
    }

    /** API 29+：零权限写入。 */
    public static Result publishModern(Context ctx, File src, String fileName) throws Exception {
        ContentResolver cr = ctx.getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + SUB_DIR);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = cr.insert(collection, cv);
        if (uri == null) throw new Exception("MediaStore 插入失败（可能已存在同名文件）");

        try (OutputStream os = cr.openOutputStream(uri);
             FileInputStream in = new FileInputStream(src)) {
            if (os == null) throw new Exception("无法打开输出流");
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        }

        Result r = new Result();
        r.uri = uri;
        r.display = "下载/" + SUB_DIR + "/" + fileName;
        return r;
    }

    /** API 28：直接写公共下载目录（调用前必须已获 WRITE_EXTERNAL_STORAGE）。 */
    public static Result publishLegacy(File src, String fileName) throws Exception {
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS), SUB_DIR);
        if (!dir.exists() && !dir.mkdirs()) throw new Exception("无法创建目录: " + dir);

        File dest = new File(dir, fileName);
        try (FileInputStream in = new FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dest)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }

        Result r = new Result();
        r.display = "下载/" + SUB_DIR + "/" + fileName;
        return r;
    }

    public static boolean needLegacyPermission() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
    }
}
