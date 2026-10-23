package com.teammatch.ai;

import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 问卷答案转成给 AI / 规则看的文字：选项变中文、多选和排序保序、时间格子只说个数。
 */
class SurveyTextTest {

    private static final List<Question> QUESTIONS = List.of(
            question("G2", "SINGLE", "你推进任务的习惯更接近？", "拿到就开始，尽早做完", "按计划均匀推进"),
            question("G9", "MULTI", "在团队里，你更常扮演哪种角色？", "想点子、提新思路", "定方向、推着大家往前走"),
            question("G11", "RANKING", "下面哪些队友行为你最不能接受？", "长时间失联", "只挂名不干活"),
            question("G5", "SLIDER", "队友发来消息，你一般多久内会回复？"),
            question("G4", "SLOT_GRID", "一周里你通常哪些时段有空？"),
            question("F1", "TEXT", "我能给队伍带来的三点"),
            question("PLEDGE", "PLEDGE", "中途退出会提前说"));

    @Test
    void singleAndMultiAndRankingAreTurnedIntoLabels() {
        Map<String, Object> answers = Map.of(
                "G2", "A",
                "G9", List.of("plant", "shaper"),
                "G11", List.of("missing", "freeride"));

        List<String> lines = SurveyText.summarize(QUESTIONS, answers);

        assertThat(lines).containsExactly(
                "你推进任务的习惯更接近？：拿到就开始，尽早做完",
                "在团队里，你更常扮演哪种角色？：想点子、提新思路 > 定方向、推着大家往前走",
                "下面哪些队友行为你最不能接受？：长时间失联 > 只挂名不干活");
    }

    @Test
    void sliderSlotGridAndTextAreSummarized() {
        Map<String, Object> answers = Map.of(
                "G5", 6,
                "G4", 7,
                "F1", "会 LaTeX 排版、执行力强",
                "PLEDGE", true);

        List<String> lines = SurveyText.summarize(QUESTIONS, answers);

        assertThat(lines).containsExactly(
                "队友发来消息，你一般多久内会回复？：6小时",
                "一周里你通常哪些时段有空？：一周有空 3 个时段",
                "我能给队伍带来的三点：会 LaTeX 排版、执行力强");
    }

    @Test
    void unansweredAndUnknownCodesAreSkipped() {
        assertThat(SurveyText.summarize(QUESTIONS, Map.of())).isEmpty();
        assertThat(SurveyText.summarize(QUESTIONS, Map.of("X9", "whatever"))).isEmpty();
    }

    private static Question question(String code, String type, String title, String... optionLabels) {
        List<Option> options = null;
        if (optionLabels.length > 0) {
            // 多选和排序题的值是英文 key，和 G9 / G11 的种子数据一致
            String[] values = switch (code) {
                case "G9" -> new String[]{"plant", "shaper"};
                case "G11" -> new String[]{"missing", "freeride"};
                default -> new String[]{"A", "B", "C", "D"};
            };
            options = new java.util.ArrayList<>();
            for (int i = 0; i < optionLabels.length; i++) {
                options.add(new Option(values[i], optionLabels[i], null));
            }
        }
        Map<String, Object> settings = "SLIDER".equals(type) ? Map.of("unit", "小时") : null;
        return new Question(code, "GENERAL", "time", type, title, null, options, settings, true, false);
    }
}
