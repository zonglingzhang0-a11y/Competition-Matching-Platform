"""二期接口测试：比赛结束、互评、信用分、暂停推荐、举报处理。
后端要用 --app.admin-emails[0]=admin@test.local --app.reminder.interval-ms=3000 --app.reminder.initial-delay-ms=1000 启动，
数据库是刚导入 schema + seed 的空库。"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = env.API
LOG = sys.argv[1]
results = []

NEVER = [0, 0, 0, 0, 0, 0]
ALWAYS = [3, 3, 3, 3, 3, 3]


def sql(statement):
    subprocess.run([*env.mysql_args(), env.DB,
                    "-e", statement], check=True, stdin=subprocess.DEVNULL)


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


def me(token):
    return call("GET", "/users/me", token=token)[1]["data"]


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


def join(team, captain, *members):
    for tok in members:
        s, r = call("POST", f"/teams/{team}/applications", {}, tok)
        assert s == 200, r
        s, r = call("POST", f"/applications/{r['data']}/accept", token=captain)
        assert s == 200, r


def submit(token, round_id, answers_by_user, self_answers=None, my_id=None):
    reviews = [{"rateeId": uid, "answers": a} for uid, a in answers_by_user.items()]
    if self_answers is not None:
        reviews.append({"rateeId": my_id, "answers": self_answers})
    return call("PUT", f"/reviews/{round_id}", {"reviews": reviews}, token)


def credit(token):
    return call("GET", "/credit/me", token=token)[1]["data"]


print("== 准备")
admin = user("admin", verify=False)
cap, a, b, c, d, solo = (user(n) for n in ("cap", "a", "b", "c", "d", "solo"))
ids = {n: me(t)["id"] for n, t in (("cap", cap), ("a", a), ("b", b), ("c", c), ("d", d), ("solo", solo))}
comps = call("GET", "/competitions", token=cap)[1]["data"]["records"]
E_math = next(x for x in comps if "数学建模" in x["name"])["currentEdition"]["id"]
E_cs = next(x for x in comps if "计算机设计" in x["name"])["currentEdition"]["id"]
comp_cs = next(x for x in comps if "计算机设计" in x["name"])["id"]
team = call("POST", "/teams", {"editionId": E_cs, "name": "四人队"}, cap)[1]["data"]
join(team, cap, a, b, c)

print("== 比赛结束")
s, r = call("POST", f"/teams/{team}/finish", {"award": "省级二等奖"}, a)
check("队员不能标记结束（403）", s == 403, r)
s, r = call("POST", f"/teams/{team}/finish", {"award": "省级二等奖"}, cap)
check("队长标记结束，开一轮互评", s == 200 and r["data"], r)
round1 = r["data"]
s, r = call("POST", f"/teams/{team}/finish", {}, cap)
check("重复标记（409）", s == 409, r)
check("队员收到互评通知", any(n["type"] == "REVIEW_OPEN" and n["refId"] == round1 for n in notes(b)))
s, r = call("GET", f"/teams/{team}", token=cap)
check("队伍状态变成比赛结束", r["data"]["status"] == "FINISHED", r["data"]["status"])
s, r = call("POST", f"/teams/{team}/leave", token=b)
check("结束后不能退队（409）", s == 409, r)
s, r = call("POST", f"/teams/{team}/disband", token=cap)
check("结束后不能解散（409）", s == 409, r)
exps = call("GET", "/profile", token=b)[1]["data"]["experiences"]
check("履历里自动记下这场比赛和奖项", any(e["competitionId"] == comp_cs and e["award"] == "省级二等奖"
                                     and e["source"] == "PLATFORM" and e["verified"] for e in exps), exps)

print("== 互评")
s, r = call("GET", "/reviews/mine", token=a)
mine = r["data"]
check("我的互评里有这一轮，还没交", len(mine) == 1 and mine[0]["id"] == round1 and not mine[0]["submitted"]
      and mine[0]["participants"] == 4 and mine[0]["teamName"] == "四人队", mine)
s, r = call("GET", f"/reviews/{round1}", token=a)
form = r["data"]
check("表单：3 位队友 + 最后是自评，6 个问题", [t["self"] for t in form["targets"]] == [False, False, False, True]
      and len(form["items"]) == 6 and form["editable"], form["targets"])
s, r = call("GET", f"/reviews/{round1}", token=d)
check("队外的人看不到（403）", s == 403, r)
s, r = submit(a, round1, {ids["cap"]: NEVER, ids["b"]: NEVER})
check("漏评一位队友（400）", s == 400, r)
s, r = submit(a, round1, {ids["cap"]: NEVER, ids["b"]: NEVER, ids["c"]: [0, 0, 0, 0, 0, 4]})
check("答案超出范围（400）", s == 400, r)
s, r = submit(a, round1, {ids["cap"]: NEVER, ids["b"]: NEVER, ids["c"]: NEVER, ids["d"]: NEVER})
check("评队外的人（400）", s == 400, r)

s, r = submit(a, round1, {ids["cap"]: NEVER, ids["b"]: NEVER, ids["c"]: [3, 3, 0, 0, 0, 0]}, [1, 0, 0, 0, 0, 0], ids["a"])
check("a 交了（含自评），还没揭晓", s == 200 and not r["data"]["published"] and r["data"]["raters"] == 1, r)
s, r = submit(a, round1, {ids["cap"]: NEVER, ids["b"]: NEVER, ids["c"]: [3, 3, 0, 0, 0, 0]}, [1, 0, 0, 0, 0, 0], ids["a"])
check("揭晓前可以改（重复提交不算两个人）", s == 200 and r["data"]["raters"] == 1, r)
submit(b, round1, {ids["cap"]: NEVER, ids["a"]: NEVER, ids["c"]: [3, 0, 0, 0, 0, 0]})
submit(cap, round1, {ids["a"]: NEVER, ids["b"]: NEVER, ids["c"]: [0, 3, 0, 0, 0, 0]})
s, r = call("GET", "/reviews/mine", token=a)
check("进度：3/4 人已交", r["data"][0]["raters"] == 3 and r["data"][0]["submitted"], r["data"])
s, r = submit(c, round1, {ids["cap"]: ALWAYS, ids["a"]: NEVER, ids["b"]: NEVER})
check("最后一个人交完，马上揭晓", s == 200 and r["data"]["published"], r)
s, r = submit(c, round1, {ids["cap"]: NEVER, ids["a"]: NEVER, ids["b"]: NEVER})
check("揭晓后不能再改（409）", s == 409, r)
check("所有人收到揭晓通知", all(any(n["type"] == "REVIEW_RESULT" for n in notes(t)) for t in (cap, a, b, c)))

print("== 信用分")
cr = credit(cap)
check("cap：c 的全打'经常'被判异常、降权，得分 80", cr["score"] == 80.0 and cr["ratedRounds"] == 1, cr)
check("cap：承诺系数 0.6 + 0.4 × 0.8 = 0.92", cr["factor"] == 0.92, cr["factor"])
check("cap：才 1 场、3 个评分人，不显示各项", not cr["itemsVisible"] and cr["items"] is None and cr["raters"] == 3, cr)
cc = credit(c)
check("c：三人都说他失联或拖延，得分 70.37", cc["score"] == 70.37, cc["score"])
check("新用户：没有分，系数 1.0", credit(d)["score"] is None and credit(d)["factor"] == 1.0, credit(d))
s, r = call("GET", "/admin/reviews/flagged", token=admin)
check("管理员能看到被标记的异常评分（c 评 cap）", any(x["rater"] == "c" and x["ratee"] == "cap" for x in r["data"]), r["data"])

print("== 信用分进推荐")
team_d = call("POST", "/teams", {"editionId": E_math, "name": "数模队"}, d)[1]["data"]
call("POST", f"/editions/{E_math}/follow", token=c)
s, r = call("GET", f"/teams/{team_d}/recommended-users", token=d)
item = next((x for x in r["data"] if x["user"]["id"] == ids["c"]), None)
check("队长看到 c 的信用分，推荐里提示", item and item["user"]["credit"] == 70.37
      and any("信用分" in w for w in item["match"]["warnings"]), item)

print("== 两场都失联 → 暂停推荐")
join(team_d, d, c)
round2 = call("POST", f"/teams/{team_d}/finish", {}, d)[1]["data"]
submit(c, round2, {ids["d"]: NEVER})
s, r = submit(d, round2, {ids["c"]: [3, 2, 1, 0, 0, 0]})
check("两人队交齐就揭晓", r["data"]["published"], r)
cc = credit(c)
check("c 第二场又被评为经常失联：暂停推荐 30 天", cc["suspendedUntil"] is not None, cc)
check("c 收到暂停通知", any(n["type"] == "CREDIT" and "暂停推荐" in n["title"] for n in notes(c)))
check("管理员收到复核通知", any("互评复核" in n["title"] for n in notes(admin)))
check("c 有 2 场了，可以看各项分数；自评没做所以没有对比",
      cc["itemsVisible"] and cc["items"][0] > 2 and cc["selfItems"] is None and cc["ratedRounds"] == 2, cc)
s, r = call("GET", "/admin/credit/suspended", token=admin)
row = next((x for x in r["data"] if x["userId"] == ids["c"]), None)
check("管理端暂停列表里有 c（不是永久）", row and not row["permanent"], r["data"])
s, r = call("POST", f"/admin/credit/{ids['c']}/lift", token=admin)
check("管理员解除暂停", s == 200 and credit(c)["suspendedUntil"] is None, r)
s, r = call("POST", f"/admin/credit/{ids['c']}/lift", token=admin)
check("没被暂停的人不能再解除（404）", s == 404, r)

print("== 到期自动揭晓、截止前提醒")
team_a = call("POST", "/teams", {"editionId": E_math, "name": "两人队"}, a)[1]["data"]
join(team_a, a, b)
round3 = call("POST", f"/teams/{team_a}/finish", {}, a)[1]["data"]
submit(a, round3, {ids["b"]: NEVER})
sql(f"UPDATE peer_review_round SET closes_at = NOW() + INTERVAL 10 HOUR WHERE id = {round3}")
time.sleep(6)
check("截止前 24 小时提醒还没交的 b", any(n["type"] == "REVIEW_OPEN" and "互评快截止" in n["title"] for n in notes(b)))
check("已经交了的 a 不提醒", not any("互评快截止" in n["title"] for n in notes(a)))
sql(f"UPDATE peer_review_round SET closes_at = NOW() - INTERVAL 1 MINUTE WHERE id = {round3}")
time.sleep(6)
s, r = call("GET", "/reviews/mine", token=b)
r3 = next(x for x in r["data"] if x["id"] == round3)
check("过了截止时间自动揭晓", r3["status"] == "PUBLISHED", r3)
check("没人评的 a 收到'信用分不变'", any("没有人评价你" in (n["content"] or "") for n in notes(a)))
cb = credit(b)
check("b 两场都是满分：100，可以看各项（全是 0）", cb["score"] == 100.0 and cb["ratedRounds"] == 2 and cb["itemsVisible"]
      and cb["items"] == [0] * 6, cb)
ca = credit(a)
check("a 只有第一场：自评对比要等满 2 场", ca["ratedRounds"] == 1 and ca["selfItems"] is None, ca)

print("== 单人队")
team_solo = call("POST", "/teams", {"editionId": E_math, "name": "独狼"}, solo)[1]["data"]
s, r = call("POST", f"/teams/{team_solo}/finish", {"award": "成功参赛"}, solo)
check("单人队没有互评，只记履历", s == 200 and r["data"] is None and call("GET", "/reviews/mine", token=solo)[1]["data"] == [], r)

print("== 举报")
s, r = call("POST", "/reports", {"targetType": "USER", "targetId": ids["c"], "reason": "ABUSE", "detail": "群里骂人"}, a)
check("举报用户", s == 200, r)
user_report = r["data"]
s, r = call("POST", "/reports", {"targetType": "USER", "targetId": ids["c"], "reason": "SPAM"}, a)
check("同一个人还没处理完不能重复举报（409）", s == 409, r)
s, r = call("POST", "/reports", {"targetType": "USER", "targetId": ids["a"], "reason": "SPAM"}, a)
check("不能举报自己（400）", s == 400, r)
s, r = call("POST", "/reports", {"targetType": "TEAM", "targetId": team_d, "reason": "SPAM", "detail": "招募信息是广告"}, a)
team_report = r["data"]
check("举报队伍", s == 200, r)
s, r = call("GET", "/admin/reports", token=a)
check("学生进不了管理端（403）", s == 403, r)
s, r = call("GET", "/admin/reports/pending-count", token=admin)
check("待处理 2 条", r["data"] == 2, r)
s, r = call("GET", "/admin/reports", token=admin, params={"status": "PENDING"})
rows = r["data"]["records"]
check("列表带举报人、对象名称和补充信息", rows[0]["reporter"] == "a" and rows[0]["targetName"] == "c"
      and rows[0]["targetSub"] == "c@test.local" and rows[1]["targetSub"] == "队长 d", rows)
s, r = call("POST", f"/admin/reports/{user_report}/handle", {"result": "CONFIRMED", "action": "CLOSE_TEAM", "note": "x"}, admin)
check("举报的是人却选关闭队伍（400）", s == 400, r)
s, r = call("POST", f"/admin/reports/{user_report}/handle", {"result": "CONFIRMED", "action": "BLOCK", "note": " "}, admin)
check("处理说明必填（400）", s == 400, r)
s, r = call("POST", f"/admin/reports/{user_report}/handle", {"result": "CONFIRMED", "action": "BLOCK", "note": "聊天记录属实"}, admin)
check("核实属实：不再出现在推荐里", s == 200, r)
row = next(x for x in call("GET", "/admin/credit/suspended", token=admin)[1]["data"] if x["userId"] == ids["c"])
check("c 被永久停止推荐", row["permanent"], row)
check("c 收到通知", any("不会再出现在推荐里" in n["title"] for n in notes(c)))
check("举报人收到处理结果", any(n["type"] == "REPORT_RESULT" and "属实" in n["content"] for n in notes(a)))
s, r = call("POST", f"/admin/reports/{team_report}/handle", {"result": "DISMISSED", "action": "CLOSE_TEAM", "note": "没看出是广告"}, admin)
check("不属实时忽略处理方式", s == 200, r)
s, r = call("GET", f"/teams/{team_d}", token=a)
check("队伍没被关闭", r["data"]["status"] == "FINISHED", r["data"]["status"])
s, r = call("POST", f"/admin/reports/{team_report}/handle", {"result": "CONFIRMED", "note": "x"}, admin)
check("重复处理（409）", s == 409, r)
s, r = call("GET", "/reports/mine", token=a)
check("我的举报：一条属实、一条不属实", sorted(x["status"] for x in r["data"]) == ["CONFIRMED", "DISMISSED"], r["data"])

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
