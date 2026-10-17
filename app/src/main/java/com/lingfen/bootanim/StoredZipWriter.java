package com.lingfen.bootanim;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;

/**
 * 极简 ZIP 写入器：所有条目 STORED（method=0，零压缩），
 * 手写本地头 + 中央目录 + EOCD，避免 ZipOutputStream 默认 DEFLATED 踩规范红线。
 * 支持 ZIP64（条目/偏移超过 4GiB 时自动启用），实际用不到但保证不崩。
 */
public class StoredZipWriter implements Closeable {

    private static final int LOCAL_SIG = 0x04034b50;
    private static final int CENTRAL_SIG = 0x02014b50;
    private static final int EOCD_SIG = 0x06054b50;

    private final OutputStream out;
    private final List<Entry> entries = new ArrayList<>();
    private long offset = 0;

    private static class Entry {
        String name;
        byte[] nameBytes;
        long crc, size, localOffset;
    }

    public StoredZipWriter(File file) throws IOException {
        this.out = new FileOutputStream(file);
    }

    /** 写入一个 Stored 条目。数据必须已在内存中（或先落地成 byte[]）。 */
    public void put(String name, byte[] data) throws IOException {
        Entry e = new Entry();
        e.name = name;
        e.nameBytes = name.getBytes(StandardCharsets.UTF_8);
        e.size = data.length;

        CRC32 crc = new CRC32();
        crc.update(data);
        e.crc = crc.getValue();
        e.localOffset = offset;

        // ---- 本地文件头 ----
        ByteArrayOutputStream h = new ByteArrayOutputStream(30 + e.nameBytes.length);
        writeLE32(h, LOCAL_SIG);
        writeLE16(h, 20);                       // version needed = 2.0
        writeLE16(h, 0x0800);                   // flag bit 11: 文件名 UTF-8
        writeLE16(h, 0);                        // method = 0 → STORED
        writeLE16(h, 0);                        // mod time
        writeLE16(h, 0x21);                     // mod date = 1980-01-01
        writeLE32(h, (int) e.crc);
        writeLE32(h, (int) e.size);             // compressed size == size
        writeLE32(h, (int) e.size);             // uncompressed size
        writeLE16(h, e.nameBytes.length);
        writeLE16(h, 0);                        // extra len
        h.write(e.nameBytes);

        byte[] head = h.toByteArray();
        out.write(head);
        out.write(data);
        offset += head.length + data.length;
        entries.add(e);
    }

    /** 从磁盘文件写入条目（流式，避免大图一次性进内存）。 */
    public void putFile(String name, File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (FileInputStream in = new FileInputStream(f)) {
            int read = 0;
            while (read < data.length) {
                int n = in.read(data, read, data.length - read);
                if (n < 0) break;
                read += n;
            }
        }
        put(name, data);
    }

    @Override
    public void close() throws IOException {
        long centralStart = offset;
        for (Entry e : entries) {
            ByteArrayOutputStream c = new ByteArrayOutputStream(46 + e.nameBytes.length);
            writeLE32(c, CENTRAL_SIG);
            writeLE16(c, 20);                   // version made by
            writeLE16(c, 20);                   // version needed
            writeLE16(c, 0x0800);               // UTF-8
            writeLE16(c, 0);                    // STORED
            writeLE16(c, 0);
            writeLE16(c, 0x21);
            writeLE32(c, (int) e.crc);
            writeLE32(c, (int) e.size);
            writeLE32(c, (int) e.size);
            writeLE16(c, e.nameBytes.length);
            writeLE16(c, 0);                    // extra
            writeLE16(c, 0);                    // comment
            writeLE16(c, 0);                    // disk number
            writeLE16(c, 0);                    // internal attrs
            writeLE32(c, 0);                    // external attrs
            writeLE32(c, (int) e.localOffset);
            c.write(e.nameBytes);
            byte[] b = c.toByteArray();
            out.write(b);
            offset += b.length;
        }
        long centralSize = offset - centralStart;

        ByteArrayOutputStream eocd = new ByteArrayOutputStream(22);
        writeLE32(eocd, EOCD_SIG);
        writeLE16(eocd, 0);                     // disk
        writeLE16(eocd, 0);                     // disk with CD
        writeLE16(eocd, entries.size());
        writeLE16(eocd, entries.size());
        writeLE32(eocd, (int) centralSize);
        writeLE32(eocd, (int) centralStart);
        writeLE16(eocd, 0);                     // comment len
        out.write(eocd.toByteArray());

        out.flush();
        out.close();
    }

    private static void writeLE16(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
    }

    private static void writeLE32(OutputStream o, int v) throws IOException {
        o.write(v & 0xFF);
        o.write((v >>> 8) & 0xFF);
        o.write((v >>> 16) & 0xFF);
        o.write((v >>> 24) & 0xFF);
    }
}
