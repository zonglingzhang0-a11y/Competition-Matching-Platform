-- 竞赛组队匹配平台 · 演示数据
-- 在 schema.sql、seed.sql 之后执行，只用于本地演示和答辩，不要导入正式环境。
--
-- 演示账号（都已经通过"示例大学"的教育邮箱认证），密码统一是 demo1234：
--   linxiao@demo.local   林晓    主角：还没组队，关注了数学建模和计算机设计大赛，看"为你推荐"
--   chen@demo.local      陈一舟  "数模冲刺队"队长，在招编程和论文，收到了孙悦的申请
--   wang@demo.local      王思远  "周末慢慢来"队长（数学建模），"计设作品组"队长（计算机设计大赛）
--   zhao@demo.local      赵子航  "国一冲冲冲"队长
--   qian@demo.local      钱多多  "国一冲冲冲"队员
--   sun@demo.local       孙悦    还没组队，擅长论文排版，向"数模冲刺队"发了申请
--   li@demo.local        李想    还没组队，只想先体验（对冲国一的队伍会被一票否决）
--   zhou@demo.local      周可    还没组队，没填问卷（匹配度显示 50 分）
--   admin@demo.local     管理员  可以调管理员接口

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
('admin@demo.local', @pwd, '管理员', 'ADMIN', 'ACTIVE', NULL, 'NONE', NULL);

SET @lin = (SELECT id FROM sys_user WHERE email = 'linxiao@demo.local');
SET @chen = (SELECT id FROM sys_user WHERE email = 'chen@demo.local');
SET @wang = (SELECT id FROM sys_user WHERE email = 'wang@demo.local');
SET @zhao = (SELECT id FROM sys_user WHERE email = 'zhao@demo.local');
SET @qian = (SELECT id FROM sys_user WHERE email = 'qian@demo.local');
SET @sun = (SELECT id FROM sys_user WHERE email = 'sun@demo.local');
SET @li = (SELECT id FROM sys_user WHERE email = 'li@demo.local');
SET @zhou = (SELECT id FROM sys_user WHERE email = 'zhou@demo.local');
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
(@admin, NULL, NULL, NULL, NULL, NULL, NULL);

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
