-- 已经导入过演示数据（demo.sql）的库，补上"各校加分认定"的演示内容。可以重复跑，不会重复插入。
-- 新导入 demo.sql 的不用跑，里面已经有了；没导入过演示数据的库跑了也不会插入任何东西。
--
-- 内容：示例大学把数学建模认定为 A 类（陈一舟标注，王思远、赵子航确认，已显示）；
-- 计算机设计大赛王思远标的是 B 类，钱多多确认了，还差一人。

SET NAMES utf8mb4;
USE team_match;

SET @school = (SELECT id FROM school WHERE name = '示例大学');
SET @chen = (SELECT id FROM sys_user WHERE email = 'chen@demo.local');
SET @wang = (SELECT id FROM sys_user WHERE email = 'wang@demo.local');
SET @zhao = (SELECT id FROM sys_user WHERE email = 'zhao@demo.local');
SET @qian = (SELECT id FROM sys_user WHERE email = 'qian@demo.local');
SET @c_math = (SELECT id FROM competition WHERE name = '全国大学生数学建模竞赛');
SET @c_cs = (SELECT id FROM competition WHERE name = '中国大学生计算机设计大赛');

-- 同一个人对同一个比赛只能有一条（唯一键），已经有了就跳过
INSERT IGNORE INTO school_competition_recognition (school_id, competition_id, grade_label, note, source_url,
                                                   submitted_by, confirm_count, status) VALUES
(@school, @c_math, 'A 类', '国家级一等奖加 4 分、二等奖加 3 分，省级奖项减半（2025 版综测细则）',
 'https://example.com/demo/zongce-2025.pdf', @chen, 0, 'PENDING'),
(@school, @c_cs, 'B 类', '学院文件里写的是 B 类，国奖加 2 分', NULL, @wang, 0, 'PENDING');

INSERT IGNORE INTO recognition_confirm (recognition_id, user_id)
SELECT r.id, u.id FROM school_competition_recognition r JOIN sys_user u ON u.id IN (@wang, @zhao)
WHERE r.school_id = @school AND r.competition_id = @c_math AND r.submitted_by = @chen;
INSERT IGNORE INTO recognition_confirm (recognition_id, user_id)
SELECT r.id, @qian FROM school_competition_recognition r
WHERE r.school_id = @school AND r.competition_id = @c_cs AND r.submitted_by = @wang;

-- 按确认记录重新算人数和状态（另外 2 位同学确认算确认够了；被管理员驳回的不动）
UPDATE school_competition_recognition r
SET r.confirm_count = (SELECT COUNT(*) FROM recognition_confirm c WHERE c.recognition_id = r.id),
    r.status = IF(r.status = 'REJECTED', 'REJECTED', IF(r.confirm_count >= 2, 'CONFIRMED', 'PENDING'))
WHERE r.school_id = @school AND r.competition_id IN (@c_math, @c_cs);
