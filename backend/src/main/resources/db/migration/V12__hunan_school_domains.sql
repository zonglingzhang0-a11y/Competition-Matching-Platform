-- 湖南省本科高校和它们的教育邮箱主域名（Flyway 迁移 V12）：用这些学校的邮箱做教育邮箱认证，验证码通过后直接认出是哪所学校，不用再等管理员审核。
-- 只收主域名就够了：SchoolResolver 从完整域名一层层往上找，学生邮箱的子域名（如 mail.stu.xxx.edu.cn）也能对上。
-- 每个域名都核实过（2026-10）：有 MX 记录，真的能收邮件；官网首页标题就是这所学校。湖南理工学院已更名，官网标题是"湖南理工大学"。
-- 没有收的：查不到能收信的 .edu.cn 域名的学校（湖南警察学院、湘潭理工学院、湖南交通工程学院、湖南应用技术学院、两所职业本科），这些学校的同学用截图认证。
-- 已经有的学校和域名不动（INSERT IGNORE），管理员在「学校与邮箱后缀」里加过的也不会被覆盖。

INSERT IGNORE INTO school (name, province, city) VALUES
  ('国防科技大学', '湖南省', '长沙市'),
  ('中南大学', '湖南省', '长沙市'),
  ('湖南大学', '湖南省', '长沙市'),
  ('湖南师范大学', '湖南省', '长沙市'),
  ('湘潭大学', '湖南省', '湘潭市'),
  ('长沙理工大学', '湖南省', '长沙市'),
  ('湖南农业大学', '湖南省', '长沙市'),
  ('中南林业科技大学', '湖南省', '长沙市'),
  ('湖南科技大学', '湖南省', '湘潭市'),
  ('湖南中医药大学', '湖南省', '长沙市'),
  ('南华大学', '湖南省', '衡阳市'),
  ('湖南工业大学', '湖南省', '株洲市'),
  ('湖南工商大学', '湖南省', '长沙市'),
  ('吉首大学', '湖南省', '吉首市'),
  ('湖南理工大学', '湖南省', '岳阳市'),
  ('湖南文理学院', '湖南省', '常德市'),
  ('衡阳师范学院', '湖南省', '衡阳市'),
  ('湖南城市学院', '湖南省', '益阳市'),
  ('湖南工程学院', '湖南省', '湘潭市'),
  ('长沙学院', '湖南省', '长沙市'),
  ('湖南第一师范学院', '湖南省', '长沙市'),
  ('湖南人文科技学院', '湖南省', '娄底市'),
  ('湖南工学院', '湖南省', '衡阳市'),
  ('湖南财政经济学院', '湖南省', '长沙市'),
  ('怀化学院', '湖南省', '怀化市'),
  ('邵阳学院', '湖南省', '邵阳市'),
  ('湖南科技学院', '湖南省', '永州市'),
  ('湘南学院', '湖南省', '郴州市'),
  ('长沙师范学院', '湖南省', '长沙市'),
  ('湖南女子学院', '湖南省', '长沙市'),
  ('湖南医药学院', '湖南省', '怀化市'),
  ('长沙医学院', '湖南省', '长沙市'),
  ('湖南信息学院', '湖南省', '长沙市'),
  ('湖南涉外经济学院', '湖南省', '长沙市');

INSERT IGNORE INTO school_email_domain (school_id, domain)
SELECT s.id, d.domain FROM school s JOIN (
            SELECT '国防科技大学' AS name, 'nudt.edu.cn' AS domain
  UNION ALL SELECT '中南大学', 'csu.edu.cn'
  UNION ALL SELECT '湖南大学', 'hnu.edu.cn'
  UNION ALL SELECT '湖南师范大学', 'hunnu.edu.cn'
  UNION ALL SELECT '湘潭大学', 'xtu.edu.cn'
  UNION ALL SELECT '长沙理工大学', 'csust.edu.cn'
  UNION ALL SELECT '湖南农业大学', 'hunau.edu.cn'
  UNION ALL SELECT '中南林业科技大学', 'csuft.edu.cn'
  UNION ALL SELECT '湖南科技大学', 'hnust.edu.cn'
  UNION ALL SELECT '湖南中医药大学', 'hnucm.edu.cn'
  UNION ALL SELECT '南华大学', 'usc.edu.cn'
  UNION ALL SELECT '湖南工业大学', 'hut.edu.cn'
  UNION ALL SELECT '湖南工商大学', 'hutb.edu.cn'
  UNION ALL SELECT '吉首大学', 'jsu.edu.cn'
  UNION ALL SELECT '湖南理工大学', 'hnist.edu.cn'
  UNION ALL SELECT '湖南文理学院', 'huas.edu.cn'
  UNION ALL SELECT '衡阳师范学院', 'hynu.edu.cn'
  UNION ALL SELECT '湖南城市学院', 'hncu.edu.cn'
  UNION ALL SELECT '湖南工程学院', 'hnie.edu.cn'
  UNION ALL SELECT '长沙学院', 'ccsu.edu.cn'
  UNION ALL SELECT '湖南第一师范学院', 'hnfnu.edu.cn'
  UNION ALL SELECT '湖南人文科技学院', 'huhst.edu.cn'
  UNION ALL SELECT '湖南工学院', 'hnit.edu.cn'
  UNION ALL SELECT '湖南财政经济学院', 'hufe.edu.cn'
  UNION ALL SELECT '怀化学院', 'hhtc.edu.cn'
  UNION ALL SELECT '邵阳学院', 'hnsyu.edu.cn'
  UNION ALL SELECT '湖南科技学院', 'huse.edu.cn'
  UNION ALL SELECT '湘南学院', 'xnu.edu.cn'
  UNION ALL SELECT '长沙师范学院', 'csnu.edu.cn'
  UNION ALL SELECT '湖南女子学院', 'hnwu.edu.cn'
  UNION ALL SELECT '湖南医药学院', 'hnmu.edu.cn'
  UNION ALL SELECT '长沙医学院', 'csmu.edu.cn'
  UNION ALL SELECT '湖南信息学院', 'hnuit.edu.cn'
  UNION ALL SELECT '湖南涉外经济学院', 'hieu.edu.cn'
) d ON d.name = s.name;
