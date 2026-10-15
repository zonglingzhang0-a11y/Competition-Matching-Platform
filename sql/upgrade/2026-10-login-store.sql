-- 升级脚本：登录状态存进数据库，后端重启后不用重新登录。新建库直接用 schema.sql，不用跑这个。
-- 可以重复跑。不跑也能用，只是和以前一样，重启后端后大家要重新登录。

SET NAMES utf8mb4;
USE team_match;

CREATE TABLE IF NOT EXISTS login_store (
  store_key   VARCHAR(255) PRIMARY KEY COMMENT 'Sa-Token 的键，比如 Authorization:login:token:<登录凭证>',
  value_type  CHAR(1)      NOT NULL COMMENT 'S 字符串 / O 对象（JSON）',
  store_value MEDIUMTEXT   NOT NULL,
  expire_at   DATETIME     NULL COMMENT '过期时间，空表示永不过期',
  KEY idx_login_store_expire (expire_at)
) COMMENT '登录状态（Sa-Token 的数据，内存里一份、这里存一份），后端重启后不用重新登录。里面有登录凭证，不要外传';
