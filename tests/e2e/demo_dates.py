"""演示比赛日期：旧库（写死 10-15 截止）里同意申请会被拦，跑 demo-refresh-dates.sql 后能同意；新导入的直接能用。
自己重置测试库、自己起停测试后端。用法：python demo_dates.py <日志目录>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import os
import sys
import time
import urllib.error
import urllib.request

LOGDIR = sys.argv[1] if len(sys.argv) > 1 else str(env.HERE / "logs")
API = env.API
results = []
proc = None


def sql(stmt=None, file=None):
    if stmt:
        return env.sql(stmt)
    env.load(file)
    return ""


def reset():
    env.reset(demo=True)


def start():
    global proc
    proc = env.start_backend(os.path.join(LOGDIR, "demo_dates_backend.log"))


def call(method, path, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = "Bearer " + token
    try:
        with urllib.request.urlopen(urllib.request.Request(API + path, data=data, headers=h, method=method)) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


PWD = env.demo_password()


def login(email):
    s, r = call("POST", "/auth/login", {"email": email, "password": PWD})
    assert s == 200, r
    return r["data"]["token"]


def pending_app():
    return sql("SELECT a.id FROM team_application a JOIN sys_user u ON u.id = a.user_id "
               "WHERE u.email = 'sun@demo.local' AND a.direction = 'APPLY' AND a.status = 'PENDING' LIMIT 1")


def math_end():
    return sql("SELECT e.registration_end FROM competition_edition e JOIN competition c ON c.id = e.competition_id "
               "WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）'")


import atexit
atexit.register(lambda: env.stop_backend(proc))

print("== 旧库：日期写死、已经过了")
reset()
sql("UPDATE competition_edition e JOIN competition c ON c.id = e.competition_id "
    "SET e.registration_start = '2026-09-20', e.registration_end = '2026-10-15 23:59:59' "
    "WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）'")
start()
app = pending_app()
check("演示数据里有孙悦的待处理申请", app != "", app)
chen = login("chen@demo.local")
s, r = call("POST", f"/applications/{app}/accept", token=chen)
check("报名截止后，陈一舟同意申请被拦", s != 200 and "截止" in (r.get("message") or ""), (s, r))
env.stop_backend(proc)

print("== 跑 demo-refresh-dates.sql")
out = sql(file=env.ROOT / "sql" / "demo-refresh-dates.sql")
check("脚本跑完，数模报名截止日期在今天之后", math_end() > time.strftime("%Y-%m-%d"), (math_end(), out))
teams = sql("SELECT COUNT(*) FROM team")
start()
chen = login("chen@demo.local")
s, r = call("POST", f"/applications/{app}/accept", token=chen)
check("刷新后陈一舟能同意孙悦的申请", s == 200, (s, r))
check("刷新不删数据", sql("SELECT COUNT(*) FROM team") == teams)
env.stop_backend(proc)

print("== 新导入")
reset()
check("新导入的数模报名截止在今天之后", math_end() > time.strftime("%Y-%m-%d"), math_end())
start()
chen = login("chen@demo.local")
s, r = call("POST", f"/applications/{pending_app()}/accept", token=chen)
check("新导入直接能同意申请", s == 200, (s, r))
lin = login("linxiao@demo.local")
s, r = call("GET", "/recommendations/home", token=lin)
names = [x["edition"]["competitionName"] for x in (r.get("data") or []) if x.get("edition")] if s == 200 else r
check("林晓的首页推荐里有数学建模", s == 200 and any("数学建模" in n for n in names), names)
env.stop_backend(proc)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
