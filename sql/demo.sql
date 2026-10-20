-- 竞赛组队匹配平台 · 演示数据
-- 后端启动过一次（表和初始数据由 Flyway 自动建好）之后再导入，只用于本地演示和答辩，不要导入正式环境。
--
-- 演示账号（除了吴迪和郑好，都已经通过"示例大学"的教育邮箱认证），密码统一是 demo1234：
--   linxiao@demo.local   林晓    主角：还没组队，关注了数学建模和计算机设计大赛，看"为你推荐"
--   chen@demo.local      陈一舟  "数模冲刺队"队长，在招编程和论文，收到了孙悦的申请
--   wang@demo.local      王思远  "周末慢慢来"队长（数学建模），"计设作品组"队长（计算机设计大赛）
--   zhao@demo.local      赵子航  "国一冲冲冲"队长
--   qian@demo.local      钱多多  "国一冲冲冲"队员
--   sun@demo.local       孙悦    还没组队，擅长论文排版，向"数模冲刺队"发了申请
--   li@demo.local        李想    还没组队，只想先体验（对冲国一的队伍会被一票否决）
--   zhou@demo.local      周可    还没组队，没填问卷（匹配度接近 50 分），往届互评一般，推荐时会再打个折
--   wu@demo.local        吴迪    教育邮箱的学校平台还没收录，在等管理员审核（演示"认证审核"）
--   zheng@demo.local     郑好    和吴迪同校，管理员通过吴迪、收录邮箱后缀时会一起通过
--   admin@demo.local     管理员  能进"管理后台"：数据看板、认证审核、比赛库管理、匹配配置、账号管理、举报与信用
--
-- v2.1 的演示内容（文件末尾）：林晓有每周二四晚上的英语课、一条时间说不准的"复习线代"、一天的校运动会；
-- 陈一舟的社团换届正好撞上国赛比赛期间，日历上会标出撞期；资料页上有"短板"和带证明链接的履历；
-- 王思远把履历设成了只给有关联的人看。日期都按导入那天往后排。
--
-- 各校加分认定（文件末尾）：示例大学把数学建模认定为 A 类（陈一舟标注，王思远、赵子航确认，已显示）；
-- 计算机设计大赛王思远标的是 B 类，钱多多确认了，还差一人——用林晓登录点"我也确认"，就会显示出来，王思远收到通知；
-- 电子设计竞赛还没人标注，可以演示"标注本校的认定"。
--
-- 往届数据（演示"互评与信用"）：陈一舟、孙悦、周可 2025 年组过队拿了省一，陈一舟和孙悦 2024 年也组过一次。
-- 互评已经揭晓：陈一舟信用 95.4、孙悦 100、周可 61.1（被评为有时失联），周可在推荐里会被降分并提示。

SET NAMES utf8mb4;
USE team_match;

SET @pwd = '$2a$10$y7rZkJEH579p5uOCr.Nc4uoewvgx8ahz/XGWQNywTh/PGr3eCjPuC';
SET @school = (SELECT id FROM school WHERE name = '示例大学');
SET @v = (SELECT id FROM survey_version WHERE code = 'v2');
SET @math = (SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id
             WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）');
SET @cs = (SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id
           WHERE c.name = '中国大学生计算机设计大赛' AND e.title = '2026 年（示例数据）');

-- 周一到周五晚上 / 周末白天（时段位图，第 day*3+seg 位）
SET @evenings = 18724;
SET @weekends = 884736;

-- ============================================================
-- 账号和档案
-- ============================================================

INSERT INTO sys_user (email, password_hash, nickname, role, status, school_id, verify_status, verified_until) VALUES
('linxiao@demo.local', @pwd, '林晓', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('chen@demo.local', @pwd, '陈一舟', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('wang@demo.local', @pwd, '王思远', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('zhao@demo.local', @pwd, '赵子航', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('qian@demo.local', @pwd, '钱多多', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('sun@demo.local', @pwd, '孙悦', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('li@demo.local', @pwd, '李想', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('zhou@demo.local', @pwd, '周可', 'STUDENT', 'ACTIVE', @school, 'VERIFIED', '2027-08-31'),
('wu@demo.local', @pwd, '吴迪', 'STUDENT', 'ACTIVE', NULL, 'PENDING', NULL),
('zheng@demo.local', @pwd, '郑好', 'STUDENT', 'ACTIVE', NULL, 'PENDING', NULL),
('admin@demo.local', @pwd, '管理员', 'ADMIN', 'ACTIVE', NULL, 'NONE', NULL);

SET @lin = (SELECT id FROM sys_user WHERE email = 'linxiao@demo.local');
SET @chen = (SELECT id FROM sys_user WHERE email = 'chen@demo.local');
SET @wang = (SELECT id FROM sys_user WHERE email = 'wang@demo.local');
SET @zhao = (SELECT id FROM sys_user WHERE email = 'zhao@demo.local');
SET @qian = (SELECT id FROM sys_user WHERE email = 'qian@demo.local');
SET @sun = (SELECT id FROM sys_user WHERE email = 'sun@demo.local');
SET @li = (SELECT id FROM sys_user WHERE email = 'li@demo.local');
SET @zhou = (SELECT id FROM sys_user WHERE email = 'zhou@demo.local');
SET @wu = (SELECT id FROM sys_user WHERE email = 'wu@demo.local');
SET @zheng = (SELECT id FROM sys_user WHERE email = 'zheng@demo.local');
SET @admin = (SELECT id FROM sys_user WHERE email = 'admin@demo.local');

INSERT INTO user_profile (user_id, real_name, major, enroll_year, degree, bio, contact_wechat) VALUES
(@lin, '林晓', '计算机科学与技术', 2023, 'UNDERGRAD', '写代码比较快，想找认真冲省奖的队伍。', 'linxiao_demo'),
(@chen, '陈一舟', '数学与应用数学', 2022, 'UNDERGRAD', '参加过两次数模，负责建模，想冲一次省一。', 'chen_demo'),
(@wang, '王思远', '统计学', 2023, 'UNDERGRAD', '周末时间多，平时课比较满。', 'wang_demo'),
(@zhao, '赵子航', '软件工程', 2022, 'UNDERGRAD', '目标国一，能接受高强度。', 'zhao_demo'),
(@qian, '钱多多', '汉语言文学', 2023, 'UNDERGRAD', '擅长写作和排版。', 'qian_demo'),
(@sun, '孙悦', '新闻学', 2023, 'UNDERGRAD', 'LaTeX 和论文写作都比较熟。', 'sun_demo'),
(@li, '李想', '工商管理', 2024, 'UNDERGRAD', '第一次参赛，想先体验一下。', 'li_demo'),
(@zhou, '周可', '电子信息工程', 2024, 'UNDERGRAD', NULL, NULL),
(@wu, '吴迪', '自动化', 2024, 'UNDERGRAD', '刚转学过来，想找队参加电子设计竞赛。', NULL),
(@zheng, '郑好', '物联网工程', 2023, 'UNDERGRAD', NULL, NULL),
(@admin, NULL, NULL, NULL, NULL, NULL, NULL);

-- 吴迪和郑好的教育邮箱已经验证过能收信，但 dhu-demo.edu.cn 这所（虚构的）学校平台还没收录，所以在排队等审核
INSERT INTO verification_request (user_id, method, status, edu_email, created_at) VALUES
(@wu, 'EDU_EMAIL', 'PENDING', 'wudi@stu.dhu-demo.edu.cn', NOW() - INTERVAL 1 DAY),
(@zheng, 'EDU_EMAIL', 'PENDING', 'zhenghao@dhu-demo.edu.cn', NOW() - INTERVAL 3 HOUR);

INSERT INTO user_skill (user_id, skill_id, level)
SELECT u.id, s.id, x.level FROM (
  SELECT @lin AS uid, 'Python' AS skill, 3 AS level UNION ALL SELECT @lin, '数学建模', 2 UNION ALL SELECT @lin, 'MATLAB', 2 UNION ALL
  SELECT @chen, '数学建模', 3 UNION ALL SELECT @chen, '运筹优化', 3 UNION ALL SELECT @chen, 'MATLAB', 2 UNION ALL
  SELECT @wang, '统计分析', 3 UNION ALL SELECT @wang, '数据分析', 2 UNION ALL SELECT @wang, 'Vue', 2 UNION ALL
  SELECT @zhao, 'C/C++', 3 UNION ALL SELECT @zhao, '数据结构与算法', 3 UNION ALL
  SELECT @qian, '论文写作', 3 UNION ALL SELECT @qian, 'LaTeX 排版', 2 UNION ALL
  SELECT @sun, '论文写作', 3 UNION ALL SELECT @sun, 'LaTeX 排版', 3 UNION ALL SELECT @sun, '英文写作', 2 UNION ALL
  SELECT @li, 'Vue', 2 UNION ALL SELECT @li, 'PPT 制作', 2 UNION ALL
  SELECT @zhou, 'C/C++', 2 UNION ALL SELECT @zhou, '电路设计', 2
) x JOIN sys_user u ON u.id = x.uid JOIN skill s ON s.name = x.skill;

INSERT INTO user_experience (user_id, competition_id, title, year, award, source) VALUES
(@lin, (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛'), '全国大学生数学建模竞赛', 2025, '省级二等奖', 'MANUAL'),
(@chen, (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛'), '全国大学生数学建模竞赛', 2024, '省级三等奖', 'MANUAL'),
(@chen, (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛'), '全国大学生数学建模竞赛', 2025, '省级一等奖', 'MANUAL'),
(@zhao, NULL, '蓝桥杯', 2025, '省级一等奖', 'MANUAL');

-- ============================================================
-- 关注
-- ============================================================

INSERT INTO competition_follow (user_id, edition_id) VALUES
(@lin, @math), (@lin, @cs), (@chen, @math), (@wang, @math), (@wang, @cs), (@zhao, @math), (@qian, @math),
(@sun, @math), (@li, @math), (@zhou, @math);

-- ============================================================
-- 问卷答案
-- ============================================================

INSERT INTO survey_response (user_id, version_id, edition_id, answers, quick, pledged_at) VALUES
-- 通用部分
(@lin, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('interest', 'postgrad'), 'G2', 'B', 'G3', 'B', 'G4', @evenings, 'G5', 2,
  'G6', 'B', 'G7', 'B', 'G8', 'B', 'G9', JSON_ARRAY('implementer', 'finisher'), 'G10', 'A',
  'G11', JSON_ARRAY('missing', 'freeride', 'sloppy'), 'F1', '写代码快；熟悉 Python 数据处理；拿过省二'), 0, NULL),
(@chen, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('interest', 'postgrad'), 'G2', 'B', 'G3', 'B', 'G4', @evenings, 'G5', 2,
  'G6', 'B', 'G7', 'B', 'G8', 'B', 'G9', JSON_ARRAY('shaper', 'coordinator'), 'G10', 'B',
  'G11', JSON_ARRAY('missing', 'freeride', 'late'), 'F1', '建模经验多；能统筹分工；会带新人'), 0, NULL),
(@wang, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('interest', 'friends'), 'G2', 'D', 'G3', 'C', 'G4', @weekends, 'G5', 12,
  'G6', 'C', 'G7', 'D', 'G8', 'C', 'G9', JSON_ARRAY('plant', 'teamworker'), 'G10', 'A',
  'G11', JSON_ARRAY('quit', 'credit', 'silent'), 'F1', '点子多；统计功底好'), 0, NULL),
(@zhao, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('prize', 'career'), 'G2', 'A', 'G3', 'C', 'G4', @evenings | @weekends, 'G5', 1,
  'G6', 'A', 'G7', 'A', 'G8', 'A', 'G9', JSON_ARRAY('shaper', 'implementer'), 'G10', 'B',
  'G11', JSON_ARRAY('freeride', 'sloppy', 'missing'), 'F1', '算法强；执行力强'), 0, NULL),
(@qian, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('postgrad'), 'G2', 'A', 'G3', 'B', 'G4', @evenings, 'G5', 3,
  'G6', 'A', 'G7', 'A', 'G8', 'B', 'G9', JSON_ARRAY('finisher', 'monitor'), 'G10', 'A',
  'G11', JSON_ARRAY('freeride', 'missing', 'sloppy'), 'F1', '文笔好；排版细致'), 0, NULL),
(@sun, @v, NULL, JSON_OBJECT('G1', JSON_ARRAY('interest', 'postgrad'), 'G2', 'B', 'G3', 'B', 'G4', @evenings, 'G5', 2,
  'G6', 'B', 'G7', 'B', 'G8', 'B', 'G9', JSON_ARRAY('finisher', 'teamworker'), 'G10', 'C',
  'G11', JSON_ARRAY('missing', 'sloppy', 'freeride'), 'F1', 'LaTeX 排版熟练；写作细致'), 0, NULL),
(@li, @v, NULL, JSON_OBJECT('G4', @weekends, 'G11', JSON_ARRAY('quit')), 1, NULL),
-- 数学建模 2026 的参赛意向
(@lin, @v, @math, JSON_OBJECT('P1', 'B', 'P2', 12, 'P3', 'B', 'P4', JSON_ARRAY('编程', '建模'), 'P5', JSON_ARRAY('none'),
  'P6', JSON_ARRAY('by_contribution', 'pre_agreed'), 'P7', JSON_ARRAY('shared_doc', 'git'), 'PLEDGE', TRUE), 0, NOW()),
(@chen, @v, @math, JSON_OBJECT('P1', 'B', 'P2', 12, 'P3', 'B', 'P4', JSON_ARRAY('建模', '论文写作与排版'), 'P5', JSON_ARRAY('none'),
  'P6', JSON_ARRAY('by_contribution', 'pre_agreed'), 'P7', JSON_ARRAY('shared_doc'), 'PLEDGE', TRUE), 0, NOW()),
(@wang, @v, @math, JSON_OBJECT('P1', 'C', 'P2', 5, 'P3', 'C', 'P4', JSON_ARRAY('建模', '编程'), 'P5', JSON_ARRAY('student_work'),
  'P6', JSON_ARRAY('rotate'), 'P7', JSON_ARRAY('none'), 'PLEDGE', TRUE), 0, NOW()),
(@zhao, @v, @math, JSON_OBJECT('P1', 'A', 'P2', 25, 'P3', 'A', 'P4', JSON_ARRAY('编程', '建模'), 'P5', JSON_ARRAY('none'),
  'P6', JSON_ARRAY('captain_first'), 'P7', JSON_ARRAY('git'), 'PLEDGE', TRUE), 0, NOW()),
(@qian, @v, @math, JSON_OBJECT('P1', 'A', 'P2', 20, 'P3', 'A', 'P4', JSON_ARRAY('论文写作与排版', '建模'), 'P5', JSON_ARRAY('none'),
  'P6', JSON_ARRAY('captain_first', 'by_contribution'), 'P7', JSON_ARRAY('git', 'shared_doc'), 'PLEDGE', TRUE), 0, NOW()),
(@sun, @v, @math, JSON_OBJECT('P1', 'B', 'P2', 10, 'P3', 'B', 'P4', JSON_ARRAY('论文写作与排版', '建模'), 'P5', JSON_ARRAY('exam'),
  'P6', JSON_ARRAY('by_contribution'), 'P7', JSON_ARRAY('shared_doc'), 'PLEDGE', TRUE), 0, NOW()),
(@li, @v, @math, JSON_OBJECT('P1', 'D', 'P2', 3, 'P4', JSON_ARRAY('论文写作与排版', '建模'), 'P5', JSON_ARRAY('online_only'),
  'PLEDGE', TRUE), 1, NOW());

-- ============================================================
-- 队伍
-- ============================================================

INSERT INTO team (edition_id, name, captain_id, intro, status, max_members, meeting_mode, meeting_slots) VALUES
(@math, '数模冲刺队', @chen, '目标省一。我负责建模，想找一位编程手（Python）和一位论文手（会 LaTeX）。平时晚上线上讨论，赛前两周每周线下碰一次。', 'RECRUITING', 3, 'MIXED', 0),
(@math, '周末慢慢来', @wang, '重在完整做一次，不熬夜。周末白天在图书馆一起做。', 'RECRUITING', 3, 'OFFLINE', 0),
(@math, '国一冲冲冲', @zhao, '冲国一，最后 72 小时会通宵。现在还缺一位能写 C++ 的编程手。', 'RECRUITING', 3, 'OFFLINE', 0),
(@cs, '计设作品组', @wang, '做一个校园二手交易小程序，缺前端。', 'RECRUITING', 4, 'MIXED', 0);

SET @t_chen = (SELECT id FROM team WHERE name = '数模冲刺队');
SET @t_wang = (SELECT id FROM team WHERE name = '周末慢慢来');
SET @t_zhao = (SELECT id FROM team WHERE name = '国一冲冲冲');
SET @t_cs = (SELECT id FROM team WHERE name = '计设作品组');

INSERT INTO team_member (team_id, edition_id, user_id, role_name, is_captain, status) VALUES
(@t_chen, @math, @chen, '建模', 1, 'ACTIVE'),
(@t_wang, @math, @wang, '建模', 1, 'ACTIVE'),
(@t_zhao, @math, @zhao, '建模', 1, 'ACTIVE'),
(@t_zhao, @math, @qian, '论文写作与排版', 0, 'ACTIVE'),
(@t_cs, @cs, @wang, '后端', 1, 'ACTIVE');

INSERT INTO team_need (team_id, role_name, headcount, filled, note, status) VALUES
(@t_chen, '编程', 1, 0, '会 Python 做数据处理和画图', 'OPEN'),
(@t_chen, '论文写作与排版', 1, 0, '会 LaTeX，写过论文最好', 'OPEN'),
(@t_wang, '论文写作与排版', 1, 0, NULL, 'OPEN'),
(@t_wang, '编程', 1, 0, '会 Python 或 MATLAB 都行', 'OPEN'),
(@t_zhao, '编程', 1, 0, 'C++ 熟练，算法功底好', 'OPEN'),
(@t_cs, '前端', 2, 0, '会 Vue，做过小程序更好', 'OPEN');

INSERT INTO team_need_skill (need_id, skill_id, min_level)
SELECT n.id, s.id, x.lv FROM (
  SELECT @t_chen AS tid, '编程' AS role, 'Python' AS skill, 2 AS lv UNION ALL
  SELECT @t_chen, '论文写作与排版', 'LaTeX 排版', 2 UNION ALL
  SELECT @t_chen, '论文写作与排版', '论文写作', 2 UNION ALL
  SELECT @t_wang, '论文写作与排版', '论文写作', 1 UNION ALL
  SELECT @t_wang, '编程', 'Python', 1 UNION ALL
  SELECT @t_zhao, '编程', 'C/C++', 3 UNION ALL
  SELECT @t_zhao, '编程', '数据结构与算法', 2 UNION ALL
  SELECT @t_cs, '前端', 'Vue', 2 UNION ALL
  SELECT @t_cs, '前端', '微信小程序', 1
) x JOIN team_need n ON n.team_id = x.tid AND n.role_name = x.role JOIN skill s ON s.name = x.skill;

-- ============================================================
-- 一条待处理的申请和对应的站内信
-- ============================================================

INSERT INTO team_application (team_id, user_id, direction, need_id, message, status) VALUES
(@t_chen, @sun, 'APPLY', (SELECT id FROM team_need WHERE team_id = @t_chen AND role_name = '论文写作与排版'),
 '我 LaTeX 比较熟，写过两篇课程论文，想一起冲省奖。', 'PENDING');

INSERT INTO notification (user_id, type, title, content, ref_type, ref_id) VALUES
(@chen, 'APPLY_RECEIVED', '新的入队申请', '孙悦 申请加入「数模冲刺队」（论文写作与排版），去队伍页处理吧。', 'TEAM', @t_chen);

-- ============================================================
-- 往届：已经结束的队伍、互评和信用分（演示"互评与信用"）
-- 陈一舟 2024、2025 各带过一支队，孙悦两次都在，周可只参加了 2025 年。
-- 互评答案是 6 项行为的频率（0 从未 / 1 偶尔 / 2 有时 / 3 经常），信用分按 CreditCalculator 的规则算好写进去：
--   陈一舟 2025：孙悦全"从未"100，周可"讨论不发言"偶尔 96.30 → 98.15；2024：孙悦"没按时交付"偶尔 92.59 → 两场平均 95.37
--   孙悦 两场都是全"从未" → 100
--   周可 2025：陈一舟 [2,1,1,1,0,0] 66.67，孙悦 [2,2,1,1,1,0] 55.56 → 61.11（推荐时的系数降到 0.844）
-- ============================================================

SET @math_c = (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛');

INSERT INTO competition_edition (competition_id, year, title, team_min, team_max, same_school_required, advisor_required,
                                 registration_start, registration_end, source, status) VALUES
(@math_c, 2025, '2025 年（示例数据）', 1, 3, 1, 0, '2025-08-20 00:00:00', '2025-09-01 23:59:59', 'MANUAL', 'PUBLISHED'),
(@math_c, 2024, '2024 年（示例数据）', 1, 3, 1, 0, '2024-08-20 00:00:00', '2024-09-01 23:59:59', 'MANUAL', 'PUBLISHED');

SET @math25 = (SELECT id FROM competition_edition WHERE competition_id = @math_c AND year = 2025);
SET @math24 = (SELECT id FROM competition_edition WHERE competition_id = @math_c AND year = 2024);

INSERT INTO team (edition_id, name, captain_id, intro, status, max_members, meeting_mode, meeting_slots, award,
                  award_recorded_at, created_at) VALUES
(@math25, '去年的冲刺队', @chen, '2025 年一起打的国赛，拿了省一。', 'FINISHED', 3, 'MIXED', 0, '省级一等奖',
 '2025-09-20 10:00:00', '2025-08-22 10:00:00'),
(@math24, '第一次参赛', @chen, '两个人第一次打国赛。', 'FINISHED', 2, 'MIXED', 0, '省级三等奖',
 '2024-09-20 10:00:00', '2024-08-22 10:00:00');

SET @t25 = (SELECT id FROM team WHERE name = '去年的冲刺队');
SET @t24 = (SELECT id FROM team WHERE name = '第一次参赛');

INSERT INTO team_member (team_id, edition_id, user_id, role_name, is_captain, status, joined_at) VALUES
(@t25, @math25, @chen, '建模', 1, 'ACTIVE', '2025-08-22 10:00:00'),
(@t25, @math25, @sun, '论文写作与排版', 0, 'ACTIVE', '2025-08-23 10:00:00'),
(@t25, @math25, @zhou, '编程', 0, 'ACTIVE', '2025-08-24 10:00:00'),
(@t24, @math24, @chen, '建模', 1, 'ACTIVE', '2024-08-22 10:00:00'),
(@t24, @math24, @sun, '论文写作与排版', 0, 'ACTIVE', '2024-08-23 10:00:00');

-- 陈一舟手填的两条履历改成平台记录；孙悦、周可补上
UPDATE user_experience SET team_id = @t25, source = 'PLATFORM', verified = 1
WHERE user_id = @chen AND competition_id = @math_c AND year = 2025;
UPDATE user_experience SET team_id = @t24, source = 'PLATFORM', verified = 1
WHERE user_id = @chen AND competition_id = @math_c AND year = 2024;
INSERT INTO user_experience (user_id, competition_id, team_id, title, year, award, source, verified) VALUES
(@sun, @math_c, @t25, '全国大学生数学建模竞赛', 2025, '省级一等奖', 'PLATFORM', 1),
(@sun, @math_c, @t24, '全国大学生数学建模竞赛', 2024, '省级三等奖', 'PLATFORM', 1),
(@zhou, @math_c, @t25, '全国大学生数学建模竞赛', 2025, '省级一等奖', 'PLATFORM', 1);

INSERT INTO peer_review_round (team_id, opens_at, closes_at, status, published_at) VALUES
(@t25, '2025-09-20 10:00:00', '2025-09-27 10:00:00', 'PUBLISHED', '2025-09-22 21:30:00'),
(@t24, '2024-09-20 10:00:00', '2024-09-27 10:00:00', 'PUBLISHED', '2024-09-21 20:00:00');

SET @r25 = (SELECT id FROM peer_review_round WHERE team_id = @t25);
SET @r24 = (SELECT id FROM peer_review_round WHERE team_id = @t24);

INSERT INTO peer_review (round_id, rater_id, ratee_id, answers, submitted_at) VALUES
(@r25, @chen, @sun, '[0,0,0,0,0,0]', '2025-09-21 20:00:00'),
(@r25, @chen, @zhou, '[2,1,1,1,0,0]', '2025-09-21 20:00:00'),
(@r25, @chen, @chen, '[0,0,0,0,0,0]', '2025-09-21 20:00:00'),
(@r25, @sun, @chen, '[0,0,0,0,0,0]', '2025-09-22 12:00:00'),
(@r25, @sun, @zhou, '[2,2,1,1,1,0]', '2025-09-22 12:00:00'),
(@r25, @zhou, @chen, '[0,0,0,1,0,0]', '2025-09-22 21:30:00'),
(@r25, @zhou, @sun, '[0,0,0,0,0,0]', '2025-09-22 21:30:00'),
(@r25, @zhou, @zhou, '[0,0,0,0,0,0]', '2025-09-22 21:30:00'),
(@r24, @chen, @sun, '[0,0,0,0,0,0]', '2024-09-21 19:00:00'),
(@r24, @sun, @chen, '[0,1,0,0,0,0]', '2024-09-21 20:00:00');

INSERT INTO user_credit (user_id, score, rated_rounds) VALUES
(@chen, 95.37, 2),
(@sun, 100.00, 2),
(@zhou, 61.11, 1);

-- ============================================================
-- v2.1：个人日程、个人资料页
-- ============================================================

SET @d = CURDATE();

-- 个人日程。林晓的英语课在她勾的晚上空闲时段里，主页的"本周可投入"会扣掉；
-- "复习线代"开始和结束都说不准（日历上是虚线框，不参与撞期检测）；
-- 陈一舟的社团换届在第 18 天下午，国赛比赛是第 17–20 天，日历上两边都会标出撞期
INSERT INTO personal_event (user_id, title, start_at, end_at, all_day, start_latest_at, end_mode, end_latest_at,
                            repeat_type, repeat_weekdays, repeat_until, exceptions, busy) VALUES
(@lin, '英语课', TIMESTAMP(@d, '19:00:00'), TIMESTAMP(@d, '20:30:00'), 0, NULL, 'FIXED', NULL,
 'WEEKLY', '2,4', @d + INTERVAL 60 DAY, '[]', 1),
(@lin, '复习线代', @d + INTERVAL 3 DAY, @d + INTERVAL 20 DAY, 1, @d + INTERVAL 10 DAY, 'RANGE', @d + INTERVAL 30 DAY,
 'NONE', NULL, NULL, '[]', 0),
(@lin, '校运动会', @d + INTERVAL 12 DAY, NULL, 1, NULL, 'FIXED', NULL, 'NONE', NULL, NULL, '[]', 1),
(@chen, '社团换届', TIMESTAMP(@d + INTERVAL 18 DAY, '14:00:00'), TIMESTAMP(@d + INTERVAL 18 DAY, '16:00:00'), 0, NULL, 'FIXED', NULL,
 'NONE', NULL, NULL, '[]', 1);

-- 问卷里"希望队友包容我的一个短板"：资料页上只给队友和主动联系过的人看
UPDATE survey_response SET answers = JSON_SET(answers, '$.F2', '容易一个人闷头做，进度要记得在群里说')
WHERE user_id = @chen AND edition_id IS NULL;
UPDATE survey_response SET answers = JSON_SET(answers, '$.F2', '代码能力一般，复杂的程序要靠队友')
WHERE user_id = @sun AND edition_id IS NULL;
UPDATE survey_response SET answers = JSON_SET(answers, '$.F2', '晚上 11 点以后基本不在线')
WHERE user_id = @lin AND edition_id IS NULL;

-- 带证明链接的履历（示例链接）：资料页上只有队友和主动联系过的人看得到链接，其他人只看到"有证明材料"
UPDATE user_experience SET proof_url = 'https://example.com/demo/linxiao-2025-award.jpg'
WHERE user_id = @lin AND year = 2025;
INSERT INTO user_experience (user_id, competition_id, title, year, award, proof_url, source) VALUES
(@sun, NULL, '全国大学生英语竞赛', 2025, '校级一等奖', 'https://example.com/demo/sunyue-english.jpg', 'MANUAL');

-- 王思远把参赛履历和组队记录设成只给有关联的人看（没关系的人打开他的资料页，会看到"履历没有公开"的提示）
INSERT INTO user_privacy (user_id, history_visibility) VALUES (@wang, 'RELATED');

-- 各校加分认定：同校同学标注，另外 2 位同学确认后显示（确认人数不算提交人）。文件链接是示例链接
SET @c_math = (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛');
SET @c_cs = (SELECT id FROM competition WHERE name = '中国大学生计算机设计大赛');
INSERT INTO school_competition_recognition (school_id, competition_id, grade_label, note, source_url, submitted_by,
                                            confirm_count, status) VALUES
(@school, @c_math, 'A 类', '国家级一等奖加 4 分、二等奖加 3 分，省级奖项减半（2025 版综测细则）',
 'https://example.com/demo/zongce-2025.pdf', @chen, 2, 'CONFIRMED'),
(@school, @c_cs, 'B 类', '学院文件里写的是 B 类，国奖加 2 分', NULL, @wang, 1, 'PENDING');
INSERT INTO recognition_confirm (recognition_id, user_id)
SELECT r.id, u.id FROM school_competition_recognition r JOIN sys_user u ON u.id IN (@wang, @zhao)
WHERE r.school_id = @school AND r.competition_id = @c_math;
INSERT INTO recognition_confirm (recognition_id, user_id)
SELECT r.id, @qian FROM school_competition_recognition r WHERE r.school_id = @school AND r.competition_id = @c_cs;
