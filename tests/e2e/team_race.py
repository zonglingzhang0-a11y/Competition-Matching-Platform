"""入队的并发测试：同时同意几个申请时不能超员、不能一人进两队，满员状态要算对。库是 schema + seed 的空库。
用法：python team_race.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import threading
import urllib.error
import urllib.parse
import urllib.request

API = env.API
LOG = sys.argv[1]
ROUNDS = 5
results = []


def call(method, path, body=None, token=None, params=None):
    url = API + path + ("?" + urllib.parse.urlencode(params) if params else "")
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def verified_user(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    edu = f"{name}@stu.example.edu.cn"
    call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200 and r["data"]["verifyStatus"] == "VERIFIED", r
    return token


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def race(fns):
    """几个请求同时出发，返回各自的 HTTP 状态码"""
    out = [None] * len(fns)
    barrier = threading.Barrier(len(fns))

    def run(i, fn):
        barrier.wait()
        out[i] = fn()[0]

    threads = [threading.Thread(target=run, args=(i, fn)) for i, fn in enumerate(fns)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    return out


def team_of(token, team):
    return call("GET", f"/teams/{team}", token=token)[1]["data"]


def apply(team, token):
    return call("POST", f"/teams/{team}/applications", {}, token)[1]["data"]


def accept(app, captain):
    return lambda: call("POST", f"/applications/{app}/accept", token=captain)


first = verified_user("race0")
edition = call("GET", "/competitions", token=first, params={"keyword": "计算机设计"})[1]["data"]["records"][0]["currentEdition"]["id"]

for n in range(1, ROUNDS + 1):
    print(f"== 第 {n} 轮")
    p, q, r_, s_, t_, o, u, v, w, x = (verified_user(f"race{n}{k}") for k in "pqrstouvwx")

    # 只剩 1 个名额，队长同时同意两个申请
    team = call("POST", "/teams", {"editionId": edition, "name": f"抢名额{n}", "maxMembers": 2}, p)[1]["data"]
    codes = race([accept(apply(team, q), p), accept(apply(team, r_), p)])
    d = team_of(p, team)
    check(f"只剩 1 个名额：一个成功一个失败，最终 2 人、满员 {codes}",
          sorted(codes) == [200, 400] and d["memberCount"] == 2 and d["status"] == "FULL", (codes, d["memberCount"], d["status"]))

    # 同一个人同时申请了两支队，两个队长同时同意
    team1 = call("POST", "/teams", {"editionId": edition, "name": f"甲队{n}"}, t_)[1]["data"]
    team2 = call("POST", "/teams", {"editionId": edition, "name": f"乙队{n}"}, o)[1]["data"]
    codes = race([accept(apply(team1, s_), t_), accept(apply(team2, s_), o)])
    joined = len(call("GET", "/teams/mine", token=s_)[1]["data"])
    check(f"一人两队：只进了一支 {codes}", sorted(codes)[0] == 200 and sorted(codes)[1] in (400, 409) and joined == 1,
          (codes, joined))

    # 还剩 2 个名额，同时同意两个申请：两个都进，队伍要变成满员，后面再同意的被拦
    team3 = call("POST", "/teams", {"editionId": edition, "name": f"两个名额{n}", "maxMembers": 3}, u)[1]["data"]
    late = apply(team3, w)
    codes = race([accept(apply(team3, v), u), accept(apply(team3, x), u)])
    d = team_of(u, team3)
    check(f"剩 2 个名额同时同意两个：都进了，状态是满员 {codes}",
          codes == [200, 200] and d["memberCount"] == 3 and d["status"] == "FULL", (codes, d["memberCount"], d["status"]))
    s, r = accept(late, u)()
    check("满员后再同意别人：被拦，不会超员", s in (400, 404, 409) and team_of(u, team3)["memberCount"] == 3, (s, r))

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
