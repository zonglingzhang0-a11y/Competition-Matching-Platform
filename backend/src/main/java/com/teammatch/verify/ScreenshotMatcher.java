package com.teammatch.verify;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/**
 * 把 OCR 认出来的文字和学生自己填的姓名、学校、入学年份对一下。纯计算，方便单元测试。
 * <p>
 * 截图可能是 P 的，所以这只是帮管理员省力的预审：全对上记"一致"，有对不上的记"有出入"，最终还是人看。
 */
public final class ScreenshotMatcher {

    private ScreenshotMatcher() {
    }

    /** 学生填的 */
    public record Claimed(String realName, String schoolName, Integer enrollYear) {
    }

    /** 每一项有没有在图里认出来 */
    public record Matched(boolean realName, boolean schoolName, boolean enrollYear) {

        public boolean all() {
            return realName && schoolName && enrollYear;
        }
    }

    public static Matched match(List<String> lines, Claimed c) {
        String text = normalize(String.join("", lines));
        String name = normalize(c.realName());
        String school = normalize(c.schoolName());
        return new Matched(
                name.length() >= 2 && text.contains(name),
                // 学校名长，OCR 认错一个字也算对上
                school.length() >= 2 && fuzzyContains(text, school, school.length() >= 4 ? 1 : 0),
                c.enrollYear() != null && text.contains(String.valueOf(c.enrollYear())));
    }

    /**
     * 图里出现了哪所已收录的学校，取名字最长的那个（"示例大学附属中学"优先于"示例大学"）。没有返回 null。
     */
    public static <T> T suggestSchool(List<String> lines, List<T> schools, Function<T, String> nameOf) {
        String text = normalize(String.join("", lines));
        return schools.stream()
                .filter(s -> {
                    String n = normalize(nameOf.apply(s));
                    return n.length() >= 2 && text.contains(n);
                })
                .max(Comparator.comparingInt(s -> nameOf.apply(s).length()))
                .orElse(null);
    }

    /** 去掉空白和标点，OCR 常把冒号、括号认成全角半角混着的 */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        s.codePoints().filter(cp -> Character.isLetterOrDigit(cp) || Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN)
                .forEach(sb::appendCodePoint);
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** text 里有没有一段和 needle 等长、最多差 maxMistakes 个字的子串 */
    static boolean fuzzyContains(String text, String needle, int maxMistakes) {
        if (text.contains(needle)) {
            return true;
        }
        if (maxMistakes == 0 || needle.length() > text.length()) {
            return false;
        }
        for (int i = 0; i + needle.length() <= text.length(); i++) {
            int mistakes = 0;
            for (int j = 0; j < needle.length() && mistakes <= maxMistakes; j++) {
                if (!Objects.equals(text.charAt(i + j), needle.charAt(j))) {
                    mistakes++;
                }
            }
            if (mistakes <= maxMistakes) {
                return true;
            }
        }
        return false;
    }
}
