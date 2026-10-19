"""忘记密码和登录失败锁定的接口测试。库是 schema + seed 的空库。
用法：python e2e_reset.py <后端日志>"""
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


def codes_for(email, purpose):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l and purpose in l]
    return [re.findall(r"(?<!\d)(\d{6})(?!\d)", l)[-1] for l in lines]


def login(email, password):
    return call("POST", "/auth/login", {"email": email, "password": password})


call("POST", "/auth/register-code", {"email": "alice@test.local"})
s, r = call("POST", "/auth/register", {"email": "alice@test.local", "code": codes_for("alice@test.local", "注册")[-1],
                                       "password": "abc12345", "nickname": "alice"})
old_token = r["data"]["token"]
s, r = login("alice@test.local", "abc12345")
second_token = r["data"]["token"]

print("== 输错密码锁定")
for i in range(5):
    s, r = login("alice@test.local", "wrong-pass1")
check("输错 5 次都是\"邮箱或密码不正确\"", s == 400 and "不正确" in r.get("message", ""), (s, r))
s, r = login("alice@test.local", "abc12345")
check("锁住后密码对了也登录不了（429），提示忘记密码", s == 429 and "忘记密码" in r.get("message", ""), (s, r))
for i in range(5):
    login("nobody@test.local", "whatever1")
s, r = login("nobody@test.local", "whatever1")
check("没注册的邮箱一样会被锁（看不出注册过没有）", s == 429, (s, r))
s, r = login("ALICE@test.local", "abc12345")
check("邮箱大小写算同一个", s == 429, (s, r))

print("== 忘记密码")
s, r = call("POST", "/auth/reset-code", {"email": "nobody@test.local"})
check("没注册的邮箱：照样返回成功", s == 200, (s, r))
check("……但不发信", codes_for("nobody@test.local", "重置密码") == [])
s, r = call("POST", "/auth/reset-code", {"email": "alice@test.local"})
code = codes_for("alice@test.local", "重置密码")
check("注册过的邮箱：发了重置密码的验证码", s == 200 and len(code) == 1, (s, r, code))
s, r = call("POST", "/auth/reset-password", {"email": "alice@test.local", "code": "000000", "password": "newpass123"})
check("验证码不对（400）", s == 400 and "验证码" in r.get("message", ""), (s, r))
s, r = call("POST", "/auth/reset-password", {"email": "alice@test.local", "code": code[-1], "password": "12345678"})
check("新密码太弱（400）", s == 400, (s, r))
s, r = call("POST", "/auth/reset-password", {"email": "alice@test.local", "code": code[-1], "password": "newpass123"})
check("重设成功", s == 200, (s, r))
s, r = call("POST", "/auth/reset-password", {"email": "alice@test.local", "code": code[-1], "password": "another123"})
check("同一个验证码不能再用", s == 400, (s, r))

print("== 重设之后")
check("之前的两处登录都失效了", call("GET", "/users/me", token=old_token)[0] == 401
      and call("GET", "/users/me", token=second_token)[0] == 401)
s, r = login("alice@test.local", "abc12345")
check("旧密码不能登录", s == 400, (s, r))
s, r = login("alice@test.local", "newpass123")
check("新密码马上能登录（锁也解开了）", s == 200 and r["data"]["token"], (s, r))
s, r = call("POST", "/auth/reset-password", {"email": "nobody@test.local", "code": "123456", "password": "newpass123"})
check("没注册的邮箱重设：验证码已失效（400）", s == 400 and "验证码" in r.get("message", ""), (s, r))

errors = [l for l in open(LOG, encoding="utf-8", errors="replace") if " ERROR " in l]
check("后端没有 ERROR 日志", not errors, errors[:3])
print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
