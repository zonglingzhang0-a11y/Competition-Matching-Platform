"""已知问题 1、2 的修复验证。
1. 已认证的人用未收录学校的邮箱重新认证，审核期间保留原认证；驳回后原认证不变；通过后换成新学校。
2. 队长被暂停推荐或停用时，他的队伍不再推荐给学生；停用时队伍停止招募、待处理申请作废、队员收到通知。
后端要用 --app.admin-emails[0]=admin@test.local 启动，库是 schema + seed 的空库。"""
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
    return r["data"]


def me(token):
    return call("GET", "/users/me", token=token)[1]["data"]


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


def pending_id(admin, email):
    s, r = call("GET", "/admin/verifications", token=admin, params={"status": "PENDING"})
    return next(i["id"] for i in r["data"]["records"] if i["eduEmail"] == email)


admin = register("admin")
comps = call("GET", "/competitions", token=admin)[1]["data"]["records"]
E = next(x for x in comps if "数学建模" in x["name"])["currentEdition"]["id"]

print("== 问题 1：已认证的人换学校")
v = register("v")
verify(v, "v@stu.example.edu.cn")
r = verify(v, "v@newu2.edu.cn")
check("提交未收录学校的邮箱：账号仍是已认证", r["verifyStatus"] == "VERIFIED" and "保留原来的认证" in r["message"], r)
m = me(v)
check("学校还是示例大学", m["verifyStatus"] == "VERIFIED" and m["school"]["name"] == "示例大学", m)
check("最近一次申请在等审核", call("GET", "/verify/latest", token=v)[1]["data"]["status"] == "PENDING")
s, r = call("POST", "/teams", {"editionId": E, "name": "换学校也能建队"}, v)
check("审核期间照样能建队", s == 200, r)
s, r = call("POST", f"/admin/verifications/{pending_id(admin, 'v@newu2.edu.cn')}/reject", {"note": "查不到这所学校"}, admin)
check("管理员驳回", s == 200, r)
m = me(v)
check("驳回后原来的认证不变", m["verifyStatus"] == "VERIFIED" and m["school"]["name"] == "示例大学", m)
check("通知里说原认证不受影响", any("原来的认证不受影响" in (n["content"] or "") for n in notes(v)))
verify(v, "v@newu3.edu.cn")
s, r = call("POST", f"/admin/verifications/{pending_id(admin, 'v@newu3.edu.cn')}/approve",
            {"newSchoolName": "新三大学", "domain": "newu3.edu.cn"}, admin)
check("再提交一个、管理员通过：换成新学校", s == 200 and me(v)["school"]["name"] == "新三大学", (s, r, me(v)))

w = register("w")
r = verify(w, "w@newu4.edu.cn")
check("没认证过的人：照旧进入待确认", r["verifyStatus"] == "PENDING" and me(w)["verifyStatus"] == "PENDING", r)
call("POST", f"/admin/verifications/{pending_id(admin, 'w@newu4.edu.cn')}/reject", {"note": "查不到"}, admin)
check("驳回后变回未认证，通知让他换邮箱", me(w)["verifyStatus"] == "NONE"
      and any("可以换一个学校邮箱" in (n["content"] or "") for n in notes(w)))

print("== 问题 2：违规队长的队伍不再推荐")
cap = register("cap")
verify(cap, "cap@stu.example.edu.cn")
mem = register("mem")
verify(mem, "mem@stu.example.edu.cn")
stu = register("stu")
verify(stu, "stu@stu.example.edu.cn")
cap_id = me(cap)["id"]
team = call("POST", "/teams", {"editionId": E, "name": "队长会出事"}, cap)[1]["data"]
app = call("POST", f"/teams/{team}/applications", {}, mem)[1]["data"]
call("POST", f"/applications/{app}/accept", token=cap)
call("POST", f"/editions/{E}/follow", token=stu)


def recommended(token):
    s, r = call("GET", f"/editions/{E}/recommended-teams", token=token)
    return [i["team"]["id"] for i in r["data"]["items"]]


check("一开始会推荐这支队", team in recommended(stu), recommended(stu))
sql(f"INSERT INTO user_credit (user_id, rated_rounds, suspended_until) VALUES ({cap_id}, 0, NOW() + INTERVAL 1 DAY)")
check("队长被暂停推荐：不再推荐他的队", team not in recommended(stu), recommended(stu))
sql(f"UPDATE user_credit SET suspended_until = NULL WHERE user_id = {cap_id}")
check("解除暂停后又能推荐", team in recommended(stu), recommended(stu))
sql(f"UPDATE user_credit SET suspended_until = NOW() - INTERVAL 1 DAY WHERE user_id = {cap_id}")
check("暂停已过期的不受影响", team in recommended(stu), recommended(stu))

apply_id = call("POST", f"/teams/{team}/applications", {"message": "想加入"}, stu)[1]["data"]
s, r = call("PUT", f"/admin/users/{cap_id}/status", {"status": "BANNED"}, admin)
check("管理员停用队长", s == 200, r)
s, r = call("GET", f"/teams/{team}", token=mem)
check("他的队伍停止招募", r["data"]["status"] == "CLOSED", r["data"]["status"])
mine = call("GET", "/applications/mine", token=stu)[1]["data"]
status = next((a["status"] for group in mine.values() if isinstance(group, list) for a in group if a["id"] == apply_id), None)
check("学生发给这支队的申请作废", status == "EXPIRED", mine)
check("队员收到通知", any("已停止招募" in n["title"] for n in notes(mem)))
check("推荐里也没有了", team not in recommended(stu), recommended(stu))

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
