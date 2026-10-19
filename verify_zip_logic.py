#!/usr/bin/env python3
"""用 Python 精确复现 Java StoredZipWriter 的字节布局，验证产出的 zip 是否合规。"""
import struct, zipfile, io, os, hashlib

def build_zip(path, entries):
    """entries: [(name, bytes)]，完全按 Java 版逻辑写（STORED + 中央目录）。"""
    out = io.BytesIO()
    central = []
    offset = 0
    for name, data in entries:
        nb = name.encode('utf-8')
        import zlib
        crc = zlib.crc32(data) & 0xFFFFFFFF
        head = struct.pack('<IHHHHHIIIHH',
                           0x04034b50,  # local sig
                           20,          # version needed
                           0x0800,      # UTF-8 flag
                           0,           # method STORED
                           0,           # time
                           0x21,        # date
                           crc,
                           len(data), len(data),
                           len(nb), 0)  # name len, extra len
        local_off = offset
        out.write(head); out.write(nb); out.write(data)
        offset += len(head) + len(nb) + len(data)
        central.append((name, nb, crc, len(data), local_off))

    cd_start = offset
    for name, nb, crc, size, loff in central:
        cd = struct.pack('<IHHHHHHIIIHHHHHII',
                         0x02014b50, 20, 20, 0x0800, 0, 0, 0x21,
                         crc, size, size, len(nb), 0, 0, 0, 0, 0, loff)
        out.write(cd); out.write(nb)
    cd_size = len(out.getvalue()) - cd_start
    eocd = struct.pack('<IHHHHIIH', 0x06054b50, 0, 0,
                       len(central), len(central), cd_size, cd_start, 0)
    out.write(eocd)
    open(path, 'wb').write(out.getvalue())

# 造 5 帧假数据
entries = [("desc.txt", b"1920 1080 25\nc 1 10 part0\nc 1 0 part1\np 0 0 part2\n")]
entries.append(("part0/000.jpg", b"\xff\xd8\xff\xe0" + b"A"*100 + b"\xff\xd9"))
for i in range(1, 4):
    entries.append((f"part1/{i:03d}.jpg", b"\xff\xd8\xff\xe0" + bytes([i])*200 + b"\xff\xd9"))
entries.append(("part2/004.jpg", b"\xff\xd8\xff\xe0" + b"C"*150 + b"\xff\xd9"))

p = "/tmp/test_zipwriter.zip"
build_zip(p, entries)
print("生成:", os.path.getsize(p), "bytes")

with zipfile.ZipFile(p) as z:
    print("testzip:", z.testzip())
    bad = [i.filename for i in z.infolist() if i.compress_type != zipfile.ZIP_STORED]
    print("非Stored条目:", bad if bad else "无 ✓")
    print("desc.txt:", repr(z.read("desc.txt").decode('ascii')))
    for i in z.infolist():
        print(" ", i.filename, i.file_size, "STORED" if i.compress_type == 0 else "DEFLATED")
    # 逐条 CRC 验证
    print("全条目读取成功，CRC 校验:", "通过" if z.testzip() is None else "失败")

print("\nSHA256:", hashlib.sha256(open(p,'rb').read()).hexdigest())
