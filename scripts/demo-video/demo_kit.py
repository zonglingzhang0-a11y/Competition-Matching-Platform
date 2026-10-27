"""录演示视频用的小工具：在页面上画鼠标指针和点击圆圈、平滑移动和打字、记字幕时间轴、抓帧合成 MP4。

录法：用 CDP 的 Page.startScreencast 抓高质量 JPEG 帧（Playwright 自带的录屏是 1 Mbps 的 VP8，字会糊），
按帧的时间戳用 OpenCV 合成 30fps 的 MP4（mp4v 编码，剪映能直接导入）。
字幕不录进画面，单独出 SRT：剪映里导入字幕后用"文本朗读"生成 AI 配音；另出一份把字幕画上去的预览版。
"""
import math
import time
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FPS = 30
OUT_SIZE = (1920, 1080)
# 剪映 AI 配音正常语速大约每秒 4～5 个字，按 4 个字算，留够念完的时间
CHARS_PER_SEC = 4.0

CURSOR_JS = r"""
(() => {
  if (window.__demoCursorInstalled) return;
  window.__demoCursorInstalled = true;
  const install = () => {
    const c = document.createElement('div');
    c.id = '__demo_cursor';
    c.innerHTML = '<svg width="30" height="30" viewBox="0 0 28 28"><path d="M4 2 L4 22 L9.5 17 L13 25 L16.5 23.5 L13 15.5 L20.5 15.5 Z" fill="#fff" stroke="#111" stroke-width="1.6" stroke-linejoin="round"/></svg>';
    Object.assign(c.style, {position: 'fixed', left: '0', top: '0', zIndex: '2147483647', pointerEvents: 'none',
      transform: 'translate(-100px,-100px)', filter: 'drop-shadow(0 1px 2px rgba(0,0,0,.35))'});
    document.documentElement.appendChild(c);
    const place = (x, y) => { c.style.transform = `translate(${x - 4}px, ${y - 2}px)`; };
    document.addEventListener('mousemove', e => place(e.clientX, e.clientY), true);
    document.addEventListener('mousedown', e => {
      const r = document.createElement('div');
      Object.assign(r.style, {position: 'fixed', left: (e.clientX - 20) + 'px', top: (e.clientY - 20) + 'px',
        width: '40px', height: '40px', borderRadius: '50%', border: '3px solid rgba(59,119,255,.95)',
        background: 'rgba(59,119,255,.18)', zIndex: '2147483646', pointerEvents: 'none',
        transition: 'transform .5s ease-out, opacity .5s ease-out', transform: 'scale(.4)', opacity: '1'});
      document.documentElement.appendChild(r);
      requestAnimationFrame(() => requestAnimationFrame(() => { r.style.transform = 'scale(1.7)'; r.style.opacity = '0'; }));
      setTimeout(() => r.remove(), 700);
    }, true);
  };
  if (document.documentElement) install(); else document.addEventListener('DOMContentLoaded', install);
})();
"""


def caption_seconds(text):
    return max(2.2, len(text) / CHARS_PER_SEC + 0.6)


class Recorder:
    def __init__(self, page, frames_dir):
        self.page = page
        self.frames_dir = Path(frames_dir)
        self.frames_dir.mkdir(parents=True, exist_ok=True)
        self.frames = []          # (时间戳, 文件)
        self.captions = []        # [开始, 结束, 台词, 画面说明]
        self.marks = {}
        self.pos = (page.viewport_size["width"] / 2, page.viewport_size["height"] * 0.6)
        self.cdp = None

    # ---------- 抓帧 ----------
    def start(self):
        self.cdp = self.page.context.new_cdp_session(self.page)
        self.cdp.on("Page.screencastFrame", self._on_frame)
        self.cdp.send("Page.startScreencast", {"format": "jpeg", "quality": 95, "everyNthFrame": 1})

    def _on_frame(self, params):
        path = self.frames_dir / f"{len(self.frames):06d}.jpg"
        import base64
        path.write_bytes(base64.b64decode(params["data"]))
        self.frames.append((params["metadata"]["timestamp"], path))
        try:
            self.cdp.send("Page.screencastFrameAck", {"sessionId": params["sessionId"]})
        except Exception:
            pass

    def stop(self):
        self.close_caption()
        self.cdp.send("Page.stopScreencast")

    def mark(self, name):
        self.marks[name] = time.time()

    # ---------- 字幕 ----------
    def say(self, text, scene=""):
        """开始一句新台词；上一句没念完就先等它念完"""
        self.close_caption()
        self.captions.append([time.time(), None, text, scene])

    def close_caption(self):
        if self.captions and self.captions[-1][1] is None:
            start, _, text, _ = self.captions[-1]
            left = start + caption_seconds(text) - time.time()
            if left > 0:
                self.wait(left)
            self.captions[-1][1] = time.time()

    # ---------- 操作 ----------
    def wait(self, seconds):
        self.page.wait_for_timeout(int(seconds * 1000))

    def move_to(self, target, dx=0.5, dy=0.5, seconds=None):
        """平滑移动鼠标到元素（或坐标）上，ease-in-out"""
        if isinstance(target, tuple):
            x, y = target
        else:
            target.scroll_into_view_if_needed()
            box = target.bounding_box()
            x, y = box["x"] + box["width"] * dx, box["y"] + box["height"] * dy
        x0, y0 = self.pos
        dist = math.hypot(x - x0, y - y0)
        seconds = seconds or min(1.0, 0.35 + dist / 1800)
        n = max(2, int(seconds * 60))
        for i in range(1, n + 1):
            k = i / n
            k = k * k * (3 - 2 * k)
            self.page.mouse.move(x0 + (x - x0) * k, y0 + (y - y0) * k)
            self.page.wait_for_timeout(16)
        self.pos = (x, y)

    def click(self, target, **kw):
        self.move_to(target, **kw)
        self.wait(0.18)
        self.page.mouse.down()
        self.page.wait_for_timeout(70)
        self.page.mouse.up()
        self.wait(0.25)

    def type(self, target, text, delay=0.09):
        self.click(target)
        self.page.keyboard.type(text, delay=int(delay * 1000))

    def scroll(self, dy, seconds=0.8):
        n = max(1, int(seconds * 30))
        for _ in range(n):
            self.page.mouse.wheel(0, dy / n)
            self.page.wait_for_timeout(33)

    def scroll_to(self, target, top=160, seconds=0.9):
        """平滑滚动，让元素的上沿停在离窗口顶部 top 像素的地方（scroll_into_view 是瞬间跳过去的，录出来很突兀）"""
        box = target.bounding_box()
        if box and abs(box["y"] - top) > 4:
            self.scroll(box["y"] - top, seconds)
            self.wait(0.2)

    def resync_cursor(self):
        """整页跳转后页面上的指针会回到屏幕外，把它放回原位"""
        self.page.mouse.move(*self.pos)


# ---------- 合成 ----------
def _font(size):
    for f in ("C:/Windows/Fonts/msyhbd.ttc", "C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf"):
        if Path(f).exists():
            return ImageFont.truetype(f, size)
    return ImageFont.load_default()


def _draw_caption(img_bgr, text):
    img = Image.fromarray(cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)).convert("RGBA")
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    font = _font(40)
    w = d.textlength(text, font=font)
    x, y = (img.width - w) / 2, img.height - 110
    d.rounded_rectangle((x - 28, y - 16, x + w + 28, y + 60), radius=14, fill=(0, 0, 0, 165))
    d.text((x, y), text, font=font, fill=(255, 255, 255, 255))
    return cv2.cvtColor(np.array(Image.alpha_composite(img, layer).convert("RGB")), cv2.COLOR_RGB2BGR)


def encode(frames, start, end, out_path, captions=None):
    """frames: [(时间戳, jpg)]，按时间铺成恒定 30fps；captions 给了就把字幕画上去（预览版）"""
    writer = cv2.VideoWriter(str(out_path), cv2.VideoWriter_fourcc(*"mp4v"), FPS, OUT_SIZE)
    idx, cur_path, cur_img, cache_key, cache_img = 0, None, None, None, None
    total = int((end - start) * FPS)
    for k in range(total):
        t = start + k / FPS
        while idx < len(frames) and frames[idx][0] <= t:
            cur_path = frames[idx][1]
            idx += 1
            cur_img = None
        if cur_path is None:
            cur_path = frames[0][1]
        if cur_img is None:
            cur_img = cv2.imread(str(cur_path))
            if (cur_img.shape[1], cur_img.shape[0]) != OUT_SIZE:
                cur_img = cv2.resize(cur_img, OUT_SIZE, interpolation=cv2.INTER_AREA)
        img = cur_img
        if captions:
            text = next((c[2] for c in captions if c[0] <= t < c[1]), None)
            if text:
                key = (cur_path, text)
                if key != cache_key:
                    cache_key, cache_img = key, _draw_caption(cur_img, text)
                img = cache_img
        writer.write(img)
    writer.release()


def srt_time(sec):
    ms = int(round(sec * 1000))
    return f"{ms // 3600000:02d}:{ms // 60000 % 60:02d}:{ms // 1000 % 60:02d},{ms % 1000:03d}"


def write_srt(captions, start, path):
    lines = []
    for i, (a, b, text, _) in enumerate(captions, 1):
        lines += [str(i), f"{srt_time(a - start)} --> {srt_time(b - start)}", text, ""]
    Path(path).write_text("\n".join(lines), encoding="utf-8-sig")
