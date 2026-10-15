-- 升级脚本：新消息汇总发邮件。新建库直接用 schema.sql，不用跑这个。
-- 只能跑一次（第二次会因为字段已经存在而报错，不影响数据）。

SET NAMES utf8mb4;
USE team_match;

ALTER TABLE sys_user
  ADD COLUMN email_notify TINYINT NOT NULL DEFAULT 1 COMMENT '1 = 不在网站上时，把没看的新消息汇总发到邮箱' AFTER calendar_token;

ALTER TABLE notification
  ADD COLUMN emailed_at DATETIME NULL COMMENT '汇总发到邮箱的时间' AFTER read_at,
  ADD KEY idx_notification_mail (emailed_at, created_at);

-- 升级前就有的消息不补发
UPDATE notification SET emailed_at = created_at WHERE emailed_at IS NULL;
