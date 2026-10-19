"""个人日程"时间说不准"的接口测试。库是 schema + seed 的空库（示例届次按今天往后排：数模比赛第 17–20 天）。
用法：python e2e_fuzzy.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, timedelta

API = env.API
LOG = sys.argv[1]
results = []
TODAY = date.today()


def day(n, t="00:00:00"):
    return f"{(TODAY + timedelta(days=n)).isoformat()}T{t}"


def call(method, path, body=None, token=None, params=None, raw=False):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            text = res.read().decode("utf-8")
            return res.status, (text if raw else json.loads(text))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l and "验证码" in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    return r["data"]["token"]


def form(**kw):
    f = {"title": "复习线代", "startAt": day(16), "endAt": None, "allDay": True, "repeatType": "NONE",
         "repeatWeekdays": [], "repeatUntil": None, "busy": True, "startLatestAt": None, "endMode": "FIXED", "endLatestAt": None}
    f.update(kw)
    return f


u = register("fuzzy")
comps = call("GET", "/competitions", token=u)[1]["data"]["records"]
E = next(x for x in comps if "数学建模" in x["name"])["currentEdition"]["id"]
call("POST", f"/editions/{E}/follow", token=u)

print("== 新建说不准的日程")
s, r = call("POST", "/events", form(startLatestAt=day(19), endMode="RANGE", endAt=day(21), endLatestAt=day(30)), u)
check("开始在第 16–19 天之间、预计第 21–30 天结束，能保存", s == 200, r)
fid = r.get("data")
s, v = call("GET", f"/events/{fid}", token=u)
v = v.get("data") or {}
check("读回来字段都在", v.get("startLatestAt") == day(19) and v.get("endMode") == "RANGE"
      and v.get("endAt") == day(21) and v.get("endLatestAt") == day(30), v)
check("说不准的日程自动不参与撞期检测", v.get("busy") is False, v)

s, r = call("POST", "/events", form(title="找队友", endMode="OPEN", endAt=day(20)), u)
oid = r.get("data")
s, v = call("GET", f"/events/{oid}", token=u)
v = v.get("data") or {}
check("做完为止：不存结束时间", s == 200 and v.get("endMode") == "OPEN" and v.get("endAt") is None, v)

# 一条确定的日程，和比赛时间（第 17–20 天）重叠，作对照
s, r = call("POST", "/events", form(title="社团换届", startAt=day(18, "14:00:00"), endAt=day(18, "16:00:00"), allDay=False), u)
fixed = r.get("data")

print("== 日历")
s, cal = call("GET", "/calendar", token=u, params={"from": TODAY.isoformat(), "to": (TODAY + timedelta(days=60)).isoformat()})
cal = cal["data"]
fe = next((e for e in cal["events"] if e["layer"] == "PERSONAL" and e["refId"] == fid), None)
check("日历上标成时间未定", fe and fe["fuzzy"] is True, fe)
check("显示到最晚结束那天", fe and fe["end"] == day(30), fe)
check("和比赛重叠也不算撞期", fe and fe["conflictWith"] is None, fe)
fx = next((e for e in cal["events"] if e["refId"] == fixed), None)
check("对照：确定的日程和比赛重叠，照样标撞期", fx and fx["conflictWith"], fx)
check("撞期列表里只有那条确定的", len(cal["conflicts"]) == 1 and "社团换届" in cal["conflicts"][0]["first"], cal["conflicts"])
oe = next((e for e in cal["events"] if e["refId"] == oid), None)
check("做完为止的也在日历上", oe and oe["fuzzy"] is True, oe)

print("== 不合法的组合")
cases = [
    ("说不准的不能重复", form(startLatestAt=day(19), repeatType="WEEKLY", repeatWeekdays=[1]), "不能重复"),
    ("最晚开始早于最早开始", form(startLatestAt=day(10)), "最晚开始"),
    ("范围缺最晚结束", form(endMode="RANGE", endAt=day(20)), "最早和最晚"),
    ("最晚结束早于最早结束", form(endMode="RANGE", endAt=day(25), endLatestAt=day(20)), "最晚结束"),
    ("结束早于最晚开始", form(startLatestAt=day(25), endAt=day(20)), "最晚开始"),
    ("开始拖太久", form(startLatestAt=day(60)), "31 天"),
    ("整段太长", form(endMode="RANGE", endAt=day(20), endLatestAt=day(120)), "90 天"),
]
for name, f, word in cases:
    s, r = call("POST", "/events", f, u)
    check(f"{name}（400）", s == 400 and word in (r.get("message") or ""), (s, r))

print("== 改回确定的时间")
s, r = call("PUT", f"/events/{fid}", form(endAt=day(20)), u)
s2, v = call("GET", f"/events/{fid}", token=u)
v = v.get("data") or {}
check("改回确定：说不准的字段清空，撞期检测按表单打开", s == 200 and v.get("endMode") == "FIXED" and v.get("startLatestAt") is None
      and v.get("endLatestAt") is None and v.get("busy") is True, (s, r, v))

print("== 日历订阅")
call("PUT", f"/events/{fid}", form(startLatestAt=day(19), endMode="RANGE", endAt=day(21), endLatestAt=day(30)), u)
path = call("GET", "/calendar/feed", token=u)[1]["data"]["path"]
s, ics = call("GET", path.replace("/api", "", 1), raw=True)
block = next((b for b in ics.split("BEGIN:VEVENT") if "复习线代" in b), "")
check("订阅里有这条日程，并说明时间还没定", s == 200 and "时间还没定" in block.replace("\r\n ", ""), block[:300])

print("== 全天日程的结束日（以前多算一天）")
s, r = call("POST", "/events", form(title="交报名表", startAt=day(5)), u)
one = r.get("data")
s, r = call("POST", "/events", form(title="社团招新", startAt=day(6), endAt=day(8)), u)
three = r.get("data")
s, cal = call("GET", "/calendar", token=u, params={"from": TODAY.isoformat(), "to": (TODAY + timedelta(days=20)).isoformat()})
evs = {e["refId"]: e for e in cal["data"]["events"] if e["layer"] == "PERSONAL"}
check("一天的全天日程：结束就是那一天", evs.get(one, {}).get("end") == day(5), evs.get(one))
check("三天的全天日程：结束是最后一天", evs.get(three, {}).get("end") == day(8), evs.get(three))
s, ics = call("GET", path.replace("/api", "", 1), raw=True)
b1 = next((b for b in ics.split("BEGIN:VEVENT") if "交报名表" in b), "")
d6 = (TODAY + timedelta(days=6)).strftime("%Y%m%d")
check("订阅里一天的全天日程 DTEND 是第二天（不含）", f"DTEND;VALUE=DATE:{d6}" in b1, b1[:300])

errors = [l for l in open(LOG, encoding="utf-8", errors="replace") if " ERROR " in l]
check("后端没有 ERROR 日志", not errors, errors[:3])
print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
