#!/usr/bin/env python3
"""把本地重写后的完整提交链通过 GitHub GitData API 强制推送到远端，彻底清除旧历史。"""
import json, os, sys, base64, subprocess, hashlib, urllib.request, urllib.error, time

REPO = "JK-GL/LingfenBootAnim"
BASE = "/var/minis/workspace/lingfen-bootanim"
TOKEN = os.environ["GITHUB_TOKEN_1"]

def api(method, path, body=None):
    url = "https://api.github.com" + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    last = None
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=90) as r:
                return json.loads(r.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            print("API ERROR", e.code, e.read().decode()[:300], file=sys.stderr)
            if e.code in (429, 500, 502, 503, 504):
                time.sleep(5 * (attempt + 1))
                continue
            raise
        except Exception as e:
            last = e
            time.sleep(3 * (attempt + 1))
    raise last

def run(args, **kw):
    return subprocess.run(args, cwd=BASE, capture_output=True, **kw)

# 1. 按时间顺序列出全部提交
commits = run(["git", "rev-list", "--reverse", "--all"]).stdout.decode().split()
seen = set(); ordered = []
for c in commits:
    if c not in seen:
        seen.add(c); ordered.append(c)
print(f"待推送提交数: {len(ordered)}")

blob_cache = {}   # git blob sha1 -> remote sha
new_commit_map = {}

for i, old_sha in enumerate(ordered):
    # 该提交下的全部文件（路径 + 模式）
    ls = run(["git", "ls-tree", "-r", old_sha]).stdout.decode()
    entries = []
    for line in ls.splitlines():
        if not line.strip():
            continue
        meta, path = line.split("\t", 1)
        mode, typ, sha = meta.split()
        if typ != "blob":
            continue
        content = run(["git", "show", f"{old_sha}:{path}"]).stdout
        h = hashlib.sha1(b"blob %d\0" % len(content) + content).hexdigest()
        if h not in blob_cache:
            blob = api("POST", f"/repos/{REPO}/git/blobs",
                       {"content": base64.b64encode(content).decode(), "encoding": "base64"})
            blob_cache[h] = blob["sha"]
        entries.append({"path": path, "mode": mode, "type": "blob", "sha": blob_cache[h]})

    tree = api("POST", f"/repos/{REPO}/git/trees", {"tree": entries})

    parents = run(["git", "rev-list", "--parents", "-n", "1", old_sha]).stdout.decode().split()[1:]
    msg = run(["git", "log", "-1", "--format=%B", old_sha]).stdout.decode()
    au = run(["git", "log", "-1", "--format=%an|%ae|%aI", old_sha]).stdout.decode().strip().split("|")

    new_parents = [new_commit_map[p] for p in parents if p in new_commit_map]
    commit = api("POST", f"/repos/{REPO}/git/commits", {
        "message": msg,
        "tree": tree["sha"],
        "parents": new_parents,
        "author": {"name": au[0], "email": au[1], "date": au[2]},
        "committer": {"name": au[0], "email": au[1], "date": au[2]},
    })
    new_commit_map[old_sha] = commit["sha"]
    print(f"[{i+1}/{len(ordered)}] {old_sha[:7]} -> {commit['sha'][:7]}  ({len(entries)} files)")

head_new = new_commit_map[ordered[-1]]
print("新 HEAD:", head_new)
api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": head_new, "force": True})
print("✅ 已强制覆盖远端 main 分支为纯净历史")
