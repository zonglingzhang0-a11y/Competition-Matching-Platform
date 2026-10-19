"""日历订阅（ICS）的接口测试。
后端要用 --app.admin-emails[0]=admin@test.local 启动，库是 schema + seed 的空库。
用法：python e2e_feed.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta

BASE = f"http://127.0.0.1:{env.PORT}"
API = BASE + "/api"
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


def fetch(path):
    """不带登录凭证去取，像日历应用那样"""
    try:
        with urllib.request.urlopen(BASE + path) as res:
            return res.status, res.headers.get("Content-Type"), res.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        return e.code, e.headers.get("Content-Type"), e.read().decode("utf-8")


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
    token = r["data"]["token"]
    edu = f"{name}@stu.example.edu.cn"
    call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    return token


def events(ics):
    return re.findall(r"BEGIN:VEVENT\r\n(.*?)END:VEVENT", ics, re.S)


admin = register("admin")
u = register("feeduser")
comps = call("GET", "/competitions", token=u)[1]["data"]["records"]
shumo = next(x for x in comps if "数学建模" in x["name"])
jisheji = next(x for x in comps if "计算机设计" in x["name"])
team = call("POST", "/teams", {"editionId": jisheji["currentEdition"]["id"], "name": "订阅测试队"}, u)[1]["data"]
call("POST", f"/editions/{shumo['currentEdition']['id']}/follow", token=u)
soon = datetime.now() + timedelta(days=5)
call("POST", f"/teams/{team}/milestones", {"title": "交作品初稿", "startAt": soon.strftime("%Y-%m-%dT18:00:00"), "allDay": False}, u)
monday = date.today() - timedelta(days=date.today().weekday())
s, r = call("POST", "/events", {"title": "周例会, 带电脑", "startAt": f"{monday}T19:00:00", "endAt": f"{monday}T20:30:00",
                                 "allDay": False, "repeatType": "WEEKLY", "repeatWeekdays": [1],
                                 "repeatUntil": str(monday + timedelta(weeks=3)), "busy": True}, u)
assert s == 200, r

print("== 拿订阅链接")
s, r = call("GET", "/calendar/feed")
check("不登录拿不到自己的链接（401）", s == 401, (s, r))
s, r = call("GET", "/calendar/feed", token=u)
path = r["data"]["path"]
check("登录后拿到链接 /api/ics/<64 位>.ics", s == 200 and re.fullmatch(r"/api/ics/[0-9a-f]{64}\.ics", path), r)
check("再拿一次还是同一条", call("GET", "/calendar/feed", token=u)[1]["data"]["path"] == path)

print("== 日历应用来取")
s, ctype, ics = fetch(path)
check("不用登录就能取，类型是 text/calendar", s == 200 and ctype.startswith("text/calendar"), (s, ctype))
check("日历名带昵称", "X-WR-CALNAME:竞搭 · feeduser的赛程" in ics.replace("\r\n ", ""), ics[:300])
ev = events(ics)
unfolded = [e.replace("\r\n ", "") for e in ev]
deadline = next((e for e in unfolded if "国赛 · 报名截止（截止）" in e), None)
check("关注的比赛：报名截止，带提前一天提醒", deadline and "BEGIN:VALARM" in deadline and "TRIGGER:-P1D" in deadline, deadline)
check("关注的比赛节点注明关注中", deadline and "比赛官方节点（关注中）" in deadline, deadline)
milestone = next((e for e in unfolded if "订阅测试队 · 交作品初稿（截止）" in e), None)
check("队伍里程碑：带时区的时间", milestone and f"DTSTART;TZID=Asia/Shanghai:{soon.strftime('%Y%m%d')}T180000" in milestone, milestone)
meetings = [e for e in unfolded if "SUMMARY:周例会\\, 带电脑\r\n" in e]
check("每周重复的个人日程展开成 4 次，逗号转义了", len(meetings) == 4 and all("DTEND;TZID=Asia/Shanghai" in e for e in meetings),
      [re.search(r"DTSTART[^:]*:(\S+)", e).group(1) for e in meetings])
contest = next((e for e in unfolded if "比赛进行" in e), None)
check("撞期写进说明：比赛期间和例会冲突", contest and "撞期" in contest and "周例会" in contest, contest)
check("每条日程的 UID 不重复", len({re.search(r"UID:(.*?)\r\n", e).group(1) for e in ev}) == len(ev))
check("每行不超过 75 字节", max(len(l.encode("utf-8")) for l in ics.split("\r\n")) <= 75)

print("== 重置和失效")
s, r = call("POST", "/calendar/feed/reset", token=u)
new_path = r["data"]["path"]
check("重置后换了新链接", s == 200 and new_path != path, r)
check("旧链接失效（404）", fetch(path)[0] == 404)
check("新链接能用", fetch(new_path)[0] == 200)
check("格式不对的链接（404）", fetch("/api/ics/abc.ics")[0] == 404)
check("格式对、但没这个人（404）", fetch("/api/ics/" + "0" * 64 + ".ics")[0] == 404)
uid = call("GET", "/users/me", token=u)[1]["data"]["id"]
call("PUT", f"/admin/users/{uid}/status", {"status": "BANNED"}, admin)
check("账号被停用后链接也失效（404）", fetch(new_path)[0] == 404)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
