"""数据库迁移（Flyway）：全新的库、各个发布版本建的旧库、跑了一部分升级脚本的旧库，启动后端后都升到最新，
表结构和全新建的一模一样，数据不丢。自己重置测试库、自己起停测试后端。
旧版本的建表脚本从 git 标签里取（git show v1.0.0:sql/schema.sql），本地没有标签时跳过那一部分。
用法：python migrate.py <日志目录>"""
import env  # 公共环境：库、端口、mysql 客户端，见 env.py
import os
import subprocess
import sys

LOGDIR = sys.argv[1] if len(sys.argv) > 1 else str(env.HERE / "logs")
results = []
# 迁移文件的编号（V1、V2……），新加了文件这里自动跟着变
VERSIONS = sorted((f.name[1:].split("__")[0] for f in env.MIGRATIONS.glob("V*__*.sql")), key=int)
FULL = [f"{v}\t1" for v in VERSIONS]                                     # 全新的库：从 V1 执行到最后
BASELINED = ["2\t1"] + [f"{v}\t1" for v in VERSIONS if int(v) > 2]       # 手工建的旧库：记成 V2，从 V3 往后执行
LATEST = VERSIONS[-1]


def check(name, cond, detail=""):
    results.append(bool(cond))
    print(("  通过  " if cond else "  失败  ") + name + (f"   [{detail}]" if detail and not cond else ""))


def start_stop(name):
    """起一次后端（启动时跑迁移）再关掉，返回日志内容"""
    log = os.path.join(LOGDIR, f"migrate_{name}.log")
    env.stop_backend(env.start_backend(log))
    return open(log, encoding="utf-8", errors="replace").read()


def errors(text):
    return [l for l in text.splitlines() if " ERROR " in l]


def history():
    return env.sql("SELECT version, success FROM flyway_schema_history ORDER BY installed_rank").splitlines()


def snapshot():
    """表结构：字段（类型、能不能空、默认值、顺序）、索引、外键。注释不比（个别字段的注释后来改过措辞）"""
    cols = env.sql(f"""SELECT TABLE_NAME, ORDINAL_POSITION, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE,
                              IFNULL(COLUMN_DEFAULT, '~'), EXTRA
                       FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = '{env.DB}'
                       AND TABLE_NAME <> 'flyway_schema_history' ORDER BY 1, 2""")
    idx = env.sql(f"""SELECT TABLE_NAME, INDEX_NAME, NON_UNIQUE, SEQ_IN_INDEX, COLUMN_NAME, IFNULL(SUB_PART, 0)
                      FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = '{env.DB}'
                      AND TABLE_NAME <> 'flyway_schema_history' ORDER BY 1, 2, 4""")
    fks = env.sql(f"""SELECT TABLE_NAME, CONSTRAINT_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                      FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = '{env.DB}'
                      AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY 1, 2""")
    return cols + "\n#\n" + idx + "\n#\n" + fks


def diff(a, b):
    sa, sb = set(a.splitlines()), set(b.splitlines())
    return {"少了": sorted(sa - sb)[:5], "多了": sorted(sb - sa)[:5]}


def git_show(tag, path):
    r = subprocess.run(["git", "-C", str(env.ROOT), "show", f"{tag}:{path}"], capture_output=True)
    return r.stdout.decode("utf-8") if r.returncode == 0 else None


print("一、全新的库（库都没有，后端自己建库、建表、填初始数据）")
env.drop()
text = start_stop("fresh")
check(f"V1 到 V{LATEST} 依次执行", history() == FULL, history())
# 学校：示例大学 + V12 湖南 34 所 + V13 985/211 112 所；比赛：5 个种子 + V14 中南大学 2019 版清单 90 个 + V15 2021 版清单新增 35 个
# 届次：V2 的示例届次之外，V15 按官方通知建了 15 个（直接发布）
check("初始数据在：技能字典、示例学校和收录的 146 所学校、130 个比赛（其中 V15 改名 8 个）、官方届次 15 个、抓取来源 4 个",
      env.sql("SELECT COUNT(*) FROM skill") != "0"
      and env.sql("SELECT COUNT(*) FROM school WHERE name = '示例大学'") == "1" and env.sql("SELECT COUNT(*) FROM school") == "147"
      and env.sql("SELECT COUNT(*) FROM competition") == "130" and env.sql("SELECT COUNT(*) FROM crawl_source") == "4"
      and env.sql("SELECT COUNT(*) FROM competition WHERE name IN ('中国大学生工程实践与创新能力大赛', '中国制冷学会创新大赛')") == "2"
      and env.sql("SELECT COUNT(*) FROM competition WHERE name IN ('全国大学生工程训练综合能力竞赛', '中国制冷协会创新大赛')") == "0"
      and env.sql("SELECT COUNT(*) FROM competition_edition WHERE status = 'PUBLISHED' AND title NOT LIKE '%示例数据%'") == "15",
      [env.sql("SELECT COUNT(*) FROM school"), env.sql("SELECT COUNT(*) FROM competition"),
       env.sql("SELECT COUNT(*) FROM competition_edition WHERE status = 'PUBLISHED' AND title NOT LIKE '%示例数据%'")])
check("比赛官网地址补上了（V4 的 5 个种子比赛）", env.sql(
    "SELECT COUNT(*) FROM competition WHERE official_url LIKE 'https://%' AND name IN ('全国大学生数学建模竞赛', "
    "'全国大学生电子设计竞赛', '中国国际大学生创新大赛', '\"挑战杯\"全国大学生课外学术科技作品竞赛', '中国大学生计算机设计大赛')") == "5")
check("没有 ERROR 日志", not errors(text), errors(text)[:3])
FRESH = snapshot()
parts = [len(x.splitlines()) for x in FRESH.split("\n#\n")]
check("表结构快照取到了（字段、索引、外键都有几十上百条，比较才有意义）", parts[0] > 300 and parts[1] > 100 and parts[2] > 50, parts)
text = start_stop("fresh_again")
check("再启动一次：没有要执行的迁移", history() == FULL and "is up to date" in text)

print("二、旧库补齐（V3）可以重复执行")
env.sql("DELETE FROM flyway_schema_history WHERE version = '3'")
text = start_stop("rerun_v3")
check("只删掉 V3 的记录再启动（排在 V4 后面补执行）：不报错，表结构不变", sorted(history(), key=lambda x: int(x.split()[0])) == FULL
      and snapshot() == FRESH and not errors(text), diff(FRESH, snapshot()))
check("抓取来源没有重复插入", env.sql("SELECT COUNT(*) FROM crawl_source") == "4")

print("三、手工导入 V1、V2 建的库（没有迁移记录）")
env.reset(demo=True)
users = env.sql("SELECT COUNT(*) FROM sys_user")
text = start_stop("manual")
check(f"记成已经到 V2，接着执行 V3 到 V{LATEST}", history() == BASELINED, history())
check("表结构和全新的一样", snapshot() == FRESH, diff(FRESH, snapshot()))
check("演示数据都在", env.sql("SELECT COUNT(*) FROM sys_user") == users)

print("四、各个发布版本建的旧库")
upgrades = {name: git_show("v2.2.0", f"sql/upgrade/{name}") for name in
            ["2026-10-crawler.sql", "2026-10-calendar-feed.sql", "2026-10-login-store.sql"]}
cases = [
    ("v1.0.0", []),
    ("v1.0.0", ["2026-10-crawler.sql", "2026-10-calendar-feed.sql", "2026-10-login-store.sql"]),
    ("v2.0.0", []),
    ("v2.1.0", []),
    ("v2.2.0", []),
]
for tag, scripts in cases:
    files = [git_show(tag, f"sql/{n}.sql") for n in ("schema", "seed", "demo")]
    label = tag + ("，跑过 " + "、".join(s[8:-4] for s in scripts) if scripts else "")
    if None in files or any(upgrades[s] is None for s in scripts):
        if os.environ.get("GITHUB_ACTIONS"):
            check(f"{label}：拿到旧版本的建表脚本（CI 要 fetch-depth: 0）", False)
        else:
            print(f"  跳过  {label}：本地没有这个标签（git fetch --tags 后再跑）")
        continue
    env.drop()
    env.create()
    for name, content in zip(("schema", "seed", "demo"), files):
        env.load(f"{tag}:sql/{name}.sql", content)
    for s in scripts:
        env.load(s, upgrades[s])
    before = {t: env.sql(f"SELECT COUNT(*) FROM {t}") for t in ("sys_user", "team", "notification", "survey_response")}
    sources = env.sql("SELECT COUNT(*) FROM information_schema.TABLES "
                      f"WHERE TABLE_SCHEMA = '{env.DB}' AND TABLE_NAME = 'crawl_source'")
    had_mail = env.sql("SELECT COUNT(*) FROM information_schema.COLUMNS "
                       f"WHERE TABLE_SCHEMA = '{env.DB}' AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'emailed_at'")
    text = start_stop(f"{tag}_{len(scripts)}")
    after = {t: env.sql(f"SELECT COUNT(*) FROM {t}") for t in before}
    check(f"{label}：启动成功，升到 V{LATEST}，没有 ERROR 日志",
          history() == BASELINED and not errors(text), (history(), errors(text)[:2]))
    check(f"{label}：表结构和全新的一样", snapshot() == FRESH, diff(FRESH, snapshot()))
    check(f"{label}：账号、队伍、消息、问卷都没丢", before == after, (before, after))
    check(f"{label}：抓取来源 4 个（原来就有的不重复插）", env.sql("SELECT COUNT(*) FROM crawl_source") == "4",
          (sources, env.sql("SELECT COUNT(*) FROM crawl_source")))
    if had_mail == "0":
        check(f"{label}：原来没有邮件提醒，升级前的消息都记成发过了，不会补发",
              env.sql("SELECT COUNT(*) FROM notification WHERE emailed_at IS NULL") == "0")

print(f"共 {len(results)} 项，通过 {sum(results)} 项")
sys.exit(0 if all(results) else 1)
