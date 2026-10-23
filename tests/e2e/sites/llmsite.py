"""爬虫接大模型的 e2e 用：假官网（18768）和假大模型（18096，OpenAI 兼容的 /v1/chat/completions）。内容都是虚构的。
假大模型按通知标题给不同的回答：正常、编造依据、HTTP 500、很慢（超时）；系统提示是比赛页 AI 总结的，回答 summary_answer。
每个请求记到日志（JSON 一行一个）。
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

# 比赛页 AI 总结：两条对得上原文的，一条编的原话，一条数字对不上原话
SUMMARY_POINTS = [
    {"label": "组队要求", "text": "每队最多 3 人，须为同一所高校的学生", "quote": "每队不超过三人，须为同一所高校学生"},
    {"label": "比赛时间", "text": "12 月 5 日在各赛区同时举行", "quote": "竞赛定于十二月五日在各赛区同时举行"},
    {"label": "报名时间", "text": "11 月 15 日截止报名", "quote": "报名截止时间为2026年11月15日"},
    {"label": "费用", "text": "报名费 100 元", "quote": "参赛对象：全日制在校本科生"},
]
summary_calls = 0

# AI 能力分析：一条对得上的优势、一条编的；技能里 MATLAB 对得上，量子计算不在字典里，LaTeX 的原话是编的
PROFILE_ANSWER = {
    "summary": "做过数学建模，负责写求解代码。",
    "strengths": [{"title": "能写求解代码", "detail": "建模比赛里负责编程", "evidence": "负责用 Python 写求解代码"},
                  {"title": "获奖经验丰富", "detail": "编的", "evidence": "拿过三次国家一等奖"}],
    "skills": [{"name": "MATLAB", "level": 2, "evidence": "也会用 MATLAB 画图"},
               {"name": "量子计算", "level": 3, "evidence": "负责用 Python 写求解代码"},
               {"name": "LaTeX 排版", "level": 2, "evidence": "论文都是我排的版"}],
    "personality": [{"trait": "愿意扛编程", "evidence": "负责用 Python 写求解代码"}],
    "axisHints": [{"axis": "WRITE", "reason": "写作还空着"}, {"axis": "MAGIC", "reason": "不存在的轴"}],
}
# AI 招募建议：WRITE 真缺；CODE 队里已经够了（后端会去掉）；MAGIC 不存在
TEAM_ANSWER = {
    "summary": "队里缺写论文、排版的人。",
    "weakAxes": [{"axis": "WRITE", "reason": "没人会排版"}, {"axis": "CODE", "reason": "其实已经够了"},
                 {"axis": "MAGIC", "reason": "x"}],
    "suggestions": ["找一个会 LaTeX 的同学"], "candidateProfile": ["写过论文"], "questions": ["排过几篇论文？"],
}


def summary_answer(user):
    """按原话在第几篇通知里填 source；第 2 次只回编的，第 3 次慢 1 秒（测同时点两次）"""
    global summary_calls
    with lock:
        summary_calls += 1
        n = summary_calls
    parts = user.split("\n第 ")[1:]
    points = []
    for p in SUMMARY_POINTS if n != 2 else SUMMARY_POINTS[2:]:
        source = next((i + 1 for i, part in enumerate(parts) if p["quote"] in part), 1)
        points.append(dict(p, source=source))
    if n == 3:
        time.sleep(1)
    return {"points": points}, [part.split("篇通知：", 1)[1].split("\n", 1)[0] for part in parts]


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
        system = body["messages"][0]["content"]
        summary = "整理比赛的官方通知" in system
        kind = "summary" if summary else "profile" if "能力分析助手" in system else "team" if "招募顾问" in system else "extract"
        notices = []
        if summary:
            answer, notices = summary_answer(user)
        elif kind == "profile":
            answer = PROFILE_ANSWER
        elif kind == "team":
            answer = TEAM_ANSWER
        with lock, open(LOG, "a", encoding="utf-8") as f:
            f.write(json.dumps({"title": title, "model": body.get("model"), "path": self.path,
                                "system": body["messages"][0]["role"] == "system",
                                "auth": self.headers.get("Authorization"), "chars": len(user),
                                "kind": kind, "notices": notices,
                                # AI 分析的请求把全文记下来，测试里查有没有把邮箱、联系方式发出去
                                "user": user if kind in ("profile", "team") else None}, ensure_ascii=False) + "\n")
        if kind == "profile" and "请让模型出错" in user:
            self.send_response(500)
            self.end_headers()
            self.wfile.write(b"engine error")
            return
        if kind == "extract":
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
