"""各个模块演示动画共用的部分：调接口、登录、开始和结束一段录制、合成出片（H.264 + 音效 + 字幕 + 文案）。
录制环境同 README：serve_video.py 起的 18093 后端（演示数据）+ frontend-video 的 5176 前端。"""
import json
import os
import shutil
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
os.environ.setdefault("E2E_PORT", "18093")
sys.path.insert(0, str(ROOT / "tests" / "e2e"))
import env  # noqa: E402

from demo_kit import (ANNOT_JS, CURSOR_JS, Recorder, Segment, compose, mux_audio, srt_time,  # noqa: E402
                      write_sfx, write_srt)

VIEW, DPR = {"width": 1536, "height": 864}, 2
BASE = os.environ.get("DEMO_FRONTEND", "http://localhost:5176")
HIDE_DEVTOOLS = """document.addEventListener('DOMContentLoaded', () => {
  const s = document.createElement('style');
  s.textContent = '#__vue-devtools-container__, #vue-inspector-container { display: none !important; }';
  document.head.appendChild(s);
});"""


def api(method, path, token=None, body=None):
    req = urllib.request.Request(env.API + path, method=method,
                                 data=json.dumps(body).encode() if body is not None else None,
                                 headers={"Content-Type": "application/json",
                                          **({"Authorization": f"Bearer {token}"} if token else {})})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())["data"]


def login(name):
    return api("POST", "/auth/login", body={"email": f"{name}@demo.local", "password": env.demo_password()})["token"]


class Module:
    """一个模块的录制：tmp 下放抓到的帧，out 下出片"""

    def __init__(self, name, data=None):
        self.name = name
        self.out = ROOT / "演示视频" / f"模块_{name}"
        self.tmp = self.out / "frames"
        self.out.mkdir(parents=True, exist_ok=True)
        shutil.rmtree(self.tmp, ignore_errors=True)
        self.data = data or {}

    def launch(self, p):
        # 无头浏览器的录屏会忽略 device_scale_factor，只给 CSS 尺寸的帧；强制设备倍率才拿得到 2 倍清晰度的帧
        self.browser = p.chromium.launch(args=[f"--force-device-scale-factor={DPR}"])
        self.card_ctx = self.browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
        self.card_ctx.add_init_script(f"window.__DATA = {json.dumps(self.data, ensure_ascii=False)};")

    def card_page(self):
        page = self.card_ctx.new_page()
        page.goto((HERE / "explain.html").as_uri())
        page.wait_for_load_state("networkidle")
        return page

    def site_page(self, who, password=None):
        """who 是演示账号的名字（林晓就是 linxiao），或者一个完整的邮箱（另给 password）"""
        ctx = self.browser.new_context(viewport=VIEW, device_scale_factor=DPR, locale="zh-CN")
        for script in (HIDE_DEVTOOLS, CURSOR_JS, ANNOT_JS):
            ctx.add_init_script(script)
        page = ctx.new_page()
        page.goto(BASE + "/login")
        page.wait_for_load_state("networkidle")
        page.get_by_placeholder("name@example.com").fill(who if "@" in who else f"{who}@demo.local")
        page.locator("input[type=password]").fill(password or env.demo_password())
        page.get_by_role("button", name="登录").click()
        page.wait_for_url(BASE + "/")
        page.wait_for_load_state("networkidle")
        page.wait_for_timeout(1000)
        return page

    def begin(self, page, folder, pos=(900, 300)):
        rec = Recorder(page, self.tmp / folder)
        rec.pos = pos
        rec.resync_cursor()
        rec.start(quality=92)
        rec.wait(0.4)
        rec.mark("start")
        return rec

    @staticmethod
    def end(rec, tail=0.6):
        rec.close_caption()
        rec.wait(tail)
        rec.mark("end")
        rec.stop()

    def chapter(self, owner, title, sub="真实操作 · 数字来自系统实时计算", hold=2.4, eyebrow=None):
        """章节卡：title 用"大字：副标题"的格式，大字逐字弹出；eyebrow 不给就写"竞搭 · 负责：owner\""""
        page = self.card_page()
        rec = self.begin(page, "chapter")
        big = title.split("：")[0]
        page.evaluate("([e, h, s]) => demo.title(e, h, s)", [eyebrow or f"竞搭 · 负责：{owner}", title, sub])
        for i in range(len(big)):
            rec.event("tick", 0.22 + i * 0.09)
        rec.event("pop", 0.32 + len(big) * 0.09)
        rec.wait(hold)
        self.end(rec, 0)
        return rec

    def render(self, parts, transitions, facts=()):
        """parts: [(Recorder, 是否放进画框)] 或者现成的段（比如分屏 SplitSegment）；facts: 文案末尾"真实数据"一节的条目"""
        segments = [Segment(x[0], x[0].marks["start"], x[0].marks["end"], framed=x[1]) if isinstance(x, tuple) else x
                    for x in parts]
        clean, captioned = self.out / f"{self.name}.mp4", self.out / f"{self.name}_带字幕.mp4"
        captions, events, duration = compose(segments, clean, captioned, transitions=transitions)
        wav = self.out / f"{self.name}_音效.wav"
        write_sfx(events, duration, wav)
        mux_audio(clean, wav)
        mux_audio(captioned, wav)
        write_srt(captions, 0, self.out / f"{self.name}.srt")
        rows = [f"# 模块演示：{self.name} · 文案", "",
                f"两个视频都已经带音效：「{self.name}.mp4」不带字幕（自己配音用），"
                f"「{self.name}_带字幕.mp4」字幕画在画面上（直接放进 PPT）。", "",
                "| # | 时间 | 画面 | 字幕 |", "|---|---|---|---|"]
        for i, (a, b, text, scene) in enumerate(captions, 1):
            rows.append(f"| {i} | {srt_time(a)[3:8]}–{srt_time(b)[3:8]} | {scene} | {text} |")
        if facts:
            rows += ["", "真实数据（录制时核对过）：", "", *[f"- {f}" for f in facts]]
        (self.out / "文案.md").write_text("\n".join(rows) + "\n", encoding="utf-8")
        shutil.rmtree(self.tmp, ignore_errors=True)
        print(f"完成：{self.name}，时长 {duration:.1f} 秒，音效点 {len(events)} 个")
