-- AI 分析结果（Flyway 迁移 V11；队友写的时候是 V8，合并时改号）。
-- 个人档案的"AI 能力分析"和队伍页的"AI 招募建议"都存一份最近结果：页面打开先读这份，
-- 不用每次都请求模型；重新分析时覆盖。source 写明是 LLM（模型）还是 RULE（本地规则），
-- 页面上要标出来，不能把本地规则说成 AI。

CREATE TABLE ai_analysis (
  id         BIGINT PRIMARY KEY AUTO_INCREMENT,
  kind       VARCHAR(16) NOT NULL COMMENT 'PROFILE / TEAM',
  subject_id BIGINT      NOT NULL COMMENT '用户 id 或队伍 id',
  payload    JSON        NOT NULL COMMENT '分析结果，结构见 AiDtos',
  source     VARCHAR(8)  NOT NULL COMMENT 'LLM / RULE',
  model      VARCHAR(64) NULL COMMENT '用的模型名，本地规则时为空',
  created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_ai_analysis_subject (kind, subject_id)
) COMMENT 'AI 分析缓存（每人/每队一份最近结果）';
