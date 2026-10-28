"""模块演示动画：队伍招募（约 40 秒）。主线是一支队伍从建起来到招满人：
1. 李想在计算机设计大赛建一支两人队（队伍挂在这一届比赛上），加一条招募需求；
2. 孙悦看到后申请；
3. 李想在「推荐邀请的人」里邀请林晓；
4. 李想同意孙悦，队伍满员，发给林晓的邀请自动失效（名额保护，只用一句话带过；
   并发同意的细节留给答辩追问，抢名额的动画留在 explain.html 的 race 场景里备用）。
脚本最后用接口核对：队伍 2/2、林晓的邀请是 EXPIRED，对不上就不出片。
会改演示数据。重录前重启 serve_video.py。
用法：python scripts/demo-video/record_module_team.py"""
import sys
import urllib.parse

from playwright.sync_api import sync_playwright

from module_common import BASE, Module, api, login

TEAM_NAME = "数据可视化小队"
li = login("li")
comp = api("GET", "/competitions?keyword=" + urllib.parse.quote("计算机设计"), li)["records"][0]
if any(t["name"] == TEAM_NAME for t in api("GET", "/teams/mine", li)):
    sys.exit(f"李想已经有「{TEAM_NAME}」了：先重启 serve_video.py 重新导入演示数据")
m = Module("队伍招募", {})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("何郝好", "队伍招募：建队、申请、邀请，招满为止")

    # ---------- 1. 李想建队、加招募需求 ----------
    page = m.site_page("li")
    page.goto(BASE + f"/competitions/{comp['id']}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(900)
    create = page.get_by_role("button", name="创建队伍")
    create.scroll_into_view_if_needed()
    page.evaluate("window.scrollBy(0, 160)")
    page.wait_for_timeout(300)
    r_new = m.begin(page, "create")
    r_new.say("建队时直接挂到比赛的这一届。", "计算机设计大赛的比赛页，李想点「创建队伍」，填队名、人数上限、简介")
    r_new.wait(0.3)
    r_new.cam(create, zoom=1.6, seconds=0.8, dy=-80)
    r_new.click(create)
    dialog = page.get_by_role("dialog")
    dialog.wait_for()
    r_new.wait(0.3)
    r_new.cam(dialog, zoom=1.5, seconds=0.7)
    r_new.type(dialog.get_by_placeholder("起个好记的名字"), TEAM_NAME, delay=0.04)
    r_new.click(dialog.locator(".el-input-number input"))
    page.keyboard.press("Control+A")
    page.keyboard.type("2")
    r_new.click(dialog.get_by_role("button", name="保存"))
    page.wait_for_url("**/teams/*")
    page.wait_for_load_state("networkidle")
    team_id = int(page.url.rstrip("/").split("/")[-1])
    r_new.cam(None, seconds=0.7)
    r_new.wait(0.5)
    r_new.say("再加一条招募需求：缺什么角色、几个人、什么要求。", "队伍页点「添加需求」，填角色和补充说明")
    add = page.get_by_role("button", name="添加需求")
    r_new.scroll_to(add, top=320)
    r_new.click(add)
    dialog = page.get_by_role("dialog")
    dialog.wait_for()
    r_new.wait(0.3)
    r_new.cam(dialog, zoom=1.5, seconds=0.7)
    r_new.type(dialog.get_by_placeholder("比如 前端、论文写作与排版"), "编程", delay=0.06)
    r_new.type(dialog.get_by_placeholder("比如 最好做过可视化、能周末线下"), "会 Python 画图", delay=0.04)
    r_new.click(dialog.get_by_role("button", name="保存"))
    page.wait_for_load_state("networkidle")
    r_new.wait(0.6)
    needs = page.locator(".block").filter(has=page.get_by_text("招募需求", exact=True)).first
    r_new.cam(needs, zoom=1.5, seconds=0.8)
    r_new.wait(0.6)
    r_new.spot(needs, pad=6)
    r_new.note(needs, "招募需求<small>角色、几个人、什么要求</small>", side="bottom")
    m.end(r_new, 0.8)

    # ---------- 2. 孙悦申请 ----------
    sun = m.site_page("sun")
    sun.goto(BASE + f"/teams/{team_id}")
    sun.wait_for_load_state("networkidle")
    sun.wait_for_timeout(800)
    r_apply = m.begin(sun, "apply")
    r_apply.say("学生看到招募，选对应的需求、写一句留言就能申请。", "孙悦打开这支队，点「申请加入」，选编程，写留言，发送")
    r_apply.wait(0.6)
    r_apply.click(sun.get_by_role("button", name="申请加入"))
    dialog = sun.get_by_role("dialog")
    dialog.wait_for()
    r_apply.wait(0.3)
    r_apply.cam(dialog, zoom=1.6, seconds=0.7)
    r_apply.click(dialog.locator(".el-radio", has_text="编程"), seconds=0.45)
    r_apply.type(dialog.locator("textarea"), "会 Python，想一起做作品。", delay=0.04)
    r_apply.click(dialog.get_by_role("button", name="发送"))
    sun.get_by_text("申请已发出", exact=False).wait_for()
    r_apply.cam(None, seconds=0.6)
    m.end(r_apply, 0.6)

    # ---------- 3、4. 李想邀请林晓，再同意孙悦 ----------
    page.reload()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(900)
    r_cap = m.begin(page, "captain")
    r_cap.say("队长也能在推荐的人里，主动邀请。", "队伍页「推荐邀请的人」，邀请林晓")
    row = page.locator(".candidate", has_text="林晓").first
    r_cap.scroll_to(row, top=330)
    r_cap.cam(row, zoom=1.7, seconds=0.8)
    r_cap.wait(0.4)
    r_cap.click(row.get_by_role("button", name="邀请"))
    dialog = page.get_by_role("dialog")
    dialog.wait_for()
    r_cap.wait(0.3)
    r_cap.click(dialog.get_by_role("button", name="发送"))
    page.wait_for_load_state("networkidle")
    r_cap.wait(0.8)
    r_cap.say("同意后队伍满员，其他还没处理的申请和邀请自动失效。", "「入队申请」里同意孙悦，队伍变成已满员，处理记录里林晓的邀请显示已失效")
    r_cap.cam(None, seconds=0.6)
    apply_row = page.locator(".block").filter(has=page.get_by_text("入队申请", exact=True)).locator(".row", has_text="孙悦").first
    r_cap.scroll_to(apply_row, top=330)
    r_cap.cam(apply_row, zoom=1.7, seconds=0.8)
    r_cap.wait(0.4)
    r_cap.click(apply_row.get_by_role("button", name="同意"))
    page.get_by_text("孙悦 已加入队伍").wait_for()
    page.wait_for_load_state("networkidle")
    r_cap.wait(0.6)
    history = page.get_by_text("处理记录", exact=False).first
    r_cap.scroll_to(history, top=300)
    r_cap.click(history)
    r_cap.wait(0.5)
    expired = page.locator(".history-row", has_text="林晓").first
    r_cap.cam(expired, zoom=1.9, seconds=0.8)
    r_cap.wait(0.5)
    r_cap.spot(expired, pad=8)
    r_cap.note(expired, "满员后自动失效<small>名额不会超</small>", side="top")
    r_cap.say("所以名额不会超，就算两份申请同时被同意也一样。", "处理记录：邀请林晓，已失效")
    m.end(r_cap, 0.8)
    m.browser.close()

team = next(t for t in api("GET", "/teams/mine", li) if t["id"] == team_id)
invite = next((a for a in api("GET", f"/teams/{team_id}/applications", li)
               if a["direction"] == "INVITE" and a["user"]["nickname"] == "林晓"), None)
if team["memberCount"] != team["maxMembers"] or not invite or invite["status"] != "EXPIRED":
    sys.exit(f"核对不通过：队伍 {team['memberCount']}/{team['maxMembers']}，林晓的邀请 {invite and invite['status']}，不出片")
m.render([(r_title, False), (r_new, True), (r_apply, True), (r_cap, True)], ["zoom", "fade", "fade"], facts=[
    f"李想建的「{TEAM_NAME}」挂在{comp['name']}上，人数上限 2",
    f"孙悦申请、李想同意后队伍 {team['memberCount']}/{team['maxMembers']}；发给林晓的邀请状态 {invite['status']}（满员后自动失效）"])
