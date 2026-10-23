package com.teammatch.radar;

import com.teammatch.radar.RadarDtos.Axis;
import com.teammatch.radar.RadarDtos.Gap;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.radar.RadarDtos.SkillRef;
import com.teammatch.radar.RadarDtos.TeamRadar;
import com.teammatch.radar.RadarMath.Entry;
import com.teammatch.radar.RadarMath.Requirement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 六维能力雷达怎么算：技能归类、打分、没填技能时的推断/基础分、需求强度、覆盖率，
 * 以及每个轴下面带的具体技能。
 */
class RadarMathTest {

    @Test
    void everyAxisAlwaysComesBackInTheSameOrderAndNeverZero() {
        Radar radar = RadarMath.person(List.of(), null);

        assertThat(radar.axes()).extracting(Axis::key)
                .containsExactly("CODE", "MODEL", "DATA", "DESIGN", "WRITE", "HARDWARE");
        // 一项技能都没填也不会是 0：按基础分给一个起点，并标明这是"基础"
        assertThat(radar.axes()).allSatisfy(a -> {
            assertThat(a.score()).isEqualTo(15);
            assertThat(a.basis()).isEqualTo(RadarMath.BASIS_BASE);
            assertThat(a.band()).isEqualTo("基础");
            assertThat(a.reason()).isNotBlank();
        });
        assertThat(radar.count()).isZero();
        assertThat(radar.advice()).contains("还没有填具体技能").contains("不是 0");
    }

    @Test
    void skillsAreGroupedByWhatTheyAreNotJustByCategory() {
        // 数据分析在"算法与数据"类别里，但归数据这一轴；3D 建模在"设计"类别里，不能被"建模"抢走
        assertThat(RadarAxis.of("数据分析", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("统计分析", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("数学建模", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("机器学习", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("3D 建模", "设计")).isEqualTo(RadarAxis.DESIGN);
        assertThat(RadarAxis.of("PPT 制作", "写作表达")).isEqualTo(RadarAxis.DESIGN);
        assertThat(RadarAxis.of("嵌入式/单片机", "编程开发")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("电路设计", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("论文写作", "写作表达")).isEqualTo(RadarAxis.WRITE);
        assertThat(RadarAxis.of("Java", "编程开发")).isEqualTo(RadarAxis.CODE);
        // "数据库/SQL"是编程，不是数据分析；需求雷达拿不到类别（只有技能名）时也要认得出
        assertThat(RadarAxis.of("数据库/SQL", "编程开发")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("数据库/SQL", null)).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("论文写作", null)).isEqualTo(RadarAxis.WRITE);
        assertThat(RadarAxis.of("电路设计", null)).isEqualTo(RadarAxis.HARDWARE);
        // 技能字典以后加了没见过的技能：按类别归，类别也不认识就按编程开发算
        assertThat(RadarAxis.of("Rust", "编程开发")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("某个新技能", "说不清的类别")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of(null, null)).isEqualTo(RadarAxis.CODE);
    }

    @Test
    void skillsAddedToTheDictionaryLandOnTheRightAxis() {
        // V7 扩充的 44 项技能
        assertThat(RadarAxis.of("Go", "编程开发")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("Docker/部署", "编程开发")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("Qt/桌面开发", "编程开发")).isEqualTo(RadarAxis.CODE);
        assertThat(RadarAxis.of("数据挖掘", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("数据采集", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("数据清洗", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("问卷调查", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("竞品分析", "算法与数据")).isEqualTo(RadarAxis.DATA);
        assertThat(RadarAxis.of("计算机视觉", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("自然语言处理", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("仿真建模", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("时间序列分析", "算法与数据")).isEqualTo(RadarAxis.MODEL);
        assertThat(RadarAxis.of("交互设计", "设计")).isEqualTo(RadarAxis.DESIGN);
        assertThat(RadarAxis.of("Figma", "设计")).isEqualTo(RadarAxis.DESIGN);
        assertThat(RadarAxis.of("可视化图表", "设计")).isEqualTo(RadarAxis.DESIGN);
        assertThat(RadarAxis.of("项目申报书", "写作表达")).isEqualTo(RadarAxis.WRITE);
        assertThat(RadarAxis.of("专利撰写", "写作表达")).isEqualTo(RadarAxis.WRITE);
        assertThat(RadarAxis.of("宣传文案", "写作表达")).isEqualTo(RadarAxis.WRITE);
        assertThat(RadarAxis.of("FPGA", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("传感器与信号处理", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("电机控制", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("机器人控制", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("焊接调试", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        // 名字里带"设计"、其实是硬件的几项，单独指定过
        assertThat(RadarAxis.of("机械结构设计", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("电源设计", "硬件")).isEqualTo(RadarAxis.HARDWARE);
        assertThat(RadarAxis.of("3D 打印", "硬件")).isEqualTo(RadarAxis.HARDWARE);
    }

    @Test
    void scoreFollowsTheBestSkillWithASmallBonusForBreadth() {
        Radar one = RadarMath.person(List.of(new Entry("Java", "编程开发", 3)));
        assertThat(axis(one, "CODE").score()).isEqualTo(100);
        assertThat(axis(one, "CODE").band()).isEqualTo("精通");
        assertThat(axis(one, "CODE").basis()).isEqualTo(RadarMath.BASIS_SKILL);

        Radar two = RadarMath.person(List.of(
                new Entry("Java", "编程开发", 2), new Entry("Python", "编程开发", 1)));
        // 熟练 70 + 会第二项 5
        assertThat(axis(two, "CODE").score()).isEqualTo(75);
        assertThat(axis(two, "CODE").band()).isEqualTo("熟练");

        Radar many = RadarMath.person(List.of(
                new Entry("Java", "编程开发", 1), new Entry("Python", "编程开发", 1),
                new Entry("Vue", "编程开发", 1), new Entry("React", "编程开发", 1),
                new Entry("C/C++", "编程开发", 1)));
        // 入门 40 + 加满 15，不会因为填得多就超过精通
        assertThat(axis(many, "CODE").score()).isEqualTo(55);
    }

    @Test
    void skillsLeakIntoNeighbouringAxesButNeverAboveAKnownSkill() {
        Radar radar = RadarMath.person(List.of(new Entry("Java", "编程开发", 3)));

        // 编程 100 → 数据按 30 估、建模按 22 估；都没到"入门"那条线
        assertThat(axis(radar, "DATA").score()).isEqualTo(30);
        assertThat(axis(radar, "DATA").basis()).isEqualTo(RadarMath.BASIS_INFERRED);
        assertThat(axis(radar, "DATA").reason()).contains("编程开发");
        assertThat(axis(radar, "DESIGN").score()).isEqualTo(22);
        assertThat(axis(radar, "DESIGN").basis()).isEqualTo(RadarMath.BASIS_BASE);

        // 有具体技能的轴永远以技能为准，推断不会盖过入门 40
        Radar entryLevel = RadarMath.person(List.of(
                new Entry("Java", "编程开发", 1), new Entry("数学建模", "算法与数据", 3)));
        assertThat(axis(entryLevel, "CODE").score()).isEqualTo(40);
        assertThat(axis(entryLevel, "CODE").basis()).isEqualTo(RadarMath.BASIS_SKILL);
    }

    @Test
    void majorGivesRelevantAxesAStartingPoint() {
        Radar cs = RadarMath.person(List.of(), "计算机科学与技术");
        assertThat(axis(cs, "CODE").score()).isEqualTo(30);
        assertThat(axis(cs, "CODE").basis()).isEqualTo(RadarMath.BASIS_INFERRED);
        assertThat(axis(cs, "CODE").reason()).contains("专业");
        // 其他轴还是基础分
        assertThat(axis(cs, "WRITE").score()).isEqualTo(22);

        Radar design = RadarMath.person(List.of(), "视觉传达设计");
        assertThat(axis(design, "DESIGN").score()).isEqualTo(30);

        Radar electronics = RadarMath.person(List.of(), "电子信息工程");
        assertThat(axis(electronics, "HARDWARE").score()).isEqualTo(30);
    }

    @Test
    void everySkillIsListedUnderItsAxisWithItsLevel() {
        Radar radar = RadarMath.person(List.of(
                new Entry("Java", "编程开发", 3),
                new Entry("Python", "编程开发", 2),
                new Entry("Go", "编程开发", 1),
                new Entry("数学建模", "算法与数据", 3),
                new Entry("MATLAB", "算法与数据", 1)));

        // 六个轴都列出来，技能一项不少，熟练度高的排在前面
        assertThat(axis(radar, "CODE").skills()).extracting(SkillRef::name)
                .containsExactly("Java", "Python", "Go");
        assertThat(axis(radar, "CODE").skills()).extracting(SkillRef::level).containsExactly(3, 2, 1);
        assertThat(axis(radar, "MODEL").skills()).extracting(SkillRef::name)
                .containsExactly("数学建模", "MATLAB");
        assertThat(axis(radar, "CODE").skills()).extracting(SkillRef::category).containsOnly("编程开发");
        assertThat(axis(radar, "CODE").roles()).isEmpty();
        assertThat(radar.count()).isEqualTo(5);
    }

    @Test
    void strongestAxisIsASKillAxisAndGuessedAxesArePointedOut() {
        Radar radar = RadarMath.person(List.of(
                new Entry("数学建模", "算法与数据", 3),
                new Entry("MATLAB", "算法与数据", 2),
                new Entry("UI 设计", "设计", 2)));

        assertThat(radar.count()).isEqualTo(3);
        assertThat(radar.strongestKey()).isEqualTo("MODEL");
        assertThat(radar.strongestLabel()).isEqualTo("建模与算法");
        assertThat(radar.advice()).contains("建模与算法").contains("2 项技能");
        assertThat(radar.advice()).contains("数据与调研").contains("写作答辩");
        // 没填技能的轴会说明是估的，而不是"空着"
        assertThat(radar.advice()).contains("估了基础分");
        assertThat(axis(radar, "DATA").basis()).isEqualTo(RadarMath.BASIS_INFERRED);
        assertThat(axis(radar, "WRITE").basis()).isEqualTo(RadarMath.BASIS_BASE);
    }

    @Test
    void needRadarTakesTheHighestRequiredLevelAndListsRolesAndSkills() {
        Radar need = RadarMath.need(List.of(
                new Requirement("后端", "Java", "编程开发", 2),
                new Requirement("后端", "Spring Boot", "编程开发", 1),
                new Requirement("建模", "数学建模", "算法与数据", 3),
                new Requirement("建模", "Python", "编程开发", 1)), 2);

        // 熟练 80 + 同轴还有 Python、Spring Boot 两项（加满 10）
        assertThat(axis(need, "CODE").score()).isEqualTo(90);
        assertThat(axis(need, "CODE").band()).isEqualTo("要求熟练");
        assertThat(axis(need, "CODE").roles()).containsExactly("后端", "建模");
        assertThat(axis(need, "CODE").basis()).isEqualTo(RadarMath.BASIS_NEED);
        assertThat(axis(need, "CODE").skills()).extracting(SkillRef::name)
                .containsExactly("Java", "Python", "Spring Boot");
        assertThat(axis(need, "MODEL").score()).isEqualTo(100);
        assertThat(axis(need, "MODEL").band()).isEqualTo("要求精通");
        assertThat(need.count()).isEqualTo(2);
        assertThat(need.strongestKey()).isEqualTo("MODEL");
        assertThat(need.advice()).contains("建模与算法").contains("1 项技能");
        // 需求没有"基础分"：没勾要求就是 0
        assertThat(axis(need, "WRITE").score()).isZero();
        assertThat(axis(need, "WRITE").basis()).isEqualTo(RadarMath.BASIS_NONE);
        assertThat(axis(need, "WRITE").band()).isEqualTo("无要求");
    }

    @Test
    void needRadarGroupsNewDictionarySkillsByCategory() {
        // 需求雷达拿到的是技能名 + 类别，FPGA 和机械结构设计都要落到硬件
        Radar need = RadarMath.need(List.of(
                new Requirement("硬件", "FPGA", "硬件", 2),
                new Requirement("硬件", "机械结构设计", "硬件", 1),
                new Requirement("调研", "问卷调查", "算法与数据", 1)), 2);

        assertThat(axis(need, "HARDWARE").score()).isEqualTo(85);
        assertThat(axis(need, "HARDWARE").skills()).extracting(SkillRef::name)
                .containsExactly("FPGA", "机械结构设计");
        assertThat(axis(need, "DATA").skills()).extracting(SkillRef::name).containsExactly("问卷调查");
        assertThat(axis(need, "CODE").score()).isZero();
    }

    @Test
    void emptyRequirementsSayWhatTheCaptainShouldWrite() {
        Radar need = RadarMath.need(List.of(), 0);

        assertThat(need.axes()).allSatisfy(a -> assertThat(a.score()).isZero());
        assertThat(need.advice()).contains("还没在招募需求里勾技能要求");
    }

    @Test
    void fitIsTheCoverageOfWhatTheTeamAsksFor() {
        Radar need = RadarMath.need(List.of(
                new Requirement("后端", "Java", "编程开发", 2),
                new Requirement("数据", "数据分析", "算法与数据", 1)), 2);
        Radar mine = RadarMath.person(List.of(
                new Entry("Java", "编程开发", 3),
                new Entry("Python", "编程开发", 2)));

        TeamRadar team = RadarMath.team(need, mine);

        // 需求 80 + 55；编程 100（按 80 算），数据按推断的 30 算
        assertThat(team.fit()).isEqualTo(81);
        assertThat(team.gaps()).hasSize(2);
        assertThat(team.gaps().get(0).key()).isEqualTo("DATA");
        assertThat(team.gaps().get(0).status()).isEqualTo("PARTIAL");
        assertThat(team.gaps().get(0).text()).contains("推断").contains("还差一档");
        assertThat(team.gaps().get(1).key()).isEqualTo("CODE");
        assertThat(team.gaps().get(1).status()).isEqualTo("COVERED");
        assertThat(team.verdict()).contains("81%").contains("编程开发").contains("数据与调研");
    }

    @Test
    void partialCoverageIsReportedAsOneLevelShort() {
        Radar need = RadarMath.need(List.of(new Requirement("后端", "Java", "编程开发", 3)), 1);
        Radar mine = RadarMath.person(List.of(new Entry("Java", "编程开发", 2)));

        TeamRadar team = RadarMath.team(need, mine);

        assertThat(team.fit()).isEqualTo(70);
        Gap gap = team.gaps().get(0);
        assertThat(gap.status()).isEqualTo("PARTIAL");
        assertThat(gap.text()).contains("要求精通").contains("你是熟练").contains("还差一档");
        assertThat(team.verdict()).contains("还差一点");
    }

    @Test
    void noFitWithoutRequirementsOrWithoutMySkills() {
        Radar need = RadarMath.need(List.of(new Requirement("后端", "Java", "编程开发", 2)), 1);
        Radar empty = RadarMath.person(List.of(), null);

        TeamRadar noMine = RadarMath.team(need, empty);
        assertThat(noMine.fit()).isNull();
        assertThat(noMine.verdict()).contains("按基础分估").contains("补上技能");
        assertThat(noMine.gaps().get(0).status()).isEqualTo("MISSING");

        TeamRadar noNeed = RadarMath.team(RadarMath.need(List.of(), 0), empty);
        assertThat(noNeed.fit()).isNull();
        assertThat(noNeed.verdict()).contains("暂时画不出需求雷达图");
        assertThat(noNeed.gaps()).isEmpty();
    }

    @Test
    void allCoveredInvitesPeopleToApply() {
        Radar need = RadarMath.need(List.of(new Requirement("后端", "Java", "编程开发", 2)), 1);
        Radar mine = RadarMath.person(List.of(new Entry("Java", "编程开发", 3)));

        TeamRadar team = RadarMath.team(need, mine);

        assertThat(team.fit()).isEqualTo(100);
        assertThat(team.verdict()).contains("可以试着申请了");
    }

    private static Axis axis(Radar radar, String key) {
        return radar.axes().stream().filter(a -> a.key().equals(key)).findFirst().orElseThrow();
    }
}
