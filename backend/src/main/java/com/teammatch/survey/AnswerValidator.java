package com.teammatch.survey;

import com.teammatch.common.BizException;
import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按题型校验答案，返回整理过的答案（只保留认识的题目，去掉首尾空格，数字统一成整数）。
 * 快速版只强制要求快速版里的题。
 */
public final class AnswerValidator {

    /** 时段格子：7 天 × 3 段 = 21 位 */
    static final int SLOT_BITS = 21;

    private AnswerValidator() {
    }

    /**
     * @param roleOptions 选项来自比赛分工模板的题（P4）用这里的角色名做选项；通用部分传空列表
     */
    public static Map<String, Object> validate(List<Question> questions, Map<String, Object> answers, boolean quick,
                                               List<String> roleOptions) {
        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Question q : questions) {
            Object value = answers.get(q.code());
            boolean required = q.required() && (!quick || q.inQuick());
            if (isEmpty(value)) {
                if (required) {
                    throw new BizException("「" + q.title() + "」还没填");
                }
                continue;
            }
            cleaned.put(q.code(), switch (q.type()) {
                case "SINGLE" -> single(q, value);
                case "MULTI" -> multi(q, value);
                case "RANKING" -> ranking(q, value, quick, roleOptions);
                case "SLIDER" -> slider(q, value);
                case "SLOT_GRID" -> slots(q, value);
                case "TEXT" -> text(q, value);
                case "PLEDGE" -> pledge(q, value);
                default -> throw new IllegalStateException("未知题型：" + q.type());
            });
        }
        return cleaned;
    }

    private static String single(Question q, Object value) {
        if (!(value instanceof String s) || !optionValues(q).contains(s)) {
            throw bad(q, "选项不对");
        }
        return s;
    }

    private static List<String> multi(Question q, Object value) {
        List<String> picked = stringList(q, value, optionValues(q));
        int min = intSetting(q.settings(), "minSelect", 1);
        Integer max = intSetting(q.settings(), "maxSelect", null);
        if (picked.size() < min) {
            throw bad(q, "至少选 " + min + " 项");
        }
        if (max != null && picked.size() > max) {
            throw bad(q, "最多选 " + max + " 项");
        }
        checkExclusive(q, picked);
        return picked;
    }

    @SuppressWarnings("unchecked")
    private static List<String> ranking(Question q, Object value, boolean quick, List<String> roleOptions) {
        boolean fromRoles = "roleTemplate".equals(q.settings().get("optionSource"));
        List<String> allowed = fromRoles ? roleOptions : optionValues(q);
        List<String> picked = stringList(q, value, allowed);
        int count = Math.min(intSetting(q.settings(), "selectCount", 1), allowed.size());
        int min = count;
        int max = count;
        if (quick && q.settings().get("quick") instanceof Map<?, ?> quickSettings) {
            Map<String, Object> qs = (Map<String, Object>) quickSettings;
            min = intSetting(qs, "minSelect", 1);
            max = intSetting(qs, "maxSelect", count);
        }
        if (picked.size() < min || picked.size() > max) {
            throw bad(q, min == max ? "要选 " + min + " 项并排序" : "选 " + min + " 到 " + max + " 项");
        }
        return picked;
    }

    private static Integer slider(Question q, Object value) {
        if (!(value instanceof Number n)) {
            throw bad(q, "要填数字");
        }
        int v = (int) Math.round(n.doubleValue());
        int min = intSetting(q.settings(), "min", 0);
        int max = intSetting(q.settings(), "max", 100);
        if (v < min || v > max) {
            throw bad(q, "要在 " + min + " 到 " + max + " 之间");
        }
        return v;
    }

    private static Integer slots(Question q, Object value) {
        if (!(value instanceof Number n) || n.longValue() < 0 || n.longValue() >= (1L << SLOT_BITS)) {
            throw bad(q, "时段数据不对");
        }
        int bits = n.intValue();
        int min = intSetting(q.settings(), "minSelect", 1);
        if (Integer.bitCount(bits) < min) {
            throw bad(q, "至少选 " + min + " 个时段");
        }
        return bits;
    }

    private static String text(Question q, Object value) {
        if (!(value instanceof String s)) {
            throw bad(q, "要填文字");
        }
        int max = intSetting(q.settings(), "maxLength", 500);
        String t = s.trim();
        if (t.length() > max) {
            throw bad(q, "最多 " + max + " 个字");
        }
        return t;
    }

    private static Boolean pledge(Question q, Object value) {
        if (!Boolean.TRUE.equals(value)) {
            throw bad(q, "需要勾选");
        }
        return true;
    }

    private static List<String> stringList(Question q, Object value, List<String> allowed) {
        if (!(value instanceof List<?> list)) {
            throw bad(q, "答案格式不对");
        }
        List<String> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Object o : list) {
            if (!(o instanceof String s) || !allowed.contains(s)) {
                throw bad(q, "有选项不对");
            }
            if (!seen.add(s)) {
                throw bad(q, "同一个选项选了两次");
            }
            out.add(s);
        }
        return out;
    }

    private static void checkExclusive(Question q, List<String> picked) {
        if (q.settings().get("exclusive") instanceof List<?> exclusive && picked.size() > 1) {
            for (Object e : exclusive) {
                if (picked.contains(e)) {
                    String label = q.options().stream().filter(o -> o.value().equals(e)).map(Option::label)
                            .findFirst().orElse(String.valueOf(e));
                    throw bad(q, "「" + label + "」不能和其他选项一起选");
                }
            }
        }
    }

    private static List<String> optionValues(Question q) {
        return q.options() == null ? List.of() : q.options().stream().map(Option::value).toList();
    }

    private static Integer intSetting(Map<String, Object> settings, String key, Integer fallback) {
        return settings.get(key) instanceof Number n ? n.intValue() : fallback;
    }

    private static boolean isEmpty(Object value) {
        return value == null
                || (value instanceof String s && s.isBlank())
                || (value instanceof List<?> l && l.isEmpty())
                || Boolean.FALSE.equals(value);
    }

    private static BizException bad(Question q, String why) {
        return new BizException("「" + q.title() + "」" + why);
    }
}
