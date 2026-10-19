#!/usr/bin/env python3
"""推送当前工作区为远端最新提交，并正确删除远端已不存在的文件。"""
import json, os, sys, base64, subprocess, urllib.request, urllib.error, time

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
                time.sleep(5 * (attempt + 1)); continue
            raise
        except Exception as e:
            last = e; time.sleep(3 * (attempt + 1))
    raise last

ref = api("GET", f"/repos/{REPO}/git/ref/heads/main")
parent = ref["object"]["sha"]
base_tree = api("GET", f"/repos/{REPO}/git/commits/{parent}")["tree"]["sha"]
print("远端 HEAD:", parent)

files = subprocess.run(["git", "ls-files"], cwd=BASE, capture_output=True, text=True).stdout.split()
remote_tree = api("GET", f"/repos/{REPO}/git/trees/{base_tree}?recursive=1")
remote_files = {t["path"]: t["sha"] for t in remote_tree.get("tree", []) if t["type"] == "blob"}

entries = []
for rel in files:
    full = os.path.join(BASE, rel)
    with open(full, "rb") as f:
        content = f.read()
    blob = api("POST", f"/repos/{REPO}/git/blobs",
               {"content": base64.b64encode(content).decode(), "encoding": "base64"})
    entries.append({"path": rel, "mode": "100755" if os.access(full, os.X_OK) else "100644",
                    "type": "blob", "sha": blob["sha"]})

# 远端有、本地没有 -> 删除
local_set = set(files)
for path in remote_files:
    if path not in local_set:
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        print("删除远端文件:", path)

tree = api("POST", f"/repos/{REPO}/git/trees", {"base_tree": base_tree, "tree": entries})
msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], cwd=BASE, capture_output=True, text=True).stdout.strip()
commit = api("POST", f"/repos/{REPO}/git/commits", {"message": msg, "tree": tree["sha"], "parents": [parent]})
api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": commit["sha"], "force": False})
print("✅ 已推送 main →", commit["sha"])
