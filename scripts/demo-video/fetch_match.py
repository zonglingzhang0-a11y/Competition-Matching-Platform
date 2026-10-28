"""从录制用的后端取一条真实的推荐结果和它用的权重，给打分讲解动画用（动画里的每个数都从这里来）。
用法：python scripts/demo-video/fetch_match.py [队伍名] [输出 json]"""
import json
import os
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("E2E_PORT", "18093")
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402

team_name = sys.argv[1] if len(sys.argv) > 1 else "数模冲刺队"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "演示视频" / "match.json"


def call(method, path, token=None, body=None):
    req = urllib.request.Request(env.API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["data"]


def login(name):
    return call("POST", "/auth/login", body={"email": f"{name}@demo.local", "password": env.demo_password()})["token"]


student = login("linxiao")
recs = call("GET", "/recommendations/home", student)
print("主页推荐的结构：", type(recs).__name__, list(recs[0].keys()) if isinstance(recs, list) and recs else recs)
print(json.dumps(recs, ensure_ascii=False)[:1500])
admin = login("admin")
configs = call("GET", "/admin/match-configs", admin)
print("配置的结构：", list(configs.keys()) if isinstance(configs, dict) else type(configs).__name__)
print(json.dumps(configs, ensure_ascii=False)[:1500])
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(json.dumps({"recs": recs, "configs": configs}, ensure_ascii=False, indent=1), encoding="utf-8")
