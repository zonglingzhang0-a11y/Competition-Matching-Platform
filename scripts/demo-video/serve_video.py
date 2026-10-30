"""录视频用的后端：在测试 MySQL 里建 team_match_video 库、导入演示数据，起 18093 端口的后端，一直跑着（Ctrl+C 停）。
不碰平时用的 team_match 库，也不和接口测试（team_match_e2e、18090）抢。数据库连接和接口测试一样用 E2E_DB_* 环境变量。
用法：python scripts/demo-video/serve_video.py [后端 jar] [日志文件]"""
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
jar = sys.argv[1] if len(sys.argv) > 1 else str(ROOT / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar")
log = sys.argv[2] if len(sys.argv) > 2 else str(ROOT / "演示视频" / "backend.log")
Path(log).parent.mkdir(parents=True, exist_ok=True)
os.environ.update(E2E_DB="team_match_video", E2E_PORT="18093", E2E_JAR=jar)
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402

env.reset(demo=True)
proc = env.start_backend(log, "--app.admin-emails[0]=admin@demo.local")
print("ready", flush=True)
proc.wait()
