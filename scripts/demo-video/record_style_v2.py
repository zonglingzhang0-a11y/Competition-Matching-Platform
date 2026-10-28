"""讲解风格样片 v2（约 50 秒）：内容和第一版一样，拍法更"活"——
网页放进窗口画框、背景缓慢流动；镜头先停在全景再推进去，停下来也在慢慢往里推；鼠标走弧线；
标注弹出、圈出的框呼吸一下；片头逐字弹出；段落之间用推镜头和色带划过转场；另出一条对齐好的音效轨。

真实、可复刻（和第一版一样）：网页画面是脚本在本机测试环境里真点出来的，讲解层不改页面内容；
打分动画的数字从接口取，先按匹配引擎的算法重算一遍，和接口返回的对不上就不录。

用法：python scripts/demo-video/record_style_v2.py [前端地址] [输出目录] [抓帧的临时目录]"""
import json
import os
import re
import shutil
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
os.environ.setdefault("E2E_PORT", "18093")
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

from demo_kit import (ANNOT_JS, CURSOR_JS, Recorder, Segment, compose, srt_time, write_player,  # noqa: E402
                      write_sfx, write_srt)

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:5176"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "演示视频" / "风格样片v2"
tmp = Path(sys.argv[3]) if len(sys.argv) > 3 else out / "frames"
out.mkdir(parents=True, exist_ok=True)
shutil.rmtree(tmp, ignore_errors=True)
VIEW, DPR = {"width": 1536, "height": 864}, 2
NAME = "风格样片v2"
HIDE_DEVTOOLS = """document.addEventListener('DOMContentLoaded', () => {
  const s = document.createElement('style');
  s.textContent = '#__vue-devtools-container__, #vue-inspector-container { display: none !important; }';
  document.head.appendChild(s);
});"""


# ---------- 1. 真实数据 ----------
def api(method, path, token=None, body=None):
    req = urllib.request.Request(env.API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["data"]


def login(name):
    return api("POST", "/auth/login", body={"email": f"{name}@demo.local", "password": env.demo_password()})["token"]


student, admin = login("linxiao"), login("admin")
edition, match = next((e["edition"], t["match"]) for e in api("GET", "/recommendations/home", student)
                      for t in e["teams"] if t["team"]["name"] == "数模冲刺队")
preset = next(c["matchPreset"] for c in api("GET", "/admin/competitions?size=50", admin)["records"]
              if c["id"] == edition["competitionId"]) or "DEFAULT"
configs = {p["preset"]: p["config"] if isinstance(p["config"], dict) else json.loads(p["config"])
           for p in api("GET", "/admin/match-configs", admin)["presets"]}
weights = {d["key"]: d["weight"] for d in configs["DEFAULT"]["dimensions"]}
weights.update(configs.get(preset, {}).get("dimensionWeights") or {})
dims = [{"name": d["name"], "score": d["score"], "weight": weights[d["key"]]}
        for d in match["dimensions"] if d["score"] is not None]
weighted = sum(d["score"] * d["weight"] for d in dims) / sum(d["weight"] for d in dims)
comp, k = match["completeness"], match["k"]
personal = next((w for w in match["warnings"] if "个人日程" in w), None)
factor = 0.9 if personal else 1.0
expected = (50 + (weighted - 50) * comp) * k * factor
if abs(expected - match["score"]) > 0.15:
    sys.exit(f"按引擎算法重算是 {expected:.2f}，接口返回 {match['score']}，对不上，不录（看看是不是有别的系数）")
event = re.search(r"「(.+?)」", personal).group(1) if personal else None
preset_name = {"SHORT_INTENSIVE": "短期高强度", "LONG_TERM": "长期", "DEFAULT": "默认"}[preset]
shown = round(match["score"])
data = {
    "eyebrow": f"林晓 × 数模冲刺队 · {edition['competitionName']}用「{preset_name}」这套权重",
    "heading": f"{shown} 分是怎么算出来的",
    "dims": dims, "weighted": round(weighted, 1), "sumHint": "五个维度的分 × 权重",
    "chips": [
        {"title": "问卷填全了" if comp >= 1 else f"问卷完整度 {comp:.0%}",
         "text": "完整度 100%，不往 50 分收" if comp >= 1 else "数据少，往 50 分收"},
        {"title": f"× 投入保障 {k:.1f}",
         "text": "信用分和备赛安排都没有要打折的" if k >= 1 else "信用分或备赛安排打了折"},
    ] + ([{"title": "× 0.9", "text": f"比赛期间林晓有个人日程「{event}」", "red": True}] if personal else []),
    "score": match["score"],
    "totalCaption": f"页面上显示 {shown} 分，和系统算出来的一致",
}
print(f"真实数据：加权 {weighted:.1f}，完整度 {comp}，投入保障 {k}，系数 {factor} → {expected:.2f}（接口 {match['score']}）")

with sync_playwright() as p:
    # 无头浏览器的录屏会忽略 device_scale_factor，只给 CSS 尺寸的帧；强制设备倍率才拿得到 2 倍清晰度的帧
    browser = p.chromium.launch(args=[f"--force-device-scale-factor={DPR}"])

    # ---------- 2. 片头：逐字弹出 ----------
    card_ctx = browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
    card_ctx.add_init_script(f"window.__DATA = {json.dumps(data, ensure_ascii=False)};")
    page = card_ctx.new_page()
    page.goto((HERE / "explain.html").as_uri())
    page.wait_for_load_state("networkidle")
    rec_title = Recorder(page, tmp / "title")
    rec_title.start(quality=92)
    rec_title.wait(0.3)
    rec_title.mark("start")
    big = "匹配推荐"
    page.evaluate(f"demo.title('竞搭 · 功能演示', '{big}：每条推荐都说得出理由', '真实操作 + 讲解动画 · 分数来自系统实时计算')")
    for i in range(len(big)):
        rec_title.event("tick", 0.22 + i * 0.09)
    rec_title.event("pop", 0.32 + len(big) * 0.09)
    rec_title.wait(3.2)
    rec_title.mark("end")
    rec_title.stop()

    # ---------- 3. 真实操作（放进窗口画框） ----------
    ui_ctx = browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
    for script in (HIDE_DEVTOOLS, CURSOR_JS, ANNOT_JS):
        ui_ctx.add_init_script(script)
    page = ui_ctx.new_page()
    page.goto(base + "/login")
    page.wait_for_load_state("networkidle")
    page.get_by_placeholder("name@example.com").fill("linxiao@demo.local")
    page.locator("input[type=password]").fill(env.demo_password())
    page.get_by_role("button", name="登录").click()
    page.wait_for_url(base + "/")
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(1500)
    rec = Recorder(page, tmp / "ui")
    rec.pos = (900, 300)
    rec.resync_cursor()
    rec.start(quality=92)
    rec.wait(0.5)
    rec.mark("start")

    rec.say("林晓还没有组队，主页右边是系统为她推荐的队伍。", "整个窗口的全景，镜头推进到右边的推荐")
    rec.wait(1.6)
    hexa = page.locator("figure.hexagon").first
    rec.move_to(hexa, seconds=0.8)
    rec.cam(hexa, zoom=1.7, seconds=1.0, dy=40)
    rec.wait(1.1)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "匹配六边形<small>五个维度，加上投入保障</small>", side="left")

    rec.say("六边形是她和数模冲刺队的匹配画像；点一下，把另一支队伍叠上来对比。", "聚光灯圈出六边形，点「叠上去对比」")
    rec.wait(2.0)
    rec.clear_notes()
    rec.click(page.locator(".compare button", has_text="计设作品组"))
    rec.wait(0.8)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "虚线是计设作品组<small>差在哪一眼就能看出来</small>", side="left")

    rec.say(f"每条推荐都写清了理由，算出来的总分是 {shown} 分。", f"镜头往下移到数模冲刺队的卡片，圈出 {shown} 分")
    rec.wait(1.0)
    rec.clear_notes()
    rec.event("whoosh")
    rec.scroll(260, seconds=0.9)
    first_card = page.locator(".rec").first
    rec.cam(first_card, zoom=1.7, seconds=0.8, dy=10, sound=False)
    rec.wait(0.9)
    rec.spot(first_card, pad=8)
    rec.note(first_card, f"总分 {shown}，附推荐理由<small>这 {shown} 分是怎么来的？</small>", side="left")
    rec.close_caption()
    rec.wait(0.4)
    rec.clear_notes()
    rec.cam(None, seconds=0.9)
    rec.wait(1.0)
    rec.mark("end")
    rec.stop()

    # ---------- 4. 打分动画 ----------
    page = card_ctx.new_page()
    page.goto((HERE / "explain.html").as_uri())
    page.wait_for_load_state("networkidle")
    rec_x = Recorder(page, tmp / "explain")
    rec_x.start(quality=92)
    rec_x.wait(0.3)
    rec_x.mark("start")
    rec_x.say(f"这 {shown} 分是这样算的：五个维度按{preset_name}比赛的权重加起来，合计 {weighted:.1f}。",
              "讲解动画：五个维度的分数逐个出来，乘权重，得出加权合计")
    page.evaluate("demo.explain()")
    rec_x.wait(0.8)
    page.evaluate("demo.rows()")
    for i in range(len(dims)):
        rec_x.event("tick", i * 0.38)
    rec_x.wait(2.8)
    page.evaluate("demo.sum()")
    rec_x.event("pop", 0.9)
    tail = f"她比赛期间有{event}，再乘 0.9，" if personal else ""
    rec_x.say(f"问卷填全了，投入保障也不打折；{tail}就是页面上的 {shown} 分。",
              f"讲解动画：右边逐个弹出系数，最后得出 {match['score']}")
    page.evaluate("demo.chips()")
    for i in range(len(data["chips"])):
        rec_x.event("pop", i * 0.45 + 0.05)
    rec_x.wait(1.7)
    page.evaluate("demo.total()")
    rec_x.event("whoosh")
    rec_x.event("ding", 1.2)
    rec_x.close_caption()
    rec_x.wait(1.3)
    rec_x.mark("end")
    rec_x.stop()
    browser.close()

segments = [Segment(rec_title, rec_title.marks["start"], rec_title.marks["end"]),
            Segment(rec, rec.marks["start"], rec.marks["end"], framed=True),
            Segment(rec_x, rec_x.marks["start"], rec_x.marks["end"])]
captions, events, duration = compose(segments, out / f"{NAME}.mp4", out / f"{NAME}_带字幕预览.mp4",
                                     transitions=["zoom", "wipe"], out_webm=out / "preview.webm")
write_srt(captions, 0, out / f"{NAME}.srt")
write_sfx(events, duration, out / f"{NAME}_音效.wav")
write_player(out / "预览（带音效）.html", "preview.webm", f"{NAME}_音效.wav", "讲解风格样片 v2")
rows = [f"# 讲解风格样片 v2 · 文案", "",
        f"剪映里：导入「{NAME}.mp4」和「{NAME}_音效.wav」，都放在第 0 秒 → 文本 → 本地字幕 → 导入「{NAME}.srt」"
        "→ 全选字幕 → 朗读；再加背景音乐，音量调低。", "",
        "| # | 时间 | 画面 | 台词 |", "|---|---|---|---|"]
for i, (a, b, text, scene) in enumerate(captions, 1):
    rows.append(f"| {i} | {srt_time(a)[3:8]}–{srt_time(b)[3:8]} | {scene} | {text} |")
rows += ["", f"音效点 {len(events)} 个（点击、镜头移动、标注弹出、数字出现、转场）。", "",
         "动画里的数字（来自接口，重算一致）：", "",
         *[f"- {d['name']} {d['score']:g} × {d['weight']}" for d in dims],
         f"- 加权合计 {weighted:.1f}；完整度 {comp:g}；投入保障 {k:g}；个人日程系数 {factor}；"
         f"得 {expected:.2f}，接口返回 {match['score']}"]
(out / "文案.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
(out / "配音文稿.txt").write_text("\n".join(c[2] for c in captions) + "\n", encoding="utf-8")
print(f"完成，时长 {duration:.1f} 秒，音效点 {len(events)} 个")
