#!/usr/bin/env python3
"""
模拟 App 的完整流水线（与 Java 实现等价），用真实视频验证产出合规。
- 抽帧：ffmpeg fps 滤镜（等价于 getFrameAtTime + 按 fps 采样）
- 打包：完全复现 BootPackager + StoredZipWriter 的字节布局
- 自检：规范第 5 节
"""
import os, sys, subprocess, struct, io, zlib, zipfile, hashlib, shutil

SRC = "/var/minis/attachments/uploads/video_14A07D25.mp4"
WORK = "/var/minis/workspace/lingfen-bootanim/sim"
FPS = 25
W, H = 1920, 1080
MAX_SEC = 0          # 0 = 全片

def tail_frames(fps): return max(1, fps)

def build_stored_zip(path, entries):
    out = io.BytesIO(); central = []; offset = 0
    for name, data in entries:
        nb = name.encode(); crc = zlib.crc32(data) & 0xFFFFFFFF
        head = struct.pack('<IHHHHHIIIHH', 0x04034b50, 20, 0x0800, 0,
                           0, 0x21, crc, len(data), len(data), len(nb), 0)
        loff = offset
        out.write(head); out.write(nb); out.write(data)
        offset += len(head) + len(nb) + len(data)
        central.append((name, nb, crc, len(data), loff))
    cd_start = offset
    for name, nb, crc, size, loff in central:
        out.write(struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 20, 20, 0x0800,
                              0, 0, 0x21, crc, size, size, len(nb), 0, 0, 0, 0, 0, loff))
        out.write(nb)
    cd_size = len(out.getvalue()) - cd_start
    out.write(struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, len(central), len(central),
                          cd_size, cd_start, 0))
    open(path, 'wb').write(out.getvalue())

shutil.rmtree(WORK, ignore_errors=True)
frames_dir = os.path.join(WORK, "frames"); os.makedirs(frames_dir)

# 1. 抽帧（等价 Java 的按 fps 取帧 + cover 缩放）
vf = f"fps={FPS},scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H}"
cmd = ["ffmpeg","-hide_banner","-v","error","-i",SRC,"-an","-vf",vf,
       "-q:v","2","-start_number","1", os.path.join(frames_dir,"%03d.jpg")]
subprocess.run(cmd, check=True,
               timeout=1800)
frames = sorted(os.listdir(frames_dir))
mainN = len(frames)
print(f"抽帧: {mainN} 帧")

# 2. 打包（复现 BootPackager）
tailN = tail_frames(FPS)
entries = []
desc = f"{W} {H} {FPS}\nc 1 10 part0\nc 1 0 part1\np 0 0 part2\n"
entries.append(("desc.txt", desc.encode('ascii')))
entries.append(("part0/000.jpg", open(os.path.join(frames_dir, frames[0]),'rb').read()))
for i, f in enumerate(frames):
    entries.append((f"part1/{i+1:03d}.jpg", open(os.path.join(frames_dir, f),'rb').read()))
last = open(os.path.join(frames_dir, frames[-1]),'rb').read()
for i in range(tailN):
    entries.append((f"part2/{mainN+1+i:03d}.jpg", last))

out = os.path.join(WORK, "sim_bootanimation.zip")
build_stored_zip(out, entries)
print(f"打包: {os.path.getsize(out)/1048576:.2f} MiB, {len(entries)} 条目")

# 3. 规范自检
probs = []
with zipfile.ZipFile(out) as z:
    names = z.namelist()
    if "desc.txt" not in names: probs.append("desc.txt 不在根")
    if z.testzip() is not None: probs.append("CRC 失败")
    bad = [i.filename for i in z.infolist() if i.compress_type != zipfile.ZIP_STORED]
    if bad: probs.append(f"{len(bad)} 条目非 Stored")
    raw = z.read("desc.txt")
    if raw.startswith(b'\xef\xbb\xbf'): probs.append("带 BOM")
    lines = raw.decode('ascii').split('\n')[:-1]
    if any(not l.strip() for l in lines): probs.append("有空行")
    h = lines[0].split()
    if [int(x) for x in h] != [W,H,FPS]: probs.append(f"首行错 {h}")
    n = {k: sum(1 for x in names if x.startswith(k+'/')) for k in ('part0','part1','part2')}
    if n['part0'] != 1: probs.append(f"part0={n['part0']}")
    if n['part1'] != mainN: probs.append(f"part1={n['part1']} != {mainN}")
    if n['part2'] != tailN: probs.append(f"part2={n['part2']}")
    # 逐帧解码 + 尺寸
    from PIL import Image
    ok = 0
    for nm in names:
        if nm == 'desc.txt': continue
        im = Image.open(z.open(nm)); im.load()
        if im.size == (W,H): ok += 1
        else: probs.append(f"{nm} 尺寸 {im.size}")
    print(f"逐帧解码: {ok}/{len(names)-1} 尺寸正确")
    # part2 与末帧一致
    lb = z.read(f"part1/{mainN:03d}.jpg")
    if not all(z.read(f"part2/{mainN+1+i:03d}.jpg")==lb for i in range(tailN)):
        probs.append("part2 与末帧不一致")

print("SHA256:", hashlib.sha256(open(out,'rb').read()).hexdigest())
print("时长: %.2f 秒 (主体 %d 帧 @ %d fps)" % (mainN/FPS, mainN, FPS))
print("\n" + ("❌ 问题:\n  " + "\n  ".join(probs) if probs else "✅ 全部自检通过"))
