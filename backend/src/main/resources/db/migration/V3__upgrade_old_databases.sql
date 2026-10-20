-- 旧库补齐（Flyway 迁移 V3）：把以前要手工跑的 5 个升级脚本合成一个，每一步先看缺不缺，缺了才补。
--
-- 为什么要有这一步：用 Flyway 之前，大家是手工导入 schema.sql、seed.sql 建的库，之后各自跑过几个
-- sql/upgrade/ 里的脚本，跑到哪一步不一定。这种没有迁移记录的库，后端第一次启动时会被记成"已经到 V2"
-- （application.yml 里的 baseline-version），然后执行这个文件，缺什么补什么。
-- 新建的库 V1 里已经都有了，这里每一步都会跳过。
--
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，所以加字段、加索引、加外键都先查 information_schema，
-- 再用 PREPARE 执行；不需要的时候执行的是一句什么都不做的 DO 0。

-- ---------- 1. 爬虫的抓取来源（原 2026-10-crawler.sql） ----------

SET @new_crawl_source = (SELECT COUNT(*) = 0 FROM information_schema.TABLES
                         WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_source');

CREATE TABLE IF NOT EXISTS crawl_source (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  competition_id   BIGINT       NOT NULL COMMENT '抓到的通知归到哪个比赛',
  name             VARCHAR(64)  NOT NULL,
  list_url         VARCHAR(512) NOT NULL COMMENT '通知列表页（或首页）',
  link_selector    VARCHAR(255) NOT NULL DEFAULT 'a' COMMENT 'CSS 选择器：列表页里的通知链接',
  title_keywords   VARCHAR(255) NULL COMMENT '标题至少包含其中一个才抓，逗号分隔；空表示都抓',
  exclude_keywords VARCHAR(255) NULL COMMENT '标题包含其中任何一个就跳过，逗号分隔，如：获奖,名单,公示',
  content_selector VARCHAR(255) NULL COMMENT '详情页正文的 CSS 选择器；空表示取整页文字',
  allow_llm        TINYINT      NOT NULL DEFAULT 1 COMMENT '网站不希望内容被 AI 使用时关掉，只用规则抽取',
  enabled          TINYINT      NOT NULL DEFAULT 1,
  note             VARCHAR(255) NULL,
  last_run_at      DATETIME     NULL,
  last_result      VARCHAR(255) NULL COMMENT '上次抓取的结果，比如：新通知 2 条 / 出错原因',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_crawl_source_competition FOREIGN KEY (competition_id) REFERENCES competition (id)
) COMMENT '爬虫的抓取来源（每个比赛官网一条，管理员维护）';

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_record' AND COLUMN_NAME = 'source_id') = 0,
              'ALTER TABLE crawl_record ADD COLUMN source_id BIGINT NULL COMMENT ''从哪个抓取来源抓到的'' AFTER id',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_record' AND COLUMN_NAME = 'note') = 0,
              'ALTER TABLE crawl_record ADD COLUMN note VARCHAR(255) NULL COMMENT ''自动忽略的原因，比如往年的旧通知'' AFTER status',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_record' AND INDEX_NAME = 'idx_crawl_url') = 0,
              'ALTER TABLE crawl_record ADD KEY idx_crawl_url (source_url(191))',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'crawl_record' AND CONSTRAINT_NAME = 'fk_crawl_source') = 0,
              'ALTER TABLE crawl_record ADD CONSTRAINT fk_crawl_source FOREIGN KEY (source_id) REFERENCES crawl_source (id)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 抓取来源只在这张表是刚建的时候填；已经有的（管理员可能改过、删过）不动。
-- 选择器是对着 2026 年 10 月各官网的页面结构写的；挑战杯的 robots.txt 不希望内容被 AI 使用，所以 allow_llm = 0、默认停用
INSERT INTO crawl_source (competition_id, name, list_url, link_selector, title_keywords, exclude_keywords,
                          content_selector, allow_llm, enabled, note)
SELECT c.id, s.name, s.list_url, s.link_selector, s.title_keywords, s.exclude_keywords, s.content_selector,
       s.allow_llm, s.enabled, s.note
FROM (
  SELECT '全国大学生数学建模竞赛' AS competition, '数模官网首页' AS name, 'https://www.mcm.edu.cn/' AS list_url,
         '#recentNodesList a' AS link_selector, '竞赛的通知,报名' AS title_keywords, '获奖,名单,公示,赛题' AS exclude_keywords,
         '#divNodeBody' AS content_selector, 1 AS allow_llm, 1 AS enabled, NULL AS note UNION ALL
  SELECT '全国大学生电子设计竞赛', '电赛培训网首页', 'https://www.nuedc-training.com.cn/',
         'a[href*=/news/details/]', '竞赛的通知,第一次通知,报名', '获奖,名单,公示,赛题,清单',
         '.newsMain-content', 1, 1, NULL UNION ALL
  SELECT '中国大学生计算机设计大赛', '计设大赛官网首页', 'https://jsjds.blcu.edu.cn/',
         'a[href*=info/]', '通知,报名', '获奖,名单,公示,结果', NULL, 1, 1, NULL UNION ALL
  SELECT '"挑战杯"全国大学生课外学术科技作品竞赛', '挑战杯官网首页', 'https://www.tiaozhanbei.net/',
         'a[href*=/article/]', '通知', '开幕,闭幕,获奖,名单', NULL, 0, 0,
         'robots.txt 不希望内容被 AI 使用：只用规则抽取。默认不启用，需要时管理员打开'
) s JOIN competition c ON c.name = s.competition
WHERE @new_crawl_source = 1
ORDER BY c.id;

-- ---------- 2. 日历订阅（原 2026-10-calendar-feed.sql） ----------

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'calendar_token') = 0,
              'ALTER TABLE sys_user ADD COLUMN calendar_token CHAR(64) NULL COMMENT ''日历订阅链接里的密钥，重置后旧链接失效'' AFTER last_login_at',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND INDEX_NAME = 'uk_user_calendar_token') = 0,
              'ALTER TABLE sys_user ADD UNIQUE KEY uk_user_calendar_token (calendar_token)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 3. 登录状态存数据库（原 2026-10-login-store.sql） ----------

CREATE TABLE IF NOT EXISTS login_store (
  store_key   VARCHAR(255) PRIMARY KEY COMMENT 'Sa-Token 的键，比如 Authorization:login:token:<登录凭证>',
  value_type  CHAR(1)      NOT NULL COMMENT 'S 字符串 / O 对象（JSON）',
  store_value MEDIUMTEXT   NOT NULL,
  expire_at   DATETIME     NULL COMMENT '过期时间，空表示永不过期',
  KEY idx_login_store_expire (expire_at)
) COMMENT '登录状态（Sa-Token 的数据，内存里一份、这里存一份），后端重启后不用重新登录。里面有登录凭证，不要外传';

-- ---------- 4. 新消息汇总发邮件（原 2026-10-notify-mail.sql） ----------

SET @sql = IF((SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email_notify') = 0,
              'ALTER TABLE sys_user ADD COLUMN email_notify TINYINT NOT NULL DEFAULT 1 COMMENT ''1 = 不在网站上时，把没看的新消息汇总发到邮箱'' AFTER calendar_token',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @new_emailed_at = (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND COLUMN_NAME = 'emailed_at');
SET @sql = IF(@new_emailed_at,
              'ALTER TABLE notification ADD COLUMN emailed_at DATETIME NULL COMMENT ''汇总发到邮箱的时间'' AFTER read_at',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 升级前就有的消息不补发。只在这个字段是刚加的时候做，否则会把还没发的消息也标成发过了
SET @sql = IF(@new_emailed_at, 'UPDATE notification SET emailed_at = created_at WHERE emailed_at IS NULL', 'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql = IF((SELECT COUNT(*) FROM information_schema.STATISTICS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification' AND INDEX_NAME = 'idx_notification_mail') = 0,
              'ALTER TABLE notification ADD KEY idx_notification_mail (emailed_at, created_at)',
              'DO 0');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ---------- 5. 个人资料页的隐私设置（原 2026-10-user-privacy.sql） ----------

CREATE TABLE IF NOT EXISTS user_privacy (
  user_id            BIGINT PRIMARY KEY,
  history_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC 所有已认证同学 / RELATED 只给队友和主动联系过的人',
  updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_privacy_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '隐私设置：个人资料页上的参赛履历和组队记录给谁看';
