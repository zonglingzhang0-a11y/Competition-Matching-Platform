"""批量撮合（只给建议）：还没组队的人看到和哪些同样还没组队的同学一起组合适。库是刚导入的 schema + seed + demo。
演示数据里数学建模那届，林晓、孙悦、李想、周可关注了、还没组队；陈一舟、王思远、赵子航、钱多多都在队里。
用法：python groups.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import sys
import urllib.error
import urllib.request

API = env.API
PWD = env.demo_password()
results = []


def call(path, token=None):
    h = {"Authorization": "Bearer " + token} if token else {}
    try:
        with urllib.request.urlopen(urllib.request.Request(API + path, headers=h)) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode() or "{}")


def login(name):
    body = json.dumps({"email": f"{name}@demo.local", "password": PWD}).encode()
    req = urllib.request.Request(API + "/auth/login", data=body, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req) as r:
        d = json.loads(r.read().decode())["data"]
    return d["token"], d["me"]["id"]


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def groups(token, edition):
    return call(f"/editions/{edition}/suggested-groups", token)[1]["data"]


def names(g):
    return sorted(m["nickname"] for m in g["members"])


lin, _ = login("linxiao")
chen, _ = login("chen")
wu, _ = login("wu")
math = env.sql("SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id "
               "WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）'")
cs = env.sql("SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id "
             "WHERE c.name = '中国大学生计算机设计大赛' AND e.title = '2026 年（示例数据）'")
zhou = env.sql("SELECT id FROM sys_user WHERE email = 'zhou@demo.local'")

print("== 林晓看数学建模")
s, r = call(f"/editions/{math}/suggested-groups")
check("没登录看不了（401）", s == 401, s)
d = groups(lin, math)
gs = d["groups"]
check("给出了建议的组合，最多 3 种", 1 <= len(gs) <= 3 and d["note"] is None, d)
check("数模每队最多 3 人：每种组合是我加另外 2 个人", d["groupSize"] == 3 and all(len(g["members"]) == 2 for g in gs), d)
pool = {"孙悦", "李想", "周可"}
check("组合里只有同样关注了、还没组队的同学（不含已经在队里的、不含自己）",
      all(set(names(g)) <= pool for g in gs), [names(g) for g in gs])
check("没有重复的组合", len({tuple(names(g)) for g in gs}) == len(gs))
scores = [g["match"]["score"] for g in gs]
check("按匹配度从高到低", scores == sorted(scores, reverse=True) and all(0 < x <= 100 for x in scores), scores)
check("带推荐理由和维度分（和推荐队伍一样的格式）",
      all(isinstance(g["match"]["reasons"], list) and g["match"]["dimensions"] for g in gs) and gs[0]["match"]["veto"] is None)
check("不显示联系方式（只有昵称、学校、专业、技能这些公开信息）",
      all("contact" not in m and "email" not in m and "realName" not in m for g in gs for m in g["members"]), gs[0]["members"][0])

print("== 不给建议的情况")
d = groups(chen, math)
check("已经在队里：告诉他在哪支队，不给建议", d["currentTeamId"] and d["groups"] == [], d)
d = groups(wu, math)
check("还没认证：提示先认证", d["groups"] == [] and "认证" in d["note"], d)
d = groups(lin, cs)
check("计设那届关注的人里没人还没组队：提示人不够", d["groups"] == [] and "不够" in d["note"], d)

print("== 暂停推荐的人不出现")
env.sql(f"UPDATE user_credit SET suspended_until = NOW() + INTERVAL 30 DAY WHERE user_id = {zhou}")
d = groups(lin, math)
check("周可被暂停推荐后，建议里没有他", d["groups"] and all("周可" not in names(g) for g in d["groups"]), d)
env.sql(f"UPDATE user_credit SET suspended_until = NULL WHERE user_id = {zhou}")

print("== 报名截止后")
env.sql(f"UPDATE competition_edition SET registration_end = NOW() - INTERVAL 1 DAY WHERE id = {math}")
d = groups(lin, math)
check("报名截止了就不给建议", d["groups"] == [] and "截止" in d["note"], d)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
