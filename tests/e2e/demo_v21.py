"""演示数据里 v2.1 之后的内容：个人日程、撞期、说不准、短板、证明链接、隐私设置、各校加分认定。库是刚导入的 schema + seed + demo。"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

API = env.API
PWD = env.demo_password()
results = []


def call(path, token=None, body=None, method="GET"):
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


tok, uid = {}, {}
for n in ["linxiao", "chen", "sun", "wang"]:
    s, r = call("/auth/login", body={"email": f"{n}@demo.local", "password": PWD}, method="POST")
    tok[n], uid[n] = r["data"]["token"], r["data"]["me"]["id"]

today = time.strftime("%Y-%m-%d")
end = time.strftime("%Y-%m-%d", time.localtime(time.time() + 40 * 86400))
cal = call(f"/calendar?from={today}&to={end}", tok["linxiao"])[1]["data"]
mine = [e for e in cal["events"] if e["layer"] == "PERSONAL"]
eng = [e for e in mine if e["title"] == "英语课"]
check("林晓的英语课每周二、四都有", len(eng) >= 10 and all(time.strptime(e["start"][:10], "%Y-%m-%d").tm_wday in (1, 3) for e in eng),
      [e["start"] for e in eng][:4])
review = next((e for e in mine if e["title"] == "复习线代"), None)
check("复习线代是说不准的（虚线框），不参与撞期", review and review["fuzzy"] and not review["busy"], review)
sport = next((e for e in mine if e["title"] == "校运动会"), None)
check("校运动会是一天的全天日程", sport and sport["allDay"] and sport["end"] == sport["start"], sport)

cal = call(f"/calendar?from={today}&to={end}", tok["chen"])[1]["data"]
check("陈一舟的社团换届和国赛比赛撞期", any("社团换届" in c["first"] for c in cal["conflicts"]), cal["conflicts"])

home = call("/home", tok["linxiao"])[1]["data"]
check("林晓的主页能打开", home is not None, home)

s, p = call(f"/users/{uid['chen']}/profile", tok["sun"])
check("孙悦（往届队友）看陈一舟：看得到短板", p["data"].get("weakness") == "容易一个人闷头做，进度要记得在群里说", p["data"].get("weakness"))
s, p = call(f"/users/{uid['chen']}/profile", tok["linxiao"])
check("林晓看陈一舟：看不到短板", p["data"].get("weakness") is None)
s, p = call(f"/users/{uid['linxiao']}/profile", tok["chen"])
exp = [e for e in p["data"]["experiences"] if e["year"] == 2025]
check("陈一舟看林晓：2025 年的履历有证明，但看不到链接", exp and exp[0]["hasProof"] and exp[0]["proofUrl"] is None, exp)
s, p = call(f"/users/{uid['wang']}/profile", tok["linxiao"])
a = p["data"]["access"]
check("林晓看王思远：履历没公开，有提示", not a["historyVisible"] and a["notice"] and not p["data"]["teams"], a)

rows = call("/competitions?size=20", tok["linxiao"])[1]["data"]["records"]
labels = {c["name"]: c["recognition"] for c in rows}
check("比赛列表：数学建模标着本校 A 类，计设还没确认够", labels.get("全国大学生数学建模竞赛") == "A 类"
      and labels.get("中国大学生计算机设计大赛") is None, labels)
sug = {x["competitionName"]: x["recognition"] for x in call("/home", tok["linxiao"])[1]["data"]["suggestions"]}
check("主页适合报名的比赛也标着本校 A 类", sug.get("全国大学生数学建模竞赛") == "A 类", sug)
exps = call("/profile", tok["chen"])[1]["data"]["experiences"]
math_exps = [e for e in exps if e["title"] == "全国大学生数学建模竞赛" or e["competitionId"] == next(c["id"] for c in rows if c["name"] == "全国大学生数学建模竞赛")]
check("陈一舟档案里数学建模的履历标着本校 A 类", math_exps and all(e["recognition"] == "A 类" for e in math_exps), exps)
cs = next(c["id"] for c in rows if c["name"] == "中国大学生计算机设计大赛")
b = call(f"/competitions/{cs}/recognitions", tok["linxiao"])[1]["data"]["mySchool"]
e = b["entries"][0] if b["entries"] else {}
check("计设：王思远标的 B 类有 1 人确认，林晓可以确认", e.get("gradeLabel") == "B 类" and e.get("confirmCount") == 1
      and b["canContribute"] and not b["hasMine"] and not e.get("confirmedByMe"), b)
s, r = call(f"/recognitions/{e.get('id')}/confirm", tok["linxiao"], method="POST")
b = call(f"/competitions/{cs}/recognitions", tok["linxiao"])[1]["data"]["mySchool"]
notes = call("/notifications?size=50", tok["wang"])[1]["data"]["records"]
check("林晓确认后显示出来，王思远收到通知", s == 200 and b["shownId"] == e.get("id")
      and any(n["type"] == "RECOGNITION" for n in notes), (s, b))

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
