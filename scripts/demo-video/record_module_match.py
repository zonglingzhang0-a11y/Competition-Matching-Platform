"""模块演示动画：匹配推荐（约 45 秒）。
林晓的主页：镜头推近匹配六边形、叠上另一支队伍对比、看推荐理由和总分；
讲解动画用接口取到的真实分数算一遍（五个维度 × 这个比赛用的权重 → 完整度 → 投入保障 → 个人日程系数），
和接口返回的分数对不上就不录。只读数据，不改演示数据。
用法：python scripts/demo-video/record_module_match.py"""
import json
import re
import sys

from playwright.sync_api import sync_playwright

from module_common import Module, api, login

student, admin = login("linxiao"), login("admin")
edition, match = next((e["edition"], t["match"]) for e in api("GET", "/recommendations/home", student)
                      for t in e["teams"] if t["team"]["name"] == "数模冲刺队")
preset = next(c["matchPreset"] for c in api("GET", "/admin/competitions?size=50", admin)["records"]
              if c["id"] == edition["competitionId"]) or "DEFAULT"
configs = {p["preset"]: p["config"] if isinstance(p["config"], dict) else json.loads(p["config"])
           for p in api("GET", "/admin/match-configs", admin)["presets"]}
weights = {d["key"]: d["weight"] for d in configs["DEFAULT"]["dimensions"]}
weights.update(configs.get(preset, {}).get("dimensionWeights") or {})
dims = [{"name": d["name"], "score": d["score"], "weight": weights[d["key"]]}
        for d in match["dimensions"] if d["score"] is not None]
weighted = sum(d["score"] * d["weight"] for d in dims) / sum(d["weight"] for d in dims)
comp, k = match["completeness"], match["k"]
personal = next((w for w in match["warnings"] if "个人日程" in w), None)
factor = 0.9 if personal else 1.0
expected = (50 + (weighted - 50) * comp) * k * factor
if abs(expected - match["score"]) > 0.15:
    sys.exit(f"按引擎算法重算是 {expected:.2f}，接口返回 {match['score']}，对不上，不录")
event = re.search(r"「(.+?)」", personal).group(1) if personal else None
preset_name = {"SHORT_INTENSIVE": "短期高强度", "LONG_TERM": "长期", "DEFAULT": "默认"}[preset]
shown = round(match["score"])
m = Module("匹配推荐", {
    "eyebrow": f"林晓 × 数模冲刺队 · {edition['competitionName']}用「{preset_name}」这套权重",
    "heading": f"{shown} 分是怎么算出来的",
    "dims": dims, "weighted": round(weighted, 1), "sumHint": "五个维度的分 × 权重",
    "chips": [
        {"title": "问卷填全了" if comp >= 1 else f"问卷完整度 {comp:.0%}",
         "text": "完整度 100%，不往 50 分收" if comp >= 1 else "数据少，往 50 分收"},
        {"title": f"× 投入保障 {k:.1f}", "text": "信用分和备赛安排都没有要打折的" if k >= 1 else "信用分或备赛安排打了折"},
    ] + ([{"title": "× 0.9", "text": f"比赛期间林晓有个人日程「{event}」", "red": True}] if personal else []),
    "score": match["score"],
    "totalCaption": f"页面上显示 {shown} 分，和系统算出来的一致",
})

with sync_playwright() as p:
    m.launch(p)
    r_title = m.chapter("张宗令", "匹配推荐：每条推荐都说得出理由")

    page = m.site_page("linxiao")
    rec = m.begin(page, "ui")
    rec.say("林晓还没组队，主页右边是为她推荐的队伍。", "窗口全景，镜头推进到右边的推荐")
    rec.wait(1.3)
    hexa = page.locator("figure.hexagon").first
    rec.move_to(hexa, seconds=0.8)
    rec.cam(hexa, zoom=1.7, seconds=1.0, dy=40)
    rec.wait(1.0)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "匹配六边形<small>五个维度，加上投入保障</small>", side="left")
    rec.say("六边形是匹配画像，点一下还能叠上另一支队伍对比。", "聚光灯圈出六边形，点「叠上去对比」")
    rec.wait(1.6)
    rec.clear_notes()
    rec.click(page.locator(".compare button", has_text="计设作品组"))
    rec.wait(0.7)
    rec.spot(hexa, pad=10)
    rec.note(hexa, "虚线是计设作品组<small>差在哪一眼就能看出来</small>", side="left")
    rec.say(f"每条推荐都写清了理由，总分 {shown} 分。", f"镜头移到数模冲刺队的卡片，圈出 {shown} 分")
    rec.wait(0.9)
    rec.clear_notes()
    rec.event("whoosh")
    rec.scroll(260, seconds=0.8)
    card = page.locator(".rec").first
    rec.cam(card, zoom=1.7, seconds=0.8, dy=10, sound=False)
    rec.wait(0.8)
    rec.spot(card, pad=8)
    rec.note(card, f"总分 {shown}，附推荐理由<small>这 {shown} 分是怎么来的？</small>", side="left")
    rec.close_caption()
    rec.wait(0.3)
    rec.clear_notes()
    rec.cam(None, seconds=0.8)
    m.end(rec, 0.8)

    page = m.card_page()
    r_x = m.begin(page, "explain")
    r_x.say(f"五个维度按{preset_name}比赛的权重加起来，合计 {weighted:.1f}。", "讲解动画：五个维度逐行出来，乘权重，得出加权合计")
    page.evaluate("demo.explain()")
    r_x.wait(0.7)
    page.evaluate("demo.rows()")
    for i in range(len(dims)):
        r_x.event("tick", i * 0.38)
    r_x.wait(2.7)
    page.evaluate("demo.sum()")
    r_x.event("pop", 0.9)
    tail = f"比赛期间有{event}再乘 0.9，" if personal else ""
    r_x.say(f"问卷填全、投入保障不打折；{tail}就是 {shown} 分。", f"讲解动画：右边逐个弹出系数，最后得出 {match['score']}")
    page.evaluate("demo.chips()")
    for i in range(len(m.data["chips"])):
        r_x.event("pop", i * 0.45 + 0.05)
    r_x.wait(1.6)
    page.evaluate("demo.total()")
    r_x.event("whoosh")
    r_x.event("ding", 1.2)
    m.end(r_x, 1.2)
    m.browser.close()

m.render([(r_title, False), (rec, True), (r_x, False)], ["zoom", "wipe"], facts=[
    *[f"{d['name']} {d['score']:g} × 权重 {d['weight']}" for d in dims],
    f"加权合计 {weighted:.1f}；完整度 {comp:g}；投入保障 {k:g}；个人日程系数 {factor}；"
    f"得 {expected:.2f}，接口返回 {match['score']}（{edition['competitionName']}用「{preset_name}」权重）"])
