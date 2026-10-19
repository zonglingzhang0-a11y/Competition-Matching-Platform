"""M5 接口端到端测试：个人日程、日历三图层、撞期、队伍时间线、定时提醒、推荐里的个人日程冲突。
后端要用 --app.reminder.interval-ms=3000 --app.reminder.initial-delay-ms=1000 启动。"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

API = env.API
LOG = sys.argv[1]
results = []


def sql(statement):
    subprocess.run([*env.mysql_args(), env.DB,
                    "-e", statement], check=True, stdin=subprocess.DEVNULL)


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


def user(name):
    email = f"{name}@test.local"
    call("POST", "/auth/register-code", {"email": email})
    s, r = call("POST", "/auth/register", {"email": email, "code": code_for(email), "password": "abc12345", "nickname": name})
    assert s == 200, r
    token = r["data"]["token"]
    edu = f"{name}@stu.example.edu.cn"
    call("POST", "/verify/edu/code", {"eduEmail": edu}, token)
    s, r = call("POST", "/verify/edu/confirm", {"eduEmail": edu, "code": code_for(edu)}, token)
    assert s == 200, r
    return token


# 示例届次的日期按导入当天往后排（seed.sql）：比赛第 17–20 天，成绩最晚第 55 天
TODAY = datetime.now().date()
def day(n):
    return (TODAY + timedelta(days=n)).isoformat()


def calendar(token, frm=day(-30), to=day(60)):
    s, r = call("GET", "/calendar", token=token, params={"from": frm, "to": to})
    assert s == 200, r
    return r["data"]


def notes(token):
    return call("GET", "/notifications", token=token, params={"size": 50})[1]["data"]["records"]


iso = lambda d: d.strftime("%Y-%m-%dT%H:%M:%S")

print("== 准备")
cap, mem, out = user("cap"), user("mem"), user("out")
s, comps = call("GET", "/competitions", token=cap, params={"keyword": "数学建模"})
comp_id = comps["data"]["records"][0]["id"]
E = comps["data"]["records"][0]["currentEdition"]["id"]
team = call("POST", "/teams", {"editionId": E, "name": "日历队"}, cap)[1]["data"]
app_id = call("POST", f"/teams/{team}/applications", {}, mem)[1]["data"]
call("POST", f"/applications/{app_id}/accept", token=cap)

print("== 个人日程")
s, r = call("POST", "/events", {"title": "坏的", "startAt": "2026-10-20T10:00:00", "endAt": "2026-10-20T09:00:00"}, mem)
check("结束早于开始（400）", s == 400, r)
s, r = call("POST", "/events", {"title": "每周", "startAt": "2026-10-12T19:00:00", "repeatType": "WEEKLY", "repeatWeekdays": []}, mem)
check("每周重复不选星期（400）", s == 400, r)
s, r = call("POST", "/events", {"title": "健身", "startAt": "2026-10-12T19:00:00", "endAt": "2026-10-12T20:00:00",
                                 "repeatType": "WEEKLY", "repeatWeekdays": [3, 1], "repeatUntil": "2026-10-31", "busy": False}, mem)
check("每周一三健身", s == 200, r)
gym = r["data"]
cal = calendar(mem, "2026-10-12", "2026-10-25")
gym_days = sorted(e["occurrenceDate"] for e in cal["events"] if e["refId"] == gym and e["layer"] == "PERSONAL")
check("两周里出现 4 次（周一、周三）", gym_days == ["2026-10-12", "2026-10-14", "2026-10-19", "2026-10-21"], gym_days)
s, r = call("POST", f"/events/{gym}/skip", token=mem, params={"date": "2026-10-14"})
gym_days = sorted(e["occurrenceDate"] for e in calendar(mem, "2026-10-12", "2026-10-25")["events"] if e["refId"] == gym and e["layer"] == "PERSONAL")
check("只删 10-14 那一次", s == 200 and "2026-10-14" not in gym_days and len(gym_days) == 3, gym_days)
s, r = call("GET", f"/events/{gym}", token=mem)
check("读回来星期是排好序的 [1,3]", r["data"]["repeatWeekdays"] == [1, 3], r["data"])
s, r = call("GET", f"/events/{gym}", token=cap)
check("看不到别人的日程（404）", s == 404, r)
s, r = call("POST", "/events", {"title": "社团换届", "startAt": day(18) + "T14:00:00", "endAt": day(18) + "T16:00:00", "busy": True}, mem)
clash_id = r["data"]

print("== 日历三图层和撞期")
call("POST", f"/editions/{E}/follow", token=mem)
cal = calendar(mem)
layers = {e["layer"] for e in cal["events"]}
check("三类都有：官方、个人（队伍节点还没加）", {"OFFICIAL", "PERSONAL"} <= layers, layers)
official = [e for e in cal["events"] if e["layer"] == "OFFICIAL"]
check("官方节点 3 个，带比赛简称", len(official) == 3 and all(e["layerName"] == "国赛" for e in official), [(e["layerName"], e["title"]) for e in official])
result = next(e for e in official if e["title"] == "成绩公布")
check("成绩公布是时间未定，显示到最晚那天", result["fuzzy"] and result["end"].startswith(day(55)), result)
contest = next(e for e in official if e["busy"])
clash = next(e for e in cal["events"] if e["refId"] == clash_id and e["layer"] == "PERSONAL")
check("社团换届和比赛撞期，两边都标出来", clash["conflictWith"] and contest["conflictWith"], (clash, contest))
check("撞期列表里有一条", len(cal["conflicts"]) == 1 and "社团换届" in cal["conflicts"][0]["first"], cal["conflicts"])
gym_e = next(e for e in cal["events"] if e["refId"] == gym and e["layer"] == "PERSONAL")
check("不参与撞期检测的健身没被标", gym_e["conflictWith"] is None)
s, r = call("GET", "/calendar", token=mem, params={"from": "2026-01-01", "to": "2026-12-31"})
check("一次查太长（400）", s == 400, r)

print("== 队伍时间线")
ms_form = {"title": "精读往届论文", "startAt": "2026-10-18T19:00:00", "endAt": "2026-10-18T21:00:00", "note": "每人读两篇"}
s, r = call("POST", f"/teams/{team}/milestones", ms_form, mem)
check("队员不能加节点（403）", s == 403, r)
before = len([n for n in notes(mem) if n["type"] == "MILESTONE"])
s, r = call("POST", f"/teams/{team}/milestones", ms_form, cap)
check("队长加节点", s == 200, r)
ms = r["data"]
check("队员收到时间线通知", len([n for n in notes(mem) if n["type"] == "MILESTONE"]) == before + 1)
cal = calendar(mem)
team_events = [e for e in cal["events"] if e["layer"] == "TEAM"]
check("队员日历里出现队伍节点", len(team_events) == 1 and team_events[0]["layerName"] == "日历队", team_events)
s, r = call("GET", f"/teams/{team}/timeline", token=mem)
tl = r["data"]
check("时间线：3 个官方节点 + 1 个队伍节点，队员不能编辑", len(tl["official"]) == 3 and len(tl["milestones"]) == 1 and tl["member"] and not tl["canEdit"], tl)
s, r = call("GET", f"/teams/{team}/timeline", token=out)
check("非队员只看到官方节点", len(r["data"]["official"]) == 3 and r["data"]["milestones"] == [] and not r["data"]["member"])
s, r = call("PUT", f"/milestones/{ms}/done", {"done": True}, mem)
check("队员勾完成", s == 200, r)
check("日历里显示已完成", next(e for e in calendar(mem)["events"] if e["layer"] == "TEAM")["done"] is True)
s, r = call("PUT", f"/milestones/{ms}/done", {"done": True}, out)
check("非队员不能勾（403）", s == 403, r)
s, r = call("PUT", f"/milestones/{ms}", {**ms_form, "title": "精读往届论文（改）"}, cap)
check("队长修改节点", s == 200 and calendar(mem)["events"] and any(e["title"] == "精读往届论文（改）" for e in calendar(mem)["events"]))

print("== 定时提醒（后端每 3 秒检查一次）")
now = datetime.now()
sql(f"UPDATE competition_edition SET registration_end = '{iso(now + timedelta(hours=30))}' WHERE id = {E}")
soon = call("POST", f"/teams/{team}/milestones", {"title": "交初稿", "startAt": iso(now + timedelta(hours=5))}, cap)[1]["data"]
sql(f"UPDATE competition_node SET start_at = '{iso(now + timedelta(hours=20))}', end_at = '{iso(now + timedelta(hours=92))}' "
    f"WHERE edition_id = {E} AND busy = 1")
time.sleep(8)
kinds = lambda tok: [n["title"] for n in notes(tok)]
mem_titles = kinds(mem)
check("报名截止提醒（72 小时那档）", any("报名快截止了" in t for t in mem_titles), mem_titles)
check("比赛开始提醒", any("快开始了" in t for t in mem_titles), mem_titles)
check("里程碑到期提醒（队员和队长都有）", "队伍节点快到了" in mem_titles and "队伍节点快到了" in kinds(cap))
count1 = len(notes(mem))
time.sleep(7)
check("再跑几轮也不重复提醒", len(notes(mem)) == count1, (count1, len(notes(mem))))
sql(f"UPDATE competition_edition SET registration_end = '{iso(now + timedelta(hours=10))}' WHERE id = {E}")
time.sleep(7)
deadline = [n for n in notes(mem) if "报名快截止了" in n["title"]]
check("进入 24 小时那档，再提醒一次（共 2 条）", len(deadline) == 2, [n["content"] for n in deadline])
check("提醒点开能跳到比赛页", deadline[0]["refType"] == "COMPETITION" and deadline[0]["refId"] == comp_id, deadline[0])
check("未关注也没参加的 out 没收到", not any("报名快截止了" in t for t in kinds(out)))

print("== 推荐里的个人日程冲突")
# 比赛时段已被挪到 now+20h ~ now+92h，给 seeker 加一个落在里面的个人日程
seeker = user("seeker")
call("PUT", "/survey/general", {"answers": {"G4": 18724, "G11": ["missing"]}, "quick": True}, seeker)
s, r = call("GET", f"/editions/{E}/recommended-teams", token=seeker)
base_score = next(i["match"]["score"] for i in r["data"]["items"] if i["team"]["id"] == team) if r["data"]["items"] else None
call("POST", "/events", {"title": "期中考试", "startAt": iso(now + timedelta(hours=30)), "endAt": iso(now + timedelta(hours=32)), "busy": True}, seeker)
s, r = call("GET", f"/editions/{E}/recommended-teams", token=seeker)
item = next((i for i in r["data"]["items"] if i["team"]["id"] == team), None)
check("推荐里提醒比赛期间有个人日程，分数降低",
      item and "比赛期间你有个人日程「期中考试」" in item["match"]["warnings"] and base_score and item["match"]["score"] < base_score,
      (base_score, item and item["match"]))

print()
print(f"一共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
