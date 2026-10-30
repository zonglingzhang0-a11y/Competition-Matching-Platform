"""模块演示动画：个人信息与赛库（约 45 秒）。
1. 新注册一个测试账号（录制前用接口注册），在镜头里用中南大学的学生邮箱子域名做教育邮箱认证，
   验证码从后端日志里读（本机是日志模式，不发真邮件），通过后直接认出「中南大学」（迁移 V12 收录的湖南高校）；
2. 讲解动画：邮箱域名一层层往上列候选，去收录的后缀里查，命中的取最长的——候选和命中情况都来自接口，
   和 school/SchoolResolver 的算法一致；认证结果对不上就不出片；
3. 林晓看数学建模的比赛页：各校加分认定、届次和时间节点。
会新增一个测试账号。重录前重启 serve_video.py（会重新导入演示数据）。
用法：python scripts/demo-video/record_module_profile.py"""
import random
import re
import secrets
import sys
import time

from playwright.sync_api import sync_playwright

from module_common import BASE, ROOT, Module, api, login

LOG = ROOT / "演示视频" / "backend.log"       # serve_video.py 的默认日志位置
tag = f"{random.randint(1000, 9999)}"
email, edu = f"tian{tag}@example.com", f"tian{tag}@mail.csu.edu.cn"
password = f"Demo{secrets.token_hex(4)}7"      # 只用于这个测试账号，不打印


def wait_code(to, timeout=15):
    """从后端日志里找发往 to 的最新验证码"""
    end = time.time() + timeout
    while time.time() < end:
        hits = re.findall(rf"发往 {re.escape(to)} 的\S*验证码：(\d{{6}})", LOG.read_text(encoding="utf-8", errors="replace"))
        if hits:
            return hits[-1]
        time.sleep(0.5)
    sys.exit(f"日志里没找到发往 {to} 的验证码（看看 {LOG}）")


# ---------- 准备：注册测试账号；算出域名的候选和命中（和 SchoolResolver 一样） ----------
api("POST", "/auth/register-code", body={"email": email})
api("POST", "/auth/register", body={"email": email, "code": wait_code(email), "password": password, "nickname": "田同学"})
admin = login("admin")
domain_school, page_no = {}, 1
while True:
    page = api("GET", f"/admin/schools?page={page_no}&size=50", admin)
    for s in page["records"]:
        for d in s["domains"]:
            domain_school[d["domain"]] = s
    if page_no * 50 >= page["total"]:
        break
    page_no += 1
host = edu.split("@")[1]
cands = []
while host.endswith(".edu.cn") and len(host) > len(".edu.cn"):
    cands.append(host)
    host = host.split(".", 1)[1]
hits = [c for c in cands if c in domain_school]
if not hits:
    sys.exit(f"{cands} 都没收录（后端是不是还没有迁移 V12？重新打包后端再起 serve_video.py）")
best = max(hits, key=len)
school = domain_school[best]
print(f"候选 {cands}，命中 {hits}，取 {best} → {school['name']}；共收录 {len(domain_school)} 个后缀")

m = Module("个人信息与赛库", {"domain": {
    "eyebrow": "教育邮箱认证 · 学校是怎么认出来的",
    "heading": "邮箱后缀一层层往上找",
    "email": edu,
    "steps": [{"domain": c, "school": domain_school[c]["name"] if c in domain_school else None, "best": c == best}
              for c in cands],
    "school": school["name"], "place": f"{school['province'] or ''}{school['city'] or ''}",
    "cards": [
        {"title": "命中几个取最长的", "text": "越长越具体，比如院系有自己的邮箱后缀时优先认它"},
        {"title": "没收录的学校", "text": "进管理员审核；通过时收录后缀，同校的人以后自动认证"},
        {"title": "没有教育邮箱", "text": "传学生证或学信网截图，本地 OCR 比对后人工审核"},
    ]}})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("田展驰", "个人信息与赛库：身份是真的，比赛信息有据可查")

    # ---------- 教育邮箱认证 ----------
    page = m.site_page(email, password)
    page.goto(BASE + "/verify")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    r_v = m.begin(page, "verify")
    r_v.say("教育邮箱认证：验证码一通过，直接认出是哪所学校。", "新同学的身份认证页，填中南大学的学生邮箱、收验证码、完成认证")
    r_v.wait(0.8)
    form = page.locator(".el-card").filter(has=page.get_by_text("完成认证")).first
    r_v.cam(form, zoom=1.5, seconds=0.9, dy=-30)
    r_v.wait(0.5)
    r_v.type(page.get_by_placeholder("比如 2023xxxx@stu.xxx.edu.cn"), edu, delay=0.05)
    r_v.click(page.get_by_role("button", name="发送验证码"))
    code = wait_code(edu)
    r_v.wait(0.4)
    r_v.type(page.get_by_placeholder("6 位数字"), code, delay=0.12)
    r_v.click(page.get_by_role("button", name="完成认证"))
    result = page.get_by_text(f"已认证 · {school['name']}")
    result.wait_for()
    r_v.wait(0.4)
    status = page.locator(".el-card").filter(has=result).first
    r_v.cam(status, zoom=1.6, seconds=0.9)
    r_v.wait(0.8)
    r_v.spot(status, pad=6)
    r_v.note(status, f"认出：{school['name']}<small>邮箱后缀 {edu.split('@')[1]}</small>", side="bottom")
    m.end(r_v, 1.2)
    me = api("GET", "/users/me", api("POST", "/auth/login", body={"email": email, "password": password})["token"])
    if (me.get("school") or {}).get("name") != school["name"]:
        sys.exit(f"认证结果是 {me.get('school')}，和算出来的 {school['name']} 对不上，不出片")

    # ---------- 讲解动画：域名怎么认学校 ----------
    page = m.card_page()
    r_x = m.begin(page, "explain")
    r_x.say("系统把邮箱域名一层层往上找，找到收录的后缀为止。", "讲解动画：候选域名逐行出现，标出有没有收录")
    page.evaluate("demo.domain()")
    r_x.wait(0.9)
    page.evaluate("demo.domainSteps()")
    for i in range(len(cands)):
        r_x.event("tick", i * 0.65)
        r_x.event("pop", i * 0.65 + 0.5)
    r_x.wait(len(cands) * 0.65 + 0.6)
    r_x.say("没收录的进管理员审核；没有教育邮箱，可以传学生证截图。", "讲解动画：认出的学校，右边是三种情况的说明")
    page.evaluate("demo.domainResult()")
    r_x.event("ding", 0.1)
    for i in range(3):
        r_x.event("pop", 0.6 + i * 0.5)
    m.end(r_x, 1.2)

    # ---------- 比赛页：加分认定、届次和时间节点 ----------
    page = m.site_page("linxiao")
    page.goto(BASE + "/competitions/1")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(900)
    r_c = m.begin(page, "competition")
    r_c.say("比赛页上有各校的加分认定，由同校同学标注、两人确认。", "数学建模的比赛页，镜头推到各校加分认定")
    r_c.wait(0.9)
    rec_panel = page.locator("#recognition")
    r_c.scroll_to(rec_panel, top=200)
    r_c.cam(rec_panel, zoom=1.5, seconds=0.9)
    r_c.wait(0.8)
    r_c.spot(rec_panel, pad=6)
    r_c.note(rec_panel, "本校认定：A 类<small>附学校文件，同校同学确认后才显示</small>", side="bottom")
    r_c.say("届次和时间节点来自爬虫抓的官网通知，管理员核对后才采用。", "往下到届次和时间节点")
    r_c.wait(1.4)
    r_c.clear_notes()
    nodes = page.locator("#editions .el-timeline").first
    r_c.scroll_to(nodes, top=300)
    r_c.cam(nodes, zoom=1.4, seconds=0.9)
    r_c.wait(0.8)
    r_c.spot(nodes, pad=10)
    r_c.note(nodes, "报名、比赛、成绩公布<small>写进日历，按节点提醒</small>", side="bottom")
    m.end(r_c, 1.0)
    m.browser.close()

m.render([(r_title, False), (r_v, True), (r_x, False), (r_c, True)], ["zoom", "wipe", "zoom"], facts=[
    f"测试账号的教育邮箱 {edu}：候选 {'、'.join(cands)}；收录的有 {'、'.join(hits)}；取 {best} → {school['name']}",
    f"认证后接口 /users/me 返回的学校：{school['name']}（和动画一致）",
    f"平台共收录 {len(domain_school)} 个邮箱后缀"])
