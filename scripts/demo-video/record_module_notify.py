"""模块演示动画：消息通知（约 25 秒）。
1. 左右分屏、同一时刻录的两个页面：左边林晓点「发送」申请，右边陈一舟的页面立刻弹出提醒、铃铛数字跳动
   （真实的 WebSocket 推送；右边 5 秒内没弹出就不出片）；
2. 陈一舟的消息页：邮件提醒——不在网站上时，新消息超过 10 分钟没看，汇总成一封邮件。
会改演示数据（林晓发出一条申请）。重录前重启 serve_video.py。
用法：python scripts/demo-video/record_module_notify.py"""
import sys

from playwright.sync_api import sync_playwright

from demo_kit import SplitSegment
from module_common import BASE, Module, api, login

chen = login("chen")
team = next(t for t in api("GET", "/teams/mine", chen) if t["name"] == "数模冲刺队")
m = Module("消息通知", {})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("张宗令", "消息通知：该知道的事，马上知道", hold=2.0)

    # ---------- 分屏：林晓申请，陈一舟实时收到 ----------
    left = m.site_page("linxiao")
    left.goto(BASE + f"/teams/{team['id']}")
    left.wait_for_load_state("networkidle")
    right = m.site_page("chen")
    left.wait_for_timeout(1200)
    r_l = m.begin(left, "left", pos=(400, 420))
    r_r = m.begin(right, "right", pos=(1300, 420))
    r_l.say("林晓刚点「发送」，陈一舟这边马上弹出提醒。", "左右分屏：左边林晓申请，右边陈一舟的页面同时弹出提醒、铃铛数字变化")
    r_l.wait(0.8)
    r_l.click(left.get_by_role("button", name="申请加入"))
    dialog = left.get_by_role("dialog")
    dialog.wait_for()
    r_l.wait(0.3)
    r_l.cam(dialog, zoom=1.9, seconds=0.8)
    r_l.click(dialog.locator(".el-radio", has_text="编程"), seconds=0.45)
    r_l.type(dialog.locator("textarea"), "我会 Python，想一起冲省一！", delay=0.05)
    r_l.click(dialog.get_by_role("button", name="发送"))
    try:
        right.locator(".el-notification").first.wait_for(timeout=5000)
    except Exception:
        sys.exit("陈一舟的页面 5 秒内没收到推送，不出片（看看前端代理的 WebSocket 是不是通的）")
    r_r.event("pop")
    r_r.cam(right.locator(".el-notification").first, zoom=2.6, seconds=0.8, dx=-60, dy=60)
    r_l.say("铃铛数字同时变化；同一个人开着几个页面，已读也会同步。", "右边的提醒和铃铛")
    r_l.wait(1.5)
    m.end(r_l, 0.6)
    m.end(r_r, 0)

    # ---------- 邮件提醒 ----------
    page = m.site_page("chen")
    page.goto(BASE + "/notifications")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    r_mail = m.begin(page, "mail")
    r_mail.say("不在网站上时，新消息超过 10 分钟没看，会汇总成一封邮件。", "陈一舟的消息页，圈出「邮件提醒」")
    r_mail.wait(0.8)
    switch = page.locator(".mail").first
    r_mail.cam(switch, zoom=1.6, seconds=0.9)
    r_mail.wait(0.8)
    r_mail.spot(switch, pad=8)
    r_mail.note(switch, "邮件提醒<small>只汇总还没看的，看过的不发</small>", side="bottom")
    m.end(r_mail, 0.8)
    m.browser.close()

split = SplitSegment(r_l, r_r, r_l.marks["start"], r_l.marks["end"],
                     labels=("林晓的页面（申请人）", "陈一舟的页面（队长）"))
m.render([(r_title, False), split, (r_mail, True)], ["zoom", "wipe"],
         facts=["右边的提醒是真实的 WebSocket 推送：林晓点发送后，陈一舟的页面在 5 秒内弹出了提醒（脚本检查过）"])
