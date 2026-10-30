"""整片：片头 → 6 个模块（按 PPT 的顺序）→ 片尾。模块自己的章节卡就是分段，直接首尾相接（不重新编码）。
先跑完 6 个模块的录制脚本，再跑这个。用法：python scripts/demo-video/make_full_video.py"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

from playwright.sync_api import sync_playwright

from demo_kit import ffmpeg_exe, srt_time
from module_common import ROOT, Module

ORDER = ["个人信息与赛库", "队伍招募", "匹配推荐", "消息通知", "互评与日程", "管理看板"]
OUT = ROOT / "演示视频" / "整片"
missing = [n for n in ORDER if not (ROOT / "演示视频" / f"模块_{n}" / f"{n}.mp4").exists()]
if missing:
    sys.exit(f"这些模块还没录：{'、'.join(missing)}")


def duration(path):
    err = subprocess.run([ffmpeg_exe(), "-hide_banner", "-i", str(path)], capture_output=True, text=True,
                         encoding="utf-8", errors="replace").stderr
    h, m, s = re.search(r"Duration: (\d+):(\d+):([\d.]+)", err).groups()
    return int(h) * 3600 + int(m) * 60 + float(s)


def read_srt(path):
    items = []
    for block in path.read_text(encoding="utf-8-sig").strip().split("\n\n"):
        lines = block.splitlines()
        a, b = (sum(float(x) * k for x, k in zip(t.replace(",", ".").split(":"), (3600, 60, 1)))
                for t in lines[1].split(" --> "))
        items.append((a, b, "\n".join(lines[2:])))
    return items


# ---------- 片头、片尾 ----------
intro = Module("片头", {})
outro = Module("片尾", {"outro": {
    "eyebrow": "2026 Web 开发技术 · 第八组", "heading": "两周，8 个版本，网站已经上线",
    "stats": [{"value": 8, "label": "个版本（10 月 10 日起两周）"}, {"value": 861, "label": "项接口测试检查，26 套"},
              {"value": 183, "label": "个后端单元测试"}, {"value": 12, "label": "个数据库迁移，旧库自动升级"}],
    "slogan": "让每一次组队，都从碰运气，变成有依据的选择", "url": "http://47.111.123.178"}})
with sync_playwright() as p:
    intro.launch(p)
    page = intro.card_page()
    r_in = intro.begin(page, "intro")
    r_in.say("竞搭：帮大学生找到能一起冲奖的队友。下面分六个部分演示。", "片头")
    page.evaluate("([e, h, s]) => demo.title(e, h, s)",
                  ["2026 Web 开发技术 · 第八组", "竞搭：竞赛组队匹配平台", "帮大学生找到能一起冲奖的队友"])
    for i in range(2):
        r_in.event("tick", 0.22 + i * 0.09)
    r_in.event("pop", 0.5)
    intro.end(r_in, 0.8)
    intro.browser.close()

    outro.launch(p)
    page = outro.card_page()
    r_out = outro.begin(page, "outro")
    r_out.say("两周八个版本，八百多项接口测试；网站已经上线，欢迎体验。", "片尾：工程数字逐个出现，最后是口号和网址")
    page.evaluate("demo.outro()")
    for i in range(4):
        r_out.event("pop", 0.6 + i * 0.38)
    r_out.event("ding", 0.6 + 4 * 0.38 + 0.5)
    outro.end(r_out, 2.0)
    outro.browser.close()
intro.render([(r_in, False)], [])
outro.render([(r_out, False)], [])

# ---------- 首尾相接 ----------
OUT.mkdir(parents=True, exist_ok=True)
parts = [("片头", intro.out)] + [(n, ROOT / "演示视频" / f"模块_{n}") for n in ORDER] + [("片尾", outro.out)]
for suffix, final in (("", "竞搭演示_完整版.mp4"), ("_带字幕", "竞搭演示_完整版_带字幕.mp4")):
    fd, listfile = tempfile.mkstemp(suffix=".txt")
    os.close(fd)
    with open(listfile, "w", encoding="utf-8") as f:
        for name, folder in parts:
            f.write(f"file '{(folder / f'{name}{suffix}.mp4').as_posix()}'\n")
    fd, tmp = tempfile.mkstemp(suffix=".mp4")
    os.close(fd)
    subprocess.run([ffmpeg_exe(), "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", listfile,
                    "-c", "copy", "-movflags", "+faststart", "-y", tmp], check=True)
    shutil.move(tmp, OUT / final)
    os.remove(listfile)

# ---------- 字幕和文案也接起来 ----------
offset, srt, rows = 0.0, [], ["# 竞搭演示 · 完整版 · 文案", "", "| 段落 | 起止 | 字幕 |", "|---|---|---|"]
for name, folder in parts:
    d = duration(folder / f"{name}.mp4")
    for a, b, text in read_srt(folder / f"{name}.srt"):
        srt.append((a + offset, b + offset, text))
        rows.append(f"| {name} | {srt_time(a + offset)[3:8]}–{srt_time(b + offset)[3:8]} | {text} |")
    offset += d
(OUT / "竞搭演示_完整版.srt").write_text(
    "\n".join(f"{i}\n{srt_time(a)} --> {srt_time(b)}\n{t}\n" for i, (a, b, t) in enumerate(srt, 1)), encoding="utf-8-sig")
(OUT / "文案.md").write_text("\n".join(rows) + f"\n\n总时长 {offset:.1f} 秒。\n", encoding="utf-8")
print(f"完成：整片 {offset:.1f} 秒（{int(offset // 60)} 分 {offset % 60:.0f} 秒），字幕 {len(srt)} 条")
