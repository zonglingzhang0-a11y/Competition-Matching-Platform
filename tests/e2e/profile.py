"""PR #12 个人资料页：按关系分级看到的内容。库是 schema + seed + demo（演示账号密码从 sql/demo.sql 开头读，不打印）。
用法：python e2e_pr12.py <仓库目录>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import os
import re
import sys
import urllib.error
import urllib.request

API = env.API
results = []
PWD = env.demo_password()


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


tok, uid = {}, {}
for n in ["chen", "sun", "zhou", "zhao", "qian", "linxiao", "wu", "admin", "wang"]:
    s, r = call("POST", "/auth/login", {"email": f"{n}@demo.local", "password": PWD})
    assert s == 200, (n, r)
    tok[n] = r["data"]["token"]
    uid[n] = r["data"]["me"]["id"]


def page(viewer, target, preview=False):
    s, r = call("GET", f"/users/{uid[target]}/profile" + ("?preview=true" if preview else ""), token=tok[viewer])
    return s, (r.get("data") or {})


def summary(p):
    a = p.get("access") or {}
    return {"level": a.get("level"), "history": a.get("historyVisible"), "teams": len(p.get("teams") or []),
            "exp": len(p.get("experiences") or []), "contact": p.get("contact") is not None, "bio": p.get("bio") is not None}


print("== 不同关系看陈一舟")
s, p = page("linxiao", "chen")
x = summary(p)
check("没关系的已认证同学：MEMBER，看得到履历（默认公开），看不到联系方式", s == 200 and x["level"] == "MEMBER"
      and x["history"] and x["teams"] > 0 and not x["contact"], x)
s, p = page("wu", "chen")
x = summary(p)
check("还没认证的人：GUEST，只有基本资料", s == 200 and x["level"] == "GUEST" and not x["history"] and x["teams"] == 0
      and not x["contact"] and not x["bio"] and p.get("nickname") == "陈一舟", x)
s, p = page("zhou", "chen")
x = summary(p)
check("往届一起比完赛的周可：算队友，看得到联系方式", s == 200 and x["level"] == "TEAMMATE" and x["contact"], x)
s, p = page("admin", "chen")
x = summary(p)
check("管理员：看得到履历，看不到联系方式", s == 200 and x["level"] == "RELATED" and x["history"] and not x["contact"], x)

print("== 证明链接和短板只给要一起组队的人")
import subprocess
s, r = call("POST", "/profile/experiences", {"title": "全国大学生数学建模竞赛（校赛）", "year": 2024, "award": "校一等奖",
                                            "proofUrl": "https://example.com/cert-chen.jpg"}, tok["chen"])
check("陈一舟加了一条带证书链接的履历", s == 200, r)
subprocess.run([*env.mysql_args(), env.DB, "-e",
                f"UPDATE survey_response SET answers = JSON_SET(answers, '$.F2', '容易拖延，需要队友提醒') "
                f"WHERE user_id = {uid['chen']} AND edition_id IS NULL"], check=True)


def proof_of(p):
    e = next((x for x in p.get("experiences") or [] if x.get("title", "").endswith("（校赛）")), {})
    return e.get("proofUrl"), e.get("hasProof")


s, p = page("linxiao", "chen")
check("没关系的同学：看得到这条履历和\"有证明材料\"，看不到链接", proof_of(p) == (None, True), proof_of(p))
check("没关系的同学：看不到短板", p.get("weakness") is None, p.get("weakness"))
s, p = page("zhou", "chen")
check("一起比完赛的队友：看得到证书链接和短板", proof_of(p)[0] == "https://example.com/cert-chen.jpg"
      and p.get("weakness") == "容易拖延，需要队友提醒", (proof_of(p), p.get("weakness")))
s, p = page("admin", "chen")
check("管理员：看得到证书链接（核对用）", proof_of(p)[0] is not None, proof_of(p))
s, p = page("chen", "chen", preview=True)
check("陈一舟预览别人看到的自己：没有链接、没有短板", proof_of(p) == (None, True) and p.get("weakness") is None,
      (proof_of(p), p.get("weakness")))
s, p = page("chen", "chen")
check("自己看自己：都有", proof_of(p)[0] is not None and p.get("weakness") is not None)

print("== 现在的队友")
s, p = page("qian", "zhao")
x = summary(p)
check("钱多多看队长赵子航：TEAMMATE，有联系方式", x["level"] == "TEAMMATE" and x["contact"], x)

print("== 隐私设置：履历只给有关联的人看")
s, r = call("PUT", "/profile/privacy", {"historyVisibility": "RELATED"}, tok["chen"])
check("陈一舟改成只给有关联的人看", s == 200, r)
s, p = page("linxiao", "chen")
x = summary(p)
check("林晓看不到陈一舟的履历和组队记录了", x["level"] == "MEMBER" and not x["history"] and x["teams"] == 0 and x["exp"] == 0, x)
check("提示里说明为什么看不到", bool((p.get("access") or {}).get("notice")), p.get("access"))
s, p = page("admin", "chen")
check("管理员照样看得到", summary(p)["history"], summary(p))
s, p = page("chen", "chen", preview=True)
x = summary(p)
check("陈一舟预览别人看到的自己：按 MEMBER，看不到履历", x["level"] == "MEMBER" and not x["history"] and (p.get("access") or {}).get("preview"), x)
s, p = page("chen", "chen")
check("自己看自己：全部", summary(p)["level"] == "SELF" and summary(p)["history"] and summary(p)["contact"], summary(p))
s, r = call("PUT", "/profile/privacy", {"historyVisibility": "WHATEVER"}, tok["chen"])
check("乱填可见范围被拒（400）", s == 400, (s, r))

print("== 主动联系：申请入队")
s, r = call("PUT", "/profile/privacy", {"historyVisibility": "RELATED"}, tok["linxiao"])
s, p = page("wang", "linxiao")
check("林晓也设成只给有关联的人看：王思远看不到", not summary(p)["history"], summary(p))
team = next(t for t in call("GET", "/teams/mine", token=tok["wang"])[1]["data"] if True)
team_id = team.get("id") or team.get("teamId")
s, r = call("POST", f"/teams/{team_id}/applications", {"message": "想加入"}, tok["linxiao"])
check("林晓申请加入王思远的队", s == 200, r)
s, p = page("wang", "linxiao")
check("申请人主动找过来：王思远这时看得到林晓的履历", summary(p)["level"] == "RELATED" and summary(p)["history"], summary(p))
s, p = page("linxiao", "chen")
check("反过来不算：林晓去申请别人，并不能看到陈一舟隐藏的履历", not summary(p)["history"], summary(p))
call("POST", f"/applications/{r.get('data')}/withdraw", token=tok["linxiao"])
s, p = page("wang", "linxiao")
check("申请撤回后，关联就没了", not summary(p)["history"], summary(p))

print("== 离队以后")
qian_team = call("GET", "/teams/mine", token=tok["qian"])[1]["data"][0]
qt = qian_team.get("id") or qian_team.get("teamId")
s, r = call("POST", f"/teams/{qt}/leave", token=tok["qian"])
check("钱多多退出了赵子航的队", s == 200, r)
s, p = page("qian", "zhao")
x = summary(p)
check("退队后：不再算队友，看不到联系方式（PR 说明里写的是\"以前同队的\"也能看）", x["level"] != "TEAMMATE" and not x["contact"], x)

print("== 停用和不存在")
s, r = call("PUT", f"/admin/users/{uid['zhou']}/status", {"status": "BANNED"}, tok["admin"])
s, p = page("linxiao", "zhou")
check("被停用的人：别人看是 404", s == 404, s)
s, p = page("admin", "zhou")
check("管理员还能看", s == 200, s)
s, r = call("GET", "/users/999999/profile", token=tok["linxiao"])
check("不存在的人 404", s == 404, s)
s, r = call("GET", f"/users/{uid['chen']}/profile")
check("没登录 401", s == 401, s)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
