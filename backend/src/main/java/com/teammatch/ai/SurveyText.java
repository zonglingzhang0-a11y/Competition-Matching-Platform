package com.teammatch.ai;

import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把问卷答案转成"题目：答案"的文字，给 AI 分析和本地规则共用。
 * 只保留和组队有关的信息：选项转成中文标签、多选按顺序拼起来、排序题保留顺序、时间格子只说有多少个时段有空，
 * 不会把整份答案原样发出去。
 */
public final class SurveyText {

    private static final int TEXT_MAX = 120;

    private SurveyText() {
    }

    /** 只传通用部分的题目进来；没有回答的题跳过 */
    public static List<String> summarize(List<Question> questions, Map<String, Object> answers) {
        List<String> out = new ArrayList<>();
        if (answers == null || answers.isEmpty()) {
            return out;
        }
        for (Question q : questions) {
            Object value = answers.get(q.code());
            if (value == null) {
                continue;
            }
            String text = format(q, value);
            if (text != null && !text.isBlank()) {
                out.add(q.title() + "：" + text);
            }
        }
        return out;
    }

    static String format(Question q, Object value) {
        String type = q.type() == null ? "" : q.type();
        return switch (type) {
            case "SINGLE" -> label(q, String.valueOf(value));
            case "MULTI", "RANKING" -> labels(q, value);
            case "SLIDER" -> slider(q, value);
            case "SLOT_GRID" -> slots(value);
            case "TEXT" -> cut(String.valueOf(value), TEXT_MAX);
            case "PLEDGE" -> null;
            default -> cut(String.valueOf(value), TEXT_MAX);
        };
    }

    private static String label(Question q, String value) {
        if (q.options() == null) {
            return value;
        }
        return q.options().stream().filter(o -> o.value().equals(value)).map(Option::label).findFirst()
                .orElse(value);
    }

    private static String labels(Question q, Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object item : list) {
            parts.add(label(q, String.valueOf(item)));
        }
        return String.join(" > ", parts);
    }

    private static String slider(Question q, Object value) {
        Object unit = q.settings() == null ? null : q.settings().get("unit");
        String u = unit == null ? "" : String.valueOf(unit);
        if (value instanceof Number n) {
            return trimNumber(n) + u;
        }
        return String.valueOf(value) + u;
    }

    private static String slots(Object value) {
        if (value instanceof Number n) {
            return "一周有空 " + Integer.bitCount(n.intValue()) + " 个时段";
        }
        return null;
    }

    private static String trimNumber(Number n) {
        double d = n.doubleValue();
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s.strip();
        if (t.isEmpty()) {
            return null;
        }
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
