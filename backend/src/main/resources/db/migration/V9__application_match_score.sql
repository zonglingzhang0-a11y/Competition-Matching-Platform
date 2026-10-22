-- 发申请或邀请时记下当时的匹配分（Flyway 迁移 V9），看板的"推荐准不准"用：分高的申请和邀请是不是更容易成。
-- 推荐分是每次打开页面时现算的，事后再算已经不准（入了队的人和队伍都变了），所以要在发出的那一刻记。之前的记录为空。
ALTER TABLE team_application
  ADD COLUMN match_score DECIMAL(5,2) NULL COMMENT '发出时的匹配分（0–100，一票否决为 0）；看板"推荐准不准"用，V9 之前的为空' AFTER need_id;
