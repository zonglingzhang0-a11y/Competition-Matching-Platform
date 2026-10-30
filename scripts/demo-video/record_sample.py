"""演示视频样片（约 1 分钟）：林晓登录 → 主页 → 匹配六边形和对比 → 推荐理由 → 数模冲刺队 → 申请加入。
录的是本机测试环境（serve_video.py 起的 18093 后端 + frontend-video 的 5176 前端），密码从演示数据里读，不打印。
会真的发出一条申请，重录前要重启 serve_video.py（会重新导入演示数据）。
用法：python scripts/demo-video/record_sample.py [前端地址] [输出目录]"""
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
os.environ.setdefault("E2E_PORT", "18093")
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

from demo_kit import CURSOR_JS, Recorder, encode, srt_time, write_srt  # noqa: E402

HIDE_DEVTOOLS = """
document.addEventListener('DOMContentLoaded', () => {
  const s = document.createElement('style');
  s.textContent = '#__vue-devtools-container__, #vue-inspector-container { display: none !important; }';
  document.head.appendChild(s);
});
"""

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:5176"
out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "演示视频" / "样片"
out.mkdir(parents=True, exist_ok=True)

with sync_playwright() as p:
    browser = p.chromium.launch()
    ctx = browser.new_context(viewport={"width": 1536, "height": 864}, device_scale_factor=1.25, locale="zh-CN")
    ctx.add_init_script(HIDE_DEVTOOLS)
    ctx.add_init_script(CURSOR_JS)
    page = ctx.new_page()
    # 先打开一次，让 Vite 把模块编译好，录的时候不会白屏
    page.goto(base + "/login")
    page.wait_for_load_state("networkidle")
    page.reload()
    page.wait_for_load_state("networkidle")
    page.wait_for_timeout(800)

    rec = Recorder(page, out / "frames")
    rec.resync_cursor()
    rec.start()
    rec.wait(0.8)
    rec.mark("start")

    # 1 登录
    rec.say("找队友总靠熟人圈？来试试竞搭。我们用还没组队的同学林晓来演示。", "登录页，输入林晓的邮箱和密码，点登录")
    rec.wait(0.8)
    rec.type(page.get_by_placeholder("name@example.com"), "linxiao@demo.local")
    rec.type(page.locator("input[type=password]"), env.demo_password(), delay=0.07)
    rec.click(page.get_by_role("button", name="登录"))
    page.wait_for_url(base + "/")
    page.wait_for_load_state("networkidle")

    # 2 主页三栏
    rec.say("登录后是她的主页：左边是赛程，中间是适合报名的比赛，右边是待办和推荐队伍。", "主页，鼠标依次指过左中右三栏")
    rec.wait(0.6)
    rec.move_to(page.get_by_text("2026年10月", exact=True))
    rec.wait(1.4)
    rec.move_to(page.locator(".panel-title", has_text="适合你报名"))
    rec.wait(1.4)
    rec.move_to(page.locator(".panel-title", has_text="待你处理"))
    rec.wait(1.0)
    rec.move_to(page.locator(".panel-title", has_text="为你推荐的队伍"))

    # 3 六边形
    rec.say("这张六边形是她和数模冲刺队的匹配画像，六个方面分别打分。", "鼠标在六边形的几个顶点之间移动")
    for label in ("目标与投入", "能力与角色", "协作风格", "时间与节奏", "责任与底线"):
        rec.move_to(page.locator("figure.hexagon text.label", has_text=label).first, seconds=0.55)
        rec.wait(0.5)

    # 4 叠上去对比
    rec.say("点一下，把另一支队伍叠上来对比，哪支更合适一目了然。", "点「叠上去对比：计设作品组」，第二个形状叠上来")
    rec.wait(0.5)
    rec.click(page.locator(".compare button", has_text="计设作品组"))
    rec.wait(1.2)
    rec.move_to(page.locator("figure.hexagon svg").first, dy=0.45)

    # 5 推荐理由
    rec.say("推荐还写清了理由：每周有5个大家都有空的时段，目标也一致。", "往下滚，鼠标指着数模冲刺队卡片上的理由")
    first_card = page.locator(".rec").first
    rec.scroll_to(first_card, top=360)
    rec.move_to(first_card.get_by_text("每周有", exact=False).first, dx=0.3)
    rec.wait(1.2)
    rec.move_to(first_card.get_by_text("目标一致", exact=False).first, dx=0.3)

    # 6 队伍页
    rec.say("点进数模冲刺队：队长负责建模，正缺一位编程手和一位论文手。", "点队名进入队伍页，鼠标指队伍介绍和招募需求")
    rec.wait(0.4)
    rec.click(first_card.locator(".rec-name"))
    page.wait_for_url("**/teams/*")
    page.wait_for_load_state("networkidle")
    rec.wait(0.6)
    rec.move_to(page.get_by_text("目标省一", exact=False).first, dx=0.25)
    rec.wait(1.2)
    needs = page.get_by_text("招募需求", exact=True)
    rec.scroll_to(needs, top=260)
    rec.move_to(page.get_by_text("会 Python 做数据处理和画图", exact=False).first, dx=0.4)

    # 7 申请
    rec.say("林晓会Python，点申请加入，选上编程，写一句自我介绍。", "滚回顶部，点「申请加入」，选编程，输入留言")
    rec.scroll(-2000, seconds=0.7)
    rec.wait(0.3)
    rec.click(page.get_by_role("button", name="申请加入"))
    dialog = page.get_by_role("dialog")
    dialog.wait_for()
    rec.wait(0.5)
    rec.click(dialog.locator(".el-radio", has_text="编程"))
    rec.type(dialog.locator("textarea"), "我会 Python，做过数据处理和画图，想一起冲省一！", delay=0.08)

    # 8 发送
    rec.say("发送之后，队长马上就会收到通知。", "点「发送」，弹出「申请已发出，等队长处理」")
    rec.wait(0.3)
    rec.click(dialog.get_by_role("button", name="发送"))
    page.get_by_text("申请已发出", exact=False).wait_for()
    rec.wait(1.0)
    rec.stop()
    rec.wait(0.3)
    browser.close()

start = rec.marks["start"]
end = rec.captions[-1][1] + 1.0
frames = sorted(rec.frames)
print(f"抓到 {len(frames)} 帧，时长 {end - start:.1f} 秒")
encode(frames, start, end, out / "样片.mp4")
encode(frames, start, end, out / "样片_带字幕预览.mp4", captions=rec.captions)
write_srt(rec.captions, start, out / "样片.srt")

rows = ["# 演示视频样片 · 文案", "",
        "剪映里：导入「样片.mp4」→ 文本 → 本地字幕 → 导入「样片.srt」→ 全选字幕 → 朗读（文本朗读）选一个音色。",
        "字幕的时间已经和画面对好；配音念得比画面快的地方，留白就行。", "",
        "| # | 时间 | 画面 | 台词 |", "|---|---|---|---|"]
for i, (a, b, text, scene) in enumerate(rec.captions, 1):
    rows.append(f"| {i} | {srt_time(a - start)[3:8]}–{srt_time(b - start)[3:8]} | {scene} | {text} |")
(out / "文案.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
(out / "配音文稿.txt").write_text("\n".join(c[2] for c in rec.captions) + "\n", encoding="utf-8")
print("完成")
