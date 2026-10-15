-- 升级脚本：日历订阅（订阅到手机日历）。新建库直接用 schema.sql，不用跑这个。
-- 只能跑一次（第二次会因为字段已经存在而报错，不影响数据）。

SET NAMES utf8mb4;
USE team_match;

ALTER TABLE sys_user
  ADD COLUMN calendar_token CHAR(64) NULL COMMENT '日历订阅链接里的密钥，重置后旧链接失效' AFTER last_login_at,
  ADD UNIQUE KEY uk_user_calendar_token (calendar_token);
