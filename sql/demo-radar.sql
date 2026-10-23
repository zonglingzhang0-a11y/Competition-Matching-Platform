-- 已经导入过演示数据（demo.sql）的库，给演示队伍的招募需求多补几项技能要求，用来看需求雷达图。
-- 可以重复跑：同一项技能要求只保留更高的那个最低熟练度。
-- 新导入 demo.sql 的库也可以跑一次；没导入演示数据的库跑了不会插入任何东西。
--
-- 补完以后，数模冲刺队的需求雷达会横跨编程开发、建模与算法、数据与调研、设计表达、写作答辩五类，
-- 而不是只有一两根线，方便演示"这支队到底缺什么样的人"。

SET NAMES utf8mb4;
USE team_match;

INSERT INTO team_need_skill (need_id, skill_id, min_level)
SELECT tn.id, s.id, x.lv FROM (
  -- 数模冲刺队：编程这个角色除了 Python，还想要会做数据处理和画图的
  SELECT '数模冲刺队' AS team, '编程' AS role, '数据分析' AS skill, 2 AS lv UNION ALL
  SELECT '数模冲刺队', '编程', 'MATLAB', 1 UNION ALL
  SELECT '数模冲刺队', '编程', '可视化图表', 1 UNION ALL
  -- 数模冲刺队：论文这个角色，除了论文写作和 LaTeX，还想要能读英文文献、写技术文档的
  SELECT '数模冲刺队', '论文写作与排版', '技术文档', 1 UNION ALL
  SELECT '数模冲刺队', '论文写作与排版', '英文文献阅读', 1 UNION ALL
  -- 周末慢慢来（数模）：编程想要一点机器学习
  SELECT '周末慢慢来', '编程', '机器学习', 1 UNION ALL
  -- 国一冲冲冲：C++ 算法之外，最好懂运筹优化
  SELECT '国一冲冲冲', '编程', '运筹优化', 1 UNION ALL
  -- 计设作品组：前端除了 Vue 和小程序，还要能做交互、会部署
  SELECT '计设作品组', '前端', '交互设计', 1 UNION ALL
  SELECT '计设作品组', '前端', 'Docker/部署', 1
) x
JOIN team t ON t.name = x.team AND t.deleted = 0
JOIN team_need tn ON tn.team_id = t.id AND tn.role_name = x.role AND tn.status <> 'CANCELLED'
JOIN skill s ON s.name = x.skill
ON DUPLICATE KEY UPDATE min_level = GREATEST(team_need_skill.min_level, VALUES(min_level));
