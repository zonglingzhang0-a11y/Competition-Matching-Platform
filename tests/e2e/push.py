"""站内信实时推送（WebSocket）的接口测试。
后端要用 --app.admin-emails[0]=admin@test.local 启动，库是 schema + seed 的空库。
用法：python e2e_push.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from websockets.exceptions import ConnectionClosed, InvalidStatus
from websockets.sync.client import connect

API = env.API
WS = env.WS
LOG = sys.argv[1]
results = []


def call(method, path, body=None, token=None, params=None):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


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
    return r["data"]["token"]


def verify(token, edu):
    call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200, r


def ticket(token):
    s, r = call("POST", "/notifications/push-ticket", token=token)
    assert s == 200, r
    return r["data"]["ticket"]


def open_ws(token, **kw):
    return connect(WS + "?ticket=" + ticket(token), open_timeout=5, **kw)


def recv(ws, timeout=3):
    """下一条推送；超时返回 None"""
    try:
        return json.loads(ws.recv(timeout=timeout))
    except TimeoutError:
        return None


def ping(ws):
    ws.send(json.dumps({"type": "ping"}))
    return recv(ws)


def close_code(ws, timeout=3):
    """发心跳后，服务器关连接时给的关闭码"""
    try:
        ws.send(json.dumps({"type": "ping"}))
        deadline = time.time() + timeout
        while time.time() < deadline:
            ws.recv(timeout=timeout)
    except ConnectionClosed as e:
        return e.rcvd.code if e.rcvd else None
    except TimeoutError:
        return "没关"
    return "没关"


def rejected_status(url, **kw):
    try:
        connect(url, open_timeout=5, **kw).close()
        return "连上了"
    except InvalidStatus as e:
        return e.response.status_code


admin = register("admin")
cap = register("cap")
verify(cap, "cap@stu.example.edu.cn")
mem = register("mem")
verify(mem, "mem@stu.example.edu.cn")
other = register("other")
comps = call("GET", "/competitions", token=admin)[1]["data"]["records"]
E = next(x for x in comps if "数学建模" in x["name"])["currentEdition"]["id"]

print("== 建立连接")
s, r = call("POST", "/notifications/push-ticket")
check("没登录拿不到票（401）", s == 401, (s, r))
check("票不对连不上（401）", rejected_status(WS + "?ticket=deadbeef") == 401)
check("不带票连不上（401）", rejected_status(WS) == 401)
t = ticket(cap)
ws = connect(WS + "?ticket=" + t, open_timeout=5)
check("用票连上了", ws is not None)
check("同一张票不能再用（401）", rejected_status(WS + "?ticket=" + t) == 401)
check("别的网站的页面连不上（来源检查，403）",
      rejected_status(WS + "?ticket=" + ticket(cap), origin="http://evil.example") == 403)
check("心跳有回应", ping(ws) == {"type": "pong"})

print("== 推送新消息")
ws2 = open_ws(cap)
mem_ws = open_ws(mem)
other_ws = open_ws(other)
team = call("POST", "/teams", {"editionId": E, "name": "推送测试队"}, cap)[1]["data"]
t0 = time.time()
s, r = call("POST", f"/teams/{team}/applications", {"message": "想加入"}, mem)
app = r["data"]
m1, m2 = recv(ws), recv(ws2)
elapsed = time.time() - t0
check("队员申请：队长马上收到推送", m1 and m1["type"] == "notification" and m1["unread"] == 1
      and m1["notification"]["refType"] == "TEAM" and m1["notification"]["refId"] == team
      and m1["notification"]["type"] == "APPLY_RECEIVED", m1)
check("推送里带着标题和内容", m1 and m1["notification"]["title"] and m1["notification"]["id"], m1)
check(f"不到 1 秒就到了（{elapsed:.2f}s）", elapsed < 1.0)
check("队长开着的第二个页面也收到了", m2 == m1, m2)
check("不相干的人收不到", recv(other_ws, 1) is None)
check("申请人自己也没收到推送（这次没有发给他的消息）", recv(mem_ws, 1) is None)

call("POST", f"/applications/{app}/accept", token=cap)
m = recv(mem_ws)
check("队长同意后，申请人马上收到结果", m and m["type"] == "notification" and m["notification"]["type"] == "APPLY_RESULT", m)

print("== 已读同步")
nid = m1["notification"]["id"]
s, r = call("POST", f"/notifications/{nid}/read", token=cap)
u1, u2 = recv(ws), recv(ws2)
check("标为已读：两个页面的未读数都变成 0", u1 == {"type": "unread", "unread": 0} and u2 == u1, (u1, u2))
call("POST", f"/notifications/{nid}/read", token=cap)
check("重复标已读不再推", recv(ws, 1) is None)
call("POST", "/notifications/read-all", token=cap)
check("没有未读时全部已读也不推", recv(ws, 1) is None)

print("== 登录失效")
ws2.close()
check("关掉一个页面，另一个照常", ping(ws) == {"type": "pong"})
call("POST", "/auth/logout", token=cap)
check("退出登录后，下一次心跳时连接被关掉（4401）", close_code(ws) == 4401)
other_id = call("GET", "/users/me", token=other)[1]["data"]["id"]
s, r = call("PUT", f"/admin/users/{other_id}/status", {"status": "BANNED"}, admin)
check("管理员停用了一个账号", s == 200, r)
check("被停用的人，下一次心跳时连接被关掉（4401）", close_code(other_ws) == 4401)
check("还登录着的人不受影响", ping(mem_ws) == {"type": "pong"})
mem_ws.close()

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
