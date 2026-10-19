"""截图 OCR 认证的接口测试。图片在 fixtures/ocr（用 PIL 画的虚构信息），OCR 是真跑的。
后端要用 --app.admin-emails[0]=admin@test.local --app.screenshot.upload-dir=<UPLOADS> 启动，库是 schema + seed。
用法：python ocr.py <后端日志> <上传目录>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import uuid
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


API = env.API
LOG = sys.argv[1]
UPLOADS = Path(sys.argv[2])
IMG = Path(__file__).parent / "fixtures" / "ocr"
results = []


def call(method, path, body=None, token=None, params=None, raw=False):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            payload = res.read()
            return res.status, (payload, res.headers.get("Content-Type")) if raw else json.loads(payload.decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


def upload(token, file_bytes, filename, real_name, school, year):
    boundary = uuid.uuid4().hex
    parts = []
    for k, v in (("realName", real_name), ("schoolName", school), ("enrollYear", str(year))):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{k}\"\r\n\r\n{v}\r\n".encode("utf-8"))
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\n"
                 f"Content-Type: application/octet-stream\r\n\r\n".encode("utf-8") + file_bytes + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    req = urllib.request.Request(API + "/verify/screenshot", data=b"".join(parts), method="POST",
                                 headers={"Content-Type": f"multipart/form-data; boundary={boundary}",
                                          "Authorization": "Bearer " + token})
    try:
        with urllib.request.urlopen(req, timeout=60) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))
    except (ConnectionError, urllib.error.URLError) as e:
        return 0, {"message": str(e)}


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    return r["data"]["token"]


def me(token):
    return call("GET", "/users/me", token=token)[1]["data"]


def files():
    d = UPLOADS / "verify"
    return sorted(p.name for p in d.iterdir()) if d.exists() else []


def pending(admin):
    return call("GET", "/admin/verifications", token=admin, params={"status": "PENDING"})[1]["data"]["records"]


read = lambda p: (IMG / p).read_bytes()

admin = register("admin")

print("== 提交和识别")
s1 = register("s1")
s, r = upload(s1, read("t_lin.png"), "学籍.png", "林小雨", "示例大学", 2023)
check("学信网截图：三项都认出来了（HIGH）", s == 200 and r["data"]["confidence"] == "HIGH"
      and r["data"]["matched"] == {"realName": True, "schoolName": True, "enrollYear": True}, r)
check("没认证过的人变成待确认", me(s1)["verifyStatus"] == "PENDING")
latest = call("GET", "/verify/latest", token=s1)[1]["data"]
check("最近一次申请是截图认证、在等审核", latest["status"] == "PENDING" and latest["method"] == "SCREENSHOT"
      and latest["eduEmail"] is None, latest)
check("原图存下来了", len(files()) == 1, files())

s2 = register("s2")
s, r = upload(s2, read("t_zhao.jpg"), "card.jpg", "王大锤", "示例大学", 2024)
check("学生证照片、姓名填得不对：记为有出入（LOW），点名是姓名", s == 200 and r["data"]["confidence"] == "LOW"
      and r["data"]["matched"]["realName"] is False and r["data"]["matched"]["schoolName"] is True
      and "姓名" in r["data"]["message"], r)

print("== 管理员审核")
items = pending(admin)
it = next(i for i in items if i["user"]["nickname"] == "s1")
shot = it["screenshot"]
check("列表里是截图认证，带预审结果", it["method"] == "SCREENSHOT" and shot["confidence"] == "HIGH"
      and shot["realName"] == "林小雨" and shot["hasImage"] and it["domains"] == [], it)
check("认出来的文字给管理员看", "林小雨" in shot["lines"] and any("2023" in l for l in shot["lines"]), shot["lines"])
check("图里出现了已收录的示例大学，默认选它", shot["suggestedSchool"] and shot["suggestedSchool"]["name"] == "示例大学", shot)
s, (img, ctype) = call("GET", f"/admin/verifications/{it['id']}/image", token=admin, raw=True)
check("管理员能看原图，内容就是上传的那张", s == 200 and ctype.startswith("image/png") and img == read("t_lin.png"), (s, ctype))
s, r = call("GET", f"/admin/verifications/{it['id']}/image", token=s2)
check("学生看不了别人的原图（403）", s == 403, s)
s, r = call("POST", f"/admin/verifications/{it['id']}/approve", {"schoolId": shot["suggestedSchool"]["id"]}, admin)
check("通过（截图认证不用选邮箱后缀）", s == 200 and r["data"]["schoolName"] == "示例大学", r)
m = me(s1)
check("s1 已认证为示例大学", m["verifyStatus"] == "VERIFIED" and m["school"]["name"] == "示例大学", m)
s, r = call("GET", f"/admin/verifications/{it['id']}/image", token=admin)
check("审核完原图删掉了（404）", s == 404, r)
check("磁盘上也删了", len(files()) == 1, files())
profile = call("GET", "/profile", token=s1)[1]["data"]
check("填的姓名和入学年份存进了档案", profile["realName"] == "林小雨" and profile["enrollYear"] == 2023, profile)

print("== 重新提交、新学校、驳回")
s, r = upload(s2, read("t_sun.png"), "x.png", "孙一帆", "新晴理工大学", 2022)
check("s2 改好重新提交：HIGH", s == 200 and r["data"]["confidence"] == "HIGH", r)
mine = [i for i in pending(admin) if i["user"]["nickname"] == "s2"]
check("旧的那条作废，只留新的一条", len(mine) == 1 and mine[0]["screenshot"]["schoolName"] == "新晴理工大学", mine)
check("旧图删掉了，只剩新图", len(files()) == 1, files())
check("没收录的学校没有默认学校", mine[0]["screenshot"]["suggestedSchool"] is None)
s, r = call("POST", f"/admin/verifications/{mine[0]['id']}/approve", {"newSchoolName": "新晴理工大学"}, admin)
check("新建学校并通过", s == 200 and me(s2)["school"]["name"] == "新晴理工大学", (s, r))
check("磁盘上已经没有图了", files() == [], files())

s3 = register("s3")
upload(s3, read("t_lin.png"), "a.png", "林小雨", "示例大学", 2023)
rid = next(i["id"] for i in pending(admin) if i["user"]["nickname"] == "s3")
s, r = call("POST", f"/admin/verifications/{rid}/reject", {"note": "截图里的人不是你"}, admin)
check("驳回：变回未认证，原图删掉", s == 200 and me(s3)["verifyStatus"] == "NONE" and files() == [], (s, files()))

print("== 已认证的人、和邮箱认证互相作废")
v = register("v")
call("POST", "/verify/edu/code", {"eduEmail": "v@stu.example.edu.cn"}, v)
call("POST", "/verify/edu/confirm", {"eduEmail": "v@stu.example.edu.cn", "code": code_for("v@stu.example.edu.cn")}, v)
s, r = upload(v, read("t_sun.png"), "x.png", "孙一帆", "新晴理工大学", 2022)
check("已认证的人提交截图：保留原认证", s == 200 and r["data"]["verifyStatus"] == "VERIFIED"
      and "保留原来的认证" in r["data"]["message"] and me(v)["verifyStatus"] == "VERIFIED", r)
call("POST", "/verify/edu/code", {"eduEmail": "v@mail.other.edu.cn"}, v)
call("POST", "/verify/edu/confirm", {"eduEmail": "v@mail.other.edu.cn", "code": code_for("v@mail.other.edu.cn")}, v)
mine = [i for i in pending(admin) if i["user"]["nickname"] == "v"]
check("再用邮箱认证：截图那条作废、原图删掉", len(mine) == 1 and mine[0]["method"] == "EDU_EMAIL" and files() == [],
      (mine, files()))

print("== 校验")
s4 = register("s4")
s, r = upload(s4, read("t_lin.png"), "a.png", " ", "示例大学", 2023)
check("姓名没填（400）", s == 400 and "姓名" in r["message"], r)
s, r = upload(s4, read("t_lin.png"), "a.png", "林小雨", "示例大学", 1980)
check("入学年份不对（400）", s == 400, r)
s, r = upload(s4, "这不是图片".encode("utf-8") * 10, "a.png", "林小雨", "示例大学", 2023)
check("不是图片，改了后缀也不行（400）", s == 400 and "jpg、png" in r["message"], r)
s, r = upload(s4, b"\x89PNG\r\n\x1a\n" + b"0" * (6 * 1024 * 1024), "big.png", "林小雨", "示例大学", 2023)
check("超过 5 MB（400）", s == 400 and "5 MB" in r["message"], (s, r))
for i in range(3):
    upload(s4, read("t_lin.png"), "a.png", "林小雨", "示例大学", 2023)
s, r = upload(s4, read("t_lin.png"), "a.png", "林小雨", "示例大学", 2023)
check("一天最多 3 次（第 4 次 429）", s == 429, r)
check("同一个人多次提交，磁盘上只留最新一张", len(files()) == 1, files())

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
