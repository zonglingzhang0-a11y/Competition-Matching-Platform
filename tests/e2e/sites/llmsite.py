"""爬虫接大模型的 e2e 用：假官网（18768）和假大模型（18096，OpenAI 兼容的 /v1/chat/completions）。内容都是虚构的。
假大模型按通知标题给不同的回答：正常、编造依据、HTTP 500、很慢（超时）。每个请求记到日志（JSON 一行一个）。
用法：python server.py <假大模型请求日志>"""
import json
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

LOG = sys.argv[1]
lock = threading.Lock()


def page(title, body):
    return f"""<!doctype html><html><head><meta charset="utf-8"><title>{title}</title></head>
<body><div id="content"><h1>{title}</h1>{body}</div></body></html>"""


def listing(links):
    items = "".join(f'<li><a href="{u}">{t}</a></li>' for u, t in links)
    return f'<!doctype html><html><head><meta charset="utf-8"></head><body><ul id="list">{items}</ul></body></html>'


CN_BODY = """<p>一、参赛对象：全日制在校本科生，每队不超过三人，须为同一所高校学生。</p>
<p>二、报名办法：参赛同学须在十一月一日至十一月二十日之间登录官网完成报名。</p>
<p>三、竞赛定于十二月五日在各赛区同时举行。</p>"""

SITE = {
    "/robots.txt": ("text/plain", "User-agent: *\nAllow: /\n"),
    "/index.html": ("text/html", listing([
        ("/n/1.html", "关于举办2026年全国大学生节能减排竞赛的通知"),
        ("/n/2.html", "关于举办2026年全国大学生物理实验竞赛的通知"),
        ("/n/3.html", "关于举办2026年全国大学生化学实验竞赛的通知"),
        ("/n/4.html", "关于举办2026年全国大学生力学竞赛的通知"),
        ("/n/5.html", "关于举办2026年全国大学生英语竞赛的通知"),
    ])),
    "/n/1.html": ("text/html", page("关于举办2026年全国大学生节能减排竞赛的通知", CN_BODY)),
    "/n/2.html": ("text/html", page("关于举办2026年全国大学生物理实验竞赛的通知", CN_BODY.replace("十一月", "十 一 月"))),
    "/n/3.html": ("text/html", page("关于举办2026年全国大学生化学实验竞赛的通知", CN_BODY + "<p>（化学）</p>")),
    "/n/4.html": ("text/html", page("关于举办2026年全国大学生力学竞赛的通知", CN_BODY + "<p>（力学）</p>")),
    "/n/5.html": ("text/html", page("关于举办2026年全国大学生英语竞赛的通知", """
<p>报名时间：2026年11月1日至11月20日。</p><p>竞赛时间：2026年12月5日8:00至12月6日18:00。</p>
<p>采用3人一队的方式组队。</p>""")),
    "/index2.html": ("text/html", listing([("/m/1.html", "关于举办2026年全国大学生数学竞赛的通知")])),
    "/m/1.html": ("text/html", page("关于举办2026年全国大学生数学竞赛的通知", CN_BODY + "<p>（数学）</p>")),
}

GOOD = {
    "year": 2026,
    "registration": {"start": "2026-11-01", "end": "2026-11-20",
                     "quote": "参赛同学须在十一月一日至十一月二十日之间登录官网完成报名"},
    "contest": {"start": "2026-12-05", "end": None, "quote": "竞赛定于十二月五日在各赛区同时举行"},
    "teamSize": {"min": 1, "max": 3, "quote": "每队不超过三人"},
    "sameSchool": {"value": True, "quote": "须为同一所高校学生"},
    "advisor": None,
}
MADE_UP = {
    "year": 2026,
    "registration": {"start": None, "end": "2026-11-15", "quote": "报名截止时间为2026年11月15日"},
    "contest": {"start": "2026-12-08", "end": None, "quote": "竞赛定于十二月五日在各赛区同时举行"},
}


class Site(BaseHTTPRequestHandler):
    def do_GET(self):
        hit = SITE.get(self.path)
        if hit is None:
            self.send_response(404)
            self.end_headers()
            return
        ctype, body = hit
        data = body.encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", f"{ctype}; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


class Llm(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))).decode("utf-8"))
        user = body["messages"][-1]["content"]
        title = user.split("\n", 1)[0]
        with lock, open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"title": title, "model": body.get("model"), "path": self.path,
                                "system": body["messages"][0]["role"] == "system",
                                "auth": self.headers.get("Authorization"), "chars": len(user)}, ensure_ascii=False) + "\n")
        if "化学" in title:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b"engine error")
            return
        if "力学" in title:
            time.sleep(3)
        answer = MADE_UP if "物理" in title else GOOD
        # 像本机模型那样：前面一句客套话，JSON 包在代码块里；响应头不带 charset
        content = "好的，抽取结果如下：\n```json\n" + json.dumps(answer, ensure_ascii=False) + "\n```"
        data = json.dumps({"choices": [{"message": {"role": "assistant", "content": content}}]}, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def log_message(self, *args):
        pass


servers = [ThreadingHTTPServer(("127.0.0.1", 18768), Site), ThreadingHTTPServer(("127.0.0.1", 18096), Llm)]
for s in servers:
    threading.Thread(target=s.serve_forever, daemon=True).start()
print("ready", flush=True)
while True:
    time.sleep(3600)
