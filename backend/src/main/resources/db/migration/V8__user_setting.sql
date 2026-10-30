-- 个人偏好设置（Flyway 迁移 V8）：先放主页的自定义——面板顺序、隐藏了哪些面板、日历栏是否加宽。
-- 一人一行，没设置过的人没有这一行，主页用默认排法。
CREATE TABLE user_setting (
  user_id     BIGINT   PRIMARY KEY,
  home_layout JSON     NULL COMMENT '主页自定义：{"order": [...], "hidden": [...], "wideCalendar": false}',
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_user_setting_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '个人偏好设置';
