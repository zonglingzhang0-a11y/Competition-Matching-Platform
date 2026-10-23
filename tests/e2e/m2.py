"""M2 接口端到端测试：比赛库、关注、管理员接口、个人档案。"""
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
    results.append(cond)
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(email, nickname, password="abc12345"):
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": password, "nickname": nickname})
    assert s == 200, r
    return r["data"]["token"], r["data"]["me"]


print("== 准备账号")
stu, stu_me = register("stu@test.local", "学生甲")
adm, adm_me = register("admin@test.local", "管理员")
check("配置里的邮箱注册后是管理员", adm_me["role"] == "ADMIN", adm_me["role"])
check("普通邮箱注册后是学生", stu_me["role"] == "STUDENT")

print("== 比赛列表")
s, r = call("GET", "/competitions", token=stu)
check("不带筛选返回全部 5 个", s == 200 and r["data"]["total"] == 5, r)
first = r["data"]["records"][0]
check("高教学会目录里的排在前面", first["inCaheList"] is True)
s, r = call("GET", "/competitions", token=stu, params={"keyword": "建模"})
check("按关键字搜到数学建模", r["data"]["total"] == 1 and "数学建模" in r["data"]["records"][0]["name"], r["data"])
math = r["data"]["records"][0]
ed = math["currentEdition"]
check("当前届是示例届次", ed and ed["title"] == "2026 年（示例数据）", ed)
check("报名阶段算对了（距截止 5 天内 → 报名中/即将截止）", ed["phase"] in ("OPEN", "CLOSING_SOON"), ed["phase"])
s, r = call("GET", "/competitions", token=stu, params={"category": "数学"})
check("按类别筛选", r["data"]["total"] == 1)
s, r = call("GET", "/competitions", token=stu, params={"openOnly": "true"})
check("只看还能报名的：2 个有在报名的届次", r["data"]["total"] == 2, r["data"]["total"])
s, r = call("GET", "/competitions", token=stu, params={"page": 1, "size": 2})
check("分页：每页 2 条，总数仍为 5", len(r["data"]["records"]) == 2 and r["data"]["total"] == 5)
s, r = call("GET", "/competitions")
check("没登录不能看（401）", s == 401)

print("== 关注")
s, r = call("GET", "/competitions", token=stu, params={"followedOnly": "true"})
check("一开始没有关注", r["data"]["total"] == 0)
s, r = call("POST", f"/editions/{ed['id']}/follow", token=stu)
check("关注成功", s == 200, r)
s, r = call("POST", f"/editions/{ed['id']}/follow", token=stu)
check("重复关注不报错", s == 200, r)
s, r = call("GET", "/competitions", token=stu, params={"followedOnly": "true"})
check("只看我关注的：1 个", r["data"]["total"] == 1 and r["data"]["records"][0]["currentEdition"]["followed"])
s, r = call("POST", "/editions/99999/follow", token=stu)
check("关注不存在的届次（404）", s == 404, r)

print("== 比赛详情")
s, r = call("GET", f"/competitions/{math['id']}", token=stu)
d = r["data"]
check("分工模板 3 个角色", d["roles"] == ["建模", "编程", "论文写作与排版"], d["roles"])
e0 = d["editions"][0]
check("关注人数 1，已关注", e0["followers"] == 1 and e0["followed"])
check("3 个时间节点，按时间排好", [n["title"] for n in e0["nodes"]] == ["报名截止", "比赛进行（72 小时）", "成绩公布"])
result_node = e0["nodes"][2]
# 示例届次的日期按导入当天往后排（seed.sql）：成绩最晚第 55 天
check("成绩公布是区间结束（RANGE）", result_node["endMode"] == "RANGE" and result_node["endLatestAt"].startswith((__import__("datetime").date.today() + __import__("datetime").timedelta(days=55)).isoformat()))
s, r = call("DELETE", f"/editions/{ed['id']}/follow", token=stu)
s, r = call("GET", f"/competitions/{math['id']}", token=stu)
check("取消关注后人数归 0", r["data"]["editions"][0]["followers"] == 0)
s, r = call("GET", "/competitions/99999", token=stu)
check("比赛不存在（404）", s == 404)

print("== 管理员接口")
form = {"name": "测试杯程序设计大赛", "category": "计算机", "level": "PROVINCIAL", "inCaheList": False}
s, r = call("POST", "/admin/competitions", form, token=stu)
check("学生调管理员接口（403）", s == 403, s)
s, r = call("POST", "/admin/competitions", form, token=adm)
check("管理员新建比赛", s == 200, r)
cid = r["data"]
s, r = call("POST", "/admin/competitions", form, token=adm)
check("同名比赛（409）", s == 409, r)
s, r = call("POST", "/admin/competitions", {**form, "name": ""}, token=adm)
check("名称为空（400）", s == 400 and "比赛名称" in r["message"], r)
edition = {"year": 2026, "title": "2026 年秋季赛", "teamMin": 3, "teamMax": 2,
           "registrationEnd": "2026-11-20T23:59:59", "status": "DRAFT"}
s, r = call("POST", f"/admin/competitions/{cid}/editions", edition, token=adm)
check("最多人数少于最少人数（400）", s == 400 and "人数" in r["message"], r)
s, r = call("POST", f"/admin/competitions/{cid}/editions", {**edition, "teamMin": 1, "teamMax": 3}, token=adm)
check("新建草稿届次", s == 200, r)
eid = r["data"]
s, r = call("GET", f"/competitions/{cid}", token=stu)
check("学生看不到草稿届次", r["data"]["editions"] == [])
s, r = call("GET", f"/admin/competitions/{cid}", token=adm)
check("管理员能看到草稿届次", len(r["data"]["editions"]) == 1)
bad_nodes = {"nodes": [{"title": "比赛", "nodeType": "CONTEST", "startAt": "2026-11-25T09:00:00", "endAt": "2026-11-24T09:00:00"}]}
s, r = call("PUT", f"/admin/editions/{eid}/nodes", bad_nodes, token=adm)
check("节点结束早于开始（400，指出第几个）", s == 400 and r["message"].startswith("第 1 个节点"), r)
nodes = {"nodes": [
    {"title": "初赛", "nodeType": "CONTEST", "startAt": "2026-11-25T09:00:00", "endAt": "2026-11-25T12:00:00", "busy": True},
    {"title": "结果公布", "nodeType": "RESULT", "startAt": "2026-12-01T00:00:00", "endMode": "OPEN", "allDay": True}]}
s, r = call("PUT", f"/admin/editions/{eid}/nodes", nodes, token=adm)
check("保存 2 个节点", s == 200, r)
s, r = call("PUT", f"/admin/editions/{eid}", {**edition, "teamMin": 1, "teamMax": 3, "status": "PUBLISHED"}, token=adm)
check("发布届次", s == 200, r)
s, r = call("GET", f"/competitions/{cid}", token=stu)
eds = r["data"]["editions"]
check("发布后学生能看到，节点 2 个", len(eds) == 1 and len(eds[0]["nodes"]) == 2, eds)
check("OPEN 节点没有结束时间", eds[0]["nodes"][1]["endMode"] == "OPEN" and eds[0]["nodes"][1]["endAt"] is None)
s, r = call("DELETE", f"/admin/competitions/{cid}", token=adm)
s, r = call("GET", "/competitions", token=stu)
check("删除后列表里没有了（逻辑删除）", r["data"]["total"] == 5)

print("== 个人档案")
s, r = call("GET", "/profile", token=stu)
check("读取档案", s == 200 and r["data"]["nickname"] == "学生甲" and r["data"]["skills"] == [])
s, r = call("PUT", "/profile", {"nickname": "学生甲乙", "major": "计算机科学与技术", "enrollYear": 2023,
                                 "degree": "UNDERGRAD", "gender": "", "contactQq": "123456"}, token=stu)
check("保存资料", s == 200, r)
s, r = call("PUT", "/profile", {"nickname": "学生甲乙", "major": "", "contactQq": ""}, token=stu)
s, r = call("GET", "/profile", token=stu)
p = r["data"]
check("清空选填项后真的变成空", p["major"] is None and p["contactQq"] is None and p["enrollYear"] is None, p)
check("昵称改了，me 也跟着变", call("GET", "/users/me", token=stu)[1]["data"]["nickname"] == "学生甲乙")
s, r = call("PUT", "/profile", {"nickname": "x", "contactQq": "12ab"}, token=stu)
check("QQ 格式不对（400）", s == 400 and "QQ" in r["message"], r)
s, skills = call("GET", "/skills", token=stu)
check("技能字典扩充到 70 项以上（V10 之后）", len(skills["data"]) >= 70)
check("技能字典里有新加的技能", any(x["name"] == "FPGA" for x in skills["data"])
      and any(x["name"] == "问卷调查" for x in skills["data"]))
vue = next(x["id"] for x in skills["data"] if x["name"] == "Vue")
java = next(x["id"] for x in skills["data"] if x["name"] == "Java")
s, r = call("PUT", "/profile/skills", {"skills": [{"skillId": vue, "level": 3}, {"skillId": vue, "level": 1}]}, token=stu)
check("同一技能填两次（400）", s == 400, r)
s, r = call("PUT", "/profile/skills", {"skills": [{"skillId": vue, "level": 4}]}, token=stu)
check("熟练度 4（400）", s == 400, r)
s, r = call("PUT", "/profile/skills", {"skills": [{"skillId": 99999, "level": 2}]}, token=stu)
check("不存在的技能（400）", s == 400, r)
s, r = call("PUT", "/profile/skills", {"skills": [{"skillId": java, "level": 2}, {"skillId": vue, "level": 3}]}, token=stu)
check("保存 2 项技能，按熟练度排序", s == 200 and [x["name"] for x in r["data"]] == ["Vue", "Java"], r)
s, r = call("PUT", "/profile/skills", {"skills": [{"skillId": java, "level": 1}]}, token=stu)
check("整体替换：只剩 Java 入门", [(x["name"], x["level"]) for x in r["data"]] == [("Java", 1)], r)
s, r = call("POST", "/profile/experiences", {"competitionId": math["id"], "title": "全国大学生数学建模竞赛",
                                              "year": 2025, "award": "省级二等奖"}, token=stu)
check("添加履历", s == 200 and r["data"]["source"] == "MANUAL", r)
exp_id = r["data"]["id"]
s, r = call("POST", "/profile/experiences", {"title": "", "year": 2025}, token=stu)
check("比赛名称为空（400）", s == 400)
s, r = call("POST", "/profile/experiences", {"title": "某比赛", "year": 2025, "proofUrl": "javascript:alert(1)"}, token=stu)
check("证明链接不是 http(s)（400，防止点开执行脚本）", s == 400 and "http" in r["message"], r)
s, r = call("POST", "/profile/experiences", {"title": "某比赛", "year": 2025, "proofUrl": "https://example.edu.cn/list.pdf"}, token=stu)
check("https 的证明链接可以", s == 200 and r["data"]["proofUrl"] == "https://example.edu.cn/list.pdf", r)
call("DELETE", f"/profile/experiences/{r['data']['id']}", token=stu)
other, _ = register("other@test.local", "学生乙")
s, r = call("DELETE", f"/profile/experiences/{exp_id}", token=other)
check("删别人的履历（404）", s == 404, r)
s, r = call("DELETE", f"/profile/experiences/{exp_id}", token=stu)
check("删自己的履历", s == 200)

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
