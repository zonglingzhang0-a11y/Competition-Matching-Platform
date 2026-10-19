"""#5 登录状态存数据库：重启测试后端后登录还在。
自己重置测试库、自己起停测试后端（要重启好几次，run.py 不替它起后端）。
用法：python login_store.py <日志目录>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

from websockets.sync.client import connect

LOGDIR = sys.argv[1] if len(sys.argv) > 1 else str(env.HERE / "logs")
API = env.API
WS = env.WS
results = []
LOG = None
proc = None


def sql(stmt=None, file=None):
    if stmt:
        return env.sql(stmt)
    env.load(file)


def start(name):
    global LOG, proc
    LOG = os.path.join(LOGDIR, f"login_store_{name}.log")
    proc = env.start_backend(LOG, "--app.admin-emails[0]=admin@test.local")
    return open(LOG, encoding="utf-8", errors="replace").read()


def stop():
    env.stop_backend(proc)


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    return r["data"]["token"], r["data"]["me"]["id"]


def login(name):
    s, r = call("POST", "/auth/login", {"email": f"{name}@test.local", "password": "abc12345"})
    assert s == 200, r
    return r["data"]["token"]


def me(token):
    return call("GET", "/users/me", token=token)[0]


def push_works(token):
    s, r = call("POST", "/notifications/push-ticket", token=token)
    if s != 200:
        return False
    with connect(WS + "?ticket=" + r["data"]["ticket"], open_timeout=5) as ws:
        ws.send(json.dumps({"type": "ping"}))
        return json.loads(ws.recv(timeout=3)).get("type") == "pong"


def rows():
    return int(sql("SELECT COUNT(*) FROM login_store") or 0)


def errors():
    return len([l for l in open(LOG, encoding="utf-8", errors="replace") if " ERROR " in l])


import atexit
atexit.register(lambda: env.stop_backend(proc))
env.reset()

print("一、第一次启动：注册、登录、退出")
text = start("1")
check("空库启动时恢复 0 条", "从数据库恢复了 0 条登录状态" in text)
alice1, alice_id = register("alice")
alice2 = login("alice")
bob, bob_id = register("bob")
admin, _ = register("admin")
gone = login("alice")
check("新登录都能用", all(me(t) == 200 for t in (alice1, alice2, bob, admin, gone)))
n = rows()
check("登录状态写进了 login_store", n >= 8, n)
stored = sql(f"SELECT store_value FROM login_store WHERE store_key = 'Authorization:login:token:{alice1}'")
check("库里存的是凭证对应的账号", stored == str(alice_id), stored)
value_type = sql(f"SELECT value_type FROM login_store WHERE store_key LIKE 'Authorization:login:session:%' LIMIT 1")
check("账号会话按 JSON 对象存", value_type == "O", value_type)
check("退出前库里有这个凭证", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{gone}'") == "1")
s, _ = call("POST", "/auth/logout", token=gone)
check("退出成功", s == 200)
check("退出的凭证从库里删了", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{gone}'") == "0")
check("没有 ERROR 日志", errors() == 0, errors())
stop()

print("二、重启后端")
text = start("2")
m = re.search(r"从数据库恢复了 (\d+) 条登录状态", text)
check("启动时从库里恢复了登录状态", m and int(m.group(1)) >= 6, m.group(0) if m else "没找到")
check("重启前的登录都还有效（同一账号两处登录）", all(me(t) == 200 for t in (alice1, alice2, bob, admin)))
check("退出过的凭证重启后仍然无效", me(gone) == 401)
check("乱写的凭证无效", me("not-a-token") == 401)
check("重启后推送连接用旧凭证照样能连", push_works(alice1))
check("停用前库里有 bob 的凭证", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{bob}'") == "1")
s, _ = call("PUT", f"/admin/users/{bob_id}/status", {"status": "BANNED"}, admin)
check("管理员停用 bob（重启前登录的）", s == 200)
check("bob 立刻被踢下线", me(bob) == 401)
check("bob 的凭证从库里删了", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{bob}'") == "0")
s, _ = call("POST", "/auth/logout", token=alice2)
check("重启前登录的凭证可以正常退出", s == 200 and me(alice2) == 401 and me(alice1) == 200)
fresh = login("alice")
check("重启后新登录可用", me(fresh) == 200)
check("没有 ERROR 日志", errors() == 0, errors())
stop()

print("三、再重启一次")
start("3")
check("被停用的 bob 重启后仍然登录不了", me(bob) == 401)
check("退出的那处重启后仍然无效", me(alice2) == 401)
check("alice 另外两处还在", me(alice1) == 200 and me(fresh) == 200)
check("admin 还在", me(admin) == 200)
check("库里有 admin 的凭证", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{admin}'") == "1")
sql("UPDATE login_store SET expire_at = NOW() - INTERVAL 1 MINUTE WHERE store_key = 'Authorization:login:token:" + admin + "'")
stop()

print("四、库里过期的凭证")
text = start("4")
check("过期的凭证启动时不恢复", me(admin) == 401)
check("过期的行启动时清掉了", sql(f"SELECT COUNT(*) FROM login_store WHERE store_key = 'Authorization:login:token:{admin}'") == "0")
stop()

print("五、旧库没建表：退回纯内存")
sql("DROP TABLE login_store")
text = start("5")
check("启动时提示跑升级脚本", "2026-10-login-store.sql" in text)
t = login("alice")
check("没建表也能登录", me(t) == 200)
check("没有 ERROR 日志", errors() == 0, errors())
stop()

print("六、补跑升级脚本（跑两次都不报错）")
sql(file=env.ROOT / "sql" / "upgrade" / "2026-10-login-store.sql")
sql(file=env.ROOT / "sql" / "upgrade" / "2026-10-login-store.sql")
start("6")
t = login("alice")
stop()
start("7")
check("跑完升级脚本后重启不用重新登录", me(t) == 200)
stop()

print(f"共 {len(results)} 项，通过 {sum(results)} 项，失败 {len(results) - sum(results)} 项")
sys.exit(0 if all(results) else 1)
