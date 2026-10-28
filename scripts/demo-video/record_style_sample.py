"""讲解风格样片（约 40 秒）：片头 → 真实操作（镜头推近六边形、叠上对比、看推荐理由）→ 用真实分数驱动的打分动画。

真实、可复刻：
- 网页画面都是脚本在本机测试环境里真点出来的（serve_video.py 起的后端 + frontend-video 前端，演示数据），
  讲解层只加镜头（合成时裁剪缩放）、聚光灯和说明气泡，不改页面内容；
- 打分动画里的每个数从后端接口取：林晓看到的推荐分、五个维度的分、这个比赛用的那套权重；
  脚本先按引擎的算法重算一遍，和接口返回的分数对不上就不录，免得动画讲的和系统不一致。
用 2 倍清晰度录，镜头推近后仍然清楚；输出 1080p。

用法：python scripts/demo-video/record_style_sample.py [前端地址] [输出目录] [抓帧的临时目录]"""
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

from demo_kit import ANNOT_JS, CURSOR_JS, Recorder, Segment, compose, srt_time, write_srt  # noqa: E402

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:5176"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "演示视频" / "风格样片"
tmp = Path(sys.argv[3]) if len(sys.argv) > 3 else out / "frames"
out.mkdir(parents=True, exist_ok=True)
shutil.rmtree(tmp, ignore_errors=True)
VIEW, DPR = {"width": 1536, "height": 864}, 2
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
base_score = 50 + (weighted - 50) * comp
personal = next((w for w in match["warnings"] if "个人日程" in w), None)
factor = 0.9 if personal else 1.0
expected = base_score * k * factor
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
        {"title": f"× 投入保障 {k:.1f}", "text": "备赛期间没有要打折的情况" if k >= 1 else "备赛期间有考试或实习，打折"},
    ] + ([{"title": "× 0.9", "text": f"比赛期间林晓有个人日程「{event}」", "red": True}] if personal else []),
    "score": match["score"],
    "totalCaption": f"页面上显示 {shown} 分，和系统算出来的一致",
}
print(f"真实数据：{[(d['name'], d['score'], d['weight']) for d in dims]}，加权 {weighted:.1f}，"
      f"完整度 {comp}，投入保障 {k}，系数 {factor} → {expected:.2f}（接口 {match['score']}）")

with sync_playwright() as p:
    # 无头浏览器的录屏会忽略 device_scale_factor，只给 CSS 尺寸的帧；强制设备倍率才拿得到 2 倍清晰度的帧
    browser = p.chromium.launch(args=[f"--force-device-scale-factor={DPR}"])

    # ---------- 2. 片头 ----------
    card_ctx = browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
    card_ctx.add_init_script(f"window.__DATA = {json.dumps(data, ensure_ascii=False)};")
    page = card_ctx.new_page()
    page.goto((HERE / "explain.html").as_uri())
    page.wait_for_load_state("networkidle")
    rec_title = Recorder(page, tmp / "title")
    rec_title.start(quality=92)
    rec_title.wait(0.3)
    rec_title.mark("start")
    page.evaluate("demo.title('竞搭 · 功能演示', '匹配推荐：每条推荐都说得出理由', '真实操作 + 讲解动画，分数来自系统实时计算')")
    rec_title.wait(3.0)
    rec_title.mark("end")
    rec_title.stop()

    # ---------- 3. 真实操作 ----------
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

    rec.say("林晓还没有组队，主页右边是系统为她推荐的队伍。", "主页全景，镜头推近右边的推荐")
    rec.wait(2.2)
    hexa = page.locator("figure.hexagon").first
    rec.cam(hexa, zoom=1.7, seconds=1.1, dy=40)
    rec.move_to(hexa, seconds=0.9)
    rec.wait(0.6)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "匹配六边形<small>五个维度，加上投入保障</small>", side="left")

    rec.say("六边形是她和数模冲刺队的匹配画像；点一下，把另一支队伍叠上来对比。", "聚光灯圈出六边形，点「叠上去对比」")
    rec.wait(2.2)
    rec.clear_notes()
    rec.click(page.locator(".compare button", has_text="计设作品组"))
    rec.wait(0.9)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "虚线是计设作品组<small>差在哪一眼就能看出来</small>", side="left")

    rec.say(f"每条推荐都写清了理由，算出来的总分是 {shown} 分。", f"镜头往下移到数模冲刺队的卡片，圈出 {shown} 分")
    rec.wait(1.0)
    rec.clear_notes()
    rec.scroll(260, seconds=0.9)
    first_card = page.locator(".rec").first
    rec.cam(first_card, zoom=1.7, seconds=0.9, dy=10)
    rec.wait(1.0)
    rec.spot(first_card, pad=8)
    rec.note(first_card, f"总分 {shown}，附推荐理由<small>这 {shown} 分是怎么来的？</small>", side="left")
    rec.close_caption()
    rec.wait(0.8)
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
    rec_x.wait(0.9)
    page.evaluate("demo.rows()")
    rec_x.wait(2.8)
    page.evaluate("demo.sum()")
    tail = f"她比赛期间有{event}，再乘 0.9，" if personal else ""
    rec_x.say(f"问卷填全了，投入保障也不打折；{tail}就是页面上的 {shown} 分。",
              f"讲解动画：右边逐个出现系数，最后得出 {match['score']}")
    page.evaluate("demo.chips()")
    rec_x.wait(1.8)
    page.evaluate("demo.total()")
    rec_x.close_caption()
    rec_x.wait(1.2)
    rec_x.mark("end")
    rec_x.stop()
    browser.close()

segments = [Segment(r, r.marks["start"], r.marks["end"]) for r in (rec_title, rec, rec_x)]
first = segments[1].frames[0][1]
print("UI 段抓了", len(segments[1].frames), "帧，", round(segments[1].duration, 1), "秒；帧尺寸",
      __import__("demo_kit").imread(first).shape[1::-1])
captions = compose(segments, out / "风格样片.mp4", out / "风格样片_带字幕预览.mp4")
write_srt(captions, 0, out / "风格样片.srt")
rows = ["# 讲解风格样片 · 文案", "",
        "剪映里：导入「风格样片.mp4」→ 文本 → 本地字幕 → 导入「风格样片.srt」→ 全选字幕 → 朗读。", "",
        "| # | 时间 | 画面 | 台词 |", "|---|---|---|---|"]
for i, (a, b, text, scene) in enumerate(captions, 1):
    rows.append(f"| {i} | {srt_time(a)[3:8]}–{srt_time(b)[3:8]} | {scene} | {text} |")
rows += ["", "动画里的数字（来自接口，重算一致）：", "",
         *[f"- {d['name']} {d['score']:g} × {d['weight']}" for d in dims],
         f"- 加权合计 {weighted:.1f}；完整度 {comp:g}；投入保障 {k:g}；个人日程系数 {factor}；"
         f"得 {expected:.2f}，接口返回 {match['score']}"]
(out / "文案.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
(out / "配音文稿.txt").write_text("\n".join(c[2] for c in captions) + "\n", encoding="utf-8")
print("完成，时长", round(sum(s.duration for s in segments) - 0.4 * (len(segments) - 1), 1), "秒")
