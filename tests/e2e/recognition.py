"""各校加分认定：同校同学标注、多人确认才显示、说法不一致时换着确认、改了要重新确认、管理员驳回和恢复。
后端要用 --app.admin-emails[0]=admin@test.local 启动，库是 schema + seed 的空库（脚本自己再加一所学校）。
用法：python recognition.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

API = env.API
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


def register(name, edu_domain="stu.example.edu.cn"):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    if edu_domain:
        edu = f"{name}@{edu_domain}"
        call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
        s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
        assert s == 200, r
    return token


def board(token, cid=None):
    return call("GET", f"/competitions/{cid or math}/recognitions", token=token)[1]["data"]


def entries(token):
    return board(token)["mySchool"]["entries"]


def entry(token, rid):
    return next((e for e in entries(token) if e["id"] == rid), None)


def mine(token, body, cid=None):
    return call("PUT", f"/competitions/{cid or math}/recognitions/mine", body, token)


def confirm(token, rid):
    return call("POST", f"/recognitions/{rid}/confirm", token=token)


def list_label(token):
    rows = call("GET", "/competitions", token=token, params={"keyword": "数学建模"})[1]["data"]["records"]
    return rows[0]["recognition"]


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


# 第二所学校（seed 里只有示例大学）
env.sql("INSERT INTO school (name, province, city) VALUES ('新晴理工大学', '示例省', '示例市')")
env.sql("INSERT INTO school_email_domain (school_id, domain) SELECT id, 'xq.edu.cn' FROM school WHERE name = '新晴理工大学'")

admin = register("admin", None)
a1, a2, a3, a4 = (register(n) for n in ("a1", "a2", "a3", "a4"))
b1 = register("b1", "xq.edu.cn")
n1 = register("n1", None)
math = call("GET", "/competitions", token=a1, params={"keyword": "数学建模"})[1]["data"]["records"][0]["id"]
URL = "https://jwc.example.edu.cn/2025/zongce.pdf"

print("== 看和提交")
b = board(n1)
check("没认证的人能看，没有本校一栏，要 2 人确认", b["mySchool"] is None and b["others"] == [] and b["confirmsNeeded"] == 2, b)
s, r = mine(n1, {"gradeLabel": "A 类"})
check("没认证不能标注（403）", s == 403, (s, r))
s, r = mine(a1, {"gradeLabel": "  "})
check("级别没填（400）", s == 400, (s, r))
s, r = mine(a1, {"gradeLabel": "A 类", "sourceUrl": "javascript:alert(1)"})
check("文件链接不是 http(s)（400）", s == 400 and "http" in r["message"], (s, r))
s, r = mine(a1, {"gradeLabel": " A 类 ", "note": "国一加 3 分，2025 版综测细则", "sourceUrl": URL})
my = (r.get("data") or {}).get("mySchool") or {}
e = (my.get("entries") or [{}])[0]
check("提交成功：本校一栏、是我的、等确认", s == 200 and my["schoolName"] == "示例大学" and my["canContribute"]
      and my["hasMine"] and my["shownId"] is None and e["mine"] and e["gradeLabel"] == "A 类"
      and e["status"] == "PENDING" and e["confirmCount"] == 0, r)
check("不返回是谁提交的", not ({"submittedBy", "submitterId", "userId", "nickname"} & set(e)), e)
r1 = e["id"]
s, r = call("GET", "/competitions/999999/recognitions", token=a1)
check("比赛不存在（404）", s == 404, s)

print("== 确认")
check("自己确认自己的（400）", confirm(a1, r1)[0] == 400)
check("别的学校的人不能确认（403）", confirm(b1, r1)[0] == 403)
check("没认证的人不能确认（403）", confirm(n1, r1)[0] == 403)
s, r = confirm(a2, r1)
e = entry(a2, r1)
check("一位同学确认：1 人，还在等", s == 200 and e["confirmCount"] == 1 and e["status"] == "PENDING"
      and e["confirmedByMe"] and not e["mine"], (s, e))
check("还没确认够：列表上不标", list_label(a1) is None)
check("还没确认够：别的学校看不到", board(b1)["others"] == [])
check("重复确认不重复计数", confirm(a2, r1)[0] == 200 and entry(a2, r1)["confirmCount"] == 1)
s, r = confirm(a3, r1)
b = board(a3)
check("第二位确认：确认够了，对外显示", s == 200 and entry(a3, r1)["status"] == "CONFIRMED" and b["mySchool"]["shownId"] == r1, b)
got = [n for n in notes(a1) if n["type"] == "RECOGNITION"]
check("提交的人收到通知，点开是比赛页", len(got) == 1 and "A 类" in got[0]["content"]
      and got[0]["refType"] == "COMPETITION" and got[0]["refId"] == math, got)
check("本校同学在比赛列表上看到 A 类", list_label(a4) == "A 类")
check("别的学校列表上不标（标的是本校的）", list_label(b1) is None)
o = board(b1)["others"]
check("别的学校在详情页看到示例大学的认定", len(o) == 1 and o[0]["schoolName"] == "示例大学" and o[0]["gradeLabel"] == "A 类"
      and o[0]["sourceUrl"] == URL and o[0]["confirmCount"] == 2, o)
check("没认证的人也能看到各校的", len(board(n1)["others"]) == 1)
call("DELETE", f"/recognitions/{r1}/confirm", token=a3)
check("取消确认：人数不够，退回等待", entry(a1, r1)["status"] == "PENDING" and board(a1)["mySchool"]["shownId"] is None)
confirm(a3, r1)

print("== 说法不一致")
s, r = mine(a4, {"gradeLabel": "B 类", "note": "我们学院的文件写的是 B 类"})
r4 = next(x["id"] for x in r["data"]["mySchool"]["entries"] if x["mine"])
check("另一位同学提交了不同的说法，显示的还是确认够的 A 类", s == 200 and r["data"]["mySchool"]["shownId"] == r1
      and len(r["data"]["mySchool"]["entries"]) == 2, r)
s, r = confirm(a4, r1)
check("自己提交过就不能再确认别人的（409）", s == 409, (s, r))
confirm(a2, r4)
check("改确认 B 类：原来对 A 类的确认自动撤掉", entry(a2, r1)["confirmCount"] == 1 and not entry(a2, r1)["confirmedByMe"]
      and entry(a2, r4)["confirmedByMe"] and entry(a2, r1)["status"] == "PENDING")
confirm(a3, r4)
b = board(a3)["mySchool"]
check("B 类确认够了，改成显示 B 类", b["shownId"] == r4 and entry(a3, r1)["confirmCount"] == 0 and list_label(a1) == "B 类", b)

print("== 修改")
mine(a4, {"gradeLabel": "B 类", "note": "补充：以教务处 9 月的通知为准"})
check("只改说明：确认还在", entry(a4, r4)["confirmCount"] == 2 and entry(a4, r4)["status"] == "CONFIRMED")
mine(a4, {"gradeLabel": "B+ 类", "note": "补充：以教务处 9 月的通知为准"})
check("改了级别：确认清零，重新等确认", entry(a4, r4)["confirmCount"] == 0 and entry(a4, r4)["status"] == "PENDING"
      and not entry(a2, r4)["confirmedByMe"] and list_label(a1) is None)
confirm(a2, r1)
s, r = mine(a2, {"gradeLabel": "C 类"})
check("自己提交一种说法：原来对别人的确认撤掉", s == 200 and entry(a2, r1)["confirmCount"] == 0 and r["data"]["mySchool"]["hasMine"])
s, r = call("DELETE", f"/competitions/{math}/recognitions/mine", token=a2)
check("撤回自己的：这条没了", s == 200 and not r["data"]["mySchool"]["hasMine"] and len(r["data"]["mySchool"]["entries"]) == 2, r)
check("没提交过的撤回（404）", call("DELETE", f"/competitions/{math}/recognitions/mine", token=a2)[0] == 404)

print("== 管理员")
confirm(a2, r1)
confirm(a3, r1)
check("A 类重新确认够了", entry(a1, r1)["status"] == "CONFIRMED")
check("学生不能看管理端（403）", call("GET", "/admin/recognitions", token=a1)[0] == 403)
s, r = call("GET", "/admin/recognitions", token=admin, params={"keyword": "示例"})
rows = r["data"]["records"]
row = next(x for x in rows if x["id"] == r1)
check("管理端按学校搜到，带比赛名、提交人、分歧数", s == 200 and len(rows) == 2 and row["competitionName"] == "全国大学生数学建模竞赛"
      and row["submitterName"] == "a1" and row["variants"] == 2 and row["confirmCount"] == 2, rows)
s, r = call("GET", "/admin/recognitions", token=admin, params={"keyword": "国赛", "status": "CONFIRMED"})
check("按比赛简称和状态筛", s == 200 and [x["id"] for x in r["data"]["records"]] == [r1], r)
check("驳回要写原因（400）", call("POST", f"/admin/recognitions/{r1}/reject", {"note": ""}, admin)[0] == 400)
s, r = call("POST", f"/admin/recognitions/{r1}/reject", {"note": "文件链接打不开"}, admin)
check("驳回", s == 200 and list_label(a2) is None and board(b1)["others"] == [], (s, r))
check("别人那里看不到驳回的", entry(a3, r1) is None)
e = entry(a1, r1)
check("提交的人自己能看到被驳回了", e and e["status"] == "REJECTED", e)
got = [n for n in notes(a1) if n["type"] == "RECOGNITION" and "驳回" in n["title"]]
check("提交的人收到驳回通知，带原因", len(got) == 1 and "文件链接打不开" in got[0]["content"], got)
check("驳回的不能再确认（400）", confirm(a4, r1)[0] == 400)
call("POST", f"/admin/recognitions/{r1}/restore", token=admin)
check("恢复：按确认人数重新算，又显示了", entry(a3, r1)["status"] == "CONFIRMED" and list_label(a2) == "A 类")
call("POST", f"/admin/recognitions/{r1}/reject", {"note": "级别写错了"}, admin)
mine(a1, {"gradeLabel": "A 类", "note": "改了说明", "sourceUrl": URL})
check("被驳回后只改说明：还是驳回", entry(a1, r1)["status"] == "REJECTED")
mine(a1, {"gradeLabel": "A+ 类", "sourceUrl": URL})
check("被驳回后改了级别：重新等确认", entry(a1, r1)["status"] == "PENDING" and entry(a1, r1)["confirmCount"] == 0)

print("== 同时操作")
elec = call("GET", "/competitions", token=a1, params={"keyword": "电子设计"})[1]["data"]["records"][0]["id"]
crowd = [register(f"c{i}") for i in range(6)]
x = next(e["id"] for e in mine(a1, {"gradeLabel": "A 类"}, elec)[1]["data"]["mySchool"]["entries"] if e["mine"])
y = next(e["id"] for e in mine(a2, {"gradeLabel": "B 类"}, elec)[1]["data"]["mySchool"]["entries"] if e["mine"])
before = len([n for n in notes(a1) if n["type"] == "RECOGNITION"])
outs = []
threads = [threading.Thread(target=lambda t=t: outs.append(confirm(t, x)[0])) for t in crowd]
for t in threads:
    t.start()
for t in threads:
    t.join()
ex = next(e for e in board(a1, elec)["mySchool"]["entries"] if e["id"] == x)
check("6 个人同时确认：人数是 6，确认够了", outs == [200] * 6 and ex["confirmCount"] == 6 and ex["status"] == "CONFIRMED", (outs, ex))
check("确认够的通知只发了一次", len([n for n in notes(a1) if n["type"] == "RECOGNITION"]) == before + 1)
# 一半人从 A 换到 B、另一半从 B 换回 A，反复几轮，同时进行
for t in crowd[3:]:
    confirm(t, y)
outs = []


def flip(token, first, second):
    for _ in range(3):
        outs.append(confirm(token, first)[0])
        outs.append(confirm(token, second)[0])


threads = [threading.Thread(target=flip, args=(t, y, x)) for t in crowd[:3]] + \
          [threading.Thread(target=flip, args=(t, x, y)) for t in crowd[3:]]
for t in threads:
    t.start()
for t in threads:
    t.join()
es = {e["id"]: e for e in board(a1, elec)["mySchool"]["entries"]}
check("来回换着确认：没有报错", all(s in (200, 409) for s in outs) and outs.count(200) >= 30, outs)
check("每人最后只算一票：两条加起来 6 人，人数和状态对得上",
      es[x]["confirmCount"] + es[y]["confirmCount"] == 6 and es[x]["confirmCount"] == 3
      and all(e["status"] == ("CONFIRMED" if e["confirmCount"] >= 2 else "PENDING") for e in es.values()), es)

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
