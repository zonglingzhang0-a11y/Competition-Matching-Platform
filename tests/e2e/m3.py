"""M3 接口端到端测试：建队、招募需求、申请和邀请、退队、转让、解散、站内信。"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request

API = env.API
LOG = sys.argv[1]
results = []


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


def register(email, nickname):
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": nickname})
    assert s == 200, r
    return r["data"]["token"]


def verify(token, edu):
    s, r = call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    assert s == 200, r
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200 and r["data"]["verifyStatus"] == "VERIFIED", r


def unread(token):
    return call("GET", "/notifications/unread-count", token=token)[1]["data"]


print("== 准备：第二所学校、6 个已认证用户、1 个未认证用户")
sql("INSERT INTO school (name) VALUES ('另一所大学'); "
    "INSERT INTO school_email_domain (school_id, domain) SELECT id, 'other.edu.cn' FROM school WHERE name = '另一所大学';")
cap, a, b, c, d = (register(f"{n}@test.local", n) for n in ("cap", "a", "b", "c", "d"))
for tok, n in ((cap, "cap"), (a, "a"), (b, "b"), (c, "c"), (d, "d")):
    verify(tok, f"{n}@stu.example.edu.cn")
y = register("y@test.local", "y")
verify(y, "y@other.edu.cn")
x = register("x@test.local", "x")  # 不认证
s, comps = call("GET", "/competitions", token=cap, params={"keyword": "数学建模"})
math_cid = comps["data"]["records"][0]["id"]
edition = comps["data"]["records"][0]["currentEdition"]["id"]
s, skills = call("GET", "/skills", token=cap)
sid = {k["name"]: k["id"] for k in skills["data"]}
call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": 3}]}, a)

print("== 建队")
form = {"editionId": edition, "name": "三人行", "intro": "冲省奖", "meetingMode": "OFFLINE"}
s, r = call("POST", "/teams", form, x)
check("没认证不能建队（403）", s == 403 and "认证" in r["message"], r)
s, r = call("POST", "/teams", {**form, "maxMembers": 5}, cap)
check("人数超出这届的上限（400）", s == 400 and "1 到 3 人" in r["message"], r)
s, r = call("POST", "/teams", form, cap)
check("建队成功", s == 200, r)
team = r["data"]
s, r = call("POST", "/teams", {**form, "name": "第二支"}, cap)
check("同一届不能建第二支（409）", s == 409, r)
s, t = call("GET", f"/teams/{team}", token=cap)
t = t["data"]
check("队长自动是队员，人数上限默认取这届的最大值", t["memberCount"] == 1 and t["maxMembers"] == 3 and t["members"][0]["captain"])
check("队长能看到自己的联系方式栏", t["members"][0]["contact"] is not None)

print("== 招募需求")
need_form = {"roleName": "编程", "headcount": 1, "note": "会 Python", "skills": [{"skillId": sid["Python"], "minLevel": 2}]}
s, r = call("POST", f"/teams/{team}/needs", need_form, a)
check("不是队长不能加需求（403）", s == 403, r)
s, r = call("POST", f"/teams/{team}/needs", {**need_form, "skills": [{"skillId": sid["Python"], "minLevel": 2}, {"skillId": sid["Python"], "minLevel": 3}]}, cap)
check("同一技能填两次（400）", s == 400, r)
s, need_code = call("POST", f"/teams/{team}/needs", need_form, cap)
need_code = need_code["data"]
s, need_paper = call("POST", f"/teams/{team}/needs", {"roleName": "论文写作与排版", "headcount": 1, "skills": []}, cap)
need_paper = need_paper["data"]
s, t = call("GET", f"/teams/{team}", token=a)
check("两条需求，技能要求带上了", len(t["data"]["needs"]) == 2 and t["data"]["needs"][0]["skills"][0]["name"] == "Python")
check("路人看不到队员联系方式", t["data"]["members"][0]["contact"] is None)

print("== 找人和申请")
call("POST", f"/editions/{edition}/follow", token=a)
call("POST", f"/editions/{edition}/follow", token=x)
s, r = call("GET", f"/teams/{team}/seekers", token=cap)
names = [k["user"]["nickname"] for k in r["data"]]
check("可邀请的人：关注了且已认证的 a 在，未认证的 x 不在", "a" in names and "x" not in names, names)
s, r = call("POST", f"/teams/{team}/applications", {"needId": need_code, "message": "我会 Python"}, a)
check("a 申请加入（编程）", s == 200, r)
app_a = r["data"]
check("队长收到通知", unread(cap) == 1)
s, r = call("POST", f"/teams/{team}/applications", {"message": "再申请一次"}, a)
check("重复申请（409）", s == 409 and "已经申请过" in r["message"], r)
s, r = call("POST", f"/teams/{team}/applications", {}, y)
check("跨校申请被拦（这届要求同校）", s == 400 and "同校" in r["message"], r)
s, r = call("POST", f"/teams/{team}/applications", {}, x)
check("没认证不能申请（403）", s == 403, r)
s, t = call("GET", f"/teams/{team}", token=a)
check("a 看到自己的待处理申请", t["data"]["me"]["pendingApplication"] is not None)
s, r = call("POST", f"/teams/{team}/invitations", {"userId": 99999}, cap)
check("邀请不存在的用户", s in (400, 401, 404), r)
b_id = call("GET", "/users/me", token=b)[1]["data"]["id"]
s, r = call("POST", f"/teams/{team}/invitations", {"userId": b_id, "needId": need_paper, "message": "来写论文吧"}, cap)
check("队长邀请 b（论文）", s == 200, r)
check("b 收到邀请通知", unread(b) == 1)
s, r = call("POST", f"/teams/{team}/applications", {}, b)
check("被邀请了还去申请：提示直接接受邀请（409）", s == 409 and "接受邀请" in r["message"], r)

print("== 处理")
s, r = call("POST", f"/applications/{app_a}/accept", token=a)
check("申请人自己不能同意自己（403）", s == 403, r)
s, r = call("POST", f"/applications/{app_a}/accept", token=cap)
check("队长同意 a", s == 200, r)
s, r = call("POST", f"/applications/{app_a}/accept", token=cap)
check("重复处理（409）", s == 409, r)
s, t = call("GET", f"/teams/{team}", token=a)
t = t["data"]
a_member = next(m for m in t["members"] if m["user"]["nickname"] == "a")
check("a 入队，角色是编程", t["memberCount"] == 2 and a_member["roleName"] == "编程")
check("编程这条需求招满了", next(n for n in t["needs"] if n["id"] == need_code)["status"] == "FILLED")
check("a 入队后能看到队友联系方式", t["members"][0]["contact"] is not None)
s, r = call("POST", "/teams", {**form, "name": "a 自己的队"}, a)
check("a 已经在队里，不能再建队（409）", s == 409, r)
s, r = call("POST", f"/teams/{team}/applications", {}, c)
check("c 申请（不选需求）", s == 200, r)
app_c = r["data"]
s, r = call("GET", "/applications/mine", token=b)
inv_b = r["data"]["received"][0]
check("b 在我的邀请里看到邀请", inv_b["status"] == "PENDING" and inv_b["needRoleName"] == "论文写作与排版")
s, r = call("POST", f"/applications/{inv_b['id']}/accept", token=b)
check("b 接受邀请", s == 200, r)
s, t = call("GET", f"/teams/{team}", token=cap)
check("满员了（3/3），状态变 FULL", t["data"]["status"] == "FULL" and t["data"]["memberCount"] == 3, t["data"]["status"])
s, r = call("GET", "/applications/mine", token=c)
check("满员后 c 的申请自动作废", r["data"]["sent"][0]["status"] == "EXPIRED", r["data"]["sent"][0]["status"])
s, r = call("POST", f"/teams/{team}/applications", {}, d)
check("满员后 d 不能申请", s == 400 and "满员" in r["message"], r)

print("== 退队、移出、转让、解散")
s, r = call("POST", f"/teams/{team}/leave", token=cap)
check("队长不能直接退队", s == 400 and "转给" in r["message"], r)
before = unread(cap)
s, r = call("POST", f"/teams/{team}/leave", token=a)
check("a 退队", s == 200, r)
s, t = call("GET", f"/teams/{team}", token=cap)
t = t["data"]
check("退队后回到招募中，编程名额还回去了", t["status"] == "RECRUITING" and next(n for n in t["needs"] if n["id"] == need_code)["filled"] == 0)
check("队长收到退队通知", unread(cap) == before + 1)
s, r = call("DELETE", f"/teams/{team}/members/{b_id}", token=a)
check("非队长不能移人（403）", s == 403, r)
s, r = call("DELETE", f"/teams/{team}/members/{b_id}", token=cap)
check("队长移出 b", s == 200, r)
check("b 收到被移出的通知", any(n["type"] == "MEMBER_REMOVED" for n in call("GET", "/notifications", token=b)[1]["data"]["records"]))
s, r = call("POST", f"/teams/{team}/applications", {}, c)
app_c2 = r["data"]
call("POST", f"/applications/{app_c2}/accept", token=cap)
c_id = call("GET", "/users/me", token=c)[1]["data"]["id"]
s, r = call("PUT", f"/teams/{team}/captain", {"userId": c_id}, cap)
check("队长转给 c", s == 200, r)
s, t = call("GET", f"/teams/{team}", token=c)
check("c 是队长了", t["data"]["me"]["captain"] and t["data"]["captainId"] == c_id)
s, r = call("POST", f"/teams/{team}/leave", token=cap)
check("原队长现在可以退队", s == 200, r)

print("== 停止招募")
s, r = call("PUT", f"/teams/{team}/recruiting", {"open": False}, c)
check("c 停止招募", s == 200, r)
s, r = call("POST", f"/teams/{team}/applications", {}, d)
check("停止招募后不能申请", s == 400 and "停止招募" in r["message"], r)
s, r = call("PUT", f"/teams/{team}/recruiting", {"open": True}, c)
s, r = call("POST", f"/teams/{team}/applications", {}, d)
check("重新开放后可以申请", s == 200, r)

print("== 解散")
s, r = call("POST", f"/teams/{team}/disband", token=d)
check("非队长不能解散（403）", s == 403, r)
s, r = call("POST", f"/teams/{team}/disband", token=c)
check("c 解散队伍", s == 200, r)
s, r = call("GET", f"/teams/{team}", token=c)
check("解散后详情 404", s == 404, r)
s, r = call("GET", f"/editions/{edition}/teams", token=d)
check("本届队伍列表里没有了", all(tm["id"] != team for tm in r["data"]))
s, r = call("GET", "/applications/mine", token=d)
check("解散后 d 的申请作废", r["data"]["sent"][0]["status"] == "EXPIRED")
s, r = call("POST", "/teams", {**form, "name": "重新开始"}, c)
check("解散后可以重新建队", s == 200, r)

print("== 报名截止")
sql("UPDATE competition_edition SET registration_end = '2026-01-01 00:00:00' WHERE id = %d" % edition)
s, r = call("POST", "/teams", {**form, "name": "太晚了"}, d)
check("报名截止后不能建队", s == 400 and "截止" in r["message"], r)
sql("UPDATE competition_edition SET registration_end = '2026-10-15 23:59:59' WHERE id = %d" % edition)

print("== 站内信")
s, r = call("GET", "/notifications", token=a)
check("a 有申请通过的通知", any(n["type"] == "APPLY_RESULT" for n in r["data"]["records"]))
first = r["data"]["records"][0]
s, r = call("POST", f"/notifications/{first['id']}/read", token=b)
check("不能把别人的消息标为已读（404）", s == 404, r)
s, r = call("POST", f"/notifications/{first['id']}/read", token=a)
check("标为已读", s == 200)
call("POST", "/notifications/read-all", token=a)
check("全部已读后未读数为 0", unread(a) == 0)

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
