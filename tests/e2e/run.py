"""接口测试：每套测试前重建测试库、起测试后端，跑完关掉，最后汇总。

    python tests/e2e/run.py              # 全部
    python tests/e2e/run.py m3 push      # 只跑这几套
    python tests/e2e/run.py --list       # 看有哪些

先在 backend 目录打包：./mvnw package -DskipTests。需要 Python 3.10+ 和 websockets（pip install websockets）。
数据库连接、库名等见 env.py。测试会删库重建，默认用 team_match_e2e，不会碰平时用的 team_match。
"""
import os
import socket
import subprocess
import sys
import time
from dataclasses import dataclass, field

import env

ADMIN = ["--app.admin-emails[0]=admin@test.local"]
REMIND = ["--app.reminder.interval-ms=3000", "--app.reminder.initial-delay-ms=1000"]
MAIL = ["--app.notify-mail.delay-seconds=3", "--app.notify-mail.interval-ms=1000", "--app.notify-mail.initial-delay-ms=1000"]
LLM = ["--app.crawler.delay-ms=200", "--app.crawler.llm.enabled=true", "--app.crawler.llm.base-url=http://127.0.0.1:18096/v1",
       "--app.crawler.llm.api-key=test-key", "--app.crawler.llm.timeout-ms=1500"]


@dataclass
class Suite:
    name: str
    title: str
    args: list = field(default_factory=list)
    demo: bool = False
    site: str | None = None
    site_port: int = 0
    self_managed: bool = False


SUITES = [
    Suite("m2", "比赛库、关注、个人档案、管理员录入", ADMIN),
    Suite("m3", "建队、招募、申请和邀请、退队、转让、解散", ADMIN),
    Suite("m4", "问卷和推荐、撞期否决", ADMIN),
    Suite("m5", "个人日程、日历三图层、撞期、提醒", REMIND),
    Suite("m6", "管理后台：认证审核、比赛库、账号、看板、匹配配置", ADMIN),
    Suite("review", "赛后互评、信用分、举报", ADMIN + REMIND),
    Suite("fix12", "#3 #4 的修复", ADMIN),
    Suite("push", "站内信实时推送", ADMIN),
    Suite("tasks", "里程碑分工待办", ADMIN + REMIND),
    Suite("feed", "订阅到手机日历", ADMIN),
    Suite("fuzzy", "时间说不准的日程、全天日程的结束日"),
    Suite("notify_mail", "新消息汇总发邮件", ADMIN + MAIL),
    Suite("reset_password", "忘记密码、登录失败锁定"),
    Suite("profile", "个人资料页按关系分级", demo=True),
    Suite("demo_v21", "演示数据里 v2.1 的内容", demo=True),
    Suite("crawl", "比赛通知爬虫（本机假官网）", ADMIN + ["--app.crawler.delay-ms=300"], site="crawlsite", site_port=18765),
    Suite("llm", "爬虫接大模型（本机假官网 + 假大模型）", ADMIN + LLM, site="llmsite", site_port=18768),
    Suite("login_store", "后端重启后登录还在", self_managed=True),
    Suite("demo_dates", "演示比赛的日期", self_managed=True),
]

LOGS = env.HERE / "logs"


def wait_port(port, seconds=20):
    end = time.time() + seconds
    while time.time() < end:
        with socket.socket() as s:
            if s.connect_ex(("127.0.0.1", port)) == 0:
                return True
        time.sleep(0.3)
    return False


def summary_line(text):
    lines = [l for l in text.splitlines() if "项，通过" in l]
    return lines[-1].strip() if lines else "（没有输出结果）"


def run(suite):
    child_env = dict(os.environ, PYTHONIOENCODING="utf-8")
    script = env.HERE / f"{suite.name}.py"
    out_path = LOGS / f"{suite.name}.txt"
    backend_log = LOGS / f"{suite.name}.log"
    site_proc = backend = None
    try:
        if suite.self_managed:
            cmd = [sys.executable, str(script), str(LOGS)]
        else:
            env.reset(demo=suite.demo)
            extra = []
            if suite.site:
                site_log = LOGS / f"{suite.name}_site.log"
                site_log.write_text("", encoding="utf-8")
                site_proc = subprocess.Popen([sys.executable, str(env.HERE / "sites" / f"{suite.site}.py"), str(site_log)],
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, env=child_env)
                if not wait_port(suite.site_port):
                    return False, "假官网没起来"
                extra = [str(site_log)]
            backend = env.start_backend(backend_log, *suite.args)
            cmd = [sys.executable, str(script), str(backend_log), *extra]
        r = subprocess.run(cmd, capture_output=True, env=child_env, cwd=env.HERE)
        text = r.stdout.decode("utf-8", "replace") + r.stderr.decode("utf-8", "replace")
        out_path.write_text(text, encoding="utf-8")
        errors = 0
        logs = [backend_log] if backend_log.exists() and not suite.self_managed else list(LOGS.glob(f"{suite.name}_*.log"))
        for log in logs:
            errors += sum(1 for l in log.read_text(encoding="utf-8", errors="replace").splitlines() if " ERROR " in l)
        ok = r.returncode == 0 and errors == 0
        return ok, summary_line(text) + (f"，后端 ERROR 日志 {errors} 条" if errors else "")
    finally:
        env.stop_backend(backend)
        if site_proc:
            site_proc.terminate()


def main():
    args = sys.argv[1:]
    if "--list" in args:
        for s in SUITES:
            print(f"{s.name:<16}{s.title}")
        return 0
    chosen = [s for s in SUITES if not args or s.name in args]
    unknown = set(args) - {s.name for s in SUITES}
    if unknown:
        print("没有这些测试：" + "、".join(sorted(unknown)) + "（--list 看有哪些）")
        return 2
    LOGS.mkdir(exist_ok=True)
    print(f"测试库 {env.DB}（{env.HOST}:{env.PORT_DB}），测试后端端口 {env.PORT}，日志在 {LOGS}")
    failed = []
    for s in chosen:
        t0 = time.time()
        ok, line = run(s)
        print(f"{'通过' if ok else '失败'}  {s.name:<16}{line}  （{time.time() - t0:.0f} 秒）", flush=True)
        if not ok:
            failed.append(s.name)
    print(f"\n{len(chosen)} 套，通过 {len(chosen) - len(failed)} 套" + (f"，失败：{'、'.join(failed)}（看 logs 里同名的 .txt）" if failed else ""))
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
