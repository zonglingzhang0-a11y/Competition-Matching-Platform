package com.teammatch.survey;

import com.teammatch.common.BizException;
import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnswerValidatorTest {

    private static final List<Option> ABCD = List.of(opt("A"), opt("B"), opt("C"), opt("D"));

    private static final List<Question> QS = List.of(
            q("P1", "SINGLE", ABCD, Map.of(), true, true),
            q("P2", "SLIDER", null, Map.of("min", 0, "max", 30), true, true),
            q("P4", "RANKING", null, Map.of("optionSource", "roleTemplate", "selectCount", 2), true, true),
            q("P5", "MULTI", List.of(opt("exam"), opt("none")), Map.of("exclusive", List.of("none")), true, true),
            q("G4", "SLOT_GRID", null, Map.of("minSelect", 3), true, true),
            q("G11", "RANKING", List.of(opt("a"), opt("b"), opt("c"), opt("d")),
                    Map.of("selectCount", 3, "quick", Map.of("minSelect", 1, "maxSelect", 2)), true, true),
            q("G1", "MULTI", List.of(opt("x"), opt("y"), opt("z")), Map.of("maxSelect", 2), true, false),
            q("F2", "TEXT", null, Map.of("maxLength", 5), false, false),
            q("PLEDGE", "PLEDGE", null, Map.of(), true, true));

    private static final List<String> ROLES = List.of("建模", "编程", "论文写作与排版");

    @Test
    void validFullAnswersAreCleaned() {
        Map<String, Object> cleaned = AnswerValidator.validate(QS, full(), false, ROLES);
        assertThat(cleaned).containsEntry("P2", 12).containsEntry("F2", "短板").containsEntry("PLEDGE", true);
        assertThat(cleaned).doesNotContainKey("UNKNOWN");
    }

    @Test
    void quickModeOnlyRequiresQuickQuestions() {
        Map<String, Object> a = full();
        a.remove("G1");
        a.put("G11", List.of("a"));
        assertThat(AnswerValidator.validate(QS, a, true, ROLES)).containsKey("G11");
        assertThatThrownBy(() -> AnswerValidator.validate(QS, a, false, ROLES)).isInstanceOf(BizException.class);
    }

    @Test
    void rejectsBadAnswers() {
        assertRejected("P1", "E", "选项不对");
        assertRejected("P2", 31, "0 到 30");
        assertRejected("P4", List.of("编程"), "要选 2 项");
        assertRejected("P4", List.of("编程", "前端"), "有选项不对");
        assertRejected("P5", List.of("exam", "none"), "不能和其他选项一起选");
        assertRejected("G4", 0b11, "至少选 3 个时段");
        assertRejected("G4", 1 << 21, "时段数据不对");
        assertRejected("G11", List.of("a", "a", "b"), "选了两次");
        assertRejected("G1", List.of("x", "y", "z"), "最多选 2 项");
        assertRejected("F2", "超过五个字了", "最多 5 个字");
        assertRejected("PLEDGE", false, "还没填");
    }

    private static void assertRejected(String code, Object value, String message) {
        Map<String, Object> a = full();
        a.put(code, value);
        assertThatThrownBy(() -> AnswerValidator.validate(QS, a, false, ROLES))
                .as(code + " = " + value)
                .isInstanceOf(BizException.class)
                .hasMessageContaining(message);
    }

    private static Map<String, Object> full() {
        Map<String, Object> a = new HashMap<>();
        a.put("P1", "A");
        a.put("P2", 12.0);
        a.put("P4", List.of("编程", "建模"));
        a.put("P5", List.of("exam"));
        a.put("G4", 0b111);
        a.put("G11", List.of("a", "b", "c"));
        a.put("G1", List.of("x"));
        a.put("F2", " 短板 ");
        a.put("PLEDGE", true);
        a.put("UNKNOWN", "ignored");
        return a;
    }

    private static Question q(String code, String type, List<Option> options, Map<String, Object> settings,
                              boolean required, boolean inQuick) {
        return new Question(code, "X", "d", type, "题" + code, null, options, settings, required, inQuick);
    }

    private static Option opt(String v) {
        return new Option(v, v, null);
    }
}
