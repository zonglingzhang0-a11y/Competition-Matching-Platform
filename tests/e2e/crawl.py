"""通知爬虫的接口测试。抓的是本机的假官网（crawlsite/server.py），不碰真网站。
后端要用 --app.admin-emails[0]=admin@test.local --app.crawler.delay-ms=300 启动，库是 schema + seed。
用法：python e2e_crawl.py <后端日志> <假官网请求日志>"""
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
SITE_LOG = sys.argv[2]
A = "http://127.0.0.1:18765"
B = "http://127.0.0.1:18766"
C = "http://127.0.0.1:18767"
results = []


def call(method, path, body=None, token=None, params=None):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
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


def sql(q):
    out = subprocess.run([*env.mysql_args(),
                          "-N", "-B", env.DB, "-e", q], capture_output=True)
    return [line.split("\t") for line in out.stdout.decode("utf-8").splitlines()]


def wait_done(admin):
    for _ in range(120):
        st = call("GET", "/admin/crawl/status", token=admin)[1]["data"]
        if not st["running"]:
            return st
        time.sleep(0.5)
    raise RuntimeError("抓取一直没结束")


def site_requests():
    with open(SITE_LOG, encoding="utf-8") as f:
        return [json.loads(l) for l in f if l.strip()]


def competition_id(admin, keyword):
    return call("GET", "/admin/competitions", token=admin, params={"keyword": keyword})[1]["data"]["records"][0]["id"]


def source_form(**kw):
    base = {"competitionId": None, "name": "", "listUrl": "", "linkSelector": "a", "titleKeywords": "通知",
            "excludeKeywords": "获奖,名单,公示", "contentSelector": "#content", "allowLlm": True, "enabled": True, "note": None}
    base.update(kw)
    return base


def records(admin, tab):
    return call("GET", "/admin/crawl/records", token=admin, params={"tab": tab, "size": 50})[1]["data"]["records"]


def by_url(rows, url):
    return next((r for r in rows if r["sourceUrl"] == url), None)


admin = register("admin")
student = register("stu")
dianzi = competition_id(admin, "电子设计")
shumo = competition_id(admin, "数学建模")

print("== 来源维护")
s, r = call("GET", "/admin/crawl/sources", token=admin)
seeded = r["data"]
check("种子里有 4 个来源，挑战杯默认停用且只用规则", len(seeded) == 4
      and not next(x for x in seeded if "挑战杯" in x["name"])["enabled"]
      and not next(x for x in seeded if "挑战杯" in x["name"])["allowLlm"], seeded)
s, r = call("GET", "/admin/crawl/sources", token=student)
check("学生不能看（403）", s == 403, s)
# 测试里不碰真网站：先把种子里的来源都停掉
for x in seeded:
    form = {k: x[k] for k in source_form()}
    form["enabled"] = False
    s, r = call("PUT", f"/admin/crawl/sources/{x['id']}", form, admin)
    assert s == 200, r
check("种子来源都停用了", not any(x["enabled"] for x in call("GET", "/admin/crawl/sources", token=admin)[1]["data"]))

s, r = call("POST", "/admin/crawl/sources", source_form(competitionId=dianzi, name="坏", listUrl=A + "/index.html", linkSelector="a["), admin)
check("选择器写错了（400）", s == 400 and "选择器" in r["message"], r)
s, r = call("POST", "/admin/crawl/sources", source_form(competitionId=dianzi, name="坏", listUrl="ftp://x/y"), admin)
check("不是 http 网址（400）", s == 400 and "http" in r["message"], r)
s, r = call("POST", "/admin/crawl/sources", source_form(competitionId=999999, name="坏", listUrl=A), admin)
check("比赛不存在（400）", s == 400, r)

form_a = source_form(competitionId=dianzi, name="假电赛网", listUrl=A + "/index.html", linkSelector="#list a")
s, r = call("POST", "/admin/crawl/sources/preview", form_a, admin)
p = r["data"] if s == 200 else {}
reasons = {l["url"]: l["reason"] for l in p.get("links", [])}
check("试抓：#list 里 9 个不同链接（带 #top 的那个和 1 合并）", s == 200 and p["linkCount"] == 9, r)
check("试抓：标题被截断时用 title 属性里的完整标题",
      any(l["title"].endswith("（完整标题）") for l in p.get("links", [])), p)
check("试抓：各种不抓的原因", reasons.get(A + "/news/3.html") == "标题里有排除词「获奖」"
      and reasons.get(A + "/private/5.html") == "robots.txt 不允许抓"
      and reasons.get("http://127.0.0.2:18765/news/9.html") == "不是这个网站的链接"
      and reasons.get(A + "/files/notice.pdf", "").startswith("附件")
      and reasons.get(A + "/news/1.html") == "会抓取", reasons)
check("试抓：会抓 5 篇（1、2、4、6、跳转的 7）", sum(l["picked"] for l in p.get("links", [])) == 5, reasons)
s, r = call("GET", "/admin/crawl/records", token=admin, params={"tab": "PENDING"})
check("试抓什么都不存", s == 200 and r["data"]["total"] == 0, r)

s, r = call("POST", "/admin/crawl/sources", form_a, admin)
src_a = r["data"]
src_b = call("POST", "/admin/crawl/sources", source_form(competitionId=shumo, name="假数模网", listUrl=B + "/index.html",
                                                         linkSelector=".news a"), admin)[1]["data"]
src_c = call("POST", "/admin/crawl/sources", source_form(competitionId=dianzi, name="robots 出错的网站", listUrl=C + "/index.html",
                                                         enabled=False), admin)[1]["data"]
check("新增了 3 个来源", all(isinstance(x, int) for x in (src_a, src_b, src_c)), (src_a, src_b, src_c))

print("== 抓取")
before = len(site_requests())
s, r = call("POST", "/admin/crawl/run", token=admin)
check("开始抓全部启用的来源", s == 200, r)
s, r = call("POST", "/admin/crawl/run", token=admin)
check("正在抓时再点（409）", s == 409, r)
st = wait_done(admin)
check("抓完了，有总结", "抓了 2 个来源" in (st["summary"] or "") and "新通知 4 条" in st["summary"]
      and "旧通知 1 条已自动忽略" in st["summary"], st)
reqs = site_requests()[before:]
check("真网站一个请求都没有（只请求了本机的假官网）", all(q["port"] in (18765, 18766) for q in reqs), reqs)
paths_a = [q["path"] for q in reqs if q["port"] == 18765]
check("A 站：列表 → 5 篇（跳转多一次；robots.txt 试抓时已缓存），附件、robots 禁止的、外站的都没请求",
      sorted(paths_a) == sorted(["/index.html", "/news/1.html", "/news/2.html", "/news/4.html",
                                  "/news/6.html", "/redirect/7", "/news/8.html"]), paths_a)
check("B 站没有 robots.txt（404）照样抓了", [q["path"] for q in reqs if q["port"] == 18766]
      == ["/robots.txt", "/index.html", "/n/2026.html"], reqs)
times = [q["t"] for q in reqs if q["port"] == 18765]
gaps = [b - a for a, b in zip(times, times[1:])]
check("同一网站两次请求至少隔 0.3 秒", gaps and min(gaps) >= 0.28, gaps)
check("User-Agent 写明了是谁和联系地址", all(q["ua"].startswith("TeamMatchCrawler/1.0 (+https://github.com/") for q in reqs),
      {q["ua"] for q in reqs})

pending = records(admin, "PENDING")
done = records(admin, "DONE")
n1 = by_url(pending, A + "/news/1.html")
x = n1["extracted"] if n1 else {}
check("电赛通知：抽出了报名、比赛时间、人数、同校、指导老师", n1 and n1["status"] == "EXTRACTED"
      and x["registrationStart"] == "2026-11-01T00:00:00" and x["registrationEnd"] == "2026-11-20T23:59:59"
      and x["contestStart"] == "2026-12-05T08:00:00" and x["contestEnd"] == "2026-12-07T20:00:00"
      and not x["contestAllDay"] and x["teamMin"] == 3 and x["teamMax"] == 3
      and x["sameSchoolRequired"] is True and x["advisorRequired"] is True, n1)
check("正文只取 #content：横幅和侧栏的年份不影响", n1 and x["year"] == 2026 and n1["competitionName"] == "全国大学生电子设计竞赛", n1)
n4 = by_url(pending, A + "/news/4.html")
check("会议通知：什么都没抽出来，状态 NEW", n4 and n4["status"] == "NEW", n4)
n7 = by_url(pending, A + "/redirect/7")
check("跳转后的补充通知：报名截止 24 时 = 23:59:59", n7 and n7["extracted"]["registrationEnd"] == "2026-11-25T23:59:59", n7)
check("转载的同一篇（正文一样）没有重复存", by_url(pending + done, A + "/news/6.html") is None)
n2 = by_url(done, A + "/news/2.html")
check("2025 年的旧通知自动忽略，写明原因", n2 and n2["status"] == "IGNORED" and "2025-08-02" in (n2["note"] or ""), n2)
nb = by_url(pending, B + "/n/2026.html")
check("数模通知：同校、3 人、时间都抽出来了", nb and nb["extracted"]["sameSchoolRequired"] is True
      and nb["extracted"]["teamMax"] == 3 and nb["extracted"]["registrationEnd"] == "2026-10-20T23:59:59"
      and nb["extracted"]["contestStart"] == "2026-10-30T18:00:00", nb)
s, r = call("GET", "/admin/crawl/records/pending-count", token=admin)
check("待确认 4 条", r["data"] == 4, r)

srcs = {x["id"]: x for x in call("GET", "/admin/crawl/sources", token=admin)[1]["data"]}
a = srcs[src_a]
check("A 站的结果记在来源上", a["lastResult"] == "新通知 3 条，旧通知 1 条已自动忽略，1 篇没抓到（打不开、robots.txt 不允许或内容重复）"
      and a["pending"] == 3 and a["total"] == 4, a)
check("A 站 robots.txt 拒绝了 AI 爬虫：自动改成只用规则抽取", a["allowLlm"] is False and "AI" in (a["note"] or ""), a)
check("B 站没拒绝，仍允许大模型", srcs[src_b]["allowLlm"] is True, srcs[src_b])

before = len(site_requests())
call("POST", "/admin/crawl/run", token=admin)
st = wait_done(admin)
paths = [q["path"] for q in site_requests()[before:]]
check("再抓一次：没有新通知，抓过的不再打开", "没有新通知" in st["summary"] and "/news/1.html" not in paths
      and "/n/2026.html" not in paths and "/redirect/7" not in paths, (st, paths))

s, r = call("POST", f"/admin/crawl/sources/{src_c}/run", token=admin)
check("停用的来源也能手动抓", s == 200, r)
wait_done(admin)
c = next(x for x in call("GET", "/admin/crawl/sources", token=admin)[1]["data"] if x["id"] == src_c)
check("robots.txt 返回 500：当作全部禁止，记为出错", (c["lastResult"] or "").startswith("出错：robots.txt 不允许抓取"), c)
check("robots.txt 出错时没再请求列表页", not any(q["port"] == 18767 and q["path"] == "/index.html" for q in site_requests()))

print("== 采用和忽略")
s, r = call("GET", f"/admin/crawl/records/{n1['id']}", token=admin)
d = r["data"]
check("详情有正文，没有横幅和侧栏", s == 200 and "3人一队" in d["rawText"] and "升级维护" not in d["rawText"]
      and "往期回顾" not in d["rawText"], d.get("rawText"))
check("电赛还没有届次", d["editions"] == [], d["editions"])


def edition_form(**kw):
    base = {"year": 2026, "title": "2026 年", "teamMin": 3, "teamMax": 3, "sameSchoolRequired": True,
            "advisorRequired": True, "englishRequired": False, "registrationStart": "2026-11-01T00:00:00",
            "registrationEnd": "2026-11-20T23:59:59", "noticeUrl": A + "/news/1.html", "status": "DRAFT"}
    base.update(kw)
    return base


def node(title, typ, start, end=None, busy=False, all_day=False):
    return {"title": title, "nodeType": typ, "startAt": start, "endAt": end, "allDay": all_day, "startLatestAt": None,
            "endMode": "FIXED", "endLatestAt": None, "busy": busy, "note": None}


nodes1 = [node("报名截止", "REGISTRATION", "2026-11-20T23:59:59"),
          node("比赛", "CONTEST", "2026-12-05T08:00:00", "2026-12-07T20:00:00", busy=True)]
s, r = call("POST", f"/admin/crawl/records/{n1['id']}/approve",
            {"editionId": None, "edition": edition_form(), "nodes": [node("坏", "OTHER", "2026-12-05T08:00:00", "2026-12-01T00:00:00")]}, admin)
check("节点时间不对（400），什么都没写进去", s == 400 and sql("SELECT COUNT(*) FROM competition_edition WHERE title = '2026 年'") == [["0"]]
      and sql(f"SELECT status FROM crawl_record WHERE id = {n1['id']}") == [["EXTRACTED"]], r)
s, r = call("POST", f"/admin/crawl/records/{n1['id']}/approve", {"editionId": None, "edition": edition_form(), "nodes": nodes1}, admin)
new_edition = r.get("data")
check("采用：新建一届", s == 200 and isinstance(new_edition, int), r)
row = sql(f"SELECT competition_id, source, status, team_min, registration_end FROM competition_edition WHERE id = {new_edition}")
check("新届次是电赛的、来源 CRAWLER、草稿", row == [[str(dianzi), "CRAWLER", "DRAFT", "3", "2026-11-20 23:59:59"]], row)
check("节点也写进去了", sql(f"SELECT title, node_type, busy FROM competition_node WHERE edition_id = {new_edition} ORDER BY sort")
      == [["报名截止", "REGISTRATION", "0"], ["比赛", "CONTEST", "1"]])
s, r = call("POST", f"/admin/crawl/records/{n1['id']}/approve", {"editionId": None, "edition": edition_form(title="再来"), "nodes": []}, admin)
check("同一条再采用（409）", s == 409, r)
s, r = call("GET", "/competitions", token=student, params={"keyword": "电子设计"})
check("草稿届次学生看不到", s == 200 and "2026 年" not in json.dumps(r["data"], ensure_ascii=False), r)

shumo_edition = int(sql("SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id "
                        "WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）'")[0][0])
s, r = call("POST", f"/admin/crawl/records/{n7['id']}/approve",
            {"editionId": shumo_edition, "edition": edition_form(), "nodes": nodes1}, admin)
check("不能用电赛的通知去更新数模的届次（400），通知仍待确认", s == 400 and "这个比赛自己的届次" in r["message"]
      and sql(f"SELECT status FROM crawl_record WHERE id = {n7['id']}") == [["EXTRACTED"]], r)
nodes7 = [node("报名截止", "REGISTRATION", "2026-11-25T23:59:59"), nodes1[1]]
s, r = call("POST", f"/admin/crawl/records/{n7['id']}/approve",
            {"editionId": new_edition, "edition": edition_form(registrationEnd="2026-11-25T23:59:59"), "nodes": nodes7}, admin)
check("补充通知：更新刚建的那一届", s == 200 and r["data"] == new_edition, r)
check("报名截止改成了 11-25", sql(f"SELECT registration_end FROM competition_edition WHERE id = {new_edition}") == [["2026-11-25 23:59:59"]]
      and sql(f"SELECT start_at FROM competition_node WHERE edition_id = {new_edition} AND node_type = 'REGISTRATION'")
      == [["2026-11-25 23:59:59"]])

old_nodes = call("GET", f"/admin/editions/{shumo_edition}/nodes", token=admin)[1]["data"]
merged = []
for n in old_nodes:
    f = {k: n[k] for k in ("title", "nodeType", "startAt", "endAt", "allDay", "startLatestAt", "endMode", "endLatestAt", "busy", "note")}
    if n["nodeType"] == "REGISTRATION":
        f["startAt"] = "2026-10-20T23:59:59"
    if n["nodeType"] == "CONTEST":
        f.update(startAt="2026-10-30T18:00:00", endAt="2026-11-02T20:00:00")
    merged.append(f)
s, r = call("POST", f"/admin/crawl/records/{nb['id']}/approve",
            {"editionId": shumo_edition, "edition": edition_form(title="2026 年（示例数据）", teamMin=1, registrationStart="2026-09-20T00:00:00",
                                                                  registrationEnd="2026-10-20T23:59:59", advisorRequired=False,
                                                                  noticeUrl=B + "/n/2026.html", status="PUBLISHED"), "nodes": merged}, admin)
check("数模通知：更新已有的示例届次", s == 200, r)
check("数模届次：报名截止延到 10-20，仍是已发布，3 个节点", sql(f"SELECT registration_end, status, source FROM competition_edition WHERE id = {shumo_edition}")
      == [["2026-10-20 23:59:59", "PUBLISHED", "MANUAL"]]
      and sql(f"SELECT COUNT(*) FROM competition_node WHERE edition_id = {shumo_edition}") == [["3"]])

s, r = call("POST", f"/admin/crawl/records/{n2['id']}/approve", {"editionId": None, "edition": edition_form(), "nodes": []}, admin)
check("自动忽略的也能改成采用，但届次重名（409），仍是已忽略", s == 409 and "同名" in r["message"]
      and sql(f"SELECT status FROM crawl_record WHERE id = {n2['id']}") == [["IGNORED"]], r)
s, r = call("POST", f"/admin/crawl/records/{n4['id']}/ignore", token=admin)
check("忽略会议通知", s == 200, r)
s, r = call("POST", f"/admin/crawl/records/{n4['id']}/ignore", token=admin)
check("再忽略一次（409）", s == 409, r)
s, r = call("POST", f"/admin/crawl/records/{n2['id']}/ignore", token=admin)
check("已忽略的不能再忽略（409）", s == 409, r)

done = records(admin, "DONE")
d1 = by_url(done, A + "/news/1.html")
check("已处理列表：采用的带届次名和处理人", d1 and d1["status"] == "APPROVED" and d1["editionTitle"] == "2026 年"
      and d1["reviewerName"] == "admin" and d1["reviewedAt"], d1)
d4 = by_url(done, A + "/news/4.html")
check("已处理列表：手动忽略的", d4 and d4["status"] == "IGNORED" and d4["note"] is None and d4["reviewerName"] == "admin", d4)
check("待确认清空了", call("GET", "/admin/crawl/records/pending-count", token=admin)[1]["data"] == 0)

print("== 删除来源")
s, r = call("DELETE", f"/admin/crawl/sources/{src_a}", token=admin)
check("抓到过通知的来源不能删（409）", s == 409 and "停用" in r["message"], r)
s, r = call("DELETE", f"/admin/crawl/sources/{src_c}", token=admin)
check("没抓到过通知的可以删", s == 200, r)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
