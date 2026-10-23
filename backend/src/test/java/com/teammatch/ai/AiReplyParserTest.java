package com.teammatch.ai;

import com.teammatch.ai.AiDtos.AxisGap;
import com.teammatch.ai.AiDtos.ProfileBody;
import com.teammatch.ai.AiDtos.SuggestedSkill;
import com.teammatch.ai.AiDtos.TeamBody;
import com.teammatch.ai.AiFacts.AxisScore;
import com.teammatch.ai.AiFacts.MemberFact;
import com.teammatch.ai.AiFacts.NeedFact;
import com.teammatch.ai.AiFacts.ProfileFacts;
import com.teammatch.ai.AiFacts.SkillFact;
import com.teammatch.ai.AiFacts.TeamFacts;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型回答怎么核对：依据在资料里找不到的、技能字典里没有的、等级不对的，都要丢掉。
 */
class AiReplyParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final Map<String, String> DICT = Map.of(
            "Java", "编程开发", "Python", "编程开发", "LaTeX 排版", "写作表达", "数据分析", "算法与数据");

    private static final ProfileFacts FACTS = new ProfileFacts(
            "林晓", "计算机科学与技术", "本科", "做过两个 Java 项目，负责后端", "我能带来执行力和文档能力",
            List.of(new SkillFact("Java", "编程开发", 2), new SkillFact("Python", "编程开发", 3)),
            List.of("2025 年 全国大学生数学建模竞赛 省级一等奖"),
            List.of("队里有分歧时，你倾向怎么做决定：讨论后投票", "你希望队里多久同步一次进度：两三天一次"),
            List.of("队里有分歧时，你倾向怎么做决定：讨论后投票"));

    @Test
    void keepsOnlyItemsWhoseEvidenceIsInTheMaterial() {
        String answer = """
                ```json
                {"summary": "工程实现能力强，适合做后端",
                 "strengths": [
                   {"title": "后端实现", "detail": "做过两个 Java 项目", "evidence": "做过两个 Java 项目"},
                   {"title": "凭空夸的", "detail": "拿过国奖", "evidence": "拿过国家一等奖"}
                 ],
                 "skills": [
                   {"name": "Java", "level": 3, "evidence": "做过两个 Java 项目"},
                   {"name": "LaTeX 排版", "level": 2, "evidence": "会 LaTeX"},
                   {"name": "Kubernetes", "level": 2, "evidence": "做过两个 Java 项目"},
                   {"name": "Python", "level": 5, "evidence": "做过两个 Java 项目"}
                 ],
                 "personality": [
                   {"trait": "偏讨论后决定", "evidence": "讨论后投票"},
                   {"trait": "外向", "evidence": "喜欢社交"}
                 ],
                 "axisHints": [
                   {"axis": "WRITE", "reason": "履历里有论文，可以再补排版"},
                   {"axis": "FOO", "reason": "不存在"}
                 ]}
                ```
                """;

        ProfileBody body = AiReplyParser.parseProfile(answer, FACTS, DICT, JSON);

        assertThat(body.summary()).contains("工程实现能力");
        // 依据找不到的那条优势丢掉
        assertThat(body.strengths()).hasSize(1);
        assertThat(body.strengths().get(0).title()).isEqualTo("后端实现");
        // 字典里没有的 Kubernetes、等级 5、依据不在资料里的 LaTeX 都丢掉；Java 留下，且标成"已填"
        assertThat(body.skills()).extracting(SuggestedSkill::name).containsExactly("Java");
        assertThat(body.skills().get(0).level()).isEqualTo(3);
        assertThat(body.skills().get(0).owned()).isTrue();
        assertThat(body.personality()).extracting(AiDtos.Trait::trait).containsExactly("偏讨论后决定");
        assertThat(body.axisHints()).extracting(AiDtos.AxisHint::key).containsExactly("WRITE");
        assertThat(body.axisHints().get(0).label()).isEqualTo("写作答辩");
    }

    @Test
    void newSkillsAreMarkedNotOwned() {
        String answer = """
                {"summary": "可以补排版",
                 "skills": [{"name": "LaTeX 排版", "level": 2, "evidence": "文档能力"}]}
                """;
        // 资料里没写 LaTeX，但写了"文档能力"，依据能对上就保留
        ProfileBody body = AiReplyParser.parseProfile(answer, FACTS, DICT, JSON);
        assertThat(body.skills()).hasSize(1);
        assertThat(body.skills().get(0).name()).isEqualTo("LaTeX 排版");
        assertThat(body.skills().get(0).owned()).isFalse();
    }

    @Test
    void stripsThinkBlocksAndRejectsAnswersWithoutJson() {
        String answer = """
                <think>先想一下</think>
                {"summary": "只有一句话"}
                """;
        assertThat(AiReplyParser.parseProfile(answer, FACTS, DICT, JSON).summary()).isEqualTo("只有一句话");

        assertThatThrownBy(() -> AiReplyParser.parseProfile("模型今天不想说话", FACTS, DICT, JSON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiReplyParser.parseProfile("{\"strengths\": []}", FACTS, DICT, JSON))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有能用的内容");
    }

    @Test
    void teamAdviceKeepsKnownAxesAndCapsListLength() {
        TeamFacts facts = new TeamFacts("数模冲刺队", "全国大学生数学建模竞赛", "2026 年（示例数据）",
                List.of(new NeedFact("编程", 1, 0, "会 Python", List.of(new SkillFact("数据分析", "算法与数据", 2)))),
                List.of(new MemberFact("陈一舟", List.of(new SkillFact("Java", "编程开发", 3)), List.of("队长"),
                        List.of(new AxisScore("CODE", "编程开发", 100)))),
                List.of(new AxisScore("CODE", "编程开发", 80), new AxisScore("DATA", "数据与调研", 55)),
                List.of(new AxisScore("CODE", "编程开发", 100), new AxisScore("DATA", "数据与调研", 0)),
                List.of(new AiDtos.SkillGap("数据分析", 2, 0, "MISSING")));

        String answer = """
                {"summary": "缺一个会做数据的人",
                 "weakAxes": [{"axis": "DATA", "reason": "没人会数据分析"}, {"axis": "CODE", "reason": "其实够了"},
                              {"axis": "NOPE", "reason": "不存在"}],
                 "suggestions": ["找会数据分析的人", "s2", "s3", "s4", "s5"],
                 "candidateProfile": ["数模背景", "会 Python"],
                 "questions": ["做过什么数据处理？", "每周能投入多少小时？", "遇到分歧怎么办？", "q4", "q5"]}
                """;

        TeamBody body = AiReplyParser.parseTeam(answer, facts, JSON);

        assertThat(body.summary()).contains("数据");
        assertThat(body.weakAxes()).extracting(AxisGap::key).containsExactly("DATA");
        assertThat(body.weakAxes().get(0).status()).isEqualTo("MISSING");
        assertThat(body.suggestions()).hasSize(4);
        assertThat(body.candidateProfile()).hasSize(2);
        assertThat(body.questions()).hasSize(4);
        // 技能差距是本地算的，不用模型给
        assertThat(body.skillGaps()).extracting(AiDtos.SkillGap::name).containsExactly("数据分析");
    }
}
