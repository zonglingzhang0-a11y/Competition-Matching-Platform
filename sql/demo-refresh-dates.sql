-- 演示数据的日期挪到今天附近：两届"2026 年（示例数据）"的报名重新开着，比赛节点跟着挪。
-- 和 seed.sql 里的排法一样：数模 9 天后截止报名，第 17–20 天比赛，第 40–55 天出成绩；计设 35 天后截止报名。
-- 不删任何数据，可以重复跑（每次都按跑的那天重新排）。往届（2024、2025）的数据不动。

SET NAMES utf8mb4;
USE team_match;

SET @d = CURDATE();
SET @math = (SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id
             WHERE c.name = '全国大学生数学建模竞赛' AND e.title = '2026 年（示例数据）');
SET @cs = (SELECT e.id FROM competition_edition e JOIN competition c ON c.id = e.competition_id
           WHERE c.name = '中国大学生计算机设计大赛' AND e.title = '2026 年（示例数据）');

UPDATE competition_edition
SET registration_start = @d - INTERVAL 16 DAY, registration_end = TIMESTAMP(@d + INTERVAL 9 DAY, '23:59:59')
WHERE id = @math;

UPDATE competition_edition
SET registration_start = @d - INTERVAL 5 DAY, registration_end = TIMESTAMP(@d + INTERVAL 35 DAY, '23:59:59')
WHERE id = @cs;

UPDATE competition_node SET start_at = TIMESTAMP(@d + INTERVAL 9 DAY, '23:59:59')
WHERE edition_id = @math AND title = '报名截止';

UPDATE competition_node
SET start_at = TIMESTAMP(@d + INTERVAL 17 DAY, '18:00:00'), end_at = TIMESTAMP(@d + INTERVAL 20 DAY, '20:00:00')
WHERE edition_id = @math AND title = '比赛进行（72 小时）';

UPDATE competition_node
SET start_at = TIMESTAMP(@d + INTERVAL 40 DAY), end_at = TIMESTAMP(@d + INTERVAL 40 DAY),
    end_latest_at = TIMESTAMP(@d + INTERVAL 55 DAY)
WHERE edition_id = @math AND title = '成绩公布';

SELECT c.name AS 比赛, e.registration_end AS 报名截止 FROM competition_edition e JOIN competition c ON c.id = e.competition_id
WHERE e.id IN (@math, @cs);
