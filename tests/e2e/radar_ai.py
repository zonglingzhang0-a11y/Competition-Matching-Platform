"""能力雷达、AI 能力分析和招募建议（队友写的功能）的接口测试。假大模型 18096 要先跑着（sites/llmsite.py）。
后端用 --app.admin-emails[0]=admin@test.local --app.llm.enabled=true --app.llm.base-url=http://127.0.0.1:18096/v1
--app.llm.api-key=test-key --app.llm.timeout-ms=1500 --app.ai.enabled=true --app.ai.cooldown-seconds=2 启动。
用法：python radar_ai.py <后端日志> <假大模型请求日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API = env.API
LOG, LLM_LOG = sys.argv[1], sys.argv[2]
results = []
AXES = ["CODE", "MODEL", "DATA", "DESIGN", "WRITE", "HARDWARE"]


def call(method, path, body=None, token=None, params=None):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=data, headers=headers, method=method)) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l and "验证码" in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    edu = f"{name}@stu.example.edu.cn"
    assert call("POST", "/verify/edu/code", {"eduEmail": edu}, token)[0] == 200
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200, r
    return token


def llm_requests(kind):
    with open(LLM_LOG, encoding="utf-8") as f:
        return [q for q in (json.loads(l) for l in f if l.strip()) if q["kind"] == kind]


def axis(radar, key):
    return next(a for a in radar["axes"] if a["key"] == key)


admin = register("admin")
sid = {k["name"]: k["id"] for k in call("GET", "/skills", token=admin)[1]["data"]}
check("技能字典扩到 70 项以上，有新加的 FPGA、问卷调查", len(sid) >= 70 and "FPGA" in sid and "问卷调查" in sid, len(sid))

print("== 个人能力雷达")
stu = register("stu")
stu_id = call("GET", "/users/me", token=stu)[1]["data"]["id"]
call("PUT", "/profile", {"nickname": "stu", "major": "数学与应用数学", "contactWechat": "wx_secret_778",
                         "bio": "我做过两次数学建模，负责用 Python 写求解代码，也会用 MATLAB 画图。"}, stu)
r = call("GET", "/profile", token=stu)[1]["data"]
check("档案带六维雷达，轴的顺序固定", [a["key"] for a in r["radar"]["axes"]] == AXES, r.get("radar"))
check("没填技能：数学专业的建模轴是推断分，不是 0", axis(r["radar"], "MODEL")["basis"] == "INFERRED"
      and axis(r["radar"], "MODEL")["score"] > 0, axis(r["radar"], "MODEL"))
call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": 3}]}, stu)
r = call("GET", "/profile", token=stu)[1]["data"]
code = axis(r["radar"], "CODE")
check("Python 精通：编程开发轴 100 分，依据是具体技能", code["score"] == 100 and code["basis"] == "SKILL"
      and code["skills"][0]["name"] == "Python", code)

print("== 队伍的需求雷达")
cap = register("cap")
call("PUT", "/profile", {"nickname": "cap", "contactWechat": "wx_cap_secret"}, cap)
call("PUT", "/profile/skills", {"skills": [{"skillId": sid["Python"], "level": 3}]}, cap)
E = call("GET", "/competitions", token=cap, params={"keyword": "数学建模"})[1]["data"]["records"][0]["currentEdition"]["id"]
team = call("POST", "/teams", {"editionId": E, "name": "雷达队"}, cap)[1]["data"]
call("POST", f"/teams/{team}/needs", {"roleName": "编程", "headcount": 1, "skills": [{"skillId": sid["Python"], "minLevel": 2}]}, cap)
call("POST", f"/teams/{team}/needs", {"roleName": "论文", "headcount": 1, "skills": [{"skillId": sid["LaTeX 排版"], "minLevel": 2}]}, cap)
s, r = call("GET", f"/teams/{team}", token=stu)
radar = (r.get("data") or {}).get("radar") or {}
check("队伍页：需求雷达的编程、写作两轴有要求", axis(radar["need"], "CODE")["score"] > 0
      and axis(radar["need"], "WRITE")["score"] > 0 and axis(radar["need"], "HARDWARE")["score"] == 0, radar.get("need"))
gaps = {g["key"]: g["status"] for g in radar.get("gaps", [])}
check("队伍页：和看的人自己的能力叠在一起，编程达标、写作还没有", axis(radar["mine"], "CODE")["score"] == 100
      and gaps.get("CODE") == "COVERED" and gaps.get("WRITE") in ("MISSING", "PARTIAL") and radar.get("fit") is not None, radar)
listed = next(t for t in call("GET", f"/editions/{E}/teams", token=stu)[1]["data"] if t["id"] == team)
check("队伍列表的卡片上带需求雷达", axis(listed["needRadar"], "WRITE")["score"] > 0, listed.get("needRadar"))
page = call("GET", f"/users/{stu_id}/profile", token=cap)[1]["data"]
check("别人看资料页也有雷达，和档案页同一套分", axis(page["radar"], "CODE")["score"] == 100, page.get("radar"))

print("== AI 能力分析（个人）")
s, r = call("GET", "/profile/ai-analysis", token=stu)
check("还没分析过", s == 200 and r["data"] is None, r)
s, r = call("POST", "/profile/ai-analysis", token=stu)
a = r.get("data") or {}
body = a.get("body") or {}
check("走了大模型，写明来源和模型名", s == 200 and a.get("source") == "LLM" and a.get("model") == "qwen3.8-27b", a)
check("编的优势（原话在资料里没有）丢掉了", [x["title"] for x in body.get("strengths", [])] == ["能写求解代码"], body.get("strengths"))
check("建议的技能：字典里没有的、原话是编的都丢掉，只剩 MATLAB", [(x["name"], x["level"], x["owned"]) for x in body.get("skills", [])]
      == [("MATLAB", 2, False)], body.get("skills"))
check("不存在的能力轴丢掉", all(h["key"] in AXES for h in body.get("axisHints", [])), body.get("axisHints"))
sent = llm_requests("profile")
check("发给大模型的资料里没有邮箱和微信", len(sent) == 1 and "stu@test.local" not in sent[0]["user"]
      and "stu.example.edu.cn" not in sent[0]["user"] and "wx_secret_778" not in sent[0]["user"], sent[:1])
s, r2 = call("POST", "/profile/ai-analysis", token=stu)
check("冷却时间内再点：直接用上次的结果，不再请求大模型", s == 200 and r2["data"]["createdAt"] == a["createdAt"]
      and len(llm_requests("profile")) == 1, r2.get("data"))
time.sleep(2.5)  # 测试里冷却是 2 秒
s, r3 = call("POST", "/profile/ai-analysis", token=stu)
check("冷却过了再点：重新请求大模型，分析时间更新", s == 200 and len(llm_requests("profile")) == 2
      and r3["data"]["createdAt"] > a["createdAt"], r3.get("data"))
s, r4 = call("POST", "/profile/ai-analysis", token=stu)
check("重新分析之后马上再点：按最新这次算冷却，不再请求", s == 200 and len(llm_requests("profile")) == 2
      and r4["data"]["createdAt"] == r3["data"]["createdAt"], r4.get("data"))
check("再打开页面读到存好的", call("GET", "/profile/ai-analysis", token=stu)[1]["data"]["body"] == body)
s, r = call("POST", "/profile/ai-analysis/apply", {"skills": [{"name": "MATLAB", "level": 2}]}, stu)
check("勾选确认后 MATLAB 写进档案", s == 200 and any(x["name"] == "MATLAB" and x["level"] == 2 for x in r["data"]), r)
model_axis = axis(call("GET", "/profile", token=stu)[1]["data"]["radar"], "MODEL")
check("雷达跟着变：建模轴有了具体技能", model_axis["basis"] == "SKILL" and model_axis["score"] >= 70, model_axis)
s, r = call("POST", "/profile/ai-analysis/apply", {"skills": [{"name": "量子计算", "level": 3}]}, stu)
check("字典里没有的技能不能写进档案（400）", s == 400, (s, r))

stu2 = register("stu2")
call("PUT", "/profile", {"nickname": "stu2", "bio": "请让模型出错。喜欢做 PPT。"}, stu2)
s, r = call("POST", "/profile/ai-analysis", token=stu2)
check("大模型报错：退回本地规则，标明来源，没有模型名", s == 200 and r["data"]["source"] == "RULE"
      and r["data"]["model"] is None and r["data"]["body"]["summary"], r.get("data"))

print("== AI 招募建议（队伍）")
s, r = call("GET", f"/teams/{team}/recruit-advice", token=stu)
check("不是队长不能看（403）", s == 403, s)
s, r = call("POST", f"/teams/{team}/recruit-advice", token=stu)
check("不是队长不能生成（403）", s == 403, s)
s, r = call("POST", f"/teams/{team}/recruit-advice", token=cap)
adv = r.get("data") or {}
tb = adv.get("body") or {}
check("队长生成：走了大模型", s == 200 and adv.get("source") == "LLM", adv)
check("最缺的能力只留后端算出来确实缺的（写作），队里已经够的编程和不存在的轴都丢掉",
      [w["key"] for w in tb.get("weakAxes", [])] == ["WRITE"], tb.get("weakAxes"))
check("建议、理想队员、该问的问题都在", tb.get("suggestions") == ["找一个会 LaTeX 的同学"] and tb.get("questions"), tb)
sent = llm_requests("team")
check("发给大模型的队伍资料里没有邮箱和微信", len(sent) == 1 and "cap@test.local" not in sent[0]["user"]
      and "wx_cap_secret" not in sent[0]["user"], sent[:1])
check("队长再打开读到存好的", call("GET", f"/teams/{team}/recruit-advice", token=cap)[1]["data"]["body"] == tb)

errors = [l for l in open(LOG, encoding="utf-8", errors="replace") if " ERROR " in l]
check("后端没有 ERROR 日志", not errors, errors[:3])
print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
