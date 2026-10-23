"""爬虫接大模型（#9）和比赛页 AI 总结的接口测试。假官网 18768、假大模型 18096 要先跑着（llmsite/server.py）。
后端用 --app.admin-emails[0]=admin@test.local --app.crawler.delay-ms=200 --app.llm.enabled=true
--app.llm.base-url=http://127.0.0.1:18096/v1 --app.llm.api-key=test-key --app.llm.timeout-ms=1500 启动。
用法：python e2e_llm.py <后端日志> <假大模型请求日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request

API = env.API
LOG, LLM_LOG = sys.argv[1], sys.argv[2]
results = []


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


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l and "验证码" in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    return r["data"]["token"]


def sql(q):
    out = subprocess.run([*env.mysql_args(),
                          "-N", "-B", env.DB, "-e", q], capture_output=True)
    return [line.split("\t") for line in out.stdout.decode("utf-8").splitlines()]


def wait_done(admin):
    for _ in range(200):
        st = call("GET", "/admin/crawl/status", token=admin)[1]["data"]
        if not st["running"]:
            return st
        time.sleep(0.5)
    raise RuntimeError("抓取一直没结束")


def record(keyword):
    rows = sql(f"SELECT status, extracted FROM crawl_record WHERE title LIKE '%{keyword}%'")
    if not rows:
        return None, {}
    return rows[0][0], json.loads(rows[0][1])


def llm_requests():
    with open(LLM_LOG, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


admin = register("admin")
comp = call("GET", "/competitions", token=admin)[1]["data"]["records"][0]["id"]
src = {"competitionId": comp, "name": "假官网", "listUrl": "http://127.0.0.1:18768/index.html", "linkSelector": "#list a",
       "titleKeywords": "", "excludeKeywords": "", "contentSelector": "#content", "allowLlm": True, "enabled": True, "note": ""}
s, r = call("POST", "/admin/crawl/sources", src, admin)
check("建了允许交给大模型的来源", s == 200, r)
a_id = r["data"]

print("== 抓允许交给大模型的来源")
t0 = time.time()
call("POST", f"/admin/crawl/sources/{a_id}/run", token=admin)
st = wait_done(admin)
check(f"抓完了（{time.time() - t0:.1f} 秒），没有整体出错", "出错" not in (st.get("summary") or ""), st)

status, e = record("节能减排")
ev = e.get("evidence") or {}
check("中文数字写的日期：规则没抽到，大模型补上了报名截止", e.get("registrationEnd", "").startswith("2026-11-20"), e)
check("比赛时间也补上了", (e.get("contestStart") or "").startswith("2026-12-05"), e)
check("人数和同校：规则已经抽到的不换，没抽到的补上", e.get("teamMax") == 3 and e.get("sameSchoolRequired") is True, e)
check("依据里标出是大模型补的", "llm:registration" in ev and "llm:contest" in ev, ev)
check("抽取方式记成 rule+llm", e.get("extractor") == "rule+llm", e.get("extractor"))
check("状态是已抽取、等确认", status == "EXTRACTED", status)

status, e = record("物理")
ev = e.get("evidence") or {}
check("大模型编的依据（原文里没有、或者日期对不上）全部丢掉", e.get("registrationEnd") is None and e.get("contestStart") is None
      and not any(k.startswith("llm:") for k in ev) and e.get("extractor") == "rule", e)

status, e = record("化学")
check("大模型报错（500）：照常只用规则，记录照样存了", status is not None and e.get("extractor") == "rule", (status, e))
status, e = record("力学")
check("大模型超时：照常只用规则，记录照样存了", status is not None and e.get("extractor") == "rule", (status, e))

status, e = record("英语")
check("规则已经抽全的通知不问大模型", e.get("extractor") == "rule" and e.get("registrationEnd") and e.get("teamMax") == 3, e)

reqs = llm_requests()
titles = [q["title"] for q in reqs]
check("只问了规则没抽全的 4 篇", len(reqs) == 4 and not any("英语" in t for t in titles), titles)
check("请求发到 /v1/chat/completions，带着模型名、系统提示和密钥",
      all(q["path"] == "/v1/chat/completions" and q["model"] == "qwen3.8-27b" and q["system"]
          and q["auth"] == "Bearer test-key" for q in reqs), reqs[:1])

print("== 不允许交给大模型的来源")
src_b = dict(src, name="不让用 AI 的官网", listUrl="http://127.0.0.1:18768/index2.html", allowLlm=False)
s, r = call("POST", "/admin/crawl/sources", src_b, admin)
b_id = r["data"]
call("POST", f"/admin/crawl/sources/{b_id}/run", token=admin)
wait_done(admin)
status, e = record("数学竞赛")
check("通知抓到了，只用规则", status is not None and e.get("extractor") == "rule", (status, e))
check("没有发给大模型", len(llm_requests()) == 4 and not any("数学竞赛" in q["title"] for q in llm_requests()))

print("== 确认弹窗看到的数据")
rid = sql("SELECT id FROM crawl_record WHERE title LIKE '%节能减排%'")[0][0]
s, r = call("GET", f"/admin/crawl/records/{rid}", token=admin)
ev = (r.get("data") or {}).get("record", {}).get("extracted", {}).get("evidence", {})
check("详情接口带着大模型的依据", s == 200 and "llm:registration" in ev, ev)

print("== 比赛页 AI 总结")
stu = register("stu")
s, r = call("GET", f"/competitions/{comp}/ai-summary", token=stu)
v = r.get("data") or {}
check("学生能看：还没整理过，看得到抓到的 6 篇通知和信息渠道", s == 200 and v.get("summary") is None
      and not v.get("canGenerate") and len(v.get("sources", [])) == 6 and len(v.get("channels", [])) >= 6, r)
chs = v.get("channels", [])
sites = [re.sub(r"^https?://(www\.)?|/+$", "", c["url"].lower()) for c in chs if c["url"] and "baidu.com" not in c["url"]]
check("信息渠道：同一个网址只列一次，教务处给了搜索链接", len(sites) == len(set(sites))
      and any((c["url"] or "").startswith("https://www.baidu.com/s?wd=") for c in chs), chs)
check("信息渠道：公众号给比赛全称让复制，加分认定能跳到本页那一块",
      any(c["copy"] and c["copy"] in c["hint"] for c in chs) and any(c["anchor"] == "recognition" for c in chs), chs)
blocked = [x for x in v.get("sources", []) if not x["aiAllowed"]]
check("不让 AI 读的那篇也列出来，标成只放链接", len(blocked) == 1 and "数学竞赛" in (blocked[0]["title"] or ""), blocked)
s, r = call("POST", f"/admin/competitions/{comp}/ai-summary", token=stu)
check("学生不能调大模型（403）", s == 403, (s, r))
s, r = call("GET", "/competitions/999999/ai-summary", token=stu)
check("没有这个比赛（404）", s == 404, (s, r))

s, r = call("POST", f"/admin/competitions/{comp}/ai-summary", token=admin)
v = r.get("data") or {}
pts = (v.get("summary") or {}).get("points", [])
labels = [p["label"] for p in pts]
check("管理员整理成功", s == 200 and v.get("canGenerate") and v.get("llmReady"), r)
check("对得上原文的两条留下了，按报名、比赛、组队的顺序", labels == ["比赛时间", "组队要求"], pts)
check("编的原话、数字对不上原话的都丢掉了", not any("15" in p["text"] or "100" in p["text"] for p in pts), pts)
check("每条带着原话和出处链接", all(p["quote"] and (p["sourceUrl"] or "").startswith("http://127.0.0.1:18768/n/") for p in pts), pts)
check("报名时间没整理出来，提醒去别的渠道查", v.get("missing") == ["报名时间"], v.get("missing"))
sreq = [q for q in llm_requests() if q["kind"] == "summary"]
sent = sreq[0]["notices"] if sreq else []
check("问了大模型一次，最多 4 篇，不让 AI 读的那篇没发", len(sreq) == 1 and 1 <= len(sent) <= 4
      and not any("数学竞赛" in t for t in sent), sreq)
used = [x for x in v.get("sources", []) if x["used"]]
check("用到的通知标出来了", len(used) == len(sent) and all(x["aiAllowed"] for x in used), used)

s, r = call("GET", f"/competitions/{comp}/ai-summary", token=stu)
saved = (r.get("data") or {}).get("summary") or {}
check("学生看到存好的总结，带模型名和整理时间", s == 200 and [p["label"] for p in saved.get("points", [])] == labels
      and saved.get("model") == "qwen3.8-27b" and saved.get("generatedAt"), saved)
check("学生打开不会调大模型", len([q for q in llm_requests() if q["kind"] == "summary"]) == 1)

s, r = call("POST", f"/admin/competitions/{comp}/ai-summary", token=admin)
kept = call("GET", f"/competitions/{comp}/ai-summary", token=stu)[1]["data"]["summary"]
check("这次的回答一条都对不上原文：报错（502），原来的总结不动", s == 502 and [p["label"] for p in kept["points"]] == labels,
      (s, r, kept))

codes = []
threads = [threading.Thread(target=lambda: codes.append(call("POST", f"/admin/competitions/{comp}/ai-summary", token=admin)[0]))
           for _ in range(2)]
for t in threads:
    t.start()
    time.sleep(0.2)
for t in threads:
    t.join()
check("同时点两次：一个在整理，另一个提示稍后再试（409）", sorted(codes) == [200, 409], codes)

other = next(c["id"] for c in call("GET", "/competitions", token=admin)[1]["data"]["records"] if c["id"] != comp)
s, r = call("POST", f"/admin/competitions/{other}/ai-summary", token=admin)
check("还没抓到通知的比赛：提示先去抓（400）", s == 400 and "先去" in r.get("message", ""), (s, r))

errors = [l for l in open(LOG, encoding="utf-8", errors="replace") if " ERROR " in l]
check("后端没有 ERROR 日志", not errors, errors[:3])
print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
