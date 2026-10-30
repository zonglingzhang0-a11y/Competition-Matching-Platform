"""新消息汇总发邮件的接口测试。
后端用 --app.admin-emails[0]=admin@test.local --app.notify-mail.delay-seconds=3 --app.notify-mail.interval-ms=1000
--app.notify-mail.initial-delay-ms=1000 启动，库是 schema + seed 的空库，邮件是 log 模式（打印在日志里）。
用法：python e2e_notify_mail.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request

from websockets.sync.client import connect

API = env.API
WS = env.WS
LOG = sys.argv[1]
WAIT = 6  # 等 3 秒 + 检查间隔 1 秒，留点余量
results = []


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


def log_text():
    return open(LOG, encoding="utf-8", errors="replace").read()


def code_for(email):
    lines = [l for l in log_text().splitlines() if email in l and "验证码" in l]
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


def student(name):
    t = register(name)
    verify(t, f"{name}@stu.example.edu.cn")
    return t


def mails_to(name):
    """日志里发给某人的邮件：[(标题, 正文)]"""
    out = []
    parts = log_text().split("[开发模式] 发往 ")
    for p in parts[1:]:
        head, _, rest = p.partition("\n")
        m = re.match(r"(\S+) 的邮件：(.*)", head)
        if m and m.group(1) == f"{name}@test.local":
            # 正文到下一条日志为止
            body = re.split(r"\n\d{4}-\d{2}-\d{2}T", rest)[0]
            out.append((m.group(2), body))
    return out


def sql(stmt):
    r = subprocess.run([*env.mysql_args(), "-N", "-B",
                        env.DB, "-e", stmt], capture_output=True, text=True, encoding="utf-8")
    return r.stdout.strip()


def new_team(token, name):
    s, r = call("POST", "/teams", {"editionId": E, "name": name}, token)
    assert s == 200, r
    return r["data"]


def apply(token, team):
    s, r = call("POST", f"/teams/{team}/applications", {"message": "想加入"}, token)
    assert s == 200, r
    return r["data"]


admin = register("admin")
comps = call("GET", "/competitions", token=admin)[1]["data"]["records"]
E = next(x for x in comps if "计算机设计" in x["name"])["currentEdition"]["id"]  # 数模那届 10-15 截止了

print("== 默认打开")
cap1 = student("cap1")
me = call("GET", "/users/me", token=cap1)[1]["data"]
check("新账号默认打开邮件提醒", me.get("emailNotify") is True, me)

print("== 准备：几个队长各收到申请")
cap2, cap3, cap4 = student("cap2"), student("cap3"), student("cap4")
m1, m2, m3, m4, m5 = (student(f"m{i}") for i in range(1, 6))

# 关掉邮件提醒的人
s, _ = call("PUT", "/users/me/email-notify", {"enabled": False}, cap4)
check("关掉邮件提醒", s == 200 and call("GET", "/users/me", token=cap4)[1]["data"]["emailNotify"] is False)

# cap3 开着网站
ws = connect(WS + "?ticket=" + call("POST", "/notifications/push-ticket", token=cap3)[1]["data"]["ticket"], open_timeout=5)

t1, t2, t3, t4 = (new_team(c, f"邮件测试队{i}") for i, c in enumerate((cap1, cap2, cap3, cap4), 1))
a1 = apply(m1, t1)
apply(m2, t1)
apply(m3, t2)
apply(m4, t3)
apply(m5, t4)

# cap2 马上在网站上看了
n2 = call("GET", "/notifications?page=1&size=20", token=cap2)[1]["data"]["records"]
for n in n2:
    call("POST", f"/notifications/{n['id']}/read", token=cap2)
check("cap2 收到申请后马上看了", len(n2) == 1)
check("刚发的消息还在等，不马上发邮件", not mails_to("cap1"))

time.sleep(WAIT)
print("== 等过了时间")
got = mails_to("cap1")
check("不在线、没看的 cap1 收到 1 封汇总邮件", len(got) == 1, got)
if got:
    subject, body = got[0]
    check("标题写了第一条和总条数", "【竞搭】" in subject and "等 2 条新消息" in subject, subject)
    check("正文列出两条申请和链接", body.count("· ") == 2 and "cap1，你好" in body
          and "http://localhost:5173/notifications" in body, body)
check("两条消息都记上了 emailed_at",
      sql("SELECT COUNT(*) FROM notification n JOIN sys_user u ON u.id = n.user_id "
          "WHERE u.email = 'cap1@test.local' AND n.emailed_at IS NOT NULL") == "2")
check("已经看过的 cap2 不发", not mails_to("cap2"))
check("开着网站的 cap3 不发", not mails_to("cap3"))
check("关掉提醒的 cap4 不发", not mails_to("cap4"))

print("== 离开网站后")
ws.close()
time.sleep(WAIT)
got3 = mails_to("cap3")
check("cap3 关掉网站后，还没看的消息发了邮件", len(got3) == 1 and "等" not in got3[0][0], got3)

print("== 只发一次")
time.sleep(WAIT)
check("cap1 没有收到第二封", len(mails_to("cap1")) == 1)
check("cap3 没有收到第二封", len(mails_to("cap3")) == 1)

print("== 只是告知的消息不发")
call("POST", f"/applications/{a1}/accept", token=cap1)
time.sleep(WAIT)
m1_mails = mails_to("m1")
check("m1 收到入队结果的邮件", len(m1_mails) == 1, m1_mails)
s, r = call("POST", f"/teams/{t1}/leave", token=m1)
check("m1 退队", s == 200, r)
time.sleep(WAIT)
left = sql("SELECT COUNT(*) FROM notification n JOIN sys_user u ON u.id = n.user_id "
           "WHERE u.email = 'cap1@test.local' AND n.type = 'MEMBER_LEFT' AND n.read_at IS NULL AND n.emailed_at IS NULL")
check("cap1 收到了「有队员退出」的站内信，但没发邮件", left == "1" and len(mails_to("cap1")) == 1, (left, len(mails_to("cap1"))))

print("== 重新打开")
s, _ = call("PUT", "/users/me/email-notify", {"enabled": True}, cap4)
time.sleep(WAIT)
check("cap4 重新打开后，3 天内没看的消息补发", len(mails_to("cap4")) == 1, mails_to("cap4"))

print("== 前后差两秒的两条也合成一封")
# created_at 只到秒：以前前后跨了一秒的两条会拆成两封，现在到点时把这个人还没看的都带上
cap5, m6, m7 = student("cap5"), student("m6"), student("m7")
t5 = new_team(cap5, "邮件测试队5")
apply(m6, t5)
time.sleep(2)
apply(m7, t5)
time.sleep(WAIT)
got5 = mails_to("cap5")
check("cap5 只收到 1 封，两条申请都在里面", len(got5) == 1 and got5[0][1].count("· ") == 2, got5)

errors = [l for l in log_text().splitlines() if " ERROR " in l]
check("后端没有 ERROR 日志", not errors, errors[:3])
print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
