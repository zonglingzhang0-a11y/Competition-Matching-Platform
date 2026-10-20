-- 竞赛组队匹配平台 · 数据库结构（Flyway 迁移 V1）
-- MySQL 8.0（需要 8.0.16 以上，CHECK 约束才会生效），字符集 utf8mb4
-- 说明见 docs/数据库设计.md
--
-- 后端启动时由 Flyway 自动执行，不用手工导入；库不存在时 JDBC 连接串里的 createDatabaseIfNotExist 会先建库。
-- 已经发布的迁移文件不要再改（改了以后，跑过它的库启动时会报校验和不一致）。要改表结构就新加一个
-- V<下一个编号>__做什么.sql，写法见 CONTRIBUTING.md。

-- ============================================================
-- 一、用户与认证
-- ============================================================

CREATE TABLE school (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(64) NOT NULL COMMENT '学校全称',
  province   VARCHAR(16) NULL,
  city       VARCHAR(32) NULL,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_school_name (name)
) COMMENT '学校';

CREATE TABLE school_email_domain (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id  BIGINT      NOT NULL,
  domain     VARCHAR(64) NOT NULL COMMENT '邮箱 @ 后面的部分，如 stu.xxx.edu.cn',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_domain (domain),
  CONSTRAINT fk_domain_school FOREIGN KEY (school_id) REFERENCES school (id)
) COMMENT '教育邮箱域名 → 学校';

CREATE TABLE sys_user (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  email          VARCHAR(128) NOT NULL COMMENT '登录邮箱，不要求是教育邮箱',
  password_hash  VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  nickname       VARCHAR(32)  NOT NULL,
  avatar_url     VARCHAR(255) NULL,
  role           VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT / TEACHER / ADMIN',
  status         VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / BANNED',
  school_id      BIGINT       NULL COMMENT '认证通过后写入',
  verify_status  VARCHAR(16)  NOT NULL DEFAULT 'NONE' COMMENT 'NONE / PENDING / VERIFIED',
  verified_until DATE         NULL COMMENT '认证有效期，每学年重新认证',
  last_login_at  DATETIME     NULL,
  calendar_token CHAR(64)     NULL COMMENT '日历订阅链接里的密钥，重置后旧链接失效',
  email_notify   TINYINT      NOT NULL DEFAULT 1 COMMENT '1 = 不在网站上时，把没看的新消息汇总发到邮箱',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_email (email),
  UNIQUE KEY uk_user_calendar_token (calendar_token),
  KEY idx_user_school (school_id),
  CONSTRAINT fk_user_school FOREIGN KEY (school_id) REFERENCES school (id)
) COMMENT '账号';

CREATE TABLE user_profile (
  user_id        BIGINT PRIMARY KEY,
  real_name      VARCHAR(32)  NULL COMMENT '只给队友看',
  gender         VARCHAR(8)   NULL COMMENT 'MALE / FEMALE / OTHER，选填',
  major          VARCHAR(64)  NULL,
  enroll_year    SMALLINT     NULL COMMENT '入学年份，用来算年级',
  degree         VARCHAR(16)  NULL COMMENT 'JUNIOR_COLLEGE / UNDERGRAD / MASTER / PHD',
  bio            VARCHAR(500) NULL,
  contact_wechat VARCHAR(64)  NULL COMMENT '只给队友看',
  contact_qq     VARCHAR(20)  NULL COMMENT '只给队友看',
  updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '个人档案（和账号一对一）';

CREATE TABLE user_privacy (
  user_id            BIGINT PRIMARY KEY,
  history_visibility VARCHAR(16) NOT NULL DEFAULT 'PUBLIC' COMMENT 'PUBLIC 所有已认证同学 / RELATED 只给队友和主动联系过的人',
  updated_at         DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_privacy_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '隐私设置：个人资料页上的参赛履历和组队记录给谁看';

CREATE TABLE email_code (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  email      VARCHAR(128) NOT NULL,
  purpose    VARCHAR(16)  NOT NULL COMMENT 'REGISTER / EDU_VERIFY / RESET_PASSWORD',
  code_hash  CHAR(64)     NOT NULL COMMENT '验证码的 SHA-256，不存明文',
  attempts   TINYINT      NOT NULL DEFAULT 0 COMMENT '已尝试次数，超过 5 次作废',
  expires_at DATETIME     NOT NULL,
  used_at    DATETIME     NULL,
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_code_lookup (email, purpose, created_at)
) COMMENT '邮箱验证码（上了 Redis 以后可以改存 Redis）';

CREATE TABLE login_store (
  store_key   VARCHAR(255) PRIMARY KEY COMMENT 'Sa-Token 的键，比如 Authorization:login:token:<登录凭证>',
  value_type  CHAR(1)      NOT NULL COMMENT 'S 字符串 / O 对象（JSON）',
  store_value MEDIUMTEXT   NOT NULL,
  expire_at   DATETIME     NULL COMMENT '过期时间，空表示永不过期',
  KEY idx_login_store_expire (expire_at)
) COMMENT '登录状态（Sa-Token 的数据，内存里一份、这里存一份），后端重启后不用重新登录。里面有登录凭证，不要外传';

CREATE TABLE verification_request (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  method         VARCHAR(16)  NOT NULL COMMENT 'EDU_EMAIL / SCREENSHOT',
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / APPROVED / REJECTED',
  school_id      BIGINT       NULL,
  edu_email      VARCHAR(128) NULL COMMENT 'EDU_EMAIL 方式',
  image_path     VARCHAR(255) NULL COMMENT 'SCREENSHOT 方式的原图，审核完清空并删除文件',
  ocr_result     JSON         NULL COMMENT 'OCR 识别出的字段：姓名、学校、入学年份',
  ocr_confidence VARCHAR(8)   NULL COMMENT 'HIGH：和档案一致 / LOW：有出入',
  reviewer_id    BIGINT       NULL,
  review_note    VARCHAR(255) NULL,
  reviewed_at    DATETIME     NULL,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_verify_user (user_id),
  KEY idx_verify_queue (status, created_at),
  CONSTRAINT fk_verify_user     FOREIGN KEY (user_id)     REFERENCES sys_user (id),
  CONSTRAINT fk_verify_school   FOREIGN KEY (school_id)   REFERENCES school (id),
  CONSTRAINT fk_verify_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user (id)
) COMMENT '身份认证申请';

-- ============================================================
-- 二、技能
-- ============================================================

CREATE TABLE skill (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(32) NOT NULL,
  category   VARCHAR(16) NOT NULL COMMENT '编程开发 / 算法与数据 / 设计 / 写作表达 / 硬件',
  sort       INT         NOT NULL DEFAULT 0,
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_skill_name (name)
) COMMENT '技能字典（管理员维护）';

CREATE TABLE user_skill (
  user_id  BIGINT  NOT NULL,
  skill_id BIGINT  NOT NULL,
  level    TINYINT NOT NULL COMMENT '1 入门 / 2 熟练 / 3 精通',
  PRIMARY KEY (user_id, skill_id),
  KEY idx_user_skill_skill (skill_id),
  CONSTRAINT chk_user_skill_level CHECK (level BETWEEN 1 AND 3),
  CONSTRAINT fk_user_skill_user  FOREIGN KEY (user_id)  REFERENCES sys_user (id),
  CONSTRAINT fk_user_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) COMMENT '个人技能';

-- ============================================================
-- 三、比赛库
-- ============================================================

CREATE TABLE role_template (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  name       VARCHAR(32) NOT NULL COMMENT '如：数学建模类',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_role_template_name (name)
) COMMENT '队内分工模板';

CREATE TABLE role_template_item (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_id BIGINT      NOT NULL,
  name        VARCHAR(32) NOT NULL COMMENT '如：建模、编程、论文写作与排版',
  sort        INT         NOT NULL DEFAULT 0,
  UNIQUE KEY uk_role_item (template_id, name),
  CONSTRAINT fk_role_item_template FOREIGN KEY (template_id) REFERENCES role_template (id)
) COMMENT '分工模板里的角色';

CREATE TABLE competition (
  id               BIGINT PRIMARY KEY AUTO_INCREMENT,
  name             VARCHAR(100) NOT NULL,
  short_name       VARCHAR(32)  NULL COMMENT '如：国赛、电赛',
  organizer        VARCHAR(200) NULL COMMENT '主办单位',
  category         VARCHAR(16)  NOT NULL COMMENT '数学 / 计算机 / 电子 / 创新创业 / 设计 / 外语 / 其他',
  level            VARCHAR(16)  NOT NULL COMMENT 'NATIONAL / PROVINCIAL / SCHOOL',
  in_cahe_list     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否在中国高等教育学会的竞赛目录里',
  official_url     VARCHAR(255) NULL,
  description      TEXT         NULL,
  role_template_id BIGINT       NULL COMMENT '问卷 P4 的选项来源',
  match_preset     VARCHAR(20)  NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT / SHORT_INTENSIVE / LONG_TERM，对应 match_config',
  created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted          TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_competition_name (name),
  CONSTRAINT fk_competition_template FOREIGN KEY (role_template_id) REFERENCES role_template (id)
) COMMENT '比赛（每年都办的那个系列）';

CREATE TABLE competition_edition (
  id                   BIGINT PRIMARY KEY AUTO_INCREMENT,
  competition_id       BIGINT       NOT NULL,
  year                 SMALLINT     NOT NULL,
  title                VARCHAR(64)  NOT NULL COMMENT '如：2026 年',
  team_min             TINYINT      NOT NULL DEFAULT 1,
  team_max             TINYINT      NOT NULL,
  same_school_required TINYINT      NOT NULL DEFAULT 1 COMMENT '是否限同校组队',
  advisor_required     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否需要指导老师',
  english_required     TINYINT      NOT NULL DEFAULT 0 COMMENT '英文赛题：队里至少一人英文读写没问题',
  registration_start   DATETIME     NULL,
  registration_end     DATETIME     NULL,
  notice_url           VARCHAR(255) NULL COMMENT '官方通知原文',
  source               VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / CRAWLER',
  status               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT / PUBLISHED / ARCHIVED',
  created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted              TINYINT      NOT NULL DEFAULT 0,
  UNIQUE KEY uk_edition (competition_id, title),
  KEY idx_edition_deadline (status, registration_end),
  CONSTRAINT chk_edition_team_size CHECK (team_min >= 1 AND team_max >= team_min),
  CONSTRAINT fk_edition_competition FOREIGN KEY (competition_id) REFERENCES competition (id)
) COMMENT '比赛届次：具体日期、人数和组队规则都按届存';

-- 时间字段沿用简程的日程模型：
--   start_at / end_at      确定的开始、结束
--   start_latest_at        开始说不准时，最晚可能的开始时间
--   end_mode = RANGE       结束在 end_at 到 end_latest_at 之间，比如"成绩预计 11 月中下旬公布"
--   end_mode = OPEN        还没有结束时间
CREATE TABLE competition_node (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  edition_id      BIGINT       NOT NULL,
  title           VARCHAR(64)  NOT NULL,
  node_type       VARCHAR(16)  NOT NULL COMMENT 'REGISTRATION / CONTEST / SUBMISSION / RESULT / OTHER',
  start_at        DATETIME     NOT NULL,
  end_at          DATETIME     NULL,
  all_day         TINYINT      NOT NULL DEFAULT 0,
  start_latest_at DATETIME     NULL,
  end_mode        VARCHAR(8)   NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED / RANGE / OPEN',
  end_latest_at   DATETIME     NULL,
  busy            TINYINT      NOT NULL DEFAULT 0 COMMENT '1 = 这段时间被比赛占满，参与撞期检测',
  note            VARCHAR(255) NULL,
  sort            INT          NOT NULL DEFAULT 0,
  KEY idx_node_edition (edition_id, start_at),
  CONSTRAINT fk_node_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id)
) COMMENT '比赛官方时间节点（日历里的"比赛官方"图层）';

CREATE TABLE competition_follow (
  user_id    BIGINT   NOT NULL,
  edition_id BIGINT   NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, edition_id),
  KEY idx_follow_edition (edition_id),
  CONSTRAINT fk_follow_user    FOREIGN KEY (user_id)    REFERENCES sys_user (id),
  CONSTRAINT fk_follow_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id)
) COMMENT '关注的比赛：节点进日历、截止前提醒';

CREATE TABLE crawl_source (
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

CREATE TABLE crawl_record (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  source_id    BIGINT       NULL COMMENT '从哪个抓取来源抓到的',
  source_url   VARCHAR(512) NOT NULL,
  title        VARCHAR(255) NULL,
  raw_text     MEDIUMTEXT   NULL COMMENT '通知原文',
  content_hash CHAR(64)     NOT NULL COMMENT '原文 SHA-256，用来去重',
  extracted    JSON         NULL COMMENT '从原文里抽出的字段和依据（规则或大模型），等管理员确认',
  status       VARCHAR(16)  NOT NULL DEFAULT 'NEW' COMMENT 'NEW 没抽出字段 / EXTRACTED 抽出了字段 / APPROVED 已采用 / IGNORED 已忽略',
  note         VARCHAR(255) NULL COMMENT '自动忽略的原因，比如往年的旧通知',
  edition_id   BIGINT       NULL COMMENT '确认后生成或更新的届次',
  fetched_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_by  BIGINT       NULL,
  reviewed_at  DATETIME     NULL,
  UNIQUE KEY uk_crawl_hash (content_hash),
  KEY idx_crawl_status (status, fetched_at),
  KEY idx_crawl_url (source_url(191)),
  CONSTRAINT fk_crawl_source   FOREIGN KEY (source_id)   REFERENCES crawl_source (id),
  CONSTRAINT fk_crawl_edition  FOREIGN KEY (edition_id)  REFERENCES competition_edition (id),
  CONSTRAINT fk_crawl_reviewer FOREIGN KEY (reviewed_by) REFERENCES sys_user (id)
) COMMENT '爬虫抓到的比赛通知';

CREATE TABLE school_competition_recognition (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  school_id      BIGINT       NOT NULL,
  competition_id BIGINT       NOT NULL,
  grade_label    VARCHAR(32)  NOT NULL COMMENT '学校自己的分级，如 A 类、一类',
  note           VARCHAR(255) NULL,
  source_url     VARCHAR(255) NULL COMMENT '学校文件',
  submitted_by   BIGINT       NOT NULL,
  confirm_count  INT          NOT NULL DEFAULT 0,
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED / REJECTED',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_recognition_submit (school_id, competition_id, submitted_by),
  KEY idx_recognition_lookup (school_id, competition_id, status),
  CONSTRAINT fk_recognition_school      FOREIGN KEY (school_id)      REFERENCES school (id),
  CONSTRAINT fk_recognition_competition FOREIGN KEY (competition_id) REFERENCES competition (id),
  CONSTRAINT fk_recognition_user        FOREIGN KEY (submitted_by)   REFERENCES sys_user (id)
) COMMENT '各校的加分认定（用户标注，同校多人确认）';

CREATE TABLE recognition_confirm (
  recognition_id BIGINT   NOT NULL,
  user_id        BIGINT   NOT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (recognition_id, user_id),
  CONSTRAINT fk_confirm_recognition FOREIGN KEY (recognition_id) REFERENCES school_competition_recognition (id),
  CONSTRAINT fk_confirm_user        FOREIGN KEY (user_id)        REFERENCES sys_user (id)
) COMMENT '加分认定的确认记录（一人一票）';

-- ============================================================
-- 四、队伍
-- ============================================================

CREATE TABLE team (
  id                BIGINT PRIMARY KEY AUTO_INCREMENT,
  edition_id        BIGINT        NOT NULL,
  name              VARCHAR(32)   NOT NULL,
  captain_id        BIGINT        NOT NULL,
  advisor_id        BIGINT        NULL COMMENT '指导老师（TEACHER 账号）',
  intro             VARCHAR(1000) NULL,
  status            VARCHAR(16)   NOT NULL DEFAULT 'RECRUITING' COMMENT 'RECRUITING / FULL / CLOSED / FINISHED / DISBANDED',
  max_members       TINYINT       NOT NULL COMMENT '不超过届次的 team_max',
  meeting_mode      VARCHAR(8)    NOT NULL DEFAULT 'MIXED' COMMENT 'OFFLINE / ONLINE / MIXED',
  meeting_slots     INT           NOT NULL DEFAULT 0 COMMENT '例会时段位图：第 day*3+seg 位，day 0 = 周一，seg 0/1/2 = 上午/下午/晚上',
  award             VARCHAR(32)   NULL COMMENT '赛后录入的获奖等级',
  award_recorded_at DATETIME      NULL,
  created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted           TINYINT       NOT NULL DEFAULT 0,
  KEY idx_team_edition (edition_id, status),
  KEY idx_team_captain (captain_id),
  CONSTRAINT fk_team_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id),
  CONSTRAINT fk_team_captain FOREIGN KEY (captain_id) REFERENCES sys_user (id),
  CONSTRAINT fk_team_advisor FOREIGN KEY (advisor_id) REFERENCES sys_user (id)
) COMMENT '队伍';

-- 同一届比赛一个人只能在一支队里：
-- active_key 只在 ACTIVE 时为 1，其余为 NULL。唯一索引不限制 NULL，
-- 所以退队记录可以保留，但"正在队里"的记录每届只能有一条。
CREATE TABLE team_member (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id    BIGINT      NOT NULL,
  edition_id BIGINT      NOT NULL COMMENT '冗余自 team，用于唯一约束',
  user_id    BIGINT      NOT NULL,
  role_name  VARCHAR(32) NULL COMMENT '在队里承担的工作',
  is_captain TINYINT     NOT NULL DEFAULT 0,
  status     VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / LEFT / REMOVED',
  joined_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  left_at    DATETIME    NULL,
  active_key TINYINT GENERATED ALWAYS AS (IF(status = 'ACTIVE', 1, NULL)) STORED,
  UNIQUE KEY uk_member_one_team (edition_id, user_id, active_key),
  KEY idx_member_team (team_id, status),
  KEY idx_member_user (user_id, status),
  CONSTRAINT fk_member_team    FOREIGN KEY (team_id)    REFERENCES team (id),
  CONSTRAINT fk_member_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id),
  CONSTRAINT fk_member_user    FOREIGN KEY (user_id)    REFERENCES sys_user (id)
) COMMENT '队员';

CREATE TABLE team_need (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id    BIGINT       NOT NULL,
  role_name  VARCHAR(32)  NOT NULL COMMENT '缺的角色，如：前端',
  headcount  TINYINT      NOT NULL DEFAULT 1,
  filled     TINYINT      NOT NULL DEFAULT 0,
  note       VARCHAR(255) NULL COMMENT '如：要求会 Vue',
  status     VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / FILLED / CANCELLED',
  created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_need_team (team_id, status),
  CONSTRAINT chk_need_count CHECK (headcount >= 1 AND filled BETWEEN 0 AND headcount),
  CONSTRAINT fk_need_team FOREIGN KEY (team_id) REFERENCES team (id)
) COMMENT '招募需求（缺什么人）';

CREATE TABLE team_need_skill (
  need_id   BIGINT  NOT NULL,
  skill_id  BIGINT  NOT NULL,
  min_level TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (need_id, skill_id),
  KEY idx_need_skill_skill (skill_id),
  CONSTRAINT chk_need_skill_level CHECK (min_level BETWEEN 1 AND 3),
  CONSTRAINT fk_need_skill_need  FOREIGN KEY (need_id)  REFERENCES team_need (id),
  CONSTRAINT fk_need_skill_skill FOREIGN KEY (skill_id) REFERENCES skill (id)
) COMMENT '招募需求要求的技能（技能互补打分用的"缺口"）';

-- 申请和邀请放在一张表，用 direction 区分。
-- 同一支队和同一个人之间，同时只能有一条待处理的记录。
CREATE TABLE team_application (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id     BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL COMMENT '申请人 / 被邀请人',
  direction   VARCHAR(8)   NOT NULL COMMENT 'APPLY：学生申请，队长处理 / INVITE：队长邀请，学生处理',
  need_id     BIGINT       NULL COMMENT '针对哪个招募需求',
  message     VARCHAR(500) NULL,
  status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / ACCEPTED / REJECTED / WITHDRAWN / EXPIRED',
  handled_by  BIGINT       NULL,
  handled_at  DATETIME     NULL,
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  pending_key TINYINT GENERATED ALWAYS AS (IF(status = 'PENDING', 1, NULL)) STORED,
  UNIQUE KEY uk_application_pending (team_id, user_id, pending_key),
  KEY idx_application_user (user_id, status),
  KEY idx_application_team (team_id, status),
  CONSTRAINT fk_application_team    FOREIGN KEY (team_id)    REFERENCES team (id),
  CONSTRAINT fk_application_user    FOREIGN KEY (user_id)    REFERENCES sys_user (id),
  CONSTRAINT fk_application_need    FOREIGN KEY (need_id)    REFERENCES team_need (id),
  CONSTRAINT fk_application_handler FOREIGN KEY (handled_by) REFERENCES sys_user (id)
) COMMENT '入队申请和邀请';

CREATE TABLE team_milestone (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id         BIGINT       NOT NULL,
  title           VARCHAR(64)  NOT NULL,
  note            VARCHAR(500) NULL,
  start_at        DATETIME     NOT NULL,
  end_at          DATETIME     NULL,
  all_day         TINYINT      NOT NULL DEFAULT 1,
  start_latest_at DATETIME     NULL,
  end_mode        VARCHAR(8)   NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED / RANGE / OPEN',
  end_latest_at   DATETIME     NULL,
  created_by      BIGINT       NOT NULL,
  done_at         DATETIME     NULL,
  created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  KEY idx_milestone_team (team_id, start_at),
  CONSTRAINT fk_milestone_team    FOREIGN KEY (team_id)    REFERENCES team (id),
  CONSTRAINT fk_milestone_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
) COMMENT '队伍里程碑（队伍页的时间线，也是日历里的队伍图层）';

CREATE TABLE team_task (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  milestone_id BIGINT      NOT NULL,
  title        VARCHAR(64) NOT NULL,
  assignee_id  BIGINT      NULL,
  done_at      DATETIME    NULL,
  sort         INT         NOT NULL DEFAULT 0,
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_milestone (milestone_id),
  KEY idx_task_assignee (assignee_id, done_at),
  CONSTRAINT fk_task_milestone FOREIGN KEY (milestone_id) REFERENCES team_milestone (id),
  CONSTRAINT fk_task_assignee  FOREIGN KEY (assignee_id)  REFERENCES sys_user (id)
) COMMENT '里程碑下的分工待办';

CREATE TABLE user_experience (
  id             BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id        BIGINT       NOT NULL,
  competition_id BIGINT       NULL COMMENT '平台外的比赛可以为空，只填 title',
  team_id        BIGINT       NULL COMMENT '平台内组队、赛后录入成绩时自动生成',
  title          VARCHAR(100) NOT NULL,
  year           SMALLINT     NOT NULL,
  award          VARCHAR(32)  NULL COMMENT '如：国家一等奖、省二等奖、成功参赛',
  proof_url      VARCHAR(255) NULL,
  source         VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / PLATFORM',
  verified       TINYINT      NOT NULL DEFAULT 0,
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_experience_user (user_id, year),
  CONSTRAINT fk_experience_user        FOREIGN KEY (user_id)        REFERENCES sys_user (id),
  CONSTRAINT fk_experience_competition FOREIGN KEY (competition_id) REFERENCES competition (id),
  CONSTRAINT fk_experience_team        FOREIGN KEY (team_id)        REFERENCES team (id)
) COMMENT '参赛履历';

CREATE TABLE advisor_topic (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  teacher_id  BIGINT       NOT NULL,
  edition_id  BIGINT       NULL,
  title       VARCHAR(100) NOT NULL,
  description TEXT         NULL,
  needs       VARCHAR(500) NULL COMMENT '想招什么样的学生',
  status      VARCHAR(16)  NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / CLOSED',
  created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  KEY idx_topic_teacher (teacher_id),
  CONSTRAINT fk_topic_teacher FOREIGN KEY (teacher_id) REFERENCES sys_user (id),
  CONSTRAINT fk_topic_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id)
) COMMENT '指导老师发布的课题（二期）';

-- ============================================================
-- 五、个人日程
-- ============================================================

CREATE TABLE personal_event (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id         BIGINT        NOT NULL,
  title           VARCHAR(64)   NOT NULL,
  notes           VARCHAR(1000) NULL,
  color           VARCHAR(16)   NULL,
  start_at        DATETIME      NOT NULL,
  end_at          DATETIME      NULL,
  all_day         TINYINT       NOT NULL DEFAULT 0,
  start_latest_at DATETIME      NULL,
  end_mode        VARCHAR(8)    NOT NULL DEFAULT 'FIXED' COMMENT 'FIXED / RANGE / OPEN',
  end_latest_at   DATETIME      NULL,
  repeat_type     VARCHAR(8)    NOT NULL DEFAULT 'NONE' COMMENT 'NONE / DAILY / WEEKDAYS / WEEKLY',
  repeat_weekdays VARCHAR(16)   NULL COMMENT 'WEEKLY 时的星期，如 1,3,5（1 = 周一）',
  repeat_until    DATE          NULL,
  exceptions      JSON          NULL COMMENT '重复日程里被单独删掉的日期',
  busy            TINYINT       NOT NULL DEFAULT 1 COMMENT '1 = 参与撞期检测',
  created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT       NOT NULL DEFAULT 0,
  KEY idx_event_user (user_id, start_at),
  CONSTRAINT fk_event_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '个人日程（日历里的"个人"图层）';

-- ============================================================
-- 六、问卷与匹配
-- ============================================================

CREATE TABLE survey_version (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  code       VARCHAR(16) NOT NULL COMMENT '如 v2',
  name       VARCHAR(64) NOT NULL,
  active     TINYINT     NOT NULL DEFAULT 0 COMMENT '当前使用的版本',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_survey_version_code (code)
) COMMENT '问卷版本';

-- 只管"问什么"。怎么打分放在 match_config。
CREATE TABLE survey_question (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT       NOT NULL,
  code       VARCHAR(8)   NOT NULL COMMENT 'G1…G11 通用 / P1…P7 比赛级 / F1…F3 开放 / PLEDGE 承诺',
  part       VARCHAR(16)  NOT NULL COMMENT 'GENERAL / COMPETITION',
  dimension  VARCHAR(16)  NULL COMMENT 'goal / skill / collab / time / duty；不计分的题为空',
  type       VARCHAR(16)  NOT NULL COMMENT 'SINGLE / MULTI / RANKING / SLIDER / SLOT_GRID / TEXT / PLEDGE',
  title      VARCHAR(255) NOT NULL,
  note       VARCHAR(500) NULL,
  options    JSON         NULL COMMENT '[{"value":"A","label":"…"}]',
  settings   JSON         NULL COMMENT '题型参数：min / max / maxSelect / selectCount / optionSource 等',
  required   TINYINT      NOT NULL DEFAULT 1,
  in_quick   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否在快速版里',
  sort       INT          NOT NULL DEFAULT 0,
  UNIQUE KEY uk_question_code (version_id, code),
  CONSTRAINT fk_question_version FOREIGN KEY (version_id) REFERENCES survey_version (id)
) COMMENT '问卷题目';

CREATE TABLE match_config (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  version_id BIGINT      NOT NULL,
  preset     VARCHAR(20) NOT NULL COMMENT 'DEFAULT / SHORT_INTENSIVE / LONG_TERM',
  config     JSON        NOT NULL COMMENT '维度、题目权重、打分器、相容矩阵、一票否决、承诺系数',
  updated_by BIGINT      NULL,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_match_config (version_id, preset),
  CONSTRAINT fk_match_config_version FOREIGN KEY (version_id) REFERENCES survey_version (id),
  CONSTRAINT fk_match_config_editor  FOREIGN KEY (updated_by) REFERENCES sys_user (id)
) COMMENT '匹配打分配置（管理员可在后台调）';

-- 通用部分 edition_id 为空，比赛级部分每届一行。
-- edition_key 把空值换成 0，这样唯一索引才能管住"通用部分只有一行"。
CREATE TABLE survey_response (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT   NOT NULL,
  version_id   BIGINT   NOT NULL,
  edition_id   BIGINT   NULL,
  edition_key  BIGINT GENERATED ALWAYS AS (IFNULL(edition_id, 0)) STORED,
  answers      JSON     NOT NULL COMMENT '{"G1":["interest"],"G4":1234567,…}',
  quick        TINYINT  NOT NULL DEFAULT 0 COMMENT '只填了快速版',
  pledged_at   DATETIME NULL COMMENT '比赛级：勾选承诺的时间',
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_response (user_id, version_id, edition_key),
  KEY idx_response_edition (edition_id),
  CONSTRAINT fk_response_user    FOREIGN KEY (user_id)    REFERENCES sys_user (id),
  CONSTRAINT fk_response_version FOREIGN KEY (version_id) REFERENCES survey_version (id),
  CONSTRAINT fk_response_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id)
) COMMENT '问卷答案';

-- 推荐结果缓存。答案、队伍成员或招募需求变了，就重算相关的行。
-- kind = USER_TEAM：a = 学生，b = 队伍（"人找队"和"队找人"共用，只是排序方向不同）
-- kind = USER_USER：a、b 都是学生，a < b（还没建队时互相推荐）
CREATE TABLE match_score (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  edition_id  BIGINT       NOT NULL,
  kind        VARCHAR(16)  NOT NULL COMMENT 'USER_TEAM / USER_USER',
  a_id        BIGINT       NOT NULL,
  b_id        BIGINT       NOT NULL,
  score       DECIMAL(5,2) NOT NULL COMMENT '0–100，已乘承诺系数',
  k_factor    DECIMAL(4,3) NOT NULL DEFAULT 1.000,
  breakdown   JSON         NOT NULL COMMENT '各维度分、推荐理由、"组队前先聊聊"',
  config_id   BIGINT       NOT NULL,
  computed_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_match_pair (edition_id, kind, a_id, b_id),
  KEY idx_match_a (edition_id, kind, a_id, score),
  KEY idx_match_b (edition_id, kind, b_id, score),
  CONSTRAINT fk_match_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id),
  CONSTRAINT fk_match_config  FOREIGN KEY (config_id)  REFERENCES match_config (id)
) COMMENT '匹配分数缓存';

CREATE TABLE batch_match_run (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  edition_id  BIGINT      NOT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / DONE / FAILED',
  params      JSON        NULL,
  result      JSON        NULL COMMENT '分组结果',
  notified_at DATETIME    NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_batch_edition (edition_id),
  CONSTRAINT fk_batch_edition FOREIGN KEY (edition_id) REFERENCES competition_edition (id)
) COMMENT '报名截止前的批量撮合（二期）';

-- ============================================================
-- 七、消息与提醒
-- ============================================================

CREATE TABLE notification (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id    BIGINT        NOT NULL COMMENT '收件人',
  type       VARCHAR(32)   NOT NULL COMMENT 'APPLY_RECEIVED / APPLY_RESULT / INVITE_RECEIVED / INVITE_RESULT / DEADLINE / MILESTONE / VERIFY_RESULT / SYSTEM',
  title      VARCHAR(128)  NOT NULL,
  content    VARCHAR(1000) NULL,
  ref_type   VARCHAR(32)   NULL COMMENT '点开后跳转到哪类对象',
  ref_id     BIGINT        NULL,
  read_at    DATETIME      NULL,
  emailed_at DATETIME      NULL COMMENT '汇总发到邮箱的时间',
  created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_notification_inbox (user_id, read_at, created_at),
  KEY idx_notification_mail (emailed_at, created_at),
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '站内信';

CREATE TABLE reminder_sent (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT      NOT NULL,
  kind         VARCHAR(32) NOT NULL COMMENT 'REGISTRATION_END / NODE_START / MILESTONE_DUE',
  ref_id       BIGINT      NOT NULL,
  offset_hours INT         NOT NULL COMMENT '提前多少小时提醒，如 72 / 24 / 1',
  created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_reminder (user_id, kind, ref_id, offset_hours),
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '已发过的提醒，定时任务靠它避免重复提醒';

-- ============================================================
-- 八、互评、信用与举报
-- ============================================================

CREATE TABLE peer_review_round (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  team_id      BIGINT      NOT NULL,
  opens_at     DATETIME    NOT NULL,
  closes_at    DATETIME    NOT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN / PUBLISHED',
  published_at DATETIME    NULL COMMENT '所有人交完或到截止时间，统一揭晓',
  UNIQUE KEY uk_round_team (team_id),
  CONSTRAINT fk_round_team FOREIGN KEY (team_id) REFERENCES team (id)
) COMMENT '互评轮次（一支队一次）';

CREATE TABLE peer_review (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  round_id     BIGINT       NOT NULL,
  rater_id     BIGINT       NOT NULL,
  ratee_id     BIGINT       NOT NULL COMMENT '和 rater_id 相同时是自评',
  answers      JSON         NOT NULL COMMENT '6 项行为的频率：0 从未 / 1 偶尔 / 2 有时 / 3 经常',
  weight       DECIMAL(3,2) NOT NULL DEFAULT 1.00 COMMENT '异常检测后可能被调低',
  flagged      TINYINT      NOT NULL DEFAULT 0,
  submitted_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_review (round_id, rater_id, ratee_id),
  KEY idx_review_ratee (ratee_id),
  CONSTRAINT fk_review_round FOREIGN KEY (round_id) REFERENCES peer_review_round (id),
  CONSTRAINT fk_review_rater FOREIGN KEY (rater_id) REFERENCES sys_user (id),
  CONSTRAINT fk_review_ratee FOREIGN KEY (ratee_id) REFERENCES sys_user (id)
) COMMENT '互评';

CREATE TABLE user_credit (
  user_id         BIGINT PRIMARY KEY,
  score           DECIMAL(5,2) NULL COMMENT '0–100，没有互评记录时为空（新用户）',
  rated_rounds    INT          NOT NULL DEFAULT 0,
  suspended_until DATETIME     NULL COMMENT '暂停推荐到这个时间',
  updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) COMMENT '信用分（由互评汇总）';

CREATE TABLE report (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT        NOT NULL,
  target_type VARCHAR(16)   NOT NULL COMMENT 'USER / TEAM',
  target_id   BIGINT        NOT NULL,
  reason      VARCHAR(16)   NOT NULL COMMENT 'PLAGIARISM / FORGERY / ABUSE / SPAM / OTHER',
  detail      VARCHAR(1000) NULL,
  evidence    JSON          NULL COMMENT '截图等证据的地址',
  status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING / CONFIRMED / DISMISSED',
  handled_by  BIGINT        NULL,
  handled_at  DATETIME      NULL,
  result_note VARCHAR(255)  NULL,
  created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_report_queue (status, created_at),
  KEY idx_report_target (target_type, target_id),
  CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user (id),
  CONSTRAINT fk_report_handler  FOREIGN KEY (handled_by)  REFERENCES sys_user (id)
) COMMENT '举报（核实后作为一票否决的依据）';
