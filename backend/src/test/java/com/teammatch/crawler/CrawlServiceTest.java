package com.teammatch.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CrawlServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 9, 0);

    @Test
    void staleWhenAllExtractedTimesHavePassed() {
        assertThat(CrawlService.staleReason("关于组织2025年全国大学生电子设计竞赛的通知",
                extracted(2025, LocalDateTime.of(2025, 5, 31, 23, 59, 59), LocalDateTime.of(2025, 8, 2, 20, 0)), NOW))
                .contains("最晚是 2025-08-02");
        // 比赛最后一天当天还不算过时
        assertThat(CrawlService.staleReason("t", extracted(2026, null, LocalDateTime.of(2026, 10, 14, 0, 0)), NOW)).isNull();
        assertThat(CrawlService.staleReason("t", extracted(2026, LocalDateTime.of(2026, 11, 1, 0, 0), null), NOW)).isNull();
    }

    @Test
    void staleByTitleYearOnlyWhenNoTimes() {
        assertThat(CrawlService.staleReason("2024年全国大学生数学建模竞赛获奖名单", extracted(2024, null, null), NOW))
                .isEqualTo("2024 年的旧通知，自动忽略");
        // 年份是从正文（比如网站横幅）里猜的，标题里没有，不能据此忽略
        assertThat(CrawlService.staleReason("关于报名的补充说明", extracted(2020, null, null), NOW)).isNull();
        assertThat(CrawlService.staleReason("2027年竞赛预告", extracted(2027, null, null), NOW)).isNull();
    }

    @Test
    void plainTextKeepsBlockBreaksAndDropsScripts() {
        Document doc = Jsoup.parse("""
                <div id="c"><h2>关于举办竞赛的通知</h2>
                <p>一、报名时间：<b>2026年5月31日</b>截止。</p><p>二、每队&nbsp;3&nbsp;人<br>三、校内组队</p>
                <script>var x = "不要";</script><table><tr><td>A</td><td>B</td></tr></table></div>
                """);
        assertThat(CrawlHttp.plainText(doc.getElementById("c"))).isEqualTo(String.join("\n",
                "关于举办竞赛的通知", "一、报名时间：2026年5月31日截止。", "二、每队 3 人", "三、校内组队", "A", "B"));
    }

    @Test
    void cleanTitleDropsListDates() {
        // 计设大赛官网列表里的两种写法
        assertThat(CrawlService.cleanTitle("09-18 .2026 中国大学生计算机设计大赛二十周年征文通知"))
                .isEqualTo("中国大学生计算机设计大赛二十周年征文通知");
        assertThat(CrawlService.cleanTitle("14 2026.09 2026年（第19届）中国大学生计算机设计大赛获奖公告"))
                .isEqualTo("2026年（第19届）中国大学生计算机设计大赛获奖公告");
        assertThat(CrawlService.cleanTitle("[2026-09-18] 关于举办竞赛的通知")).isEqualTo("关于举办竞赛的通知");
        assertThat(CrawlService.cleanTitle("关于举办竞赛的通知 2026-09-18")).isEqualTo("关于举办竞赛的通知");
        // 标题本身以年份开头的不动
        assertThat(CrawlService.cleanTitle("2026 年（第 19 届）中国大学生计算机设计大赛圆满落幕"))
                .isEqualTo("2026 年（第 19 届）中国大学生计算机设计大赛圆满落幕");
        assertThat(CrawlService.cleanTitle("4C2025关于参赛作品信息限时核查完善的通知")).isEqualTo("4C2025关于参赛作品信息限时核查完善的通知");
        assertThat(CrawlService.cleanTitle("2026-09-18")).isEqualTo("2026-09-18");
    }

    @Test
    void keywordsSplitOnChinesePunctuationToo() {
        assertThat(CrawlService.keywords("获奖, 名单，公示、赛题,,")).containsExactly("获奖", "名单", "公示", "赛题");
        assertThat(CrawlService.keywords(null)).isEmpty();
        assertThat(CrawlService.keywords("  ")).isEmpty();
    }

    private static Extracted extracted(Integer year, LocalDateTime registrationEnd, LocalDateTime contestEnd) {
        return new Extracted(year, year == null ? null : year + " 年", null, registrationEnd, contestEnd, contestEnd,
                false, null, null, null, null, Map.of(), "rule");
    }
}
