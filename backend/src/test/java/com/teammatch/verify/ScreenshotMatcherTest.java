package com.teammatch.verify;

import com.teammatch.verify.ScreenshotMatcher.Claimed;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenshotMatcherTest {

    /** 学信网学籍页截图的 OCR 结果（真实跑过 RapidOCR 的输出） */
    private static final List<String> XUEXIN = List.of("学籍信息", "姓名：", "林小雨", "女", "性别:", "出生日期:",
            "2005年03月12日", "院校：", "示例大学", "层次", "本科", "专业:", "计算机科学与技术", "入学日期:", "2023年09月01日");

    @Test
    void allThreeMatch() {
        var m = ScreenshotMatcher.match(XUEXIN, new Claimed("林小雨", "示例大学", 2023));
        assertThat(m.realName()).isTrue();
        assertThat(m.schoolName()).isTrue();
        assertThat(m.enrollYear()).isTrue();
        assertThat(m.all()).isTrue();
    }

    @Test
    void mismatchesAreReportedSeparately() {
        var m = ScreenshotMatcher.match(XUEXIN, new Claimed("林大雨", "另一所大学", 2024));
        assertThat(m.realName()).isFalse();
        assertThat(m.schoolName()).isFalse();
        assertThat(m.enrollYear()).isFalse();
    }

    @Test
    void ignoresSpacesAndPunctuation() {
        var lines = List.of("姓 名 : 林 小 雨", "学校（院系）：示例 大学");
        assertThat(ScreenshotMatcher.match(lines, new Claimed(" 林小雨 ", "示例大学", null)).realName()).isTrue();
        assertThat(ScreenshotMatcher.match(lines, new Claimed("林小雨", "示例大学", null)).schoolName()).isTrue();
    }

    @Test
    void schoolToleratesOneMisreadCharacter() {
        // OCR 把"示例"认成"示列"
        var lines = List.of("示列大学", "学生证");
        assertThat(ScreenshotMatcher.match(lines, new Claimed("x", "示例大学", null)).schoolName()).isTrue();
        // 差两个字就不算
        assertThat(ScreenshotMatcher.match(List.of("示列大字"), new Claimed("x", "示例大学", null)).schoolName()).isFalse();
        // 名字短，必须完全一致
        assertThat(ScreenshotMatcher.match(List.of("林小两"), new Claimed("林小雨", "x", null)).realName()).isFalse();
    }

    @Test
    void blankClaimsNeverMatch() {
        var m = ScreenshotMatcher.match(XUEXIN, new Claimed("", null, null));
        assertThat(m.realName()).isFalse();
        assertThat(m.schoolName()).isFalse();
        assertThat(m.enrollYear()).isFalse();
    }

    @Test
    void suggestsTheLongestKnownSchoolInTheText() {
        List<String> schools = List.of("示例大学", "示例大学附属中学", "另一所大学");
        assertThat(ScreenshotMatcher.suggestSchool(XUEXIN, schools, Function.identity())).isEqualTo("示例大学");
        assertThat(ScreenshotMatcher.suggestSchool(List.of("示例大学附属中学 学生证"), schools, Function.identity()))
                .isEqualTo("示例大学附属中学");
        assertThat(ScreenshotMatcher.suggestSchool(List.of("没有学校"), schools, Function.identity())).isNull();
    }
}
