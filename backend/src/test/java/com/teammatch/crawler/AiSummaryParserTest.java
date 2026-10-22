package com.teammatch.crawler;

import com.teammatch.crawler.AiSummaryParser.Point;
import com.teammatch.crawler.AiSummaryParser.Source;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AI 总结的回答核对：依据要在原文里、数字要在依据里，核对不过的丢掉 */
class AiSummaryParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final List<Source> SOURCES = List.of(
            new Source(11, "关于举办2026年某竞赛的通知", """
                    一、参赛对象：全日制在校本科生，每队不超过三人。
                    二、报名办法：参赛同学须在十一月一日至十一月二十日之间登录官网完成报名，报名费每队 200 元。
                    三、竞赛定于十二月五日在各赛区同时举行。"""),
            new Source(12, "关于提交作品的补充通知", "作品以 PDF 格式提交，文件不超过 20MB。"));

    private static List<Point> parse(String points) {
        return AiSummaryParser.parse("{\"points\": [" + points + "]}", SOURCES, JSON);
    }

    @Test
    void 依据和数字都对得上的要点留下() {
        List<Point> ps = parse("""
                {"label": "报名时间", "text": "11月1日至11月20日在官网报名", "quote": "参赛同学须在十一月一日至十一月二十日之间登录官网完成报名", "source": 1},
                {"label": "组队要求", "text": "每队最多3人", "quote": "每队不超过三人", "source": 1},
                {"label": "提交作品", "text": "交 PDF，不超过20MB", "quote": "作品以 PDF 格式提交，文件不超过 20MB", "source": 2}""");
        assertThat(ps).extracting(Point::label).containsExactly("报名时间", "组队要求", "提交作品");
        assertThat(ps.get(2).recordId()).isEqualTo(12);
    }

    @Test
    void 原文里没有的依据丢掉() {
        assertThat(parse("""
                {"label": "报名时间", "text": "11月15日截止", "quote": "报名截止时间为11月15日", "source": 1}""")).isEmpty();
    }

    @Test
    void 要点里的数字依据里没有就丢掉() {
        // 依据是真的，但 text 把 20 日改成了 25 日
        assertThat(parse("""
                {"label": "报名时间", "text": "11月25日前报名", "quote": "参赛同学须在十一月一日至十一月二十日之间登录官网完成报名", "source": 1}""")).isEmpty();
    }

    @Test
    void 年份可以出现在通知别的地方() {
        assertThat(parse("""
                {"label": "比赛时间", "text": "2026年12月5日各赛区同时比赛", "quote": "竞赛定于十二月五日在各赛区同时举行", "source": 1}"""))
                .hasSize(1);
    }

    @Test
    void 第几篇写错了的丢掉() {
        assertThat(parse("""
                {"label": "组队要求", "text": "每队最多3人", "quote": "每队不超过三人", "source": 2},
                {"label": "组队要求", "text": "每队最多3人", "quote": "每队不超过三人", "source": 9}""")).isEmpty();
    }

    @Test
    void 不认识的分类归到其他注意事项_按分类顺序排() {
        List<Point> ps = parse("""
                {"label": "费用说明", "text": "报名费每队200元", "quote": "报名费每队 200 元", "source": 1},
                {"label": "参赛对象", "text": "全日制在校本科生", "quote": "参赛对象：全日制在校本科生", "source": 1}""");
        assertThat(ps).extracting(Point::label).containsExactly("参赛对象", "其他注意事项");
    }

    @Test
    void 同一类最多两条_重复的去掉() {
        String one = "{\"label\": \"组队要求\", \"text\": \"每队最多3人\", \"quote\": \"每队不超过三人\", \"source\": 1}";
        String two = "{\"label\": \"组队要求\", \"text\": \"不超过三人一队\", \"quote\": \"每队不超过三人\", \"source\": 1}";
        String three = "{\"label\": \"组队要求\", \"text\": \"三人以内\", \"quote\": \"每队不超过三人\", \"source\": 1}";
        assertThat(parse(one + "," + one + "," + two + "," + three)).hasSize(2);
    }

    @Test
    void 带思考过程和多余文字也能解析_不是JSON的报错() {
        String answer = "<think>先看看</think>好的，整理如下：{\"points\": [{\"label\": \"组队要求\", \"text\": \"每队最多3人\","
                + " \"quote\": \"每队不超过三人\", \"source\": 1}]} 以上。";
        assertThat(AiSummaryParser.parse(answer, SOURCES, JSON)).hasSize(1);
        assertThatThrownBy(() -> AiSummaryParser.parse("抱歉，我不知道。", SOURCES, JSON))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AiSummaryParser.parse("{\"summary\": \"…\"}", SOURCES, JSON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 几篇通知平分字数() {
        String prompt = AiSummaryParser.userPrompt("某竞赛",
                List.of(new Source(1, "甲", "字".repeat(5000)), new Source(2, "乙", "短")), 6000);
        assertThat(prompt).contains("第 1 篇通知：甲", "第 2 篇通知：乙", "（后面的内容省略）");
        assertThat(prompt.length()).isLessThan(3300);
    }
}
