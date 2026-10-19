#!/usr/bin/env python3
"""通过 GitHub GitData API 推送（github.com:443 不可达时的唯一可用通道）。"""
import json, os, sys, base64, subprocess, time, urllib.request, urllib.error

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
            print("API ERROR", e.code, e.read().decode()[:500], file=sys.stderr)
            raise
        except Exception as e:
            last = e
            wait = 3 * (attempt + 1)
            print(f"  重试 {attempt+1}/6 ({type(e).__name__}) wait {wait}s", file=sys.stderr)
            time.sleep(wait)
    raise last

# 1. 当前远端 HEAD
ref = api("GET", f"/repos/{REPO}/git/ref/heads/main")
parent = ref["object"]["sha"]
print("远端 HEAD:", parent)
base_commit = api("GET", f"/repos/{REPO}/git/commits/{parent}")
base_tree = base_commit["tree"]["sha"]
print("基线 tree:", base_tree)

# 2. 收集要推送的文件（用 git 的索引，确保与本地 commit 一致）
files = subprocess.run(["git", "ls-files"], cwd=BASE, capture_output=True, text=True).stdout.split()
print("文件数:", len(files))

# 3. 逐个创建 blob（只对内容变化的文件，先比对远端 tree）
remote_tree = api("GET", f"/repos/{REPO}/git/trees/{base_tree}?recursive=1")
remote_files = {t["path"]: t.get("sha") for t in remote_tree.get("tree", []) if t["type"] == "blob"}

tree_entries = []
changed = []
for rel in files:
    full = os.path.join(BASE, rel)
    with open(full, "rb") as f:
        content = f.read()
    blob = api("POST", f"/repos/{REPO}/git/blobs", {
        "content": base64.b64encode(content).decode(),
        "encoding": "base64"
    })
    tree_entries.append({
        "path": rel, "mode": "100755" if os.access(full, os.X_OK) else "100644",
        "type": "blob", "sha": blob["sha"]
    })
    if remote_files.get(rel) != blob["sha"]:
        changed.append(rel)

print("变更文件:", changed if changed else "(无变化)")

# 4. 创建 tree（基于远端 tree，覆盖全部文件）
new_tree = api("POST", f"/repos/{REPO}/git/trees", {
    "base_tree": base_tree,
    "tree": tree_entries
})
print("新 tree:", new_tree["sha"])

# 5. 创建 commit
msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], cwd=BASE,
                     capture_output=True, text=True).stdout.strip()
new_commit = api("POST", f"/repos/{REPO}/git/commits", {
    "message": msg, "tree": new_tree["sha"], "parents": [parent]
})
print("新 commit:", new_commit["sha"])

# 6. 更新 ref
api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {
    "sha": new_commit["sha"], "force": False
})
print("✅ 已推送 main →", new_commit["sha"])
