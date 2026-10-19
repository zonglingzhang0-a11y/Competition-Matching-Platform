-- 升级脚本：个人资料页的隐私设置（参赛履历和组队记录给谁看）。新建库直接用 schema.sql，不用跑这个。
-- 可以重复跑。不跑也能用：所有人的资料页都按"公开"处理，只是在档案页改可见范围时会提示先跑这个脚本。

SET NAMES utf8mb4;
USE team_match;

CREATE TABLE IF NOT EXISTS user_privacy (
  user_id            BIGINT PRIMARY KEY,
  history_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC 所有已认证同学 / RELATED 只给队友和主动联系过的人',
  updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_privacy_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '隐私设置：个人资料页上的参赛履历和组队记录给谁看';
