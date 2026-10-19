"""M1 接口端到端测试：注册（验证码、重发间隔、密码强度）、教育邮箱认证、登录退出、接口文档。
后端要用 --app.code.resend-seconds=60 启动（要测"马上重发被拦"），库是 schema + seed 的空库。
用法：python m1.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import urllib.error
import urllib.request

API = env.API
LOG = sys.argv[1]
results = []


def call(method, path, body=None, token=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(API + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8") or "{}")


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l and "验证码" in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1] if lines else None


print("== 注册")
s, r = call("POST", "/auth/register-code", {"email": "Alice@Test.local"})
check("发注册验证码（邮箱大小写不敏感）", s == 200 and code_for("alice@test.local"), (s, r))
s, r = call("POST", "/auth/register-code", {"email": "alice@test.local"})
check("马上重发被拦（429）", s == 429, (s, r))
code = code_for("alice@test.local")
s, r = call("POST", "/auth/register", {"email": "alice@test.local", "code": "000000", "password": "abc12345", "nickname": "小A"})
check("验证码不对（400）", s == 400, (s, r))
s, r = call("POST", "/auth/register", {"email": "alice@test.local", "code": code, "password": "12345678", "nickname": "小A"})
check("密码只有数字（400）", s == 400, (s, r))
s, r = call("POST", "/auth/register", {"email": "alice@test.local", "code": code, "password": "abc12345", "nickname": "小A"})
token = (r.get("data") or {}).get("token")
check("注册成功，直接登录", s == 200 and token and r["data"]["me"]["verifyStatus"] == "NONE", (s, r))
s, r = call("POST", "/auth/register", {"email": "alice@test.local", "code": code, "password": "abc12345", "nickname": "小A"})
check("同一个邮箱再注册（409）", s == 409, (s, r))
s, r = call("POST", "/auth/register-code", {"email": "alice@test.local"})
check("注册过的邮箱不再发注册验证码（409）", s == 409, (s, r))

print("== 登录状态")
check("不带凭证查自己（401）", call("GET", "/users/me")[0] == 401)
s, r = call("GET", "/users/me", token=token)
check("带凭证查自己", s == 200 and r["data"]["email"] == "alice@test.local", (s, r))

print("== 教育邮箱认证")
s, r = call("POST", "/verify/edu/code", {"eduEmail": "alice@qq.com"}, token)
check("不是教育邮箱（400）", s == 400, (s, r))
s, r = call("POST", "/verify/edu/code", {"eduEmail": "2023001@stu.example.edu.cn"}, token)
check("教育邮箱发码", s == 200, (s, r))
s, r = call("POST", "/verify/edu/confirm", {"eduEmail": "2023001@stu.example.edu.cn", "code": code_for("2023001@stu.example.edu.cn")}, token)
check("确认后认证通过，学校是示例大学", s == 200, (s, r))
me = call("GET", "/users/me", token=token)[1]["data"]
check("查自己：已认证", me["verifyStatus"] == "VERIFIED" and me["school"] and me["school"]["name"] == "示例大学", me)

call("POST", "/auth/register-code", {"email": "bob@test.local"})
s, r = call("POST", "/auth/register", {"email": "bob@test.local", "code": code_for("bob@test.local"), "password": "bob12345", "nickname": "小B"})
bob = r["data"]["token"]
s, r = call("POST", "/verify/edu/code", {"eduEmail": "2023001@stu.example.edu.cn"}, bob)
check("别人用过的教育邮箱（409）", s == 409, (s, r))
call("POST", "/verify/edu/code", {"eduEmail": "bob@mail.unknown.edu.cn"}, bob)
s, r = call("POST", "/verify/edu/confirm", {"eduEmail": "bob@mail.unknown.edu.cn", "code": code_for("bob@mail.unknown.edu.cn")}, bob)
me = call("GET", "/users/me", token=bob)[1]["data"]
check("没收录的学校：进人工审核", s == 200 and me["verifyStatus"] == "PENDING", (s, r, me))

print("== 登录和退出")
s, r = call("POST", "/auth/login", {"email": "alice@test.local", "password": "wrong123"})
check("密码错误（400）", s == 400, (s, r))
s, r = call("POST", "/auth/login", {"email": "ALICE@test.local", "password": "abc12345"})
t3 = (r.get("data") or {}).get("token")
check("邮箱大写也能登录", s == 200 and t3, (s, r))
s, r = call("POST", "/auth/logout", token=t3)
check("退出后旧凭证失效（401）", s == 200 and call("GET", "/users/me", token=t3)[0] == 401)
check("另一处登录不受影响", call("GET", "/users/me", token=token)[0] == 200)

try:
    with urllib.request.urlopen(f"http://127.0.0.1:{env.PORT}/v3/api-docs") as res:
        docs_status = res.status
except urllib.error.HTTPError as e:
    docs_status = e.code
check("接口文档能打开", docs_status == 200, docs_status)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
