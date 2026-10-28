"""模块演示动画：队伍招募（约 45 秒）。
1. 林晓看数模冲刺队的招募需求，申请编程这个位置；
2. 陈一舟在主页「待你处理」里一键同意，队伍变成 2/3；
3. 只剩最后一个名额：周可也申请，脚本用两个线程同一时刻同意孙悦和周可（真实请求），
   讲解动画放的是系统真实返回的结果——一个成功、一个被拦下，以及被拦时的原话；
4. 陈一舟的队伍页：3/3 人。
会改演示数据。重录前重启 serve_video.py。
用法：python scripts/demo-video/record_module_team.py"""
import json
import sys
import threading
import urllib.error
import urllib.request

from playwright.sync_api import sync_playwright

from module_common import BASE, Module, api, env, login

TEAM = "数模冲刺队"


def call_raw(method, path, token):
    """返回 (状态码, 提示)，4xx 也照样拿回来"""
    req = urllib.request.Request(env.API + path, method=method, headers={"Authorization": f"Bearer {token}"})
    try:
        with urllib.request.urlopen(req) as r:
            body = json.loads(r.read())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read()).get("message", "")
    return 200, body.get("message", "")


chen, zhou = login("chen"), login("zhou")
team = next(t for t in api("GET", "/teams/mine", chen) if t["name"] == TEAM)
if team["memberCount"] != 1:
    sys.exit(f"{TEAM} 现在 {team['memberCount']} 人，不是演示数据的初始状态：先重启 serve_video.py")
m = Module("队伍招募", {})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("何郝好", "队伍招募：建队、申请、同意，名额不会超")

    # ---------- 林晓看需求、申请 ----------
    page = m.site_page("linxiao")
    page.goto(BASE + f"/teams/{team['id']}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    r_apply = m.begin(page, "apply")
    r_apply.say("队长建队时关联比赛的一届，写清缺什么人、要什么技能。", "数模冲刺队的队伍页，镜头推到招募需求")
    r_apply.wait(1.0)
    needs = page.locator(".block").filter(has=page.get_by_text("招募需求", exact=True)).first
    r_apply.scroll_to(needs, top=230)
    r_apply.cam(needs, zoom=1.45, seconds=0.9)
    r_apply.wait(0.9)
    r_apply.spot(needs, pad=6)
    r_apply.note(needs, "招募需求<small>角色、技能要求、还缺几人</small>", side="bottom")
    r_apply.say("学生选对应的需求、写一句留言，就能申请。", "滚回顶部，点「申请加入」，选编程，写留言，发送")
    r_apply.wait(1.0)
    r_apply.clear_notes()
    r_apply.cam(None, seconds=0.7)
    r_apply.scroll(-2000, seconds=0.6)
    r_apply.click(page.get_by_role("button", name="申请加入"))
    dialog = page.get_by_role("dialog")
    dialog.wait_for()
    r_apply.wait(0.3)
    r_apply.cam(dialog, zoom=1.6, seconds=0.7)
    r_apply.click(dialog.locator(".el-radio", has_text="编程"), seconds=0.45)
    r_apply.type(dialog.locator("textarea"), "我会 Python，做过数据处理和画图，想一起冲省一！", delay=0.05)
    r_apply.click(dialog.get_by_role("button", name="发送"))
    page.get_by_text("申请已发出", exact=False).wait_for()
    r_apply.cam(None, seconds=0.7)
    m.end(r_apply, 0.7)

    # ---------- 陈一舟一键同意 ----------
    page = m.site_page("chen")
    r_ok = m.begin(page, "accept")
    r_ok.say("队长在主页一键同意，还能看到同意后队伍变成几人。", "陈一舟主页的「待你处理」，圈出林晓的申请，点同意")
    r_ok.wait(1.0)
    item = page.locator("article.pending", has_text="林晓").first
    r_ok.cam(item, zoom=1.9, seconds=0.9)
    r_ok.wait(0.8)
    r_ok.spot(item, pad=6)
    r_ok.note(item, "同意前就告诉你：<small>同意后队伍是几个人</small>", side="bottom")
    r_ok.wait(1.4)
    r_ok.clear_notes()
    r_ok.click(item.get_by_role("button", name="同意"))
    r_ok.wait(1.0)
    r_ok.cam(None, seconds=0.7)
    m.end(r_ok, 0.6)

    # ---------- 真实并发：同一时刻同意孙悦和周可 ----------
    api("POST", f"/teams/{team['id']}/applications", zhou, {"message": "想试试，可以负责查资料"})
    pending = {x["who"]: x["applicationId"] for x in api("GET", "/home", chen)["pending"] if x["kind"] != "INVITE"}
    names = [n for n in ("孙悦", "周可") if n in pending]
    if len(names) != 2:
        sys.exit(f"待处理的申请里没找齐孙悦和周可：{pending}")
    barrier, results = threading.Barrier(2), {}

    def accept(name):
        barrier.wait()
        results[name] = call_raw("POST", f"/applications/{pending[name]}/accept", chen)

    threads = [threading.Thread(target=accept, args=(n,)) for n in names]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    after = next(t for t in api("GET", "/teams/mine", chen) if t["name"] == TEAM)
    winners = [n for n in names if results[n][0] == 200]
    print("并发结果：", results, "队伍", after["memberCount"], "/", after["maxMembers"])
    if len(winners) != 1 or after["memberCount"] != after["maxMembers"]:
        sys.exit("并发结果不是一个成功一个失败，不出片")
    a, b = names
    race = {"eyebrow": "真实发出的两个请求 · 同一时刻同意两份申请", "heading": "最后一个名额，两个人同时被同意",
            "team": TEAM, "before": after["memberCount"] - 1, "after": after["memberCount"], "max": after["maxMembers"],
            "a": {"name": a, "code": results[a][0], "msg": results[a][1]},
            "b": {"name": b, "code": results[b][0], "msg": results[b][1]},
            "winner": "a" if winners[0] == a else "b",
            "how": "做法：同意时先锁住队伍那一行（SELECT … FOR UPDATE）再数人数；满员后这支队其他没处理的申请自动作废；"
                   "同一届一人只能在一支队，由数据库唯一约束兜底。"}

    # ---------- 讲解动画 ----------
    page = m.card_page()
    page.evaluate("d => Object.assign(window.__DATA, d)", {"race": race})
    r_x = m.begin(page, "explain")
    r_x.say("只剩最后一个名额时，我们真的同时发出了两个「同意」：", "讲解动画：两份申请同时被同意，冲向最后一个名额")
    page.evaluate("demo.race()")
    r_x.event("pop", 0.5)
    r_x.wait(1.8)
    page.evaluate("demo.raceGo()")
    r_x.event("whoosh")
    r_x.event("click", 0.7)
    r_x.event("ding", 1.3)
    r_x.say("先锁住队伍再数人数：一个成功，另一个被拦下，不会超员。", "讲解动画：先拿到锁的进队、满员，另一个被拦下，显示真实的返回")
    r_x.wait(1.2)
    page.evaluate("demo.raceEnd()")
    r_x.event("pop", 0.05)
    m.end(r_x, 1.2)

    # ---------- 队伍页：3/3 ----------
    page = m.site_page("chen")
    page.goto(BASE + f"/teams/{team['id']}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    r_end = m.begin(page, "end")
    full = f"{after['memberCount']}/{after['maxMembers']}"
    r_end.say(f"队伍最终 {full} 人，满员后不会再多进一个。", f"陈一舟的队伍页，圈出队员（{full}）")
    r_end.wait(0.9)
    members = page.locator(".block").filter(has=page.get_by_text(f"队员（{full}）", exact=True)).first
    r_end.cam(members, zoom=1.45, seconds=0.9, dy=-70)
    r_end.wait(0.9)
    r_end.spot(members, pad=6)
    r_end.note(members, f"{full} 人<small>{winners[0]} 进队</small>", side="top")
    m.end(r_end, 0.8)
    m.browser.close()

loser = b if winners[0] == a else a
m.render([(r_title, False), (r_apply, True), (r_ok, True), (r_x, False), (r_end, True)],
         ["zoom", "fade", "wipe", "zoom"], facts=[
             f"并发同意两份申请：{a} → {results[a][0]}「{results[a][1]}」；{b} → {results[b][0]}「{results[b][1]}」",
             f"最终 {TEAM} {full} 人，{winners[0]} 进队，{loser} 被拦下"])
