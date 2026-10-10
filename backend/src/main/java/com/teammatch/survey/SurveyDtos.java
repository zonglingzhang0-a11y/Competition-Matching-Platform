package com.teammatch.survey;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SurveyDtos {

    private SurveyDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Option(String value, String label, String group) {
    }

    /** 题目。options 为空时由 settings 决定选项来源（比如 P4 来自比赛的分工模板） */
    public record Question(String code, String part, String dimension, String type, String title, String note,
                           List<Option> options, Map<String, Object> settings, boolean required, boolean inQuick) {
    }

    public record Survey(String version, List<Question> questions) {
    }

    /**
     * 答案：题目代码 → 值。
     * SINGLE 是 "A"，MULTI / RANKING 是 ["x","y"]，SLIDER 是数字，SLOT_GRID 是位图整数，TEXT 是字符串，PLEDGE 是 true。
     */
    public record AnswersForm(@NotNull(message = "答案不能为空") Map<String, Object> answers, boolean quick) {
    }

    public record MyAnswers(Map<String, Object> answers, boolean quick, LocalDateTime submittedAt,
                            LocalDateTime pledgedAt) {
    }

    /** 比赛级部分还带上这场比赛的分工角色，P4 的选项从这里来 */
    public record EditionAnswers(Long editionId, String competitionName, String editionTitle, List<String> roles,
                                 MyAnswers mine) {
    }

    /** 问卷填写进度，首页和推荐页提示用 */
    public record Progress(boolean generalDone, boolean generalQuick, List<Long> editionsDone) {
    }
}
