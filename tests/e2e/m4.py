"""M4 接口端到端测试：问卷读写和校验、推荐队伍、推荐队员、撞期否决、首页推荐。"""
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


def user(name, verify=True):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    if verify:
        edu = f"{name}@stu.example.edu.cn"
        call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
        s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
        assert s == 200, r
    return token


def bits(*positions):
    return sum(1 << p for p in positions)


EVENINGS = bits(2, 5, 8, 11, 14)
WEEKENDS = bits(15, 16, 18, 19)


def general(g2="B", g4=EVENINGS, g8="B", g9=("implementer", "finisher"), g10="A"):
    return {"G1": ["interest", "postgrad"], "G2": g2, "G3": "B", "G4": g4, "G5": 2, "G6": "B", "G7": "B", "G8": g8,
            "G9": list(g9), "G10": g10, "G11": ["missing", "freeride", "sloppy"], "F1": "执行力强"}


def edition(p1, p4, p2=12, p5=("none",)):
    return {"P1": p1, "P2": p2, "P3": "B", "P4": list(p4), "P5": list(p5), "P6": ["by_contribution", "pre_agreed"],
            "P7": ["shared_doc", "git"], "PLEDGE": True}


print("== 准备")
admin = user("admin", verify=False)
s, comps = call("GET", "/competitions", token=admin, params={"keyword": "数学建模"})
math_comp = comps["data"]["records"][0]["id"]
E = comps["data"]["records"][0]["currentEdition"]["id"]
s, skills = call("GET", "/skills", token=admin)
sid = {k["name"]: k["id"] for k in skills["data"]}

print("== 问卷")
s, r = call("GET", "/survey", token=admin)
qs = r["data"]["questions"]
check("问卷 22 题，7 题在快速版", len(qs) == 22 and sum(q["inQuick"] for q in qs) == 7, (len(qs), sum(q["inQuick"] for q in qs)))
cand = user("cand")
s, r = call("GET", "/survey/general", token=cand)
check("没填过时返回空", s == 200 and r["data"] is None)
s, r = call("PUT", "/survey/general", {"answers": {"G11": ["missing"]}, "quick": True}, cand)
check("快速版缺时段格子（400）", s == 400 and "有空" in r["message"], r)
s, r = call("PUT", "/survey/general", {"answers": {"G4": EVENINGS, "G11": ["missing"]}, "quick": True}, cand)
check("快速版只填 2 题就能保存", s == 200, r)
s, r = call("PUT", "/survey/general", {"answers": {**general(), "G11": ["missing"]}, "quick": False}, cand)
check("完整版里排序题要选满 3 项（400）", s == 400 and "3" in r["message"], r)
s, r = call("PUT", "/survey/general", {"answers": general(), "quick": False}, cand)
check("完整版保存", s == 200, r)
s, r = call("GET", "/survey/editions/%d" % E, token=cand)
check("参赛意向带上这场比赛的分工角色", r["data"]["roles"] == ["建模", "编程", "论文写作与排版"], r["data"]["roles"])
s, r = call("PUT", "/survey/editions/%d" % E, {"answers": {**edition("A", ["编程", "建模"]), "PLEDGE": False}, "quick": False}, cand)
check("没勾承诺（400）", s == 400, r)
s, r = call("PUT", "/survey/editions/%d" % E, {"answers": edition("A", ["编程", "前端"]), "quick": False}, cand)
check("角色不在这场比赛的分工里（400）", s == 400, r)
s, r = call("PUT", "/survey/editions/%d" % E, {"answers": edition("A", ["编程", "建模"], p5=("none", "exam")), "quick": False}, cand)
check("「没有限制」和其他选项一起选（400）", s == 400 and "没有限制" in r["message"], r)
s, r = call("PUT", "/survey/editions/%d" % E, {"answers": edition("A", ["编程", "建模"]), "quick": False}, cand)
check("参赛意向保存", s == 200, r)
s, r = call("GET", "/survey/editions/%d" % E, token=cand)
check("记下了承诺时间", r["data"]["mine"]["pledgedAt"] is not None)
call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": 3}]}, cand)


def make_team(name, goal, need_role, need_skill, g2="B", g4=EVENINGS, meeting="MIXED"):
    cap = user(name)
    call("PUT", "/survey/general", {"answers": general(g2=g2, g4=g4, g9=("shaper", "coordinator"), g10="B"), "quick": False}, cap)
    call("PUT", "/survey/editions/%d" % E, {"answers": edition(goal, ["建模", "论文写作与排版"]), "quick": False}, cap)
    s, r = call("POST", "/teams", {"editionId": E, "name": name, "meetingMode": meeting}, cap)
    assert s == 200, r
    team = r["data"]
    skills_req = [{"skillId": sid[need_skill], "minLevel": 2}] if need_skill else []
    call("POST", f"/teams/{team}/needs", {"roleName": need_role, "headcount": 1, "skills": skills_req}, cap)
    return cap, team


print("== 推荐队伍")
cap_hi, t_hi = make_team("高手队", "A", "编程", "Python")
cap_zen, t_zen = make_team("佛系队", "C", "论文写作与排版", "LaTeX 排版", g2="D", g4=WEEKENDS)
cap_fun, t_fun = make_team("体验队", "D", "编程", "Python")
s, r = call("GET", f"/editions/{E}/recommended-teams", token=cand)
rec = r["data"]
names = [i["team"]["name"] for i in rec["items"]]
scores = {i["team"]["name"]: i["match"]["score"] for i in rec["items"]}
check("问卷都填了", rec["generalSurveyDone"] and rec["editionSurveyDone"])
check("高手队排第一", names and names[0] == "高手队", (names, scores))
check("体验队（目标冲国一 vs 先体验）被否决，不在推荐里", "体验队" not in names, names)
check("高手队明显高于佛系队", scores.get("高手队", 0) > scores.get("佛系队", 100) + 15, scores)
hi = rec["items"][0]["match"]
check("推荐理由提到能补上编程或想做编程", any("编程" in x for x in hi["reasons"]), hi["reasons"])
check("理由最多 3 条，完整度高", len(hi["reasons"]) <= 3 and hi["completeness"] > 0.8, (hi["reasons"], hi["completeness"]))
zen = next(i["match"] for i in rec["items"] if i["team"]["name"] == "佛系队")
check("佛系队有\"组队前先聊聊\"", zen["talkAbout"] is not None and zen["talkAbout"].startswith("组队前先聊聊"), zen)
print("     分数：", scores)
print("     高手队理由：", hi["reasons"])
print("     佛系队先聊：", zen["talkAbout"])

print("== 推荐队员（队长视角）")
call("POST", f"/editions/{E}/follow", token=cand)
casual = user("casual")
s1, r1 = call("PUT", "/survey/general", {"answers": general(g2="D", g4=WEEKENDS), "quick": False}, casual)
s2, r2 = call("PUT", "/survey/editions/%d" % E, {"answers": edition("D", ["论文写作与排版", "建模"]), "quick": False}, casual)
check("casual 的问卷保存成功（测试数据本身没问题）", s1 == 200 and s2 == 200, (r1, r2))
call("POST", f"/editions/{E}/follow", token=casual)
blank = user("blank")
call("POST", f"/editions/{E}/follow", token=blank)
s, r = call("GET", f"/teams/{t_hi}/recommended-users", token=cap_hi)
order = [(u["user"]["nickname"], u["match"]["score"], u["match"]["veto"]) for u in r["data"]]
check("cand 排第一", order and order[0][0] == "cand", order)
check("目标差太远的 casual 被否决、排在最后", order[-1][0] == "casual" and order[-1][2] is not None, order)
check("队长视角用\"TA / 你们队\"", any("你们队" in x or "TA" in x for x in r["data"][0]["match"]["reasons"] + [r["data"][-1]["match"]["veto"] or ""]), r["data"][0]["match"])
blank_row = next(u for u in r["data"] if u["user"]["nickname"] == "blank")
check("没填问卷的人也能打分，提示数据不全，分数在中间", blank_row["match"]["veto"] is None and blank_row["match"]["completeness"] < 0.3 and blank_row["match"]["score"] == 50.0, blank_row["match"])
s, r = call("GET", f"/teams/{t_hi}/recommended-users", token=cand)
check("非队长不能看（403）", s == 403, r)
print("     顺序：", order)

print("== 首页推荐")
s, r = call("GET", "/recommendations/home", token=cand)
check("首页推荐里有关注的数学建模", len(r["data"]) == 1 and r["data"][0]["edition"]["editionId"] == E and r["data"][0]["teams"][0]["team"]["name"] == "高手队", r["data"])

print("== 撞期否决")
s, r = call("GET", "/competitions", token=admin, params={"keyword": "计算机设计"})
E2 = r["data"]["records"][0]["currentEdition"]["id"]
# 管理员账号在启动参数里配置为 admin@test.local；用它给计算机设计大赛加一个和数学建模比赛时段重叠的节点
# 数模那届的比赛在导入后第 17–20 天（seed.sql），决赛放在第 18–19 天
D18 = (date.today() + timedelta(days=18)).isoformat()
D19 = (date.today() + timedelta(days=19)).isoformat()
nodes = {"nodes": [{"title": "现场决赛", "nodeType": "CONTEST", "startAt": D18 + "T09:00:00", "endAt": D19 + "T18:00:00", "busy": True}]}
s, r = call("PUT", f"/admin/editions/{E2}/nodes", nodes, admin)
check("管理员加了一个重叠的比赛节点", s == 200, r)
s, r = call("POST", "/teams", {"editionId": E2, "name": "计设队"}, cand)
check("cand 在计算机设计大赛建队", s == 200, r)
s, r = call("GET", f"/editions/{E}/recommended-teams", token=cand)
check("数学建模不再推荐，说明撞期", r["data"]["items"] == [] and "重叠" in (r["data"]["note"] or ""), r["data"])
s, r = call("GET", f"/teams/{t_hi}/recommended-users", token=cap_hi)
cand_row = next((u for u in r["data"] if u["user"]["nickname"] == "cand"), None)
check("队长那边 cand 也被标为撞期", cand_row and "重叠" in (cand_row["match"]["veto"] or ""), cand_row and cand_row["match"])

print("== 已经在队里")
s, r = call("GET", f"/editions/{E2}/recommended-teams", token=cand)
check("在这一届已经有队时返回所在队伍", r["data"]["currentTeamId"] is not None and r["data"]["items"] == [])

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
