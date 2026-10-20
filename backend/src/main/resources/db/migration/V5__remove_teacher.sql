-- 去掉"指导老师"相关的表和字段（Flyway 迁移 V5）。
-- 指导老师都是同学自己找的，平台不做老师角色和老师课题。下面这些从 v1 起就建着，一直没用过：
--   advisor_topic       老师发布的课题
--   team.advisor_id     队伍的指导老师账号
--   sys_user.role 里的 TEACHER
-- 比赛"是否需要指导老师"（competition_edition.advisor_required）是比赛本身的规定，照常保留、照常显示。

DROP TABLE IF EXISTS advisor_topic;

ALTER TABLE team DROP FOREIGN KEY fk_team_advisor;
ALTER TABLE team DROP COLUMN advisor_id;

-- 万一有人手工把账号改成过 TEACHER，按学生处理
UPDATE sys_user SET role = 'STUDENT' WHERE role = 'TEACHER';
ALTER TABLE sys_user MODIFY role VARCHAR(16) NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT / ADMIN';
