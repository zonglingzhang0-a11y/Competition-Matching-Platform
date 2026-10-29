-- 985、211 高校和它们的教育邮箱主域名（Flyway 迁移 V13），做法和 V12 一样：用这些学校的邮箱做教育邮箱认证，
-- 验证码通过后直接认出是哪所学校，不用等管理员审核。共 112 所（985 36 所、其余 211 76 所）；
-- 湖南的湖南大学、中南大学、国防科技大学、湖南师范大学已经在 V12 里。
-- 学校名单按 985、211 工程名单；第二军医大学、第四军医大学用现在的校名（海军军医大学、空军军医大学）。
-- 只收主域名：SchoolResolver 从完整域名一层层往上找，学生邮箱的子域名（如 mail.ustc.edu.cn、stu.pku.edu.cn）也能对上。
-- 每个域名都核实过（2026-10-30，连同湖南那 4 所一起核了 116 所）：有 MX 记录，真的能收邮件；是学校自己的域名——105 所看官网首页标题，
-- 另外 11 所官网对脚本访问有反爬（兰州大学、北京工业大学、北京邮电大学、北京体育大学、中国政法大学、南京理工大学、
-- 南京师范大学、中南财经政法大学、西南交通大学、石河子大学，以及西藏大学），改看限定这个域名的搜索结果，都是学校自己的页面。
-- 西藏大学的官网已从 utibet.edu.cn 迁到 xzu.edu.cn，两个域名都有 MX 记录，老同学的邮箱可能还是旧域名，两个都收。
-- 已经有的学校和域名不动（INSERT IGNORE），管理员在「学校与邮箱后缀」里加过的也不会被覆盖。

INSERT IGNORE INTO school (name, province, city) VALUES
  ('北京大学', '北京市', '北京市'),
  ('清华大学', '北京市', '北京市'),
  ('中国人民大学', '北京市', '北京市'),
  ('北京航空航天大学', '北京市', '北京市'),
  ('北京理工大学', '北京市', '北京市'),
  ('中国农业大学', '北京市', '北京市'),
  ('北京师范大学', '北京市', '北京市'),
  ('中央民族大学', '北京市', '北京市'),
  ('南开大学', '天津市', '天津市'),
  ('天津大学', '天津市', '天津市'),
  ('大连理工大学', '辽宁省', '大连市'),
  ('东北大学', '辽宁省', '沈阳市'),
  ('吉林大学', '吉林省', '长春市'),
  ('哈尔滨工业大学', '黑龙江省', '哈尔滨市'),
  ('复旦大学', '上海市', '上海市'),
  ('同济大学', '上海市', '上海市'),
  ('上海交通大学', '上海市', '上海市'),
  ('华东师范大学', '上海市', '上海市'),
  ('南京大学', '江苏省', '南京市'),
  ('东南大学', '江苏省', '南京市'),
  ('浙江大学', '浙江省', '杭州市'),
  ('中国科学技术大学', '安徽省', '合肥市'),
  ('厦门大学', '福建省', '厦门市'),
  ('山东大学', '山东省', '济南市'),
  ('中国海洋大学', '山东省', '青岛市'),
  ('武汉大学', '湖北省', '武汉市'),
  ('华中科技大学', '湖北省', '武汉市'),
  ('中山大学', '广东省', '广州市'),
  ('华南理工大学', '广东省', '广州市'),
  ('四川大学', '四川省', '成都市'),
  ('电子科技大学', '四川省', '成都市'),
  ('重庆大学', '重庆市', '重庆市'),
  ('西安交通大学', '陕西省', '西安市'),
  ('西北工业大学', '陕西省', '西安市'),
  ('西北农林科技大学', '陕西省', '咸阳市'),
  ('兰州大学', '甘肃省', '兰州市'),
  ('北京交通大学', '北京市', '北京市'),
  ('北京工业大学', '北京市', '北京市'),
  ('北京科技大学', '北京市', '北京市'),
  ('北京化工大学', '北京市', '北京市'),
  ('北京邮电大学', '北京市', '北京市'),
  ('北京林业大学', '北京市', '北京市'),
  ('北京中医药大学', '北京市', '北京市'),
  ('北京外国语大学', '北京市', '北京市'),
  ('中国传媒大学', '北京市', '北京市'),
  ('中央财经大学', '北京市', '北京市'),
  ('对外经济贸易大学', '北京市', '北京市'),
  ('北京体育大学', '北京市', '北京市'),
  ('中央音乐学院', '北京市', '北京市'),
  ('中国政法大学', '北京市', '北京市'),
  ('华北电力大学', '北京市', '北京市'),
  ('中国矿业大学（北京）', '北京市', '北京市'),
  ('中国石油大学（北京）', '北京市', '北京市'),
  ('中国地质大学（北京）', '北京市', '北京市'),
  ('北京协和医学院', '北京市', '北京市'),
  ('天津医科大学', '天津市', '天津市'),
  ('河北工业大学', '天津市', '天津市'),
  ('太原理工大学', '山西省', '太原市'),
  ('内蒙古大学', '内蒙古自治区', '呼和浩特市'),
  ('辽宁大学', '辽宁省', '沈阳市'),
  ('大连海事大学', '辽宁省', '大连市'),
  ('延边大学', '吉林省', '延吉市'),
  ('东北师范大学', '吉林省', '长春市'),
  ('哈尔滨工程大学', '黑龙江省', '哈尔滨市'),
  ('东北农业大学', '黑龙江省', '哈尔滨市'),
  ('东北林业大学', '黑龙江省', '哈尔滨市'),
  ('华东理工大学', '上海市', '上海市'),
  ('东华大学', '上海市', '上海市'),
  ('上海外国语大学', '上海市', '上海市'),
  ('上海财经大学', '上海市', '上海市'),
  ('上海大学', '上海市', '上海市'),
  ('海军军医大学', '上海市', '上海市'),
  ('苏州大学', '江苏省', '苏州市'),
  ('南京航空航天大学', '江苏省', '南京市'),
  ('南京理工大学', '江苏省', '南京市'),
  ('中国矿业大学', '江苏省', '徐州市'),
  ('河海大学', '江苏省', '南京市'),
  ('江南大学', '江苏省', '无锡市'),
  ('南京农业大学', '江苏省', '南京市'),
  ('中国药科大学', '江苏省', '南京市'),
  ('南京师范大学', '江苏省', '南京市'),
  ('安徽大学', '安徽省', '合肥市'),
  ('合肥工业大学', '安徽省', '合肥市'),
  ('福州大学', '福建省', '福州市'),
  ('南昌大学', '江西省', '南昌市'),
  ('中国石油大学（华东）', '山东省', '青岛市'),
  ('郑州大学', '河南省', '郑州市'),
  ('中国地质大学（武汉）', '湖北省', '武汉市'),
  ('武汉理工大学', '湖北省', '武汉市'),
  ('华中农业大学', '湖北省', '武汉市'),
  ('华中师范大学', '湖北省', '武汉市'),
  ('中南财经政法大学', '湖北省', '武汉市'),
  ('暨南大学', '广东省', '广州市'),
  ('华南师范大学', '广东省', '广州市'),
  ('广西大学', '广西壮族自治区', '南宁市'),
  ('海南大学', '海南省', '海口市'),
  ('西南大学', '重庆市', '重庆市'),
  ('西南交通大学', '四川省', '成都市'),
  ('四川农业大学', '四川省', '雅安市'),
  ('西南财经大学', '四川省', '成都市'),
  ('贵州大学', '贵州省', '贵阳市'),
  ('云南大学', '云南省', '昆明市'),
  ('西藏大学', '西藏自治区', '拉萨市'),
  ('西北大学', '陕西省', '西安市'),
  ('西安电子科技大学', '陕西省', '西安市'),
  ('长安大学', '陕西省', '西安市'),
  ('陕西师范大学', '陕西省', '西安市'),
  ('空军军医大学', '陕西省', '西安市'),
  ('青海大学', '青海省', '西宁市'),
  ('宁夏大学', '宁夏回族自治区', '银川市'),
  ('新疆大学', '新疆维吾尔自治区', '乌鲁木齐市'),
  ('石河子大学', '新疆维吾尔自治区', '石河子市');

INSERT IGNORE INTO school_email_domain (school_id, domain)
SELECT s.id, d.domain FROM school s JOIN (
            SELECT '北京大学' AS name, 'pku.edu.cn' AS domain
  UNION ALL SELECT '清华大学', 'tsinghua.edu.cn'
  UNION ALL SELECT '中国人民大学', 'ruc.edu.cn'
  UNION ALL SELECT '北京航空航天大学', 'buaa.edu.cn'
  UNION ALL SELECT '北京理工大学', 'bit.edu.cn'
  UNION ALL SELECT '中国农业大学', 'cau.edu.cn'
  UNION ALL SELECT '北京师范大学', 'bnu.edu.cn'
  UNION ALL SELECT '中央民族大学', 'muc.edu.cn'
  UNION ALL SELECT '南开大学', 'nankai.edu.cn'
  UNION ALL SELECT '天津大学', 'tju.edu.cn'
  UNION ALL SELECT '大连理工大学', 'dlut.edu.cn'
  UNION ALL SELECT '东北大学', 'neu.edu.cn'
  UNION ALL SELECT '吉林大学', 'jlu.edu.cn'
  UNION ALL SELECT '哈尔滨工业大学', 'hit.edu.cn'
  UNION ALL SELECT '复旦大学', 'fudan.edu.cn'
  UNION ALL SELECT '同济大学', 'tongji.edu.cn'
  UNION ALL SELECT '上海交通大学', 'sjtu.edu.cn'
  UNION ALL SELECT '华东师范大学', 'ecnu.edu.cn'
  UNION ALL SELECT '南京大学', 'nju.edu.cn'
  UNION ALL SELECT '东南大学', 'seu.edu.cn'
  UNION ALL SELECT '浙江大学', 'zju.edu.cn'
  UNION ALL SELECT '中国科学技术大学', 'ustc.edu.cn'
  UNION ALL SELECT '厦门大学', 'xmu.edu.cn'
  UNION ALL SELECT '山东大学', 'sdu.edu.cn'
  UNION ALL SELECT '中国海洋大学', 'ouc.edu.cn'
  UNION ALL SELECT '武汉大学', 'whu.edu.cn'
  UNION ALL SELECT '华中科技大学', 'hust.edu.cn'
  UNION ALL SELECT '中山大学', 'sysu.edu.cn'
  UNION ALL SELECT '华南理工大学', 'scut.edu.cn'
  UNION ALL SELECT '四川大学', 'scu.edu.cn'
  UNION ALL SELECT '电子科技大学', 'uestc.edu.cn'
  UNION ALL SELECT '重庆大学', 'cqu.edu.cn'
  UNION ALL SELECT '西安交通大学', 'xjtu.edu.cn'
  UNION ALL SELECT '西北工业大学', 'nwpu.edu.cn'
  UNION ALL SELECT '西北农林科技大学', 'nwafu.edu.cn'
  UNION ALL SELECT '兰州大学', 'lzu.edu.cn'
  UNION ALL SELECT '北京交通大学', 'bjtu.edu.cn'
  UNION ALL SELECT '北京工业大学', 'bjut.edu.cn'
  UNION ALL SELECT '北京科技大学', 'ustb.edu.cn'
  UNION ALL SELECT '北京化工大学', 'buct.edu.cn'
  UNION ALL SELECT '北京邮电大学', 'bupt.edu.cn'
  UNION ALL SELECT '北京林业大学', 'bjfu.edu.cn'
  UNION ALL SELECT '北京中医药大学', 'bucm.edu.cn'
  UNION ALL SELECT '北京外国语大学', 'bfsu.edu.cn'
  UNION ALL SELECT '中国传媒大学', 'cuc.edu.cn'
  UNION ALL SELECT '中央财经大学', 'cufe.edu.cn'
  UNION ALL SELECT '对外经济贸易大学', 'uibe.edu.cn'
  UNION ALL SELECT '北京体育大学', 'bsu.edu.cn'
  UNION ALL SELECT '中央音乐学院', 'ccom.edu.cn'
  UNION ALL SELECT '中国政法大学', 'cupl.edu.cn'
  UNION ALL SELECT '华北电力大学', 'ncepu.edu.cn'
  UNION ALL SELECT '中国矿业大学（北京）', 'cumtb.edu.cn'
  UNION ALL SELECT '中国石油大学（北京）', 'cup.edu.cn'
  UNION ALL SELECT '中国地质大学（北京）', 'cugb.edu.cn'
  UNION ALL SELECT '北京协和医学院', 'pumc.edu.cn'
  UNION ALL SELECT '天津医科大学', 'tmu.edu.cn'
  UNION ALL SELECT '河北工业大学', 'hebut.edu.cn'
  UNION ALL SELECT '太原理工大学', 'tyut.edu.cn'
  UNION ALL SELECT '内蒙古大学', 'imu.edu.cn'
  UNION ALL SELECT '辽宁大学', 'lnu.edu.cn'
  UNION ALL SELECT '大连海事大学', 'dlmu.edu.cn'
  UNION ALL SELECT '延边大学', 'ybu.edu.cn'
  UNION ALL SELECT '东北师范大学', 'nenu.edu.cn'
  UNION ALL SELECT '哈尔滨工程大学', 'hrbeu.edu.cn'
  UNION ALL SELECT '东北农业大学', 'neau.edu.cn'
  UNION ALL SELECT '东北林业大学', 'nefu.edu.cn'
  UNION ALL SELECT '华东理工大学', 'ecust.edu.cn'
  UNION ALL SELECT '东华大学', 'dhu.edu.cn'
  UNION ALL SELECT '上海外国语大学', 'shisu.edu.cn'
  UNION ALL SELECT '上海财经大学', 'sufe.edu.cn'
  UNION ALL SELECT '上海大学', 'shu.edu.cn'
  UNION ALL SELECT '海军军医大学', 'smmu.edu.cn'
  UNION ALL SELECT '苏州大学', 'suda.edu.cn'
  UNION ALL SELECT '南京航空航天大学', 'nuaa.edu.cn'
  UNION ALL SELECT '南京理工大学', 'njust.edu.cn'
  UNION ALL SELECT '中国矿业大学', 'cumt.edu.cn'
  UNION ALL SELECT '河海大学', 'hhu.edu.cn'
  UNION ALL SELECT '江南大学', 'jiangnan.edu.cn'
  UNION ALL SELECT '南京农业大学', 'njau.edu.cn'
  UNION ALL SELECT '中国药科大学', 'cpu.edu.cn'
  UNION ALL SELECT '南京师范大学', 'njnu.edu.cn'
  UNION ALL SELECT '安徽大学', 'ahu.edu.cn'
  UNION ALL SELECT '合肥工业大学', 'hfut.edu.cn'
  UNION ALL SELECT '福州大学', 'fzu.edu.cn'
  UNION ALL SELECT '南昌大学', 'ncu.edu.cn'
  UNION ALL SELECT '中国石油大学（华东）', 'upc.edu.cn'
  UNION ALL SELECT '郑州大学', 'zzu.edu.cn'
  UNION ALL SELECT '中国地质大学（武汉）', 'cug.edu.cn'
  UNION ALL SELECT '武汉理工大学', 'whut.edu.cn'
  UNION ALL SELECT '华中农业大学', 'hzau.edu.cn'
  UNION ALL SELECT '华中师范大学', 'ccnu.edu.cn'
  UNION ALL SELECT '中南财经政法大学', 'zuel.edu.cn'
  UNION ALL SELECT '暨南大学', 'jnu.edu.cn'
  UNION ALL SELECT '华南师范大学', 'scnu.edu.cn'
  UNION ALL SELECT '广西大学', 'gxu.edu.cn'
  UNION ALL SELECT '海南大学', 'hainanu.edu.cn'
  UNION ALL SELECT '西南大学', 'swu.edu.cn'
  UNION ALL SELECT '西南交通大学', 'swjtu.edu.cn'
  UNION ALL SELECT '四川农业大学', 'sicau.edu.cn'
  UNION ALL SELECT '西南财经大学', 'swufe.edu.cn'
  UNION ALL SELECT '贵州大学', 'gzu.edu.cn'
  UNION ALL SELECT '云南大学', 'ynu.edu.cn'
  UNION ALL SELECT '西藏大学', 'utibet.edu.cn'
  UNION ALL SELECT '西藏大学', 'xzu.edu.cn'
  UNION ALL SELECT '西北大学', 'nwu.edu.cn'
  UNION ALL SELECT '西安电子科技大学', 'xidian.edu.cn'
  UNION ALL SELECT '长安大学', 'chd.edu.cn'
  UNION ALL SELECT '陕西师范大学', 'snnu.edu.cn'
  UNION ALL SELECT '空军军医大学', 'fmmu.edu.cn'
  UNION ALL SELECT '青海大学', 'qhu.edu.cn'
  UNION ALL SELECT '宁夏大学', 'nxu.edu.cn'
  UNION ALL SELECT '新疆大学', 'xju.edu.cn'
  UNION ALL SELECT '石河子大学', 'shzu.edu.cn'
) d ON d.name = s.name;
