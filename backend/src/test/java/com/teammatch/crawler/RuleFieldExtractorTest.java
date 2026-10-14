package com.teammatch.crawler;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 规则抽取。前两个用例的句子摘自电赛官网的真实通知，其余是常见的其他写法和容易抽错的句子。
 */
class RuleFieldExtractorTest {

    private final RuleFieldExtractor extractor = new RuleFieldExtractor();

    /** 2025 年电赛的组织通知（节选），混进了网站横幅和评奖规则两句干扰 */
    private static final String NUEDC_2025 = String.join("\n",
            "全国大学生电子设计竞赛培训网将于2020年5月19日 21:00-23:00 进行升级维护，期间部分功能将会无法使用。",
            "7. 全国竞赛仍采用广泛征题、全国统一命题方式。请各赛区将征题汇总后于4月30日前报全国竞赛组委会秘书处。",
            "四、竞赛时间与形式",
            "1. 报名时间：2025年5月31日截止。各赛区负责本赛区的报名与情况汇总工作。",
            "2. 竞赛时间： 2025年全国竞赛时间为7月30日（周三）8:00至8月2日（周六）20:00。竞赛以赛区为单位统一组织报名、竞赛、评审和评奖工作。",
            "3. 学生采用自愿组合、3人一队的原则组成参赛队，由所在学校统一向赛区竞赛组委会报名。",
            "2025年全国竞赛继续采取网上发题方式。7月30日8:00开赛时，将依托全国大学生电子设计竞赛网站发布全国竞赛题目。",
            "同一题目、同一所学校获得全国一、二等奖的总队数合计不超过4个，其中一等奖队数不超过2个。");

    /** 2026 年电赛专题赛的第一次通知（节选） */
    private static final String NUEDC_2026_SPECIAL = String.join("\n",
            "专题赛初赛的参赛队成员为各省市区普通高等学校具有正式学籍的全日制在校本科或高职高专学生，"
                    + "采用校内自愿组合、3人一队的原则组建参赛队，1人只能参加一队，由所在学校统一向赛区竞赛组委会报名参加省级大学生电子设计竞赛。",
            "专题赛决赛每支参赛队可配备不超过2名赛前指导教师。",
            "专题赛决赛将于2026年8月23日—27日在武汉大学举行。报到时间为2026年8月23日（逾期未报到者视作放弃参赛）。",
            "各赛区组委会在赛区初赛评奖结束后，推荐初赛中的优秀参赛队参加决赛，并在8月10日前将参赛队信息报送专题赛分组委会。",
            "专题赛决赛不收取参赛报名费。");

    @Test
    void realNoticeWithRegistrationDeadlineAndTimedContest() {
        Extracted e = extractor.extract("关于组织2025年全国大学生电子设计竞赛的通知", NUEDC_2025);
        assertThat(e.year()).isEqualTo(2025);
        assertThat(e.editionTitle()).isEqualTo("2025 年");
        assertThat(e.registrationStart()).isNull();
        assertThat(e.registrationEnd()).isEqualTo(LocalDateTime.of(2025, 5, 31, 23, 59, 59));
        assertThat(e.contestStart()).isEqualTo(LocalDateTime.of(2025, 7, 30, 8, 0));
        assertThat(e.contestEnd()).isEqualTo(LocalDateTime.of(2025, 8, 2, 20, 0));
        assertThat(e.contestAllDay()).isFalse();
        assertThat(e.teamMin()).isEqualTo(3);
        assertThat(e.teamMax()).isEqualTo(3);
        // "同一所学校获得全国一、二等奖"是评奖规则，不是组队要求
        assertThat(e.sameSchoolRequired()).isNull();
        assertThat(e.advisorRequired()).isNull();
        assertThat(e.evidence().get("registration")).contains("5月31日截止");
        assertThat(e.evidence().get("contest")).contains("7月30日");
        assertThat(e.empty()).isFalse();
    }

    @Test
    void realNoticeWithAllDayContestRangeShorthand() {
        Extracted e = extractor.extract("关于开展2026年全国大学生电子设计竞赛 模拟电子系统设计专题赛的第一次通知", NUEDC_2026_SPECIAL);
        assertThat(e.year()).isEqualTo(2026);
        assertThat(e.contestStart()).isEqualTo(LocalDateTime.of(2026, 8, 23, 0, 0));
        assertThat(e.contestEnd()).isEqualTo(LocalDateTime.of(2026, 8, 27, 0, 0));
        assertThat(e.contestAllDay()).isTrue();
        assertThat(e.teamMin()).isEqualTo(3);
        assertThat(e.teamMax()).isEqualTo(3);
        assertThat(e.sameSchoolRequired()).isTrue();
        assertThat(e.advisorRequired()).isFalse();
        // 报名费那句不算报名时间
        assertThat(e.registrationEnd()).isNull();
    }

    @Test
    void registrationRangeAndMidnightDeadline() {
        Extracted range = extractor.extract("2026年某竞赛通知", "报名时间：2026年3月1日至3月20日，逾期不予受理。");
        assertThat(range.registrationStart()).isEqualTo(LocalDateTime.of(2026, 3, 1, 0, 0));
        assertThat(range.registrationEnd()).isEqualTo(LocalDateTime.of(2026, 3, 20, 23, 59, 59));

        Extracted midnight = extractor.extract("2026年某竞赛通知", "报名截止时间为2026年4月10日24时。");
        assertThat(midnight.registrationEnd()).isEqualTo(LocalDateTime.of(2026, 4, 10, 23, 59, 59));
    }

    @Test
    void contestAcrossNewYearAndIsoDates() {
        Extracted winter = extractor.extract("2026年冬季赛通知", "比赛时间：2026年12月28日至1月3日。");
        assertThat(winter.contestStart()).isEqualTo(LocalDateTime.of(2026, 12, 28, 0, 0));
        assertThat(winter.contestEnd()).isEqualTo(LocalDateTime.of(2027, 1, 3, 0, 0));

        Extracted iso = extractor.extract("2026年通知", "竞赛时间：2026-05-10 09:00 至 2026-05-10 17:00。");
        assertThat(iso.contestStart()).isEqualTo(LocalDateTime.of(2026, 5, 10, 9, 0));
        assertThat(iso.contestEnd()).isEqualTo(LocalDateTime.of(2026, 5, 10, 17, 0));
        assertThat(iso.contestAllDay()).isFalse();
    }

    @Test
    void fullWidthDigitsAreNormalized() {
        Extracted e = extractor.extract("２０２６年通知", "报名截止：２０２６年５月３１日。");
        assertThat(e.year()).isEqualTo(2026);
        assertThat(e.registrationEnd()).isEqualTo(LocalDateTime.of(2026, 5, 31, 23, 59, 59));
    }

    @Test
    void teamSizePhrasings() {
        assertTeam("每队不超过5人。", 1, 5);
        assertTeam("每个参赛队由3名学生组成。", 3, 3);
        assertTeam("参赛团队人数为3-5人。", 3, 5);
        assertTeam("每支队伍最多4名队员，须为同一所高校在籍学生。", 1, 4);
        assertTeam("每队至少2人。", 2, null);
        assertTeam("每队由三名学生组成。", 3, 3);
    }

    @Test
    void sameSchoolAndAdvisorFlags() {
        assertThat(extractor.extract("t", "参赛队员须为同一所高校在籍学生。").sameSchoolRequired()).isTrue();
        assertThat(extractor.extract("t", "允许跨校组队。").sameSchoolRequired()).isFalse();
        assertThat(extractor.extract("t", "每队须配备1名指导教师。").advisorRequired()).isTrue();
        assertThat(extractor.extract("t", "每队可以聘请指导教师。").advisorRequired()).isFalse();
        // "应按照……计算指导教师的工作量"不是要求必须有指导老师
        assertThat(extractor.extract("t", "应按照有关文件精神，承认并计算赛前指导教师的工作量。").advisorRequired()).isNull();
    }

    @Test
    void noticeWithoutFieldsIsEmpty() {
        Extracted e = extractor.extract("2026年全国大学生数学建模竞赛赛题讲评与经验交流会通知",
                "会议将邀请命题专家讲评赛题，欢迎各校指导教师和学生参加。");
        assertThat(e.year()).isEqualTo(2026);
        assertThat(e.empty()).isTrue();
    }

    @Test
    void yearFromBareTitleOrLatestInBody() {
        assertThat(extractor.extract("4C2025关于参赛作品信息限时核查完善的通知", "请于规定时间内完善。").year()).isEqualTo(2025);
        // 标题没有年份：正文回顾往届的年份不算，取最晚的
        assertThat(extractor.extract("中国大学生计算机设计大赛二十周年征文通知",
                "大赛始于2008年，2026年迎来二十周年。").year()).isEqualTo(2026);
    }

    private void assertTeam(String text, Integer min, Integer max) {
        Extracted e = extractor.extract("2026年通知", text);
        assertThat(e.teamMin()).as(text).isEqualTo(min);
        assertThat(e.teamMax()).as(text).isEqualTo(max);
    }
}
