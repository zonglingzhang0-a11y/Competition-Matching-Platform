"""爬虫 e2e 用的假官网（内容都是虚构的）。三个端口：
18765：有 robots.txt（禁 /private/，拒绝 GPTBot），各种通知链接
18766：没有 robots.txt（404，等于不限制），一篇数模通知
18767：robots.txt 返回 500（按 RFC 当作全部禁止）
每个请求记到 requests.log（JSON 一行一个：时间、端口、路径、User-Agent）。
用法：python server.py <日志文件>"""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = sys.argv[1]
lock = threading.Lock()

SIDEBAR = '<div id="side"><h3>往期回顾</h3><p>2019年8月1日至8月5日举办了第一届。</p></div>'


def page(title, body):
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>{title} - 示例竞赛网</title></head>
<body><div id="banner">本站将于2020年5月19日 21:00-23:00 进行升级维护。</div>
<div id="content"><h1>{title}</h1>{body}</div>{SIDEBAR}</body></html>"""


NOTICE_1 = page("关于举办2026年全国大学生电子设计竞赛的通知", """
<p>一、参赛对象：普通高校全日制在校学生，采用校内自愿组合、3人一队的原则组成参赛队。</p>
<p>二、每队须配备1名指导教师。</p>
<p>三、报名时间：2026年11月1日至11月20日。</p>
<p>四、竞赛时间：2026年12月5日（周六）8:00至12月7日（周一）20:00。</p>
<p>附件：<a href="/files/notice.pdf">报名表.pdf</a></p>""")

SITE_A = {
    "/robots.txt": ("text/plain", "User-agent: *\nDisallow: /private/\n\nUser-agent: GPTBot\nDisallow: /\n"),
    "/index.html": ("text/html", """<!doctype html><html><head><meta charset="utf-8"><title>示例竞赛网</title></head><body>
<ul id="list">
<li><a href="/news/1.html">关于举办2026年全国大学生电子设计竞赛的通知</a></li>
<li><a href="/news/2.html" title="关于举办2025年全国大学生电子设计竞赛的通知（完整标题）">关于举办2025年全国大学生电子设计…</a></li>
<li><a href="/news/3.html">2026年竞赛获奖名单公示的通知</a></li>
<li><a href="/news/4.html">关于召开赛题讲评会议的通知</a></li>
<li><a href="/private/5.html">内部工作通知</a></li>
<li><a href="http://127.0.0.2:18765/news/9.html">外站转载的通知</a></li>
<li><a href="/files/notice.pdf">竞赛通知附件</a></li>
<li><a href="/news/6.html">转载：关于举办2026年全国大学生电子设计竞赛的通知</a></li>
<li><a href="/redirect/7">补充通知（报名截止调整）</a></li>
<li><a href="/news/1.html#top">关于举办2026年全国大学生电子设计竞赛的通知</a></li>
</ul>
<div id="other"><a href="/about.html">关于我们的通知</a></div>
</body></html>"""),
    "/news/1.html": ("text/html", NOTICE_1),
    "/news/2.html": ("text/html", page("关于举办2025年全国大学生电子设计竞赛的通知", """
<p>报名时间：2025年5月31日截止。</p><p>竞赛时间：2025年7月30日8:00至8月2日20:00。</p>""")),
    "/news/3.html": ("text/html", page("2026年竞赛获奖名单公示", "<p>名单略。</p>")),
    "/news/4.html": ("text/html", page("关于召开赛题讲评会议的通知", "<p>会议将邀请命题专家讲评赛题，欢迎各校师生参加。</p>")),
    "/private/5.html": ("text/html", page("内部工作通知", "<p>不应被抓到。</p>")),
    # 和 1 正文一模一样（转载），去重
    "/news/6.html": ("text/html", NOTICE_1),
    "/news/8.html": ("text/html", page("补充通知（报名截止调整）", "<p>经研究，报名截止时间延长至2026年11月25日24时。</p>")),
    "/files/notice.pdf": ("application/pdf", "%PDF-1.4 fake"),
}

SITE_B = {
    "/index.html": ("text/html", """<!doctype html><html><head><meta charset="utf-8"></head><body>
<div class="news"><a href="/n/2026.html">关于举办2026年全国大学生数学建模竞赛的通知</a></div></body></html>"""),
    "/n/2026.html": ("text/html", page("关于举办2026年全国大学生数学建模竞赛的通知", """
<p>参赛队由3名学生组成，须为同一所学校的在籍学生。</p>
<p>报名截止时间：2026年10月20日。</p>
<p>竞赛时间为2026年10月30日18:00至11月2日20:00。</p>""")),
}

SITE_C = {
    "/index.html": ("text/html", "<html><body><a href='/x.html'>通知</a></body></html>"),
}

SITES = {18765: SITE_A, 18766: SITE_B, 18767: SITE_C}


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        port = self.server.server_address[1]
        with lock, open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"t": time.time(), "port": port, "path": self.path,
                                "ua": self.headers.get("User-Agent", "")}, ensure_ascii=False) + "\n")
        if port == 18767 and self.path == "/robots.txt":
            self.send_error(500)
            return
        if self.path.startswith("/redirect/7"):
            self.send_response(302)
            self.send_header("Location", "/news/8.html")
            self.end_headers()
            return
        item = SITES[port].get(self.path)
        if item is None:
            self.send_error(404)
            return
        ctype, body = item
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", ctype + ("; charset=utf-8" if ctype.startswith("text") else ""))
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


servers = [ThreadingHTTPServer(("127.0.0.1", p), Handler) for p in SITES]
for s in servers[1:]:
    threading.Thread(target=s.serve_forever, daemon=True).start()
print("fixture sites up", flush=True)
servers[0].serve_forever()
