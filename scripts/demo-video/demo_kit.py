"""录演示视频用的工具：在真实页面上操作并抓帧，合成时加讲解层（镜头、画框、转场、音效）。

录法：用 CDP 的 Page.startScreencast 抓高质量 JPEG 帧（Playwright 自带的录屏是 1 Mbps 的 VP8，字会糊），
按帧的时间戳用 OpenCV 合成 30fps 的 MP4（mp4v 编码，剪映能直接导入）。
字幕不录进画面，单独出 SRT：剪映里导入字幕后用"文本朗读"生成 AI 配音；另出一份把字幕画上去的预览版。

讲解层只改"怎么拍"，不改"拍什么"：
- 页面上叠鼠标指针、聚光灯和说明气泡（不改页面内容）；
- 合成时加虚拟镜头（推近、平移、缓动、慢速漂移）、窗口画框和流动背景、段落之间的转场；
- 按画面里的点击、镜头移动、标注出现等事件生成一条对齐好的音效 WAV。
"""
import glob
import math
import os
import random
import shutil
import subprocess
import tempfile
import time
import wave
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

FPS = 60
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
      transform: 'translate(-100px,-100px)', filter: 'drop-shadow(0 2px 3px rgba(0,0,0,.35))'});
    const svg = c.firstChild;
    svg.style.transition = 'transform .12s ease';
    svg.style.transformOrigin = '4px 2px';
    document.documentElement.appendChild(c);
    const place = (x, y) => { c.style.transform = `translate(${x - 4}px, ${y - 2}px)`; };
    document.addEventListener('mousemove', e => place(e.clientX, e.clientY), true);
    document.addEventListener('mousedown', e => {
      svg.style.transform = 'scale(.82)';
      const r = document.createElement('div');
      Object.assign(r.style, {position: 'fixed', left: (e.clientX - 28) + 'px', top: (e.clientY - 28) + 'px',
        width: '56px', height: '56px', borderRadius: '50%', border: '3px solid rgba(215,154,91,.95)',
        background: 'rgba(215,154,91,.22)', zIndex: '2147483646', pointerEvents: 'none',
        transition: 'transform .55s cubic-bezier(.2,.8,.2,1), opacity .55s ease', transform: 'scale(.3)', opacity: '1'});
      document.documentElement.appendChild(r);
      requestAnimationFrame(() => requestAnimationFrame(() => { r.style.transform = 'scale(1.6)'; r.style.opacity = '0'; }));
      setTimeout(() => r.remove(), 700);
    }, true);
    document.addEventListener('mouseup', () => { svg.style.transform = 'scale(1)'; }, true);
  };
  if (document.documentElement) install(); else document.addEventListener('DOMContentLoaded', install);
})();
"""

# 只是叠在真实页面上的一层标注（深色气泡、古铜色描边），不改页面本身的任何内容
ANNOT_JS = r"""
(() => {
  if (window.__demoAnnotInstalled) return;
  window.__demoAnnotInstalled = true;
  const move = 'cubic-bezier(.65,0,.35,1)';
  const css = `
  .__spot{position:fixed;border:3px solid #D79A5B;border-radius:16px;box-shadow:0 0 0 9999px rgba(24,22,19,.5);
    pointer-events:none;z-index:2147483640;opacity:0;
    transition:opacity .4s ease,left .6s ${move},top .6s ${move},width .6s ${move},height .6s ${move}}
  .__spot.on{opacity:1}
  .__spot.jump{transition:none}
  .__spot::after{content:'';position:absolute;inset:-3px;border-radius:16px;border:3px solid #E7B47D;opacity:0}
  .__spot.pulse::after{animation:__demoPulse .9s ease-out 1}
  @keyframes __demoPulse{0%{opacity:.95;transform:scale(1)}100%{opacity:0;transform:scale(1.07)}}
  .__call{position:fixed;max-width:360px;background:#1B1A17;color:#F7F2EA;
    font:600 20px/1.45 'MiSans','Microsoft YaHei',sans-serif;padding:10px 16px;border-radius:10px;
    pointer-events:none;z-index:2147483641;opacity:0;transform:translateY(10px) scale(.9);
    transition:opacity .3s ease,transform .45s cubic-bezier(.34,1.56,.64,1)}
  .__call.on{opacity:1;transform:none}
  .__call small{display:block;font-weight:400;font-size:16px;color:#C9C2B6;margin-top:2px}
  .__call::before{content:'';position:absolute;width:12px;height:12px;background:#1B1A17;transform:rotate(45deg)}
  .__call[data-side=right]::before{left:-6px;top:calc(50% - 6px)}
  .__call[data-side=left]::before{right:-6px;top:calc(50% - 6px)}
  .__call[data-side=top]::before{bottom:-6px;left:calc(50% - 6px)}
  .__call[data-side=bottom]::before{top:-6px;left:calc(50% - 6px)}`;
  const install = () => {
    const s = document.createElement('style'); s.textContent = css; document.head.appendChild(s);
    const spot = document.createElement('div'); spot.className = '__spot'; document.documentElement.appendChild(spot);
    const call = document.createElement('div'); call.className = '__call'; document.documentElement.appendChild(call);
    const place = (r, pad) => Object.assign(spot.style, {left: (r.x - pad) + 'px', top: (r.y - pad) + 'px',
      width: (r.width + 2 * pad) + 'px', height: (r.height + 2 * pad) + 'px'});
    window.__demo = {
      spot(r, pad) {
        if (!spot.classList.contains('on')) {
          // 第一次出现：先放大一圈，再收紧到目标上
          spot.classList.add('jump'); place(r, pad + 36); void spot.offsetWidth; spot.classList.remove('jump');
          spot.classList.add('on');
        }
        place(r, pad);
        spot.classList.remove('pulse');
        setTimeout(() => { void spot.offsetWidth; spot.classList.add('pulse'); }, 600);
      },
      call(r, html, side) {
        call.classList.remove('on');
        call.innerHTML = html;
        call.dataset.side = side;
        const cw = call.offsetWidth, ch = call.offsetHeight;
        let x, y;
        if (side === 'left') { x = r.x - cw - 24; y = r.y + r.height / 2 - ch / 2; }
        else if (side === 'top') { x = r.x + r.width / 2 - cw / 2; y = r.y - ch - 24; }
        else if (side === 'bottom') { x = r.x + r.width / 2 - cw / 2; y = r.y + r.height + 24; }
        else { x = r.x + r.width + 24; y = r.y + r.height / 2 - ch / 2; }
        call.style.left = Math.max(8, Math.min(innerWidth - cw - 8, x)) + 'px';
        call.style.top = Math.max(8, Math.min(innerHeight - ch - 8, y)) + 'px';
        requestAnimationFrame(() => requestAnimationFrame(() => call.classList.add('on')));
      },
      clear() { spot.classList.remove('on', 'pulse'); call.classList.remove('on'); }
    };
  };
  if (document.head) install(); else document.addEventListener('DOMContentLoaded', install);
})();
"""


def caption_seconds(text):
    return max(2.2, len(text) / CHARS_PER_SEC + 0.6)


def ease_in_out_cubic(k):
    return 4 * k * k * k if k < 0.5 else 1 - (-2 * k + 2) ** 3 / 2


class Recorder:
    def __init__(self, page, frames_dir):
        self.page = page
        self.frames_dir = Path(frames_dir)
        self.frames_dir.mkdir(parents=True, exist_ok=True)
        self.frames = []          # (时间戳, 文件)
        self.captions = []        # [开始, 结束, 台词, 画面说明]
        self.marks = {}
        self.events = []          # (时间, 音效名)，合成音效轨用
        self.cams = []            # (开始时间, 用时, 取景框)；取景框是页面 CSS 像素 (x, y, w, h)，None 表示全景
        self.pos = (page.viewport_size["width"] / 2, page.viewport_size["height"] * 0.6)
        self.cdp = None
        self._flip = False

    # ---------- 抓帧 ----------
    def start(self, quality=95):
        self.cdp = self.page.context.new_cdp_session(self.page)
        self.cdp.on("Page.screencastFrame", self._on_frame)
        self.cdp.send("Page.startScreencast", {"format": "jpeg", "quality": quality, "everyNthFrame": 1})

    def _on_frame(self, params):
        import base64
        path = self.frames_dir / f"{len(self.frames):06d}.jpg"
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

    def event(self, kind, delay=0.0):
        """记一个音效点（delay 秒之后）"""
        self.events.append((time.time() + delay, kind))

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

    def move_to(self, target, dx=0.5, dy=0.5, seconds=None, curve=0.16):
        """像人一样移动鼠标：走一段弧线，先加速后减速"""
        if isinstance(target, tuple):
            x, y = target
        else:
            target.scroll_into_view_if_needed()
            box = target.bounding_box()
            x, y = box["x"] + box["width"] * dx, box["y"] + box["height"] * dy
        x0, y0 = self.pos
        dist = math.hypot(x - x0, y - y0)
        seconds = seconds or min(0.85, 0.3 + dist / 2200)
        self._flip = not self._flip
        off = curve * dist * (1 if self._flip else -1)
        nx, ny = -(y - y0), (x - x0)
        norm = math.hypot(nx, ny) or 1
        cx, cy = (x0 + x) / 2 + nx / norm * off, (y0 + y) / 2 + ny / norm * off
        n = max(2, int(seconds * 60))
        for i in range(1, n + 1):
            e = ease_in_out_cubic(i / n)
            self.page.mouse.move((1 - e) ** 2 * x0 + 2 * (1 - e) * e * cx + e * e * x,
                                 (1 - e) ** 2 * y0 + 2 * (1 - e) * e * cy + e * e * y)
            self.page.wait_for_timeout(16)
        self.pos = (x, y)

    def click(self, target, **kw):
        self.move_to(target, **kw)
        self.wait(0.15)
        self.event("click")
        self.page.mouse.down()
        self.page.wait_for_timeout(90)
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

    # ---------- 讲解层：镜头和标注（只加镜头和标注，不改页面内容） ----------
    def cam(self, target=None, zoom=1.0, seconds=0.85, dx=0.0, dy=0.0, sound=True):
        """镜头推到元素上（target 为空就拉回全景）。合成时按缓动从上一个取景框过渡过来，到了以后慢慢往里推"""
        vw, vh = self.page.viewport_size["width"], self.page.viewport_size["height"]
        rect = None
        if target is not None and zoom > 1.0:
            box = target.bounding_box()
            w, h = vw / zoom, vh / zoom
            cx, cy = box["x"] + box["width"] / 2 + dx, box["y"] + box["height"] / 2 + dy
            rect = (min(max(cx - w / 2, 0), vw - w), min(max(cy - h / 2, 0), vh - h), w, h)
        if sound:
            self.event("whoosh")
        self.cams.append((time.time(), seconds, rect))

    def spot(self, target, pad=8):
        """聚光灯：别的地方压暗，圈出这个元素"""
        self.page.evaluate("([r, p]) => window.__demo.spot(r, p)", [target.bounding_box(), pad])

    def note(self, target, html, side="right"):
        """元素旁边的说明气泡"""
        self.event("pop", 0.05)
        self.page.evaluate("([r, h, s]) => window.__demo.call(r, h, s)", [target.bounding_box(), html, side])

    def clear_notes(self):
        self.page.evaluate("window.__demo.clear()")


# ---------- 图片读写（OpenCV 在 Windows 上读写不了带中文的路径） ----------
def imread(path):
    """先读成字节再解码"""
    return cv2.imdecode(np.fromfile(str(path), dtype=np.uint8), cv2.IMREAD_COLOR)


class VideoOut:
    """先写到系统临时目录，关闭时再挪过去"""

    def __init__(self, path):
        self.path = Path(path)
        fd, self.tmp = tempfile.mkstemp(suffix=".mp4")
        os.close(fd)
        self.writer = cv2.VideoWriter(self.tmp, cv2.VideoWriter_fourcc(*"mp4v"), FPS, OUT_SIZE)

    def write(self, frame):
        self.writer.write(frame)

    def release(self):
        self.writer.release()
        shutil.move(self.tmp, self.path)


def ffmpeg_exe():
    """pip 装的 imageio-ffmpeg 自带的 ffmpeg（有 H.264 和 AAC）；没装就返回 None"""
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except ImportError:
        return None


class H264Out:
    """H.264 的 MP4（PowerPoint、WPS、浏览器都能直接放）；没有 ffmpeg 时退回 mp4v"""

    def __init__(self, path):
        self.path = Path(path)
        exe = ffmpeg_exe()
        self.fallback = None if exe else VideoOut(path)
        if self.fallback:
            return
        fd, self.tmp = tempfile.mkstemp(suffix=".mp4")
        os.close(fd)
        self.proc = subprocess.Popen([exe, "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24",
                                      "-s", f"{W_OUT}x{H_OUT}", "-r", str(FPS), "-i", "pipe:0",
                                      # 限制线程和预读帧数：两路编码同时跑时内存峰值小很多（电脑上别的程序占内存多时不至于申请失败）
                                      "-c:v", "libx264", "-preset", "medium", "-crf", "17", "-threads", "4",
                                      "-x264-params", "rc-lookahead=20", "-pix_fmt", "yuv420p",
                                      "-movflags", "+faststart", "-y", self.tmp], stdin=subprocess.PIPE)

    def write(self, frame):
        if self.fallback:
            self.fallback.write(frame)
        else:
            self.proc.stdin.write(np.ascontiguousarray(frame).tobytes())

    def release(self):
        if self.fallback:
            self.fallback.release()
            return
        self.proc.stdin.close()
        self.proc.wait()
        shutil.move(self.tmp, self.path)


def mux_audio(video, wav):
    """把音效轨合进 MP4（AAC），原地替换"""
    exe = ffmpeg_exe()
    if not exe:
        return False
    fd, tmp = tempfile.mkstemp(suffix=".mp4")
    os.close(fd)
    subprocess.run([exe, "-loglevel", "error", "-i", str(video), "-i", str(wav), "-c:v", "copy", "-c:a", "aac",
                    "-b:a", "192k", "-shortest", "-movflags", "+faststart", "-y", tmp], check=True)
    shutil.move(tmp, video)
    return True


class WebmOut:
    """浏览器能直接播的 VP9 预览（借 Playwright 自带的 ffmpeg，它只有 VP9 编码器、没有音频编码器）"""

    def __init__(self, path):
        exe = sorted(glob.glob(os.path.join(os.environ.get("LOCALAPPDATA", ""), "ms-playwright", "ffmpeg-*",
                                            "ffmpeg-win64.exe")))
        self.path = Path(path)
        self.proc = None
        if not exe:
            print("没找到 Playwright 的 ffmpeg，跳过网页预览")
            return
        fd, self.tmp = tempfile.mkstemp(suffix=".webm")
        os.close(fd)
        self.proc = subprocess.Popen([exe[-1], "-loglevel", "error", "-f", "image2pipe", "-c:v", "mjpeg",
                                      "-framerate", str(FPS), "-i", "pipe:0", "-c:v", "libvpx-vp9", "-b:v", "6M",
                                      "-deadline", "realtime", "-cpu-used", "6", "-row-mt", "1", "-y", self.tmp],
                                     stdin=subprocess.PIPE)

    def write(self, frame):
        if self.proc:
            self.proc.stdin.write(cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, 92])[1].tobytes())

    def release(self):
        if self.proc:
            self.proc.stdin.close()
            self.proc.wait()
            shutil.move(self.tmp, self.path)


# ---------- 字幕 ----------
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
    """（第一版样片用）frames: [(时间戳, jpg)]，按时间铺成恒定 30fps；captions 给了就把字幕画上去"""
    writer = VideoOut(out_path)
    idx, cur_path, cur_img, cache_key, cache_img = 0, None, None, None, None
    for k in range(int((end - start) * FPS)):
        t = start + k / FPS
        while idx < len(frames) and frames[idx][0] <= t:
            cur_path = frames[idx][1]
            idx += 1
            cur_img = None
        if cur_path is None:
            cur_path = frames[0][1]
        if cur_img is None:
            cur_img = imread(cur_path)
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


# ---------- 画框：网页放进圆角窗口，下面是缓慢流动的暖色背景 ----------
W_OUT, H_OUT = OUT_SIZE
WIN = (176, 118, 1568, 882)       # 网页内容在画布上的位置（16:9）
BAR_H = 34                        # 窗口标题栏
_layers = {}


def _layer(name):
    if name in _layers:
        return _layers[name]
    x, y, w, h = WIN
    if name == "bg":
        bw, bh = int(W_OUT * 1.3), int(H_OUT * 1.3)
        yy, xx = np.mgrid[0:bh, 0:bw].astype(np.float32)
        img = np.zeros((bh, bw, 3), np.float32)
        img[:] = (211, 223, 233)                                  # 暖灰白（BGR）
        for cx, cy, r, col, a in ((0.18, 0.25, 0.55, (138, 178, 214), 0.55), (0.85, 0.2, 0.45, (122, 160, 201), 0.45),
                                  (0.75, 0.85, 0.6, (94, 138, 185), 0.40), (0.25, 0.9, 0.4, (196, 214, 230), 0.6)):
            g = np.exp(-(((xx - cx * bw) ** 2 + (yy - cy * bh) ** 2) / (2 * (r * bw / 2.2) ** 2)))[..., None] * a
            img = img * (1 - g) + np.array(col, np.float32) * g
        _layers[name] = img.astype(np.uint8)
    elif name == "mask":
        m = Image.new("L", (W_OUT * 2, H_OUT * 2), 0)
        ImageDraw.Draw(m).rounded_rectangle((x * 2, (y - BAR_H) * 2, (x + w) * 2, (y + h) * 2), radius=28, fill=255)
        _layers[name] = cv2.resize(np.array(m, np.float32) / 255, OUT_SIZE, interpolation=cv2.INTER_AREA)
    elif name == "shadow":
        m = np.zeros((H_OUT, W_OUT), np.float32)
        cv2.rectangle(m, (x + 10, y - BAR_H + 26), (x + w - 10, y + h + 26), 1.0, -1)
        _layers[name] = cv2.GaussianBlur(m, (0, 0), 34) * 0.42
    elif name == "bar":
        s = 2
        im = Image.new("RGB", (w * s, BAR_H * s), (236, 232, 225))
        d = ImageDraw.Draw(im)
        for i, col in enumerate(((236, 106, 94), (245, 191, 79), (98, 197, 84))):
            d.ellipse((s * (18 + i * 20), s * 11, s * (30 + i * 20), s * 23), fill=col)
        d.rounded_rectangle((s * (w / 2 - 170), s * 6, s * (w / 2 + 170), s * 28), radius=s * 8, fill=(246, 244, 240))
        f = _font(15 * s)
        label = "竞搭 · 竞赛组队匹配平台"
        d.text((s * w / 2 - d.textlength(label, font=f) / 2, s * 8), label, font=f, fill=(107, 101, 92))
        d.line((0, BAR_H * s - 1, w * s, BAR_H * s - 1), fill=(211, 204, 193), width=2)
        _layers[name] = cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)
    return _layers[name]


def _paste(dst, src, rect):
    """把 src 整张缩放后放到 dst 的 rect（输出坐标，可以超出画面），只算看得见的那部分"""
    rx, ry, rw, rh = rect
    x0, y0 = max(0, int(math.floor(rx))), max(0, int(math.floor(ry)))
    x1, y1 = min(W_OUT, int(math.ceil(rx + rw))), min(H_OUT, int(math.ceil(ry + rh)))
    if x1 <= x0 or y1 <= y0:
        return
    sh, sw = src.shape[:2]
    sx0, sy0 = (x0 - rx) / rw * sw, (y0 - ry) / rh * sh
    sx1, sy1 = (x1 - rx) / rw * sw, (y1 - ry) / rh * sh
    sub = src[max(0, int(sy0)):min(sh, int(math.ceil(sy1))), max(0, int(sx0)):min(sw, int(math.ceil(sx1)))]
    interp = cv2.INTER_AREA if sub.shape[1] > (x1 - x0) else cv2.INTER_CUBIC
    dst[y0:y1, x0:x1] = cv2.resize(sub, (x1 - x0, y1 - y0), interpolation=interp)


def _render_framed(page, cam, t):
    """画布（1920×1080，窗口在 WIN）经过镜头 cam=(x, y, w, h) 拍出来的一帧"""
    x, y, w, h = cam
    z = W_OUT / w
    px, py, pw, ph = WIN
    to_out = lambda r: ((r[0] - x) * z, (r[1] - y) * z, r[2] * z, r[3] * z)
    if x >= px and y >= py and x + w <= px + pw and y + h <= py + ph:
        out = np.empty((H_OUT, W_OUT, 3), np.uint8)
        _paste(out, page, to_out(WIN))
        return out
    bg = _layer("bg")
    bh, bw = bg.shape[:2]
    zb = 1 + (z - 1) * 0.35
    vw, vh = W_OUT / 1.15 / zb, H_OUT / 1.15 / zb
    cx = bw / 2 + (x + w / 2 - W_OUT / 2) * 0.35 + 40 * math.sin(t / 6.0)
    cy = bh / 2 + (y + h / 2 - H_OUT / 2) * 0.35 + 26 * math.cos(t / 7.5)
    bx, by = min(max(cx - vw / 2, 0), bw - vw), min(max(cy - vh / 2, 0), bh - vh)
    out = cv2.resize(bg[int(by):int(by + vh), int(bx):int(bx + vw)], OUT_SIZE, interpolation=cv2.INTER_LINEAR)
    m = np.float32([[z, 0, -x * z], [0, z, -y * z]])
    shadow = cv2.warpAffine(_layer("shadow"), m, OUT_SIZE)[..., None]
    out = out.astype(np.float32) * (1 - shadow)
    win = np.zeros((H_OUT, W_OUT, 3), np.uint8)
    _paste(win, _layer("bar"), to_out((px, py - BAR_H, pw, BAR_H)))
    _paste(win, page, to_out(WIN))
    mask = cv2.warpAffine(_layer("mask"), m, OUT_SIZE)[..., None]
    return (out * (1 - mask) + win.astype(np.float32) * mask).astype(np.uint8)


# ---------- 合成：虚拟镜头 + 段落之间转场 ----------
class Segment:
    """一段画面：一个 Recorder 抓到的帧、截取的时间范围、镜头、台词和音效点。
    framed=True 时网页放进窗口画框，镜头在整块画布上推拉"""

    def __init__(self, rec, start, end, framed=False):
        self.frames = sorted(rec.frames)
        self.start, self.end = start, end
        self.captions = list(rec.captions)
        self.events = list(rec.events)
        self.framed = framed
        self.vw, self.vh = rec.page.viewport_size["width"], rec.page.viewport_size["height"]
        if framed:
            s = WIN[2] / self.vw
            self.full = (0.0, 0.0, float(W_OUT), float(H_OUT))
            conv = lambda r: None if r is None else (WIN[0] + r[0] * s, WIN[1] + r[1] * s, r[2] * s, r[3] * s)
        else:
            self.full = (0.0, 0.0, float(self.vw), float(self.vh))
            conv = lambda r: r
        self.cams = [(t0, dur, conv(r)) for t0, dur, r in sorted(rec.cams, key=lambda c: c[0])]

    @property
    def duration(self):
        return self.end - self.start

    def rect_at(self, t):
        """t 时刻的取景框：移动时按缓动过渡；停下来以后每秒往里推 0.8%，最多 3.5%，画面不会定格"""
        prev, arrived = self.full, self.start
        for t0, dur, r in self.cams:
            if t < t0:
                break
            target = r or self.full
            if t < t0 + dur:
                k = ease_in_out_cubic((t - t0) / dur)
                start = _drift(prev, t0 - arrived)
                return tuple(p + (q - p) * k for p, q in zip(start, target))
            prev, arrived = target, t0 + dur
        return _drift(prev, t - arrived)


def _drift(rect, dt):
    x, y, w, h = rect
    f = 1 - min(0.035, 0.008 * max(dt, 0))
    return (x + w * (1 - f) / 2, y + h * (1 - f) / 2, w * f, h * f)


class SplitSegment:
    """左右两个窗口同时播两个页面（同一段时间一起录的），每边只取页面上的一块区域（CSS 像素，16:9）"""
    SIZE = (880, 495)
    POS = ((64, 300), (976, 300))

    def __init__(self, left, right, start, end, labels):
        """两边各用自己 Recorder 的镜头（rec.cam 推近、拉回整页），取景框按缓动过渡"""
        self.sides = (sorted(left.frames), sorted(right.frames))
        self.cams = (Segment(left, start, end), Segment(right, start, end))
        self.start, self.end = start, end
        self.captions = list(left.captions)
        self.events = sorted(list(left.events) + list(right.events))
        self.vw = left.page.viewport_size["width"]
        self.labels = labels

    @property
    def duration(self):
        return self.end - self.start

    def _base(self):
        """静态部分：背景、两个窗口的阴影和标题栏、上面的名字"""
        bg = _layer("bg")
        bh, bw = bg.shape[:2]
        vw, vh = W_OUT / 1.15, H_OUT / 1.15
        base = cv2.resize(bg[int((bh - vh) / 2):int((bh + vh) / 2), int((bw - vw) / 2):int((bw + vw) / 2)], OUT_SIZE)
        w, h = self.SIZE
        shadow = np.zeros((H_OUT, W_OUT), np.float32)
        for x, y in self.POS:
            cv2.rectangle(shadow, (x + 8, y - BAR_H + 22), (x + w - 8, y + h + 22), 1.0, -1)
        shadow = cv2.GaussianBlur(shadow, (0, 0), 26)[..., None] * 0.4
        base = (base.astype(np.float32) * (1 - shadow)).astype(np.uint8)
        im = Image.fromarray(cv2.cvtColor(base, cv2.COLOR_BGR2RGB))
        d = ImageDraw.Draw(im)
        f = _font(34)
        for (x, y), label in zip(self.POS, self.labels):
            d.rounded_rectangle((x, y - BAR_H, x + w, y + 12), radius=12, fill=(236, 232, 225))
            for i, col in enumerate(((236, 106, 94), (245, 191, 79), (98, 197, 84))):
                d.ellipse((x + 16 + i * 18, y - BAR_H + 12, x + 26 + i * 18, y - BAR_H + 22), fill=col)
            d.text((x, y - BAR_H - 56), label, font=f, fill=(27, 26, 23))
        return cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)

    def render_frames(self):
        base = self._base()
        idx, cur, img = [0, 0], [None, None], [None, None]
        w, h = self.SIZE
        for k in range(int(round(self.duration * FPS))):
            t = self.start + k / FPS
            out = base.copy()
            for side in (0, 1):
                frames = self.sides[side]
                while idx[side] < len(frames) and frames[idx[side]][0] <= t:
                    cur[side] = frames[idx[side]][1]
                    idx[side] += 1
                    img[side] = None
                if cur[side] is None:
                    cur[side] = frames[0][1]
                if img[side] is None:
                    img[side] = imread(cur[side])
                page = img[side]
                s = page.shape[1] / self.vw
                cx, cy, cw, ch = (v * s for v in self.cams[side].rect_at(t))
                x, y = self.POS[side]
                out[y:y + h, x:x + w] = cv2.resize(page[int(cy):int(cy + ch), int(cx):int(cx + cw)], self.SIZE,
                                                   interpolation=cv2.INTER_AREA)
            # 整个画面很慢地往里推，不定格
            f = 1 + min(0.035, 0.008 * (t - self.start))
            m = cv2.getRotationMatrix2D((W_OUT / 2, H_OUT / 2), 0, f)
            yield cv2.warpAffine(out, m, OUT_SIZE, borderMode=cv2.BORDER_REFLECT)


def segment_frames(seg):
    """按 30fps 逐帧取画面，按当时的取景框裁剪、缩放到 1080p（framed 时先放进画框）"""
    if isinstance(seg, SplitSegment):
        yield from seg.render_frames()
        return
    idx, cur, img = 0, None, None
    for k in range(int(round(seg.duration * FPS))):
        t = seg.start + k / FPS
        while idx < len(seg.frames) and seg.frames[idx][0] <= t:
            cur = seg.frames[idx][1]
            idx += 1
            img = None
        if cur is None:
            cur = seg.frames[0][1]
        if img is None:
            img = imread(cur)
        rect = seg.rect_at(t)
        if seg.framed:
            yield _render_framed(img, rect, t)
        else:
            out = np.empty((H_OUT, W_OUT, 3), np.uint8)
            s = img.shape[1] / seg.vw
            x, y, w, h = rect
            _paste(out, img, (-x * s * W_OUT / (w * s), -y * s * H_OUT / (h * s), img.shape[1] * W_OUT / (w * s),
                              img.shape[0] * H_OUT / (h * s)))
            yield out


def _scale(img, f):
    m = cv2.getRotationMatrix2D((W_OUT / 2, H_OUT / 2), 0, f)
    return cv2.warpAffine(img, m, OUT_SIZE, borderMode=cv2.BORDER_REFLECT)


def _transition(kind, a, b, p):
    """a 是上一段的画面，b 是下一段的，p 从 0 到 1"""
    e = ease_in_out_cubic(p)
    if kind == "zoom":
        return cv2.addWeighted(_scale(a, 1 + 0.12 * e), 1 - e, _scale(b, 0.93 + 0.07 * e), e, 0)
    if kind == "wipe":
        band = 260
        edge = int(-band + e * (W_OUT + 2 * band))
        out = a.copy()
        if edge > 0:
            out[:, :min(edge, W_OUT)] = b[:, :min(edge, W_OUT)]
        lo, hi = max(0, edge - band // 2), min(W_OUT, edge + band // 2)
        if hi > lo:
            out[:, lo:hi] = (43, 90, 138)                          # 古铜色色带（BGR）
            out[:, max(lo, hi - 18):hi] = (91, 154, 215)
        return out
    return cv2.addWeighted(a, 1 - e, b, e, 0)


def compose(segments, out_clean, out_preview=None, fade=0.4, transitions=None, out_webm=None):
    """把几段接成一个视频，段与段之间用 transitions 里的转场（fade / zoom / wipe），默认交叉淡化。
    返回 (全片台词 [(开始, 结束, 台词, 画面说明)], 全片音效点 [(时间, 音效名)], 总时长)"""
    transitions = transitions or ["fade"] * (len(segments) - 1)
    w_clean = H264Out(out_clean)
    w_prev = H264Out(out_preview) if out_preview else None
    w_webm = WebmOut(out_webm) if out_webm else None
    captions, events, offset, held, count = [], [], 0.0, [], 0

    def emit(frame):
        nonlocal count
        w_clean.write(frame)
        if w_prev or w_webm:
            t = count / FPS
            text = next((c[2] for c in captions if c[0] <= t < c[1]), None)
            shown = _draw_caption(frame, text) if text else frame
            if w_prev:
                w_prev.write(shown)
            if w_webm:
                w_webm.write(shown)
        count += 1

    for i, seg in enumerate(segments):
        shift = offset - seg.start
        captions += [(a + shift, b + shift, text, scene) for a, b, text, scene in seg.captions]
        events += [(t + shift, kind) for t, kind in seg.events]
        kind_in = transitions[i - 1] if i > 0 else None
        if kind_in:
            events.append((offset, "whoosh"))
        last = i == len(segments) - 1
        overlap = 0 if last else (0.5 if transitions[i] in ("zoom", "wipe") else fade)
        nf = int(round(overlap * FPS))
        tail = []
        for j, frame in enumerate(segment_frames(seg)):
            if j < len(held):
                frame = _transition(kind_in, held[j], frame, (j + 1) / (len(held) + 1))
            if last:
                emit(frame)
                continue
            tail.append(frame.copy())
            if len(tail) > nf:
                emit(tail.pop(0))
        held = tail
        offset += seg.duration - overlap
    w_clean.release()
    if w_prev:
        w_prev.release()
    if w_webm:
        w_webm.release()
    return captions, events, count / FPS


# ---------- 音效：按事件时间合成一条 WAV，剪映里放到第一帧就对齐 ----------
SR = 48000
_rng = np.random.default_rng(7)


def _t(sec):
    return np.arange(int(sec * SR)) / SR


def _sfx(kind):
    if kind == "click":
        t = _t(0.06)
        return (np.sin(2 * np.pi * 2300 * t) * np.exp(-t * 95) * 0.55
                + _rng.normal(0, 1, t.size) * np.exp(-t * 420) * 0.35)
    if kind == "pop":
        t = _t(0.16)
        freq = 320 + 520 * (1 - np.exp(-t * 40))
        return np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.minimum(1, t * 300) * np.exp(-t * 26)
    if kind == "tick":
        t = _t(0.04)
        return np.sin(2 * np.pi * 1550 * t) * np.exp(-t * 160)
    if kind == "ding":
        t = _t(1.3)
        s = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((880, 1), (1320, 0.5), (1760, 0.32), (2640, 0.14)))
        return s * np.minimum(1, t * 250) * np.exp(-t * 3.2) * 0.6
    if kind == "whoosh":
        t = _t(0.5)
        noise = _rng.normal(0, 1, t.size)
        hi = np.convolve(noise, np.hanning(48) / np.hanning(48).sum(), "same")
        lo = np.convolve(noise, np.hanning(400) / np.hanning(400).sum(), "same")
        return (hi - lo) * np.sin(np.pi * t / t[-1]) ** 2 * 2.2
    raise ValueError(kind)


VOLUME = {"click": 0.35, "pop": 0.28, "tick": 0.16, "ding": 0.30, "whoosh": 0.20}


def write_sfx(events, duration, path):
    track = np.zeros(int((duration + 1.5) * SR), np.float32)
    for t, kind in events:
        s = _sfx(kind) * VOLUME[kind]
        i = int(max(t, 0) * SR)
        n = min(s.size, track.size - i)
        if n > 0:
            track[i:i + n] += s[:n]
    track = track[:int(duration * SR)]
    peak = np.abs(track).max() or 1
    if peak > 0.85:
        track *= 0.85 / peak
    pcm = (np.repeat(track[:, None], 2, axis=1) * 32767).astype(np.int16)
    fd, tmp = tempfile.mkstemp(suffix=".wav")
    os.close(fd)
    with wave.open(tmp, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    shutil.move(tmp, path)


def write_player(path, video_name, audio_name, title):
    """本地网页预览：画面和音效一起播放（mp4v 浏览器放不了，所以用 VP9 的 webm）"""
    Path(path).write_text(f"""<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>{title}</title></head>
<body style="margin:0;background:#141311;color:#C9C2B6;font-family:'Microsoft YaHei',sans-serif;
  display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;gap:12px">
<video id="v" src="{video_name}" controls style="max-width:96vw;max-height:86vh;border-radius:8px"></video>
<audio id="a" src="{audio_name}" preload="auto"></audio>
<p>{title}：点播放，画面和音效一起放（配音、背景音乐在剪映里加）。</p>
<script>
const v = document.getElementById('v'), a = document.getElementById('a');
v.addEventListener('play', () => {{ a.currentTime = v.currentTime; a.play(); }});
v.addEventListener('pause', () => a.pause());
v.addEventListener('seeked', () => {{ a.currentTime = v.currentTime; }});
setInterval(() => {{ if (!v.paused && Math.abs(a.currentTime - v.currentTime) > 0.12) a.currentTime = v.currentTime; }}, 400);
</script>
</body></html>
""", encoding="utf-8")
