"""里程碑下的分工待办：接口测试。
后端要用 --app.admin-emails[0]=admin@test.local --app.reminder.interval-ms=3000 --app.reminder.initial-delay-ms=1000 启动，
库是 schema + seed 的空库。用法：python e2e_tasks.py <后端日志>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

API = env.API
LOG = sys.argv[1]
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


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def code_for(email):
    lines = [l for l in open(LOG, encoding="utf-8", errors="replace") if email in l]
    return re.findall(r"(?<!\d)(\d{6})(?!\d)", lines[-1])[-1]


def register(name, verified=True):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    if verified:
        edu = f"{name}@stu.example.edu.cn"
        call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
        s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
        assert s == 200, r
    return token


def uid(token):
    return call("GET", "/users/me", token=token)[1]["data"]["id"]


def timeline(token):
    return call("GET", f"/teams/{team}/timeline", token=token)[1]["data"]


def tasks_of(token, milestone_id):
    return next(m for m in timeline(token)["milestones"] if m["id"] == milestone_id)["tasks"]


def task(token, milestone_id, task_id):
    return next(t for t in tasks_of(token, milestone_id) if t["id"] == task_id)


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%S")


cap = register("cap")
m1 = register("m1")
m2 = register("m2")
outsider = register("outsider")
cap_id, m1_id, m2_id, out_id = uid(cap), uid(m1), uid(m2), uid(outsider)
comps = call("GET", "/competitions", token=cap)[1]["data"]["records"]
E = next(x for x in comps if "数学建模" in x["name"])["currentEdition"]["id"]
team = call("POST", "/teams", {"editionId": E, "name": "分工测试队"}, cap)[1]["data"]
for t in (m1, m2):
    app = call("POST", f"/teams/{team}/applications", {}, t)[1]["data"]
    call("POST", f"/applications/{app}/accept", token=cap)
week = datetime.now() + timedelta(days=7)
s, r = call("POST", f"/teams/{team}/milestones", {"title": "精读往届论文", "startAt": iso(week), "allDay": False}, cap)
ms = r["data"]

print("== 时间线带上任务和队员")
tl = timeline(m1)
check("队员看到里程碑，任务为空", tl["milestones"][0]["tasks"] == [], tl["milestones"])
check("分任务用的队员名单：队长在前", [m["id"] for m in tl["members"]] == [cap_id, m1_id, m2_id]
      and tl["members"][0]["nickname"] == "cap", tl["members"])
tl = timeline(outsider)
check("不是队员：看不到里程碑，也没有队员名单", tl["milestones"] == [] and tl["members"] == [], tl)

print("== 加任务")
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "整理 2023 年 A 题", "assigneeId": m1_id}, m1)
t_self = r["data"]
check("队员加任务分给自己", s == 200, r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "整理 2022 年 A 题", "assigneeId": m2_id}, m1)
check("队员不能把任务分给别人（403）", s == 403 and "队长" in r["message"], r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "整理 2022 年 A 题", "assigneeId": None}, m1)
t_free = r["data"]
check("队员加任务先不分配", s == 200, r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "x", "assigneeId": None}, outsider)
check("不是队员不能加（403）", s == 403, r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "x", "assigneeId": out_id}, cap)
check("队长也只能分给现在的队员（400）", s == 400 and "现在的队员" in r["message"], r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "   ", "assigneeId": None}, cap)
check("任务名不能是空的（400）", s == 400, r)
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "写模型假设", "assigneeId": m2_id}, cap)
t_m2 = r["data"]
check("队长把任务分给 m2", s == 200, r)
n = notes(m2)[0]
check("m2 收到站内信", n["type"] == "TASK" and "写模型假设" in n["content"] and "精读往届论文" in n["content"]
      and n["refType"] == "TEAM" and n["refId"] == team, n)
before = len(notes(cap))
call("POST", f"/milestones/{ms}/tasks", {"title": "队长自己的", "assigneeId": cap_id}, cap)
check("分给自己不发通知", len(notes(cap)) == before)
lst = tasks_of(m1, ms)
check("时间线里的任务按添加顺序，带负责人名字", [t["title"] for t in lst] == ["整理 2023 年 A 题", "整理 2022 年 A 题", "写模型假设", "队长自己的"]
      and lst[0]["assigneeName"] == "m1" and lst[1]["assigneeId"] is None, lst)

print("== 认领和放弃")
s, r = call("POST", f"/tasks/{t_free}/claim", token=m2)
check("m2 认领没人负责的任务", s == 200 and task(m2, ms, t_free)["assigneeId"] == m2_id, r)
s, r = call("POST", f"/tasks/{t_free}/claim", token=m1)
check("已经有人负责的不能再认领（409）", s == 409, r)
s, r = call("POST", f"/tasks/{t_free}/claim", token=m2)
check("认领自己的任务：没事", s == 200, r)
s, r = call("POST", f"/tasks/{t_free}/release", token=m1)
check("不能放弃别人的任务（403）", s == 403, r)
s, r = call("POST", f"/tasks/{t_free}/release", token=m2)
check("m2 放弃，又变回没人负责", s == 200 and task(m2, ms, t_free)["assigneeId"] is None, r)

print("== 勾完成")
s, r = call("PUT", f"/tasks/{t_m2}/done", {"done": True}, m1)
check("不能勾别人负责的任务（403）", s == 403 and "m2" in r["message"], r)
s, r = call("PUT", f"/tasks/{t_m2}/done", {"done": True}, m2)
check("负责人勾完成", s == 200 and task(m1, ms, t_m2)["doneAt"], r)
s, r = call("PUT", f"/tasks/{t_m2}/done", {"done": False}, cap)
check("队长可以取消别人的完成", s == 200 and task(m1, ms, t_m2)["doneAt"] is None, r)
s, r = call("PUT", f"/tasks/{t_free}/done", {"done": True}, m1)
t = task(m1, ms, t_free)
check("没人负责的任务，队员直接勾完成就算他做的", s == 200 and t["doneAt"] and t["assigneeId"] == m1_id, t)

print("== 队长调整")
s, r = call("PUT", f"/tasks/{t_self}", {"title": "改名", "assigneeId": m1_id}, m1)
check("队员不能改（403）", s == 403, r)
before = len(notes(m2))
s, r = call("PUT", f"/tasks/{t_self}", {"title": "整理 2023 年 A、B 题", "assigneeId": m2_id}, cap)
t = task(m1, ms, t_self)
check("队长改名并改分给 m2", s == 200 and t["title"] == "整理 2023 年 A、B 题" and t["assigneeId"] == m2_id, t)
check("换了负责人，新负责人收到通知", len(notes(m2)) == before + 1)
call("PUT", f"/tasks/{t_self}", {"title": "整理 2023 年 A、B、C 题", "assigneeId": m2_id}, cap)
check("只改名、负责人没变：不再通知", len(notes(m2)) == before + 1)
s, r = call("DELETE", f"/tasks/{t_free}", token=m1)
check("队员不能删（403）", s == 403, r)
s, r = call("DELETE", f"/tasks/{t_free}", token=cap)
check("队长删任务", s == 200 and all(t["id"] != t_free for t in tasks_of(cap, ms)), r)

print("== 我的待办")
mine = call("GET", "/tasks/mine", token=m2)[1]["data"]
check("m2 的待办：两个没做完的，带队伍和里程碑", sorted(t["title"] for t in mine) == ["写模型假设", "整理 2023 年 A、B、C 题"]
      and all(t["teamName"] == "分工测试队" and t["milestoneTitle"] == "精读往届论文" and t["teamId"] == team for t in mine), mine)
call("PUT", f"/tasks/{t_m2}/done", {"done": True}, m2)
mine = call("GET", "/tasks/mine", token=m2)[1]["data"]
check("做完的不在待办里", [t["title"] for t in mine] == ["整理 2023 年 A、B、C 题"], mine)

print("== 抢着认领")
contested = call("POST", f"/milestones/{ms}/tasks", {"title": "抢着做的任务", "assigneeId": None}, cap)[1]["data"]
codes = []
barrier = threading.Barrier(2)


def grab(token):
    barrier.wait()
    codes.append(call("POST", f"/tasks/{contested}/claim", token=token)[0])


threads = [threading.Thread(target=grab, args=(tk,)) for tk in (m1, m2)]
[th.start() for th in threads]
[th.join() for th in threads]
check("两个人同时认领：只有一个成功", sorted(codes) == [200, 409], codes)

print("== 有人离队")
s, r = call("POST", f"/teams/{team}/leave", token=m2)
check("m2 退队", s == 200, r)
t = task(m1, ms, t_self)
check("m2 没做完的任务：显示原负责人已离队", t["assigneeLeft"] and t["assigneeName"] == "m2", t)
check("退队后 m2 的待办里没有这支队的任务了", call("GET", "/tasks/mine", token=m2)[1]["data"] == [])
s, r = call("POST", f"/tasks/{t_self}/claim", token=m1)
t = task(m1, ms, t_self)
check("m1 认领离队的人留下的任务", s == 200 and t["assigneeId"] == m1_id and not t["assigneeLeft"], (r, t))
s, r = call("POST", f"/milestones/{ms}/tasks", {"title": "x", "assigneeId": None}, m2)
check("离队的人不能再加任务（403）", s == 403, r)

print("== 上限")
for i in range(40):
    s, r = call("POST", f"/milestones/{ms}/tasks", {"title": f"凑数 {i}", "assigneeId": None}, cap)
    if s != 200:
        break
check("一个节点下最多 30 个任务", s == 400 and "30" in r["message"] and len(tasks_of(cap, ms)) == 30, (s, r))

print("== 里程碑快到时的提醒带上自己没做完的任务")
soon = datetime.now() + timedelta(hours=3)
ms2 = call("POST", f"/teams/{team}/milestones", {"title": "交初稿", "startAt": iso(soon), "allDay": False}, cap)[1]["data"]
call("POST", f"/milestones/{ms2}/tasks", {"title": "写摘要", "assigneeId": m1_id}, cap)
done_task = call("POST", f"/milestones/{ms2}/tasks", {"title": "画流程图", "assigneeId": m1_id}, cap)[1]["data"]
call("PUT", f"/tasks/{done_task}/done", {"done": True}, m1)
reminder = None
for _ in range(20):
    reminder = next((n for n in notes(m1) if n["title"] == "队伍节点快到了" and "交初稿" in (n["content"] or "")), None)
    if reminder:
        break
    time.sleep(1)
check("m1 的提醒里写着还没完成的「写摘要」，不提已完成的", reminder and "你负责的「写摘要」还没完成" in reminder["content"]
      and "画流程图" not in reminder["content"], reminder)
cap_reminder = next((n for n in notes(cap) if n["title"] == "队伍节点快到了" and "交初稿" in (n["content"] or "")), None)
check("队长自己在这一项下没有任务：提醒里不提任务", cap_reminder and "你负责的" not in cap_reminder["content"], cap_reminder)

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
