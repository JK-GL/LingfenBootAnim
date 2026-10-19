#!/usr/bin/env python3
"""推送工作区到远端；显式跟随 307/308 重定向（urllib 默认不跟随 POST 的 307）。"""
import json, os, sys, base64, subprocess, urllib.request, urllib.error, time

REPO = "JK-GL/LingfenBootAnim"
BASE = "/var/minis/workspace/lingfen-bootanim"
TOKEN = os.environ["GITHUB_TOKEN_1"]


class Follow307(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if code in (307, 308):
            hdrs = {k: v for k, v in req.headers.items()}
            if "blob.core.windows.net" in newurl:
                hdrs.pop("Authorization", None)
                hdrs.pop("authorization", None)
            print(f"  ↪ {code} -> {newurl.split('?')[0]}", file=sys.stderr)
            return urllib.request.Request(newurl, data=req.data, headers=hdrs, method=req.get_method())
        return super().redirect_request(req, fp, code, msg, headers, newurl)


OPENER = urllib.request.build_opener(Follow307)


def api(method, path, body=None):
    url = "https://api.github.com" + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", "token " + TOKEN)
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("Content-Type", "application/json")
    req.add_header("User-Agent", "Minis-CI")
    last = None
    for attempt in range(6):
        try:
            with OPENER.open(req, timeout=90) as r:
                return json.loads(r.read().decode() or "{}")
        except urllib.error.HTTPError as e:
            txt = e.read().decode()[:300]
            print("API ERROR", e.code, txt, file=sys.stderr)
            if e.code in (429, 500, 502, 503, 504):
                time.sleep(5 * (attempt + 1)); continue
            raise
        except Exception as e:
            last = e
            print(f"  重试 {attempt+1}/6 ({type(e).__name__})", file=sys.stderr)
            time.sleep(3 * (attempt + 1))
    raise last


ref = api("GET", f"/repos/{REPO}/git/ref/heads/main")
parent = ref["object"]["sha"]
base_tree = api("GET", f"/repos/{REPO}/git/commits/{parent}")["tree"]["sha"]
print("远端 HEAD:", parent)

files = subprocess.run(["git", "ls-files"], cwd=BASE, capture_output=True, text=True).stdout.split()
remote_tree = api("GET", f"/repos/{REPO}/git/trees/{base_tree}?recursive=1")
remote_files = {t["path"]: t["sha"] for t in remote_tree.get("tree", []) if t["type"] == "blob"}

entries, changed = [], []
for rel in files:
    full = os.path.join(BASE, rel)
    content = open(full, "rb").read()
    blob = api("POST", f"/repos/{REPO}/git/blobs",
               {"content": base64.b64encode(content).decode(), "encoding": "base64"})
    entries.append({"path": rel, "mode": "100755" if os.access(full, os.X_OK) else "100644",
                    "type": "blob", "sha": blob["sha"]})
    if remote_files.get(rel) != blob["sha"]:
        changed.append(rel)

for path in remote_files:
    if path not in set(files):
        entries.append({"path": path, "mode": "100644", "type": "blob", "sha": None})
        changed.append("删除 " + path)

print("变更:", changed if changed else "(无)")

tree = api("POST", f"/repos/{REPO}/git/trees", {"base_tree": base_tree, "tree": entries})
msg = subprocess.run(["git", "log", "-1", "--pretty=%B"], cwd=BASE, capture_output=True, text=True).stdout.strip()
commit = api("POST", f"/repos/{REPO}/git/commits", {"message": msg, "tree": tree["sha"], "parents": [parent]})
api("PATCH", f"/repos/{REPO}/git/refs/heads/main", {"sha": commit["sha"], "force": False})
print("✅ 已推送 main →", commit["sha"])
