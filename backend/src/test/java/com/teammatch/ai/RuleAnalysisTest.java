package com.teammatch.ai;

import com.teammatch.ai.AiDtos.AxisGap;
import com.teammatch.ai.AiDtos.ProfileBody;
import com.teammatch.ai.AiDtos.TeamBody;
import com.teammatch.ai.AiFacts.AxisScore;
import com.teammatch.ai.AiFacts.MemberFact;
import com.teammatch.ai.AiFacts.NeedFact;
import com.teammatch.ai.AiFacts.ProfileFacts;
import com.teammatch.ai.AiFacts.SkillFact;
import com.teammatch.ai.AiFacts.TeamFacts;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.radar.RadarMath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 没配模型时的本地规则分析：不联网也要给出能看的结果，而且不能假装是 AI。
 */
class RuleAnalysisTest {

    @Test
    void profileFallbackUsesSkillsAndSurveyAnswers() {
        ProfileFacts facts = new ProfileFacts("林晓", "计算机科学与技术", "本科", "做过后端", "执行力强",
                List.of(new SkillFact("Java", "编程开发", 3), new SkillFact("Python", "编程开发", 2)),
                List.of("2025 年 全国大学生数学建模竞赛 省级一等奖"),
                List.of("队里有分歧时，你倾向怎么做决定：讨论后投票", "你参加这类比赛主要为了什么：保研加分"),
                List.of("队里有分歧时，你倾向怎么做决定：讨论后投票"));
        Radar radar = RadarMath.person(facts.skills().stream()
                .map(s -> new RadarMath.Entry(s.name(), s.category(), s.level())).toList(), facts.major());

        ProfileBody body = RuleAnalysis.profile(facts, radar);

        assertThat(body.summary()).contains("林晓").contains("编程开发");
        // 优势只来自有具体技能的轴，推断出来的轴不冒充优势
        assertThat(body.strengths()).hasSize(1);
        assertThat(body.strengths().get(0).title()).isEqualTo("编程开发");
        assertThat(body.strengths().get(0).detail()).contains("Java");
        assertThat(body.personality()).hasSize(1);
        assertThat(body.personality().get(0).trait()).contains("讨论后投票");
        // 没填技能的轴给建议，并说明现在是估的，不会凭空推荐具体技能
        assertThat(body.axisHints()).extracting(AiDtos.AxisHint::key).containsExactly("MODEL", "DATA", "DESIGN");
        assertThat(body.axisHints().get(1).reason()).contains("估");
        assertThat(body.skills()).isEmpty();
    }

    @Test
    void profileWithoutSkillsStillHasAStartingPoint() {
        ProfileBody body = RuleAnalysis.profile(new ProfileFacts("新同学", "计算机科学与技术", null, null, null,
                        List.of(), List.of(), List.of(), List.of()),
                RadarMath.person(List.of(), "计算机科学与技术"));

        assertThat(body.summary()).contains("还没有填具体技能");
        assertThat(body.strengths()).isEmpty();
        assertThat(body.axisHints()).hasSize(3);
    }

    @Test
    void teamFallbackFindsTheWeakAxisAndSaysWhatToLookFor() {
        TeamFacts facts = new TeamFacts("数模冲刺队", "全国大学生数学建模竞赛", "2026 年（示例数据）",
                List.of(new NeedFact("编程", 1, 0, "会 Python 做数据处理",
                        List.of(new SkillFact("数据分析", "算法与数据", 2)))),
                List.of(new MemberFact("陈一舟", List.of(new SkillFact("Java", "编程开发", 3)),
                        List.of("队里有分歧时，你倾向怎么做决定：讨论后投票"),
                        List.of(new AxisScore("CODE", "编程开发", 100)))),
                List.of(new AxisScore("CODE", "编程开发", 80), new AxisScore("DATA", "数据与调研", 55)),
                List.of(new AxisScore("CODE", "编程开发", 100), new AxisScore("DATA", "数据与调研", 0)),
                List.of(new AiDtos.SkillGap("数据分析", 2, 0, "MISSING")));

        TeamBody body = RuleAnalysis.team(facts);

        assertThat(body.summary()).contains("数模冲刺队").contains("数据与调研");
        assertThat(body.weakAxes()).extracting(AxisGap::key).containsExactly("DATA");
        assertThat(body.weakAxes().get(0).status()).isEqualTo("MISSING");
        assertThat(body.suggestions()).anySatisfy(s -> assertThat(s).contains("数据分析"));
        assertThat(body.candidateProfile()).isNotEmpty();
        assertThat(body.questions()).hasSize(3);
        assertThat(body.skillGaps()).extracting(AiDtos.SkillGap::name).containsExactly("数据分析");
    }

    @Test
    void teamWithoutRequirementsSaysToWriteThemFirst() {
        TeamFacts facts = new TeamFacts("空队", "某比赛", "2026",
                List.of(), List.of(), List.of(), List.of(), List.of());

        TeamBody body = RuleAnalysis.team(facts);

        assertThat(body.weakAxes()).isEmpty();
        assertThat(body.suggestions()).anySatisfy(s -> assertThat(s).contains("还没写技能要求"));
    }
}
