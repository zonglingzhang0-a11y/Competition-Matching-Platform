"""模块演示动画：互评与日程（约 50 秒，60 帧 H.264，音效直接合在视频里）。

内容：
1. 章节卡；
2. 钱多多在镜头里真实地填互评表、提交（录制前用接口让「国一冲冲冲」结束比赛，赵子航先交了，所以她一交就揭晓）；
3. 讲解动画：用她刚选的 6 项算出赵子航这一轮的得分、信用分和推荐时的信用折算；
4. 切到赵子航的「互评与信用」页，页面上的分数和动画算的一致（脚本会核对，对不上就不出片）；
5. 林晓用一句话添加一条比赛期间的日程，日历上真实地标出撞期。

会改演示数据（结束比赛、交互评、加日程）。重录前重启 serve_video.py（会重新导入演示数据）。
用法：python scripts/demo-video/record_module_review.py [前端地址] [输出目录] [抓帧的临时目录]"""
import json
import os
import shutil
import sys
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
os.environ.setdefault("E2E_PORT", "18093")
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

from demo_kit import (ANNOT_JS, CURSOR_JS, Recorder, Segment, compose, mux_audio, srt_time,  # noqa: E402
                      write_sfx, write_srt)

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:5176"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "演示视频" / "模块_互评与日程"
tmp = Path(sys.argv[3]) if len(sys.argv) > 3 else out / "frames"
NAME = "互评与日程"
out.mkdir(parents=True, exist_ok=True)
shutil.rmtree(tmp, ignore_errors=True)
VIEW, DPR = {"width": 1536, "height": 864}, 2
HIDE_DEVTOOLS = """document.addEventListener('DOMContentLoaded', () => {
  const s = document.createElement('style');
  s.textContent = '#__vue-devtools-container__, #vue-inspector-container { display: none !important; }';
  document.head.appendChild(s);
});"""
# 钱多多给赵子航的 6 项（0 从未 / 1 偶尔 / 2 有时 / 3 经常）；分量和 review/CreditCalculator.WEIGHTS 一致
ANSWERS = [0, 1, 0, 2, 0, 0]
WEIGHTS = [2, 2, 2, 1, 1, 1]


def api(method, path, token=None, body=None):
    req = urllib.request.Request(env.API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["data"]


def login(name):
    return api("POST", "/auth/login", body={"email": f"{name}@demo.local", "password": env.demo_password()})["token"]


# ---------- 1. 准备：结束比赛，赵子航先交互评 ----------
zhao, qian, lin, admin = login("zhao"), login("qian"), login("linxiao"), login("admin")
qian_id = api("GET", "/users/me", qian)["id"]
team = next(t for t in api("GET", "/teams/mine", zhao) if t["name"] == "国一冲冲冲")
if api("GET", "/reviews/mine", zhao):
    sys.exit("「国一冲冲冲」已经有互评了：先重启 serve_video.py 重新导入演示数据，再录")
round_id = api("POST", f"/teams/{team['id']}/finish", zhao, {"award": "省级一等奖"})
api("PUT", f"/reviews/{round_id}", zhao, {"reviews": [{"rateeId": qian_id, "answers": [0] * 6}]})
form = api("GET", f"/reviews/{round_id}", qian)
items, freqs = form["items"], form["frequencies"]
penalty = sum(a * w for a, w in zip(ANSWERS, WEIGHTS))
max_pen = sum(3 * w for w in WEIGHTS)
score = round(100 * (1 - penalty / max_pen), 2)
history = next(p["config"] if isinstance(p["config"], dict) else json.loads(p["config"])
               for p in api("GET", "/admin/match-configs", admin)["presets"] if p["preset"] == "DEFAULT")["commitment"]["history"]
kh = history["min"] + (history["max"] - history["min"]) * score / 100
contest = next(n for n in api("GET", f"/competitions/{team['competitionId']}" if "competitionId" in team else
                              "/competitions/1", lin)["editions"][0]["nodes"] if n["nodeType"] == "CONTEST")
day = datetime.fromisoformat(contest["startAt"]) + timedelta(days=1)
sentence = f"{day.month}月{day.day}日下午2点到5点 社团换届"
print(f"钱多多的选择 {ANSWERS} → 扣 {penalty}/{max_pen} → {score}；信用折算 {kh:.3f}；日程：{sentence}")

data = {"credit": {
    "eyebrow": "钱多多给赵子航的互评 · 匿名，揭晓后只显示汇总",
    "heading": f"赵子航这一轮得 {score:.1f} 分",
    "items": [{"name": n, "freq": freqs[a], "value": a, "weight": w} for n, a, w in zip(items, ANSWERS, WEIGHTS)],
    "penalty": penalty, "max": max_pen, "score": score,
    "cards": [
        {"title": f"信用分 {score:.1f}", "text": "历次互评得分的平均；这是他的第 1 轮"},
        {"title": f"推荐时 × {kh:.2f}", "text": f"信用折算 = {history['min']} + {history['max'] - history['min']:.1f} × {score:.1f} ÷ 100，"
                                                 "算在投入保障里"},
        {"title": "防恶意打分", "text": "评分人有 3 个以上时，和别人差太远的评分只算一半"},
        {"title": "两场被评为经常失联", "text": "暂停推荐 30 天，管理员复核", "red": True},
    ]}}


def framed_login(browser, who):
    ctx = browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
    for script in (HIDE_DEVTOOLS, CURSOR_JS, ANNOT_JS):
        ctx.add_init_script(script)
    page = ctx.new_page()
    page.goto(base + "/login")
    page.wait_for_load_state("networkidle")
    page.get_by_placeholder("name@example.com").fill(f"{who}@demo.local")
    page.locator("input[type=password]").fill(env.demo_password())
    page.get_by_role("button", name="登录").click()
    page.wait_for_url(base + "/")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1000)
    return page


def begin(page, folder, pos=(900, 300)):
    rec = Recorder(page, tmp / folder)
    rec.pos = pos
    rec.resync_cursor()
    rec.start(quality=92)
    rec.wait(0.4)
    rec.mark("start")
    return rec


def finish(rec, tail=0.6):
    rec.close_caption()
    rec.wait(tail)
    rec.mark("end")
    rec.stop()


with sync_playwright() as p:
    browser = p.chromium.launch(args=[f"--force-device-scale-factor={DPR}"])
    card_ctx = browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
    card_ctx.add_init_script(f"window.__DATA = {json.dumps(data, ensure_ascii=False)};")

    # ---------- 2. 章节卡 ----------
    page = card_ctx.new_page()
    page.goto((HERE / "explain.html").as_uri())
    page.wait_for_load_state("networkidle")
    r_title = begin(page, "title")
    big = "互评与日程"
    page.evaluate(f"demo.title('竞搭 · 负责：何金豪', '{big}：赛后互评算成信用，日程放进一张日历', '真实操作 · 数字来自系统实时计算')")
    for i in range(len(big)):
        r_title.event("tick", 0.22 + i * 0.09)
    r_title.event("pop", 0.32 + len(big) * 0.09)
    r_title.wait(2.4)
    finish(r_title, 0)

    # ---------- 3. 钱多多填互评 ----------
    page = framed_login(browser, "qian")
    page.goto(base + f"/reviews/{round_id}")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    r_form = begin(page, "form")
    r_form.say("比赛结束后匿名互评：六种行为，按发生的频率选。", "互评表全景，镜头推近，钱多多逐项选择")
    r_form.wait(1.2)
    card = page.locator(".el-card").filter(has=page.locator(".item")).first
    r_form.cam(card, zoom=1.35, seconds=0.9, dy=-40)
    r_form.wait(0.6)
    for i, a in enumerate(ANSWERS):
        r_form.click(card.locator(".item").nth(i).locator(".el-radio-button", has_text=freqs[a]), seconds=0.4)
        r_form.wait(0.1)
    r_form.spot(card, pad=6)
    r_form.note(card.locator(".item").first, "前三项是硬伤<small>失联、拖延、敷衍，分量加倍</small>", side="top")
    r_form.say("交齐就统一揭晓，谁给谁打了什么，别人看不到。", "提交，提示结果已经揭晓")
    r_form.wait(1.4)
    r_form.clear_notes()
    submit = page.get_by_role("button", name="提交互评")
    r_form.scroll_to(submit, top=520)
    r_form.cam(submit, zoom=1.6, seconds=0.7, dy=-120)
    r_form.click(submit)
    page.get_by_text("结果已经揭晓").wait_for()
    r_form.wait(0.3)
    r_form.cam(None, seconds=0.8)
    finish(r_form, 0.8)

    # ---------- 4. 讲解动画：信用分 ----------
    page = card_ctx.new_page()
    page.goto((HERE / "explain.html").as_uri())
    page.wait_for_load_state("networkidle")
    r_x = begin(page, "explain")
    r_x.say(f"按分量扣分：一共扣 {penalty} 分，赵子航这轮得 {score:.1f}。",
            "讲解动画：六项选择逐行出现，扣分合计，算出本轮得分")
    page.evaluate("demo.credit()")
    r_x.wait(0.7)
    page.evaluate("demo.creditRows()")
    for i in range(len(ANSWERS)):
        r_x.event("tick", i * 0.33)
    r_x.wait(2.3)
    page.evaluate("demo.creditSum()")
    r_x.event("pop", 0.85)
    r_x.say(f"并进历史平均就是信用分，推荐他时再乘 {kh:.2f}。",
            "讲解动画：右边依次弹出信用分、信用折算、防恶意打分、暂停推荐")
    page.evaluate("demo.creditSide()")
    for i in range(4):
        r_x.event("pop", i * 0.5 + 0.05)
    finish(r_x, 1.0)

    # ---------- 5. 赵子航的信用页 ----------
    page = framed_login(browser, "zhao")
    page.goto(base + "/reviews")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)
    shown = float(page.locator(".score .number").first.inner_text())
    real = api("GET", "/credit/me", zhao)["score"]
    if abs(shown - score) > 0.05 or abs(real - score) > 0.05:
        sys.exit(f"页面显示 {shown}、接口 {real}，和动画算的 {score} 对不上，不出片")
    r_zhao = begin(page, "zhao")
    r_zhao.say(f"赵子航的信用页上，显示的正是 {score:.1f}。", f"赵子航的「互评与信用」页，圈出 {score:.1f}")
    r_zhao.wait(0.8)
    num = page.locator(".score").first
    r_zhao.cam(num, zoom=2.0, seconds=0.8, dx=160)
    r_zhao.wait(0.9)
    r_zhao.spot(num, pad=10)
    r_zhao.note(num, f"{score:.1f} 分<small>和动画算的一致</small>", side="right")
    finish(r_zhao, 0.6)

    # ---------- 6. 林晓一句话加日程，日历标出撞期 ----------
    page = framed_login(browser, "linxiao")
    r_cal = begin(page, "calendar")
    r_cal.say("个人日程一句话就能加：日期、时间自动认出来。", "主页输入一句话，下面实时显示认出的日期和时间，点添加")
    r_cal.wait(1.0)
    box = page.get_by_placeholder("一句话添加日程，比如：周三晚上7点到9点 队伍开会")
    r_cal.cam(box, zoom=1.9, seconds=0.9, dx=120, dy=60)
    r_cal.wait(0.5)
    r_cal.type(box, sentence, delay=0.07)
    r_cal.wait(0.4)
    preview = page.locator(".quick .preview").first
    r_cal.spot(preview, pad=6)
    r_cal.note(preview, "自动认出的日期和时间", side="bottom")
    r_cal.wait(1.0)
    r_cal.clear_notes()
    r_cal.click(page.locator(".quick").get_by_role("button", name="添加"))
    r_cal.wait(0.8)
    r_cal.say("这天正好在比赛期间：日历上会用红框标出撞期。", "进日历页，红框和撞期提醒")
    r_cal.cam(None, seconds=0.7)
    r_cal.click(page.get_by_text("日历", exact=True).first)
    page.wait_for_url("**/calendar")
    page.wait_for_load_state("networkidle")
    r_cal.wait(0.8)
    # 日历只检查当前显示的月份；日程在比赛那个月，翻过去才看得到红框和撞期提醒
    if day.month != datetime.now().month:
        r_cal.click(page.locator(".fc-next-button"))
        page.wait_for_load_state("networkidle")
        r_cal.wait(0.8)
    alert = page.locator(".conflicts")
    if not alert.count():
        sys.exit("日历页没有撞期提醒，和字幕对不上，不出片")
    red = page.locator(".fc-event.is-conflict").first
    r_cal.cam(red, zoom=1.8, seconds=0.9)
    r_cal.wait(0.9)
    r_cal.spot(red, pad=6)
    r_cal.note(red, "红框：撞期", side="bottom")
    r_cal.wait(1.3)
    r_cal.clear_notes()
    r_cal.cam(alert, zoom=1.7, seconds=0.9)
    r_cal.wait(0.9)
    r_cal.spot(alert, pad=6)
    r_cal.note(alert, "撞期提醒<small>个人日程和比赛时间重叠</small>", side="bottom")
    finish(r_cal, 1.0)
    browser.close()

segments = [Segment(r_title, r_title.marks["start"], r_title.marks["end"]),
            Segment(r_form, r_form.marks["start"], r_form.marks["end"], framed=True),
            Segment(r_x, r_x.marks["start"], r_x.marks["end"]),
            Segment(r_zhao, r_zhao.marks["start"], r_zhao.marks["end"], framed=True),
            Segment(r_cal, r_cal.marks["start"], r_cal.marks["end"], framed=True)]
clean, preview_mp4 = out / f"{NAME}.mp4", out / f"{NAME}_带字幕.mp4"
captions, events, duration = compose(segments, clean, preview_mp4, transitions=["zoom", "wipe", "zoom", "fade"])
wav = out / f"{NAME}_音效.wav"
write_sfx(events, duration, wav)
mux_audio(clean, wav)
mux_audio(preview_mp4, wav)
write_srt(captions, 0, out / f"{NAME}.srt")
rows = [f"# 模块演示：{NAME} · 文案", "",
        f"两个视频都已经带音效：「{NAME}.mp4」不带字幕（自己配音用），「{NAME}_带字幕.mp4」字幕画在画面上（直接放进 PPT）。",
        "", "| # | 时间 | 画面 | 字幕 |", "|---|---|---|---|"]
for i, (a, b, text, scene) in enumerate(captions, 1):
    rows.append(f"| {i} | {srt_time(a)[3:8]}–{srt_time(b)[3:8]} | {scene} | {text} |")
rows += ["", "真实数据（录制时核对过）：", "",
         f"- 钱多多给赵子航的 6 项：{'、'.join(f'{n} {freqs[a]}' for n, a in zip(items, ANSWERS))}",
         f"- 扣分 {penalty} / 满分 {max_pen}，本轮得分 100 × (1 − {penalty} ÷ {max_pen}) = {score}；"
         f"赵子航信用页显示 {score:.1f}",
         f"- 信用折算 {history['min']} + {history['max'] - history['min']:.1f} × {score} ÷ 100 = {kh:.3f}",
         f"- 日程「{sentence}」落在比赛进行时间（{contest['startAt']} ~ {contest['endAt']}）里"]
(out / "文案.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
print(f"完成，时长 {duration:.1f} 秒，音效点 {len(events)} 个")
