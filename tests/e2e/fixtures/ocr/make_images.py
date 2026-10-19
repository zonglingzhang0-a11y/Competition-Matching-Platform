"""重新生成 OCR 测试图（fixtures/ocr 里已经有生成好的，一般不用跑）：学信网学籍页样式的截图、学生证样式的"照片"（有旋转和噪点）。都是虚构信息。"""
import os
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent
# 要有中文字体：Windows 默认用微软雅黑，别的系统用 OCR_FONT 指定，比如 Noto Sans CJK
FONT = os.environ.get("OCR_FONT", "C:/Windows/Fonts/msyh.ttc")


def xuexin(name, school, year, path):
    img = Image.new("RGB", (900, 620), "white")
    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(FONT, 34)
    body = ImageFont.truetype(FONT, 26)
    d.rectangle([0, 0, 900, 70], fill=(38, 117, 191))
    d.text((30, 14), "学籍信息", font=title, fill="white")
    rows = [("姓名", name), ("性别", "女"), ("出生日期", "2005年03月12日"), ("院校", school),
            ("层次", "本科"), ("专业", "计算机科学与技术"), ("学制", "4 年"),
            ("入学日期", f"{year}年09月01日"), ("学籍状态", "在籍（注册学籍）")]
    y = 100
    for k, v in rows:
        d.text((60, y), f"{k}：", font=body, fill=(90, 90, 90))
        d.text((240, y), v, font=body, fill="black")
        y += 55
    img.save(path)


def student_card(name, school, year, path):
    card = Image.new("RGB", (860, 540), (245, 240, 230))
    d = ImageDraw.Draw(card)
    big = ImageFont.truetype(FONT, 40)
    body = ImageFont.truetype(FONT, 30)
    d.text((240, 30), school, font=big, fill=(150, 20, 20))
    d.text((330, 90), "学 生 证", font=big, fill=(150, 20, 20))
    d.rectangle([50, 170, 250, 430], outline=(120, 120, 120), width=3)
    d.text((110, 280), "照片", font=body, fill=(150, 150, 150))
    for i, line in enumerate([f"姓名：{name}", "学院：信息学院", f"入学年份：{year}", "学号：2023010521"]):
        d.text((300, 180 + i * 62), line, font=body, fill="black")
    # 拍照的效果：放在桌面背景上、转一点角度、加噪点、稍微模糊
    bg = Image.new("RGB", (1100, 800), (110, 100, 90))
    rotated = card.rotate(4, expand=True, fillcolor=(110, 100, 90))
    bg.paste(rotated, (90, 90))
    px = bg.load()
    rnd = random.Random(7)
    for _ in range(25000):
        x, y = rnd.randrange(bg.width), rnd.randrange(bg.height)
        r, g, b = px[x, y]
        n = rnd.randint(-40, 40)
        px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)))
    bg.filter(ImageFilter.GaussianBlur(0.8)).save(path, quality=85)


if __name__ == "__main__":
    xuexin("林小雨", "示例大学", 2023, OUT / "t_lin.png")
    student_card("赵晴", "示例大学", 2024, OUT / "t_zhao.jpg")
    xuexin("孙一帆", "新晴理工大学", 2022, OUT / "t_sun.png")
    print("ok")
