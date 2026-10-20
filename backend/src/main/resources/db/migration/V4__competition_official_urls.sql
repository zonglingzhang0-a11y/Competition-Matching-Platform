-- 种子比赛的官网地址（Flyway 迁移 V4）。地址由队友整理（2026-10 核对过能访问）。
-- 主页"我参加的比赛""适合报名的比赛"和比赛详情页的"官网"链接都读这一列，空着就不显示。
-- 只补还空着的：管理员自己改过的不覆盖。
--
--   数模      mcm.edu.cn（组委会官网）
--   电赛      nuedc.com.cn（组委会官网；nuedc-training.com.cn 是培训网，爬虫抓的是那个）
--   创新大赛   cy.ncss.cn（报名系统和通知都在这里）
--   挑战杯    tiaozhanbei.net
--   计设      jsjds.blcu.edu.cn（北京语言大学承办）

UPDATE competition SET official_url = 'https://www.mcm.edu.cn'
WHERE name = '全国大学生数学建模竞赛' AND (official_url IS NULL OR official_url = '');

UPDATE competition SET official_url = 'https://www.nuedc.com.cn'
WHERE name = '全国大学生电子设计竞赛' AND (official_url IS NULL OR official_url = '');

UPDATE competition SET official_url = 'https://cy.ncss.cn'
WHERE name = '中国国际大学生创新大赛' AND (official_url IS NULL OR official_url = '');

UPDATE competition SET official_url = 'https://www.tiaozhanbei.net'
WHERE name = '"挑战杯"全国大学生课外学术科技作品竞赛' AND (official_url IS NULL OR official_url = '');

UPDATE competition SET official_url = 'https://jsjds.blcu.edu.cn'
WHERE name = '中国大学生计算机设计大赛' AND (official_url IS NULL OR official_url = '');
