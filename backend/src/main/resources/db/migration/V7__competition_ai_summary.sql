-- 比赛页的 AI 总结（Flyway 迁移 V7）：大模型根据抓到的官方通知整理的要点，管理员点"生成"时更新。
-- 每条要点都带原文里的一句话，后端核对过这句话在通知原文里真的有，才存进来。
CREATE TABLE competition_ai_summary (
  competition_id BIGINT      PRIMARY KEY,
  points         JSON        NOT NULL COMMENT '要点：[{label, text, quote, recordId}]',
  record_ids     JSON        NOT NULL COMMENT '这次交给大模型的通知（crawl_record.id）',
  model          VARCHAR(64) NOT NULL COMMENT '用的哪个模型',
  generated_at   DATETIME    NOT NULL,
  generated_by   BIGINT      NULL COMMENT '哪个管理员点的生成',
  CONSTRAINT fk_ai_summary_competition FOREIGN KEY (competition_id) REFERENCES competition (id),
  CONSTRAINT fk_ai_summary_user        FOREIGN KEY (generated_by)   REFERENCES sys_user (id)
) COMMENT '比赛页的 AI 总结（根据抓到的官方通知整理）';
