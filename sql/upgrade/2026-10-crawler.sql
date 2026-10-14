-- 升级脚本：给已经建好的库加上爬虫的抓取来源。新建库直接用 schema.sql + seed.sql，不用跑这个。
-- 只能跑一次（第二次会因为表已经存在而报错，不影响数据）。

SET NAMES utf8mb4;
USE team_match;

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

ALTER TABLE crawl_record
  ADD COLUMN source_id BIGINT NULL COMMENT '从哪个抓取来源抓到的' AFTER id,
  ADD COLUMN note VARCHAR(255) NULL COMMENT '自动忽略的原因，比如往年的旧通知' AFTER status,
  ADD KEY idx_crawl_url (source_url(191)),
  ADD CONSTRAINT fk_crawl_source FOREIGN KEY (source_id) REFERENCES crawl_source (id);

-- 爬虫的抓取来源。选择器是对着 2026 年 10 月各官网的页面结构写的，网站改版了就在管理后台改。
-- 中国国际大学生创新大赛的官网是前端渲染的，静态抓取拿不到内容，没有加。
-- 挑战杯官网的 robots.txt 允许普通爬虫抓 /article/，但明确不希望内容被 AI 使用，所以 allow_llm = 0，默认也不启用。
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
ORDER BY c.id;
