"""M6 接口端到端测试：认证审核、学校和邮箱后缀、账号停用、比赛库管理、数据看板、匹配配置。
后端要用 --app.admin-emails[0]=admin@test.local 启动，数据库是刚导入 schema + seed 的空库。"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
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


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    return r["data"]["token"]


def verify(token, edu):
    s, r = call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    assert s == 200, r
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200, r
    return r["data"]


def me(token):
    return call("GET", "/users/me", token=token)[1]["data"]


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


print("== 准备")
admin = register("admin")
check("admin@test.local 是管理员", me(admin)["role"] == "ADMIN")
stu = register("stu")
verify(stu, "stu@stu.example.edu.cn")

print("== 权限")
for path in ["/admin/verifications", "/admin/schools", "/admin/users", "/admin/competitions",
             "/admin/dashboard/overview", "/admin/dashboard/skill-gap", "/admin/match-configs"]:
    s, r = call("GET", path, token=stu)
    check(f"学生访问 {path}（403）", s == 403, s)

print("== 认证审核")
pend = register("pend")
pend2 = register("pend2")
rej = register("rej")
r1 = verify(pend, "pend@mail.newu.edu.cn")
check("没收录的学校进入待审核", r1["verifyStatus"] == "PENDING", r1)
verify(pend2, "pend2@newu.edu.cn")
verify(rej, "rej@foo.edu.cn")
s, r = call("GET", "/admin/verifications", token=admin, params={"status": "PENDING"})
items = r["data"]["records"]
check("待审核 3 条，先来的在前", [i["eduEmail"] for i in items] == ["pend@mail.newu.edu.cn", "pend2@newu.edu.cn", "rej@foo.edu.cn"], items)
first = items[0]
check("列出可以收录的后缀", first["domains"] == ["mail.newu.edu.cn", "newu.edu.cn"], first["domains"])
check("带上申请人信息", first["user"]["nickname"] == "pend" and first["user"]["email"] == "pend@test.local", first["user"])
s, r = call("GET", "/admin/verifications/pending-count", token=admin)
check("待审核计数 3", r["data"] == 3, r)

s, r = call("POST", f"/admin/verifications/{first['id']}/approve", {"newSchoolName": "新大学", "domain": "evil.edu.cn"}, admin)
check("后缀不是这个邮箱的（400）", s == 400 and "mail.newu.edu.cn" in r["message"], r)
s, r = call("POST", f"/admin/verifications/{first['id']}/approve", {"schoolId": 1, "newSchoolName": "新大学", "domain": "newu.edu.cn"}, admin)
check("学校二选一（400）", s == 400, r)
s, r = call("POST", f"/admin/verifications/{first['id']}/approve", {"domain": "newu.edu.cn"}, admin)
check("学校都没选（400）", s == 400, r)
s, r = call("POST", f"/admin/verifications/{first['id']}/approve",
            {"newSchoolName": "新大学", "province": "浙江", "city": "杭州", "domain": "newu.edu.cn"}, admin)
check("新建学校并通过，同校的 pend2 一起通过", s == 200 and r["data"] == {"schoolName": "新大学", "alsoApproved": 1}, r)
m = me(pend)
check("pend 已认证为新大学", m["verifyStatus"] == "VERIFIED" and m["school"]["name"] == "新大学", m)
check("pend2 也已认证", me(pend2)["verifyStatus"] == "VERIFIED")
check("pend 收到认证通过的通知", any(n["type"] == "VERIFY_RESULT" and "通过" in n["title"] for n in notes(pend)))
s, r = call("POST", f"/admin/verifications/{first['id']}/approve", {"newSchoolName": "新大学", "domain": "newu.edu.cn"}, admin)
check("重复审核（409）", s == 409, r)

rej_id = items[2]["id"]
s, r = call("POST", f"/admin/verifications/{rej_id}/reject", {"note": " "}, admin)
check("不通过要写原因（400）", s == 400, r)
s, r = call("POST", f"/admin/verifications/{rej_id}/reject", {"note": "邮箱和档案里的学校对不上"}, admin)
check("驳回", s == 200, r)
check("rej 变回未认证", me(rej)["verifyStatus"] == "NONE")
s, r = call("GET", "/verify/latest", token=rej)
check("学生能看到最近一次申请和原因", r["data"]["status"] == "REJECTED" and "对不上" in r["data"]["reviewNote"], r)
check("rej 收到没通过的通知", any("没有通过" in n["title"] and "对不上" in n["content"] for n in notes(rej)))
s, r = call("GET", "/verify/latest", token=admin)
check("没提交过认证时 latest 为空", s == 200 and r["data"] is None, r)

verify(rej, "rej@bar.edu.cn")
verify(rej, "rej@baz.edu.cn")
s, r = call("GET", "/admin/verifications", token=admin, params={"status": "PENDING"})
check("重新提交后旧申请作废，只留最新一条", [i["eduEmail"] for i in r["data"]["records"]] == ["rej@baz.edu.cn"], r["data"]["records"])
s, r = call("GET", "/admin/verifications", token=admin)
superseded = next(i for i in r["data"]["records"] if i["eduEmail"] == "rej@bar.edu.cn")
check("作废的那条写明原因", superseded["status"] == "REJECTED" and "重新提交" in superseded["reviewNote"], superseded)
approved = next(i for i in r["data"]["records"] if i["eduEmail"] == "pend@mail.newu.edu.cn")
check("已处理的记录带审核人和学校", approved["reviewer"] == "admin" and approved["school"]["name"] == "新大学", approved)

print("== 学校和邮箱后缀")
s, r = call("GET", "/admin/schools", token=admin, params={"keyword": "newu"})
rows = r["data"]["records"]
check("按后缀搜到新大学，2 人已认证", len(rows) == 1 and rows[0]["name"] == "新大学" and rows[0]["verifiedUsers"] == 2, rows)
new_school = rows[0]["id"]
s, r = call("POST", f"/admin/schools/{new_school}/domains", {"domain": "@BAZ.edu.cn "}, admin)
check("收录后缀（自动去掉 @、转小写），排队的 rej 自动通过", s == 200 and r["data"] == 1, r)
check("rej 已认证", me(rej)["verifyStatus"] == "VERIFIED")
s, r = call("POST", "/admin/schools/1/domains", {"domain": "newu.edu.cn"}, admin)
check("后缀已属于别的学校（409）", s == 409 and "新大学" in r["message"], r)
s, r = call("POST", f"/admin/schools/{new_school}/domains", {"domain": "gmail.com"}, admin)
check("不是 .edu.cn（400）", s == 400, r)
s, r = call("POST", "/admin/schools", {"name": "新大学"}, admin)
check("学校重名（409）", s == 409, r)
s, r = call("GET", "/admin/schools", token=admin, params={"keyword": "新大学"})
baz = next(d for d in r["data"]["records"][0]["domains"] if d["domain"] == "baz.edu.cn")
s, r = call("DELETE", f"/admin/school-domains/{baz['id']}", token=admin)
check("删除后缀", s == 200)

print("== 账号")
s, r = call("GET", "/admin/users", token=admin, params={"keyword": "pend"})
check("搜到 2 个账号，带学校", r["data"]["total"] == 2 and all(u["schoolName"] == "新大学" for u in r["data"]["records"]), r["data"])
admin_id = me(admin)["id"]
stu_id = me(stu)["id"]
s, r = call("PUT", f"/admin/users/{admin_id}/status", {"status": "BANNED"}, admin)
check("不能停用自己（400）", s == 400, r)
s, r = call("PUT", f"/admin/users/{stu_id}/status", {"status": "BANNED"}, admin)
check("停用学生", s == 200, r)
s, r = call("GET", "/users/me", token=stu)
check("被停用的人立刻掉线（401）", s == 401, s)
s, r = call("POST", "/auth/login", {"email": "stu@test.local", "password": "abc12345"})
check("被停用后登录不了（403）", s == 403, r)
s, r = call("GET", "/admin/users", token=admin, params={"status": "BANNED"})
check("按状态筛出停用的账号", [u["email"] for u in r["data"]["records"]] == ["stu@test.local"], r["data"])
call("PUT", f"/admin/users/{stu_id}/status", {"status": "ACTIVE"}, admin)
s, r = call("POST", "/auth/login", {"email": "stu@test.local", "password": "abc12345"})
check("恢复后能登录", s == 200, r)
stu = r["data"]["token"]

print("== 比赛库管理")
s, r = call("GET", "/admin/competitions", token=admin)
check("列表里 5 个种子比赛", r["data"]["total"] == 5, r["data"]["total"])
math = next(c for c in r["data"]["records"] if "数学建模" in c["name"])
E = math["editions"][0]["id"]
check("届次带状态、阶段、节点数", math["editions"][0]["status"] == "PUBLISHED" and math["editions"][0]["nodes"] == 3, math["editions"][0])
s, r = call("GET", "/admin/role-templates", token=admin)
check("分工模板 4 套，带角色", len(r["data"]) == 4 and all(t["roles"] for t in r["data"]), r["data"])
template = r["data"][0]["id"]

form = {"name": "测试杯", "shortName": "测杯", "category": "计算机", "level": "PROVINCIAL", "inCaheList": False,
        "roleTemplateId": template, "matchPreset": "SHORT_INTENSIVE"}
s, r = call("POST", "/admin/competitions", dict(form, officialUrl="javascript:alert(1)"), admin)
check("官网不是 http(s) 网址（400）", s == 400 and "http" in r["message"], (s, r))
s, r = call("POST", "/admin/competitions", form, admin)
cid = r["data"]
s, r = call("POST", f"/admin/competitions/{cid}/editions",
            {"year": 2026, "title": "坏链接", "teamMin": 2, "teamMax": 4, "noticeUrl": "javascript:alert(1)", "status": "DRAFT"}, admin)
check("届次的通知链接不是 http(s)（400）", s == 400 and "http" in r["message"], (s, r))
ed_form = {"year": 2026, "title": "测试杯 2026", "teamMin": 2, "teamMax": 4,
           "registrationStart": "2026-10-01T00:00:00", "registrationEnd": "2026-12-01T23:59:59", "status": "DRAFT"}
s, r = call("POST", f"/admin/competitions/{cid}/editions", ed_form, admin)
eid = r["data"]
check("建比赛和草稿届次", s == 200 and eid, r)
nodes = [{"title": "报名", "nodeType": "REGISTRATION", "startAt": "2026-10-01T00:00:00", "endAt": "2026-12-01T23:59:59", "allDay": True},
         {"title": "决赛", "nodeType": "CONTEST", "startAt": "2026-12-10T09:00:00", "endAt": "2026-12-11T18:00:00", "busy": True}]
s, r = call("PUT", f"/admin/editions/{eid}/nodes", {"nodes": nodes}, admin)
s, r = call("GET", f"/admin/editions/{eid}/nodes", token=admin)
check("保存并读回 2 个节点", [n["title"] for n in r["data"]] == ["报名", "决赛"] and r["data"][1]["busy"], r["data"])
s, r = call("GET", f"/competitions/{cid}", token=stu)
check("学生看不到草稿届次", s == 200 and r["data"]["editions"] == [], r)
call("PUT", f"/admin/editions/{eid}", {**ed_form, "status": "PUBLISHED"}, admin)
s, r = call("GET", f"/competitions/{cid}", token=stu)
check("发布后学生能看到", len(r["data"]["editions"]) == 1, r)
s, r = call("GET", "/admin/competitions", token=admin, params={"keyword": "测杯"})
row = r["data"]["records"][0]
check("列表返回编辑用的原始字段", row["roleTemplateId"] == template and row["matchPreset"] == "SHORT_INTENSIVE" and row["level"] == "PROVINCIAL", row)

cap = register("cap")
verify(cap, "cap@stu.example.edu.cn")
team_test = call("POST", "/teams", {"editionId": eid, "name": "测试队"}, cap)[1]["data"]
s, r = call("DELETE", f"/admin/editions/{eid}", token=admin)
check("有队伍的届次不能删（409）", s == 409 and "1 支队伍" in r["message"], r)
s, r = call("DELETE", f"/admin/competitions/{cid}", token=admin)
check("有队伍的比赛不能删（409）", s == 409, r)
call("POST", f"/teams/{team_test}/disband", token=cap)
s, r = call("DELETE", f"/admin/competitions/{cid}", token=admin)
check("队伍解散后可以删比赛（连同届次）", s == 200, r)
s, r = call("GET", f"/competitions/{cid}", token=stu)
check("删掉后学生端 404", s == 404, r)

print("== 数据看板")
s, skills = call("GET", "/skills", token=admin)
sid = {k["name"]: k["id"] for k in skills["data"]}
team = call("POST", "/teams", {"editionId": E, "name": "看板队"}, cap)[1]["data"]
s, r = call("POST", f"/teams/{team}/needs", {"roleName": "编程", "headcount": 2,
                                             "skills": [{"skillId": sid["Python"], "minLevel": 2}]}, cap)
check("队长发布需求：编程 2 人，要 Python", s == 200, r)
coding_need = r["data"]
call("POST", f"/teams/{team}/needs", {"roleName": "论文", "headcount": 1, "skills": [{"skillId": sid["LaTeX 排版"], "minLevel": 1}]}, cap)
# 数模这一届要求同校，找队的人也用示例大学的邮箱
s1 = register("s1")
verify(s1, "s1@stu.example.edu.cn")
s2 = register("s2")
verify(s2, "s2@stu.example.edu.cn")
for tok, lv in ((s1, 2), (s2, 3)):
    call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": lv}, {"skillId": sid["Vue"], "level": 1}]}, tok)
    call("POST", f"/editions/{E}/follow", token=tok)
call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": 1}]}, rej)  # 没关注，不算
gap = call("GET", "/admin/dashboard/skill-gap", token=admin, params={"editionId": E})[1]["data"]
by = {g["name"]: g for g in gap}
check("Python：需要 2 人，在找队的有 2 人（熟练 1、精通 1）",
      by["Python"]["demand"] == 2 and by["Python"]["supply"] == 2 and by["Python"]["supplyByLevel"] == [0, 1, 1], by.get("Python"))
check("LaTeX：需要 1 人、没人会，缺口最大排第一", gap[0]["name"] == "LaTeX 排版" and gap[0]["gap"] == 1, gap[:2])
check("Vue：没人需要，有 2 人会", by["Vue"]["demand"] == 0 and by["Vue"]["supply"] == 2 and by["Vue"]["gap"] == -2, by.get("Vue"))
all_gap = call("GET", "/admin/dashboard/skill-gap", token=admin)[1]["data"]
check("不选届次时汇总报名中的届次（结果一样）", {g["name"]: g["gap"] for g in all_gap} == {g["name"]: g["gap"] for g in gap})
app_id = call("POST", f"/teams/{team}/applications", {"needId": coding_need}, s1)[1]["data"]
s, r = call("POST", f"/applications/{app_id}/accept", token=cap)
check("s1 申请编程岗，队长通过", s == 200, r)
by = {g["name"]: g for g in call("GET", "/admin/dashboard/skill-gap", token=admin, params={"editionId": E})[1]["data"]}
check("s1 入队后：Python 供给少 1", by["Python"]["supply"] == 1, by["Python"])

ov = call("GET", "/admin/dashboard/overview", token=admin)[1]["data"]
check("概况：8 个账号、7 个已认证、0 条待审核", ov["users"] == 8 and ov["verifiedUsers"] == 7 and ov["pendingVerify"] == 0, ov)
check("概况：1 支在招的队、1 支解散的队", ov["teams"].get("RECRUITING") == 1 and ov["teams"].get("DISBANDED") == 1, ov["teams"])
check("概况：还差 2 个人（编程 1 + 论文 1）", ov["openSlots"] == 2, ov["openSlots"])
check("概况：申请 1 条已通过", ov["applications"].get("ACCEPTED") == 1, ov["applications"])
eds = call("GET", "/admin/dashboard/editions", token=admin)[1]["data"]
row = next(e for e in eds if e["editionId"] == E)
check("届次表：数模 1 支在招、差 2 人、1 人在找队、2 人关注",
      row["recruitingTeams"] == 1 and row["openSlots"] == 2 and row["seekers"] == 1 and row["followers"] == 2, row)
check("报名中的届次排在已截止的前面", [e["phase"] == "CLOSED" for e in eds] == sorted(e["phase"] == "CLOSED" for e in eds), [e["phase"] for e in eds])
s, r = call("GET", "/admin/dashboard/funnel", token=stu, params={"editionId": E})
check("学生看不了组队漏斗（403）", s == 403, s)
s, r = call("GET", "/admin/dashboard/funnel", token=admin, params={"editionId": E})
f = r.get("data") or {}
# 人群：关注的 s1、s2，加上没点关注的队长 cap；s1 申请后入队，cap 建了队；队伍还在招人
check("组队漏斗：3 人里 0 人填了问卷，2 人申请过或建了队，2 人进了队，0 人的队伍组好了",
      s == 200 and [f.get(k) for k in ("people", "surveyed", "engaged", "joined", "formed")] == [3, 0, 2, 2, 0], f)
s, r = call("GET", "/admin/dashboard/calibration", token=stu)
check("学生看不了推荐准不准（403）", s == 403, s)
s, r = call("GET", "/admin/dashboard/calibration", token=admin)
cal = r.get("data") or {}
got = [(b["from"], b["total"], b["accepted"]) for b in cal.get("buckets", []) if b["total"]]
# s1 发申请时记下了匹配分（都没填问卷，接近 50 分），队长同意了
check("推荐准不准：s1 的申请发出时记下了匹配分，落在 40–60 分这一段、算成了；没有没记分的老记录",
      s == 200 and len(cal.get("buckets", [])) == 5 and got == [(40, 1, 1)] and cal.get("unscored") == 0, cal)

print("== 匹配配置")
s, r = call("GET", "/admin/match-configs", token=admin)
page = r["data"]
check("三套配置和题目列表", [p["preset"] for p in page["presets"]] == ["DEFAULT", "SHORT_INTENSIVE", "LONG_TERM"]
      and any(q["code"] == "G2" for q in page["questions"]), page["presets"])
default_raw = page["presets"][0]["config"]
cfg = json.loads(default_raw)

# 候选人 s2 和队长 cap 的 G2（推进节奏）一个最早动手、一个拖到最后，矩阵里只有 0.1 分
for tok, g2 in ((cap, "A"), (s2, "D")):
    s, r = call("PUT", "/survey/general", {"answers": {"G2": g2, "G4": 18724, "G11": ["missing"]}, "quick": True}, tok)
    assert s == 200, r


def score_for_s2():
    s, r = call("GET", f"/editions/{E}/recommended-teams", token=s2)
    item = next(i for i in r["data"]["items"] if i["team"]["id"] == team)
    return item["match"]["score"]


before = score_for_s2()
s, r = call("PUT", "/admin/match-configs/DEFAULT", {"config": "{not json"}, admin)
check("不是 JSON（400）", s == 400 and "JSON" in r["message"], r)
broken = json.loads(default_raw)
broken["dimensions"] = [d for d in broken["dimensions"] if d["key"] != "time"]
s, r = call("PUT", "/admin/match-configs/DEFAULT", {"config": json.dumps(broken, ensure_ascii=False)}, admin)
check("删掉 time 维度会让预设对不上（400）", s == 400 and "SHORT_INTENSIVE" in r["message"] and "time" in r["message"], r)
s, r = call("PUT", "/admin/match-configs/SHORT_INTENSIVE", {"config": '{"dimensionWeights": {"luck": 1}}'}, admin)
check("预设引用不存在的维度（400）", s == 400 and "luck" in r["message"], r)
s, r = call("PUT", "/admin/match-configs/NOPE", {"config": "{}"}, admin)
check("预设名不对（400）", s == 400, r)

g2 = next(i for d in cfg["dimensions"] for i in d["items"] if i["q"] == "G2")
g2["matrix"][0][3] = g2["matrix"][3][0] = 1.0
s, r = call("PUT", "/admin/match-configs/DEFAULT", {"config": json.dumps(cfg, ensure_ascii=False)}, admin)
check("把 G2 的 A–D 改成 1 分，保存成功", s == 200, r)
after = score_for_s2()
check("推荐分数马上变高（配置缓存已清）", after > before, (before, after))
page = call("GET", "/admin/match-configs", token=admin)[1]["data"]
check("记录修改人", page["presets"][0]["updatedBy"] == "admin", page["presets"][0])
s, r = call("PUT", "/admin/match-configs/DEFAULT", {"config": default_raw}, admin)
check("改回原配置", s == 200 and score_for_s2() == before, (before, score_for_s2()))
s, r = call("PUT", "/admin/match-configs/LONG_TERM", {"config": '{"extends": "DEFAULT", "dimensionWeights": {"goal": 0.5}}'}, admin)
check("保存预设", s == 200, r)

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
