-- 竞赛组队匹配平台 · 初始数据
-- 在 schema.sql 之后执行。包含：技能字典、分工模板、示例学校和比赛、问卷 v2 的题目和匹配配置。
-- 标了"示例"的数据只用于开发演示，上线前替换。

SET NAMES utf8mb4;
USE team_match;

-- ============================================================
-- 技能字典
-- ============================================================

INSERT INTO skill (name, category, sort) VALUES
('Java', '编程开发', 10), ('Python', '编程开发', 11), ('C/C++', '编程开发', 12),
('JavaScript/TypeScript', '编程开发', 13), ('Vue', '编程开发', 14), ('React', '编程开发', 15),
('Spring Boot', '编程开发', 16), ('数据库/SQL', '编程开发', 17), ('微信小程序', '编程开发', 18),
('Android', '编程开发', 19), ('嵌入式/单片机', '编程开发', 20),
('数据结构与算法', '算法与数据', 30), ('机器学习', '算法与数据', 31), ('深度学习', '算法与数据', 32),
('数据分析', '算法与数据', 33), ('MATLAB', '算法与数据', 34), ('数学建模', '算法与数据', 35),
('统计分析', '算法与数据', 36), ('运筹优化', '算法与数据', 37),
('UI 设计', '设计', 50), ('平面设计', '设计', 51), ('视频剪辑', '设计', 52), ('3D 建模', '设计', 53),
('论文写作', '写作表达', 70), ('LaTeX 排版', '写作表达', 71), ('英文写作', '写作表达', 72),
('商业计划书', '写作表达', 73), ('路演答辩', '写作表达', 74), ('PPT 制作', '写作表达', 75),
('电路设计', '硬件', 90), ('PCB 设计', '硬件', 91);

-- ============================================================
-- 分工模板（问卷 P4 的选项来源）
-- ============================================================

INSERT INTO role_template (name) VALUES ('数学建模类'), ('软件开发类'), ('创新创业类'), ('电子设计类');

INSERT INTO role_template_item (template_id, name, sort)
SELECT t.id, r.name, r.sort FROM role_template t JOIN (
  SELECT '数学建模类' AS tpl, '建模' AS name, 1 AS sort UNION ALL
  SELECT '数学建模类', '编程', 2 UNION ALL
  SELECT '数学建模类', '论文写作与排版', 3 UNION ALL
  SELECT '软件开发类', '前端', 1 UNION ALL
  SELECT '软件开发类', '后端', 2 UNION ALL
  SELECT '软件开发类', '算法', 3 UNION ALL
  SELECT '软件开发类', 'UI 设计', 4 UNION ALL
  SELECT '软件开发类', '产品与答辩', 5 UNION ALL
  SELECT '创新创业类', '技术', 1 UNION ALL
  SELECT '创新创业类', '商业计划书', 2 UNION ALL
  SELECT '创新创业类', '财务', 3 UNION ALL
  SELECT '创新创业类', '路演答辩', 4 UNION ALL
  SELECT '创新创业类', '市场调研', 5 UNION ALL
  SELECT '电子设计类', '硬件电路', 1 UNION ALL
  SELECT '电子设计类', '嵌入式软件', 2 UNION ALL
  SELECT '电子设计类', '测试与报告', 3
) r ON r.tpl = t.name;

-- ============================================================
-- 示例学校
-- ============================================================

INSERT INTO school (name, province, city) VALUES ('示例大学', '示例省', '示例市');
INSERT INTO school_email_domain (school_id, domain)
SELECT id, d.domain FROM school JOIN (
  SELECT 'stu.example.edu.cn' AS domain UNION ALL SELECT 'example.edu.cn'
) d WHERE school.name = '示例大学';

-- ============================================================
-- 比赛（名称和级别来自中国高等教育学会竞赛目录；主办单位等细节等爬虫补全）
-- ============================================================

INSERT INTO competition (name, short_name, organizer, category, level, in_cahe_list, role_template_id, match_preset)
SELECT c.name, c.short_name, c.organizer, c.category, 'NATIONAL', 1, t.id, c.preset FROM (
  SELECT '全国大学生数学建模竞赛' AS name, '国赛' AS short_name, '中国工业与应用数学学会' AS organizer, '数学' AS category, '数学建模类' AS tpl, 'SHORT_INTENSIVE' AS preset UNION ALL
  SELECT '全国大学生电子设计竞赛', '电赛', NULL, '电子', '电子设计类', 'SHORT_INTENSIVE' UNION ALL
  SELECT '中国国际大学生创新大赛', NULL, NULL, '创新创业', '创新创业类', 'LONG_TERM' UNION ALL
  SELECT '"挑战杯"全国大学生课外学术科技作品竞赛', '挑战杯', NULL, '创新创业', '创新创业类', 'LONG_TERM' UNION ALL
  SELECT '中国大学生计算机设计大赛', '计设', NULL, '计算机', '软件开发类', 'DEFAULT'
) c LEFT JOIN role_template t ON t.name = c.tpl;

-- 爬虫的抓取来源。选择器是对着 2026 年 10 月各官网的页面结构写的，网站改版了就在管理后台改。
-- 中国国际大学生创新大赛的官网是前端渲染的，静态抓取拿不到内容，没有加。
-- 挑战杯官网的 robots.txt 允许普通爬虫抓 /article/，但明确不希望内容被 AI 使用，所以 allow_llm = 0，默认也不启用。
INSERT INTO crawl_source (competition_id, name, list_url, link_selector, title_keywords, exclude_keywords,
                          content_selector, allow_llm, enabled, note)
SELECT c.id, s.name, s.list_url, s.link_selector, s.title_keywords, s.exclude_keywords, s.content_selector,
       s.allow_llm, s.enabled, s.note
FROM (
  SELECT '全国大学生数学建模竞赛' AS competition, '数模官网首页' AS name, 'https://www.mcm.edu.cn/' AS list_url,
         '#recentNodesList a' AS link_selector, '竞赛的通知,报名' AS title_keywords, '获奖,名单,公示,赛题' AS exclude_keywords,
         '#divNodeBody' AS content_selector, 1 AS allow_llm, 1 AS enabled, NULL AS note UNION ALL
  SELECT '全国大学生电子设计竞赛', '电赛培训网首页', 'https://www.nuedc-training.com.cn/',
         'a[href*=/news/details/]', '竞赛的通知,第一次通知,报名', '获奖,名单,公示,赛题,清单',
         '.newsMain-content', 1, 1, NULL UNION ALL
  SELECT '中国大学生计算机设计大赛', '计设大赛官网首页', 'https://jsjds.blcu.edu.cn/',
         'a[href*=info/]', '通知,报名', '获奖,名单,公示,结果', NULL, 1, 1, NULL UNION ALL
  SELECT '"挑战杯"全国大学生课外学术科技作品竞赛', '挑战杯官网首页', 'https://www.tiaozhanbei.net/',
         'a[href*=/article/]', '通知', '开幕,闭幕,获奖,名单', NULL, 0, 0,
         'robots.txt 不希望内容被 AI 使用：只用规则抽取。默认不启用，需要时管理员打开'
) s JOIN competition c ON c.name = s.competition
ORDER BY c.id;

-- 示例届次：日期是虚构的，只为演示日历和撞期检测。按导入当天往后排，什么时候导入报名都还开着：
-- 数模 9 天后截止报名，第 17–20 天比赛，第 40–55 天出成绩；计设 35 天后截止报名。
-- 已经导入过、日期过期了的库，跑一次 sql/demo-refresh-dates.sql
SET @d = CURDATE();

INSERT INTO competition_edition (competition_id, year, title, team_min, team_max, same_school_required, advisor_required,
                                 registration_start, registration_end, source, status)
SELECT id, 2026, '2026 年（示例数据）', 1, 3, 1, 0, @d - INTERVAL 16 DAY, TIMESTAMP(@d + INTERVAL 9 DAY, '23:59:59'), 'MANUAL', 'PUBLISHED'
FROM competition WHERE name = '全国大学生数学建模竞赛';

INSERT INTO competition_edition (competition_id, year, title, team_min, team_max, same_school_required, advisor_required,
                                 registration_start, registration_end, source, status)
SELECT id, 2026, '2026 年（示例数据）', 1, 5, 1, 1, @d - INTERVAL 5 DAY, TIMESTAMP(@d + INTERVAL 35 DAY, '23:59:59'), 'MANUAL', 'PUBLISHED'
FROM competition WHERE name = '中国大学生计算机设计大赛';

INSERT INTO competition_node (edition_id, title, node_type, start_at, end_at, all_day, end_mode, end_latest_at, busy, sort)
SELECT e.id, n.title, n.node_type, n.start_at, n.end_at, n.all_day, n.end_mode, n.end_latest_at, n.busy, n.sort
FROM competition_edition e JOIN competition c ON c.id = e.competition_id JOIN (
  SELECT '报名截止' AS title, 'REGISTRATION' AS node_type, TIMESTAMP(@d + INTERVAL 9 DAY, '23:59:59') AS start_at, CAST(NULL AS DATETIME) AS end_at, 0 AS all_day, 'FIXED' AS end_mode, CAST(NULL AS DATETIME) AS end_latest_at, 0 AS busy, 1 AS sort UNION ALL
  SELECT '比赛进行（72 小时）', 'CONTEST', TIMESTAMP(@d + INTERVAL 17 DAY, '18:00:00'), TIMESTAMP(@d + INTERVAL 20 DAY, '20:00:00'), 0, 'FIXED', NULL, 1, 2 UNION ALL
  SELECT '成绩公布', 'RESULT', TIMESTAMP(@d + INTERVAL 40 DAY), TIMESTAMP(@d + INTERVAL 40 DAY), 1, 'RANGE', TIMESTAMP(@d + INTERVAL 55 DAY), 0, 3
) n WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）';

-- ============================================================
-- 问卷 v2（题目内容见 docs/问卷草案-v2.md）
-- ============================================================

INSERT INTO survey_version (code, name, active) VALUES ('v2', '组队匹配问卷 v2', 1);
SET @v = (SELECT id FROM survey_version WHERE code = 'v2');

INSERT INTO survey_question (version_id, code, part, dimension, type, title, note, options, settings, required, in_quick, sort) VALUES
-- 通用部分
(@v, 'G1', 'GENERAL', 'goal', 'MULTI', '你参加这类比赛主要为了什么？', '最多选 2 项',
 '[{"value":"interest","label":"兴趣，想学东西"},{"value":"postgrad","label":"保研或综测加分"},{"value":"career","label":"简历和求职"},{"value":"prize","label":"奖金"},{"value":"friends","label":"和朋友一起"},{"value":"required","label":"学院或老师要求"}]',
 '{"maxSelect":2}', 1, 0, 10),
(@v, 'G2', 'GENERAL', 'time', 'SINGLE', '你推进任务的习惯更接近？', NULL,
 '[{"value":"A","label":"拿到就开始，尽早做完"},{"value":"B","label":"按计划均匀推进"},{"value":"C","label":"前期慢慢想，后期集中发力"},{"value":"D","label":"截止前最有效率"}]',
 NULL, 1, 0, 20),
(@v, 'G3', 'GENERAL', 'time', 'SINGLE', '你的作息更接近？', NULL,
 '[{"value":"A","label":"早起型"},{"value":"B","label":"正常"},{"value":"C","label":"夜猫子"}]',
 NULL, 1, 0, 30),
(@v, 'G4', 'GENERAL', 'time', 'SLOT_GRID', '一周里你通常哪些时段有空？', '至少选 3 个时段',
 NULL, '{"days":["周一","周二","周三","周四","周五","周六","周日"],"segments":["上午","下午","晚上"],"encoding":"bitmask, bit = day * 3 + seg","minSelect":3}', 1, 1, 40),
(@v, 'G5', 'GENERAL', 'time', 'SLIDER', '队友发来消息，你一般多久内会回复？', NULL,
 NULL, '{"min":0,"max":24,"step":1,"unit":"小时"}', 1, 0, 50),
(@v, 'G6', 'GENERAL', 'collab', 'SINGLE', '你希望队里多久同步一次进度？', NULL,
 '[{"value":"A","label":"每天"},{"value":"B","label":"两三天一次"},{"value":"C","label":"每周一次"},{"value":"D","label":"有事再说"}]',
 NULL, 1, 0, 60),
(@v, 'G7', 'GENERAL', 'collab', 'SINGLE', '队里有分歧时，你倾向怎么做决定？', NULL,
 '[{"value":"A","label":"队长拍板"},{"value":"B","label":"讨论后投票"},{"value":"C","label":"谁负责的部分谁决定"},{"value":"D","label":"讨论到大家都同意"}]',
 NULL, 1, 0, 70),
(@v, 'G8', 'GENERAL', 'collab', 'SINGLE', '和队友意见不一致时，你通常会？', NULL,
 '[{"value":"A","label":"当场说出来，语气可能有点冲"},{"value":"B","label":"摆事实讲理由，一起找方案"},{"value":"C","label":"先冷静一两天，再找时间谈"},{"value":"D","label":"找队长或指导老师来判断"},{"value":"E","label":"不想起冲突，就按别人的来"}]',
 NULL, 1, 0, 80),
(@v, 'G9', 'GENERAL', 'skill', 'MULTI', '在团队里，你更常扮演哪种角色？', '选 2 个',
 '[{"value":"plant","label":"想点子、提新思路"},{"value":"shaper","label":"定方向、推着大家往前走"},{"value":"coordinator","label":"分配任务、协调大家"},{"value":"implementer","label":"拿到任务就埋头做完"},{"value":"finisher","label":"抠细节、检查、收尾"},{"value":"resource","label":"找资料、对接老师和外部资源"},{"value":"monitor","label":"挑毛病、判断方案靠不靠谱"},{"value":"teamworker","label":"照顾气氛、调和矛盾"}]',
 '{"minSelect":2,"maxSelect":2}', 1, 0, 90),
(@v, 'G10', 'GENERAL', 'skill', 'SINGLE', '关于经验，你更希望？', NULL,
 '[{"value":"A","label":"和经验差不多的人一起"},{"value":"B","label":"愿意带新人"},{"value":"C","label":"希望有经验的人带一带"}]',
 NULL, 1, 0, 100),
(@v, 'G11', 'GENERAL', 'duty', 'RANKING', '下面哪些队友行为你最不能接受？', '选 3 项并排序，最不能接受的放第一',
 '[{"value":"missing","label":"长时间失联"},{"value":"freeride","label":"只挂名不干活"},{"value":"sloppy","label":"交的东西敷衍凑数"},{"value":"silent","label":"讨论时不发言"},{"value":"quit","label":"不打招呼就退队"},{"value":"overwrite","label":"改别人负责的部分不打招呼"},{"value":"credit","label":"争署名和排名"},{"value":"late","label":"开会迟到或临时放鸽子"}]',
 '{"selectCount":3,"quick":{"type":"MULTI","minSelect":1,"maxSelect":2}}', 1, 1, 110),
(@v, 'F1', 'GENERAL', NULL, 'TEXT', '我能给队伍带来的三点', NULL,
 NULL, '{"maxLength":300,"placeholder":"比如：熟悉 LaTeX 排版、拿过省奖、执行力强"}', 1, 0, 120),
(@v, 'F2', 'GENERAL', NULL, 'TEXT', '希望队友包容我的一个短板', NULL,
 NULL, '{"maxLength":200,"placeholder":"比如：周三全天有课、不太擅长当众讲"}', 0, 0, 130),
-- 比赛级部分
(@v, 'P1', 'COMPETITION', 'goal', 'SINGLE', '这场比赛，你的目标是？', NULL,
 '[{"value":"A","label":"冲国家级一等奖"},{"value":"B","label":"争取省奖或国家级二三等奖"},{"value":"C","label":"有奖更好，重点是完整做一次"},{"value":"D","label":"先体验一下，量力而行"}]',
 NULL, 1, 1, 210),
(@v, 'P2', 'COMPETITION', 'goal', 'SLIDER', '备赛期间，每周大约能投入多少小时？', NULL,
 NULL, '{"min":0,"max":30,"step":1,"unit":"小时"}', 1, 1, 220),
(@v, 'P3', 'COMPETITION', 'goal', 'SINGLE', '冲刺阶段你能接受的强度是？', '比如截止前几天、72 小时赛',
 '[{"value":"A","label":"可以通宵"},{"value":"B","label":"熬到一两点"},{"value":"C","label":"尽量保持正常作息"}]',
 NULL, 1, 0, 230),
(@v, 'P4', 'COMPETITION', 'skill', 'RANKING', '这场比赛你想承担哪块工作？', '按意愿排前 2',
 NULL, '{"optionSource":"roleTemplate","selectCount":2}', 1, 1, 240),
(@v, 'P5', 'COMPETITION', NULL, 'MULTI', '这次参赛，你有哪些限制？', '可多选，用来提前沟通，不会让你在推荐里被排除',
 '[{"value":"exam","label":"期中或期末考试","group":"time"},{"value":"internship","label":"实习","group":"time"},{"value":"exam_prep","label":"考研、考公或考证","group":"time"},{"value":"other_contest","label":"同期还有别的比赛","group":"time"},{"value":"student_work","label":"学生工作或社团职务","group":"time"},{"value":"online_only","label":"只能线上参与","group":"other"},{"value":"no_device","label":"没有合适的电脑或设备","group":"other"},{"value":"budget","label":"自费部分难以承担","group":"other"},{"value":"english_weak","label":"英文读写比较吃力","group":"other"},{"value":"none","label":"没有限制"}]',
 '{"exclusive":["none"]}', 1, 1, 250),
(@v, 'P6', 'COMPETITION', 'duty', 'MULTI', '证书上的署名顺序，你能接受哪些规则？', '可多选',
 '[{"value":"by_contribution","label":"按贡献记录排"},{"value":"pre_agreed","label":"赛前协商好写下来"},{"value":"captain_first","label":"队长第一，其余按贡献"},{"value":"rotate","label":"多次参赛时轮流排前"},{"value":"school_rule","label":"按学校综测规则"}]',
 '{"minSelect":1}', 1, 0, 260),
(@v, 'P7', 'COMPETITION', 'duty', 'MULTI', '贡献怎么记录，你能接受哪些方式？', '可多选',
 '[{"value":"shared_doc","label":"共享文档里的分工表"},{"value":"git","label":"Git 提交记录"},{"value":"weekly_report","label":"每周简短周报"},{"value":"none","label":"不需要专门记录"}]',
 '{"minSelect":1,"exclusive":["none"]}', 1, 0, 270),
(@v, 'F3', 'COMPETITION', NULL, 'TEXT', '一句话说说你对这场比赛的想法', NULL,
 NULL, '{"maxLength":100}', 0, 0, 280),
(@v, 'PLEDGE', 'COMPETITION', NULL, 'PLEDGE', '如果中途确实无法继续，我会至少提前 3 天告诉队友，并交接我负责的部分', '勾选后记录在案，中途退出却没提前告知会在互评里体现',
 NULL, '{"noticeDays":3}', 1, 1, 290);

-- ============================================================
-- 匹配配置
-- 相容矩阵：options 给出行列顺序，matrix[i][j] 是选项 i 对选项 j 的得分
-- ============================================================

INSERT INTO match_config (version_id, preset, config) VALUES
(@v, 'DEFAULT', '{
  "aggregate": {"pairToTeam": "mean", "captainOnly": ["G7"]},
  "dimensions": [
    {"key": "goal", "name": "目标与投入", "weight": 0.25, "items": [
      {"q": "P1", "scorer": "single", "weight": 0.45, "options": ["A","B","C","D"],
       "matrix": [[1,0.6,0.2,0],[0.6,1,0.7,0.3],[0.2,0.7,1,0.8],[0,0.3,0.8,1]]},
      {"q": "P2", "scorer": "slider", "weight": 0.35, "tolerance": 10},
      {"q": "P3", "scorer": "single", "weight": 0.10, "options": ["A","B","C"],
       "matrix": [[1,0.6,0.2],[0.6,1,0.6],[0.2,0.6,1]]},
      {"q": "G1", "scorer": "tags", "weight": 0.10}
    ]},
    {"key": "skill", "name": "能力与角色", "weight": 0.20, "items": [
      {"q": "SKILLS", "scorer": "skillComplement", "weight": 0.45, "source": "user_skill"},
      {"q": "P4", "scorer": "roleComplement", "weight": 0.30, "rankScores": [1.0, 0.6]},
      {"q": "G9", "scorer": "roleDiversity", "weight": 0.15,
       "base": 0.6, "perNewRole": 0.2, "perDuplicateScarce": -0.4, "scarce": ["shaper","coordinator","plant"]},
      {"q": "G10", "scorer": "mentorResource", "weight": 0.10,
       "canMentor": {"answers": ["B"], "minExperiences": 2}, "wantsMentor": ["C"],
       "scores": {"matched": 1.0, "noneNeeded": 0.9, "unmet": 0.5}}
    ]},
    {"key": "collab", "name": "协作风格", "weight": 0.15, "items": [
      {"q": "G6", "scorer": "single", "weight": 0.30, "options": ["A","B","C","D"],
       "matrix": [[1,0.7,0.3,0],[0.7,1,0.7,0.3],[0.3,0.7,1,0.7],[0,0.3,0.7,1]]},
      {"q": "G7", "scorer": "single", "weight": 0.30, "options": ["A","B","C","D"], "compareWith": "captain",
       "matrix": [[1,0.7,0.5,0.3],[0.7,1,0.7,0.5],[0.5,0.7,1,0.7],[0.3,0.5,0.7,1]]},
      {"q": "G8", "scorer": "single", "weight": 0.40, "options": ["A","B","C","D","E"],
       "matrix": [[0.3,0.7,0.4,0.6,0.5],[0.7,1,0.8,0.8,0.7],[0.4,0.8,0.7,0.7,0.4],[0.6,0.8,0.7,0.8,0.5],[0.5,0.7,0.4,0.5,0.3]]}
    ]},
    {"key": "time", "name": "时间与节奏", "weight": 0.20, "items": [
      {"q": "G4", "scorer": "slotOverlap", "weight": 0.40, "meetingSlotWeight": 2},
      {"q": "G2", "scorer": "single", "weight": 0.25, "options": ["A","B","C","D"],
       "matrix": [[1,0.8,0.4,0.1],[0.8,1,0.7,0.3],[0.4,0.7,0.8,0.5],[0.1,0.3,0.5,0.3]]},
      {"q": "G5", "scorer": "slider", "weight": 0.25, "tolerance": 12,
       "slowPenalty": {"whenTeamGoal": ["A"], "overHours": 12, "factor": 0.5}},
      {"q": "G3", "scorer": "single", "weight": 0.10, "options": ["A","B","C"],
       "matrix": [[1,0.7,0.3],[0.7,1,0.7],[0.3,0.7,1]]}
    ]},
    {"key": "duty", "name": "责任与底线", "weight": 0.20, "items": [
      {"q": "G11", "scorer": "ranking", "weight": 0.45, "k": 3},
      {"q": "P6", "scorer": "setIntersect", "weight": 0.35, "hit": 1.0, "miss": 0.2, "missReason": "署名规则可能谈不拢"},
      {"q": "P7", "scorer": "setIntersect", "weight": 0.20, "hit": 1.0, "miss": 0.5}
    ]}
  ],
  "commitment": {
    "history": {"newUser": 1.0, "min": 0.6, "max": 1.0},
    "constraint": {"q": "P5", "timeTags": ["exam","internship","exam_prep","other_contest","student_work"],
                   "highGoals": ["A","B"], "perHitHighGoal": 0.1, "perHitOther": 0.05, "floor": 0.7}
  },
  "hardFilters": [
    {"type": "answerPair", "q": "P1", "pairs": [["A","D"]]},
    {"type": "competitionTimeConflict"},
    {"type": "editionRules"},
    {"type": "platformBlock"}
  ],
  "teamRules": [
    {"type": "onlineOnlyVsOffline", "q": "P5", "tag": "online_only", "factor": 0.7},
    {"type": "englishRequired", "q": "P5", "tag": "english_weak"}
  ],
  "quick": {"redistributeMissingWeights": true}
}'),
(@v, 'SHORT_INTENSIVE', '{
  "extends": "DEFAULT",
  "dimensionWeights": {"goal": 0.20, "skill": 0.20, "collab": 0.10, "time": 0.30, "duty": 0.20},
  "matrixOverrides": {"G2": [{"row": "D", "col": "D", "value": 0.2}]}
}'),
(@v, 'LONG_TERM', '{
  "extends": "DEFAULT",
  "dimensionWeights": {"goal": 0.30, "skill": 0.20, "collab": 0.15, "time": 0.15, "duty": 0.20}
}');
