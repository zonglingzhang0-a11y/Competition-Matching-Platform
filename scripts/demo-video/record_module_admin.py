"""模块演示动画：管理看板（约 35 秒）。管理员首页的数据看板，镜头依次推到四块：
组队风险图、组队漏斗、运营，每块配一句"该做什么"。只读数据，不改演示数据。
用法：python scripts/demo-video/record_module_admin.py"""
from playwright.sync_api import sync_playwright

from module_common import BASE, Module

m = Module("管理看板", {})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("", "管理看板：每一块都写着该做什么", eyebrow="竞搭 · 管理员视角", sub="真实页面 · 演示数据")

    page = m.site_page("admin")
    page.goto(BASE + "/admin")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(2200)
    rec = m.begin(page, "dashboard")
    rec.say("管理员首页是数据看板：每一块都写着该做什么。", "数据看板全景")
    rec.wait(1.4)

    risk = page.locator(".risk-grid > section").nth(0)
    funnel = page.locator(".risk-grid > section").nth(1)
    rec.say("风险图：快截止的比赛里，还有多少关注的人没进队。", "镜头推到各届组队风险图")
    rec.scroll_to(risk, top=150)
    rec.cam(risk, zoom=1.45, seconds=0.9, dx=110)
    rec.wait(0.9)
    rec.spot(risk, pad=8)
    rec.note(risk, "左边高处的最该管<small>离截止近、没进队的人多</small>", side="right")

    rec.say("漏斗：大家卡在关注、申请、入队的哪一步。", "移到组队漏斗")
    rec.wait(0.4)
    rec.clear_notes()
    rec.cam(funnel, zoom=1.45, seconds=0.9, dx=-110)
    rec.wait(0.9)
    rec.spot(funnel, pad=8)
    rec.note(funnel, "哪一步掉人最多<small>就从哪一步下手</small>", side="left")

    card = lambda title: page.locator(".el-card.block").filter(  # noqa: E731
        has=page.locator(".el-card__header", has_text=title)).first
    ops = card("运营")
    rec.say("运营：积压的申请、待审核的认证、爬虫来源，每块都带着去处理的链接。", "往下到运营四小块")
    rec.wait(0.4)
    rec.clear_notes()
    rec.cam(None, seconds=0.7)
    rec.scroll_to(ops, top=120)
    rec.cam(ops, zoom=1.4, seconds=0.9)
    rec.wait(0.9)
    rec.spot(ops, pad=6)
    m.end(rec, 1.0)
    m.browser.close()

m.render([(r_title, False), (rec, True)], ["zoom"])
