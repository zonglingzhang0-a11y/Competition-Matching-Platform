package com.teammatch.crawler;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmFieldExtractorTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final String TEXT = """
            关于举办2026年全国大学生节能减排竞赛的通知
            各参赛学校请于 2026 年 10 月 20 日至 11 月 5 日 17:00 前通过官网完成网上报名。
            比赛将于11月21日—22日在线上进行。
            每支队伍由三名学生组成，须为同一所高校在读学生。
            每队可配备一名指导教师。
            """;

    @Test
    void usesFieldsWhoseQuotesAreInTheText() {
        String answer = """
                {"year": 2026,
                 "registration": {"start": "2026-10-20", "end": "2026-11-05 17:00", "quote": "各参赛学校请于 2026 年 10 月 20 日至 11 月 5 日 17:00 前通过官网完成网上报名。"},
                 "contest": {"start": "2026-11-21", "end": "2026-11-22", "quote": "比赛将于11月21日—22日在线上进行。"},
                 "teamSize": {"min": 3, "max": 3, "quote": "每支队伍由三名学生组成，须为同一所高校在读学生。"},
                 "sameSchool": {"value": true, "quote": "须为同一所高校在读学生"},
                 "advisor": {"value": true, "quote": "每队可配备一名指导教师。"}}
                """;
        Extracted e = LlmFieldExtractor.parse(answer, TEXT, JSON);
        assertThat(e.year()).isEqualTo(2026);
        assertThat(e.registrationStart()).isEqualTo(LocalDateTime.of(2026, 10, 20, 0, 0));
        assertThat(e.registrationEnd()).isEqualTo(LocalDateTime.of(2026, 11, 5, 17, 0));
        assertThat(e.contestStart()).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
        assertThat(e.contestAllDay()).isTrue();
        assertThat(e.teamMin()).isEqualTo(3);
        assertThat(e.teamMax()).as("三名 = 3").isEqualTo(3);
        assertThat(e.sameSchoolRequired()).isTrue();
        assertThat(e.advisorRequired()).isTrue();
        assertThat(e.evidence()).containsKeys("llm:registration", "llm:contest", "llm:teamSize", "llm:sameSchool", "llm:advisor");
        assertThat(e.extractor()).isEqualTo("llm");
    }

    @Test
    void dropsFieldsTheModelMadeUp() {
        String answer = """
                {"registration": {"start": null, "end": "2026-11-05", "quote": "报名截止时间为11月5日"},
                 "contest": {"start": "2026-12-01", "end": null, "quote": "比赛将于11月21日—22日在线上进行。"},
                 "teamSize": {"min": 1, "max": 5, "quote": "每支队伍由三名学生组成，须为同一所高校在读学生。"}}
                """;
        Extracted e = LlmFieldExtractor.parse(answer, TEXT, JSON);
        assertThat(e.registrationEnd()).as("原文里没有这句话").isNull();
        assertThat(e.contestStart()).as("原句里没有 12 月 1 日").isNull();
        assertThat(e.teamMax()).as("原句里没有 5 人").isNull();
        assertThat(e.evidence()).isEmpty();
    }

    @Test
    void ignoresThinkingAndChatter() {
        String answer = "<think>先找报名时间……{\"year\": 1999}</think>好的，结果如下：\n```json\n"
                + "{\"year\": 2026, \"teamSize\": {\"max\": 3, \"quote\": \"每支队伍由三名学生组成\"}}\n```";
        Extracted e = LlmFieldExtractor.parse(answer, TEXT, JSON);
        assertThat(e.year()).isEqualTo(2026);
        assertThat(e.teamMin()).isEqualTo(1);
        assertThat(e.teamMax()).isEqualTo(3);
    }

    @Test
    void rejectsAnswersWithoutJson() {
        assertThatThrownBy(() -> LlmFieldExtractor.parse("抱歉，我无法回答。", TEXT, JSON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LlmFieldExtractor.parse("{year: 2026,", TEXT, JSON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ruleResultsComeFirst() {
        Extracted rule = new Extracted(2026, "2026 年", null, LocalDateTime.of(2026, 11, 5, 17, 0), null, null, false,
                null, null, null, null, Map.of("registration", "规则找到的报名句子"), "rule");
        Extracted llm = new Extracted(2026, "2026 年", null, LocalDateTime.of(2026, 11, 6, 0, 0),
                LocalDateTime.of(2026, 11, 21, 0, 0), LocalDateTime.of(2026, 11, 22, 0, 0), true, 3, 3, true, null,
                Map.of("llm:registration", "别的句子", "llm:contest", "比赛句子", "llm:teamSize", "人数句子",
                        "llm:sameSchool", "同校句子"), "llm");
        Extracted merged = LlmFieldExtractor.merge(rule, llm);
        assertThat(merged.registrationEnd()).as("规则抽到的不换").isEqualTo(LocalDateTime.of(2026, 11, 5, 17, 0));
        assertThat(merged.contestStart()).isEqualTo(LocalDateTime.of(2026, 11, 21, 0, 0));
        assertThat(merged.teamMax()).isEqualTo(3);
        assertThat(merged.sameSchoolRequired()).isTrue();
        assertThat(merged.evidence())
                .containsEntry("registration", "规则找到的报名句子")
                .doesNotContainKey("llm:registration")
                .containsKeys("llm:contest", "llm:teamSize", "llm:sameSchool");
        assertThat(merged.extractor()).isEqualTo("rule+llm");

        assertThat(LlmFieldExtractor.worthAsking(merged)).as("都齐了就不用再问").isFalse();
        assertThat(LlmFieldExtractor.merge(merged, new Extracted(null, null, null, null, null, null, false, null, null,
                null, null, Map.of(), "llm")).extractor()).as("大模型什么都没补，标记不变").isEqualTo("rule+llm");
    }

    @Test
    void chineseNumeralDates() {
        String text = "参赛同学须在十月二十日至十一月五日之间登录官网完成报名，竞赛定于十二月一日举行。";
        String answer = """
                {"registration": {"start": "2026-10-20", "end": "2026-11-05", "quote": "参赛同学须在十月二十日至十一月五日之间登录官网完成报名"},
                 "contest": {"start": "2026-12-02", "end": null, "quote": "竞赛定于十二月一日举行"}}
                """;
        Extracted e = LlmFieldExtractor.parse(answer, text, JSON);
        assertThat(e.registrationEnd()).isEqualTo(LocalDateTime.of(2026, 11, 5, 23, 59, 59));
        assertThat(e.contestStart()).as("原句是一日，给的是二日").isNull();
        assertThat(LlmFieldExtractor.cnToDigits("十月二十日至十一月五日，三十一号")).isEqualTo("10月20日至11月5日，31号");
    }

    @Test
    void numbersInQuotes() {
        assertThat(LlmFieldExtractor.mentionsNumber("每队 3 人", 3)).isTrue();
        assertThat(LlmFieldExtractor.mentionsNumber("每队两人", 2)).isTrue();
        assertThat(LlmFieldExtractor.mentionsNumber("每队不超过五名学生", 5)).isTrue();
        assertThat(LlmFieldExtractor.mentionsNumber("每队 13 人", 3)).isFalse();
    }
}
