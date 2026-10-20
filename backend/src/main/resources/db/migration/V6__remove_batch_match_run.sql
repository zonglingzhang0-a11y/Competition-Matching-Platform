-- 去掉没用上的批量撮合记录表（Flyway 迁移 V6）。
-- 最初设计是报名截止前系统统一分组、把结果存在 batch_match_run 里；后来改成"只给建议"：
-- 每个人打开比赛页时现算几种合适的组合，组不组自己决定，不需要存分组结果。
DROP TABLE IF EXISTS batch_match_run;
