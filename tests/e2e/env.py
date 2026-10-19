"""接口测试的公共环境：连哪个 MySQL、用哪个库、后端 jar 在哪、怎么起停后端。都可以用环境变量改：

    E2E_MYSQL      mysql 客户端（默认找 PATH 里的 mysql，Windows 上再找 MySQL 8.0 的默认安装位置）
    E2E_DB_HOST    默认 127.0.0.1
    E2E_DB_PORT    默认 3306
    E2E_DB_USER    默认 root
    E2E_DB_PASSWORD 默认空
    E2E_DB         测试用的库名，默认 team_match_e2e。每套测试开始前都会删掉重建，所以不许叫 team_match
    E2E_JAR        后端 jar，默认 backend/target/backend-0.0.1-SNAPSHOT.jar
    E2E_PORT       测试后端的端口，默认 18090

测试会删库重建，千万不要指向放着真实数据的库。
"""
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent

DB = os.environ.get("E2E_DB", "team_match_e2e")
if DB == "team_match" or not re.fullmatch(r"[A-Za-z0-9_]+", DB):
    sys.exit(f"E2E_DB={DB!r} 不行：测试会删库重建，不能用 team_match（平时用的库），库名只能有字母数字下划线")

HOST = os.environ.get("E2E_DB_HOST", "127.0.0.1")
PORT_DB = os.environ.get("E2E_DB_PORT", "3306")
USER = os.environ.get("E2E_DB_USER", "root")
PASSWORD = os.environ.get("E2E_DB_PASSWORD", "")
PORT = int(os.environ.get("E2E_PORT", "18090"))
API = f"http://127.0.0.1:{PORT}/api"
WS = f"ws://127.0.0.1:{PORT}/api/ws/notifications"
JAR = Path(os.environ.get("E2E_JAR", ROOT / "backend" / "target" / "backend-0.0.1-SNAPSHOT.jar"))


def _mysql_exe():
    exe = os.environ.get("E2E_MYSQL") or shutil.which("mysql")
    if not exe and os.name == "nt":
        guess = Path(r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe")
        exe = str(guess) if guess.exists() else None
    if not exe:
        sys.exit("找不到 mysql 客户端：装上 MySQL，或者用环境变量 E2E_MYSQL 指定 mysql 的路径")
    return exe


MYSQL = _mysql_exe()


def mysql_args():
    """mysql 客户端的连接参数（不带库名）"""
    args = [MYSQL, f"--user={USER}", f"--host={HOST}", f"--port={PORT_DB}", "--default-character-set=utf8mb4"]
    if PASSWORD:
        args.append(f"--password={PASSWORD}")
    return args


def sql(stmt):
    """在测试库上执行一句 SQL，返回输出（不带表头，按制表符分列）"""
    r = subprocess.run(mysql_args() + ["-N", "-B", DB, "-e", stmt], capture_output=True)
    if r.returncode != 0:
        raise RuntimeError("SQL 出错：" + r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8").strip()


def load(sql_file):
    """导入一个 SQL 文件，文件里写死的 team_match 换成测试库的名字"""
    text = Path(sql_file).read_text(encoding="utf-8")
    text = re.sub(r"\bteam_match\b", DB, text)
    r = subprocess.run(mysql_args(), input=text.encode("utf-8"), capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(f"导入 {sql_file} 出错：" + r.stderr.decode("utf-8", "replace"))


def reset(demo=False):
    """删掉测试库，按 schema + seed（+ demo）重建"""
    r = subprocess.run(mysql_args() + ["-e", f"DROP DATABASE IF EXISTS `{DB}`"], capture_output=True)
    if r.returncode != 0:
        raise RuntimeError("连不上 MySQL：" + r.stderr.decode("utf-8", "replace"))
    for name in ["schema", "seed"] + (["demo"] if demo else []):
        load(ROOT / "sql" / f"{name}.sql")


def backend_command(*extra):
    url = (f"jdbc:mysql://{HOST}:{PORT_DB}/{DB}?useUnicode=true&characterEncoding=utf8"
           f"&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false")
    defaults = ["--spring.profiles.active=e2e", "--app.mail.mode=log", "--app.code.resend-seconds=0",
                "--app.crawler.enabled=false"]
    # 同一个参数传两遍 Spring 会拼成"0,60"，所以测试自己给了的就不再放默认值
    given = {a.split("=", 1)[0] for a in extra}
    defaults = [d for d in defaults if d.split("=", 1)[0] not in given]
    return ["java", "-jar", str(JAR), f"--server.port={PORT}", f"--spring.datasource.url={url}",
            f"--spring.datasource.username={USER}", f"--spring.datasource.password={PASSWORD}",
            *defaults, *extra]


def start_backend(log_path, *extra):
    """起测试后端，等到启动完成；起不来就退出"""
    if not JAR.exists():
        sys.exit(f"找不到后端 jar：{JAR}。先在 backend 目录跑 ./mvnw package -DskipTests")
    out = open(log_path, "w", encoding="utf-8")
    proc = subprocess.Popen(backend_command(*extra), stdout=out, stderr=subprocess.STDOUT, cwd=ROOT / "backend")
    for _ in range(180):
        text = Path(log_path).read_text(encoding="utf-8", errors="replace")
        if "Started TeamMatchApplication" in text:
            return proc
        if "APPLICATION FAILED" in text or proc.poll() is not None:
            break
        time.sleep(1)
    proc.kill()
    sys.exit(f"测试后端没起来，看 {log_path}")


def stop_backend(proc):
    if proc and proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(30)
        except subprocess.TimeoutExpired:
            proc.kill()


def demo_password():
    """演示账号的统一密码，写在 sql/demo.sql 开头的注释里"""
    head = (ROOT / "sql" / "demo.sql").read_text(encoding="utf-8")[:3000]
    return re.search(r"密码统一是\s*(\S+?)：", head).group(1)
