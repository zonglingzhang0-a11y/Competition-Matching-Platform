package com.teammatch.calendar;

import com.teammatch.calendar.CalendarDtos.CalendarEvent;
import com.teammatch.calendar.CalendarDtos.Layer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class IcsWriterTest {

    private static final Instant NOW = Instant.parse("2026-10-15T04:00:00Z");

    @Test
    void timedAndAllDayEvents() {
        String ics = new IcsWriter("竞搭 · 测试", NOW)
                .event("node-1", "国赛 · 报名截止", "比赛官方节点", LocalDateTime.of(2026, 10, 15, 23, 59, 59), null, false, true)
                .event("node-2", "国赛 · 比赛进行", null, LocalDateTime.of(2026, 10, 23, 0, 0), LocalDateTime.of(2026, 10, 26, 0, 0), true, false)
                .finish();
        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\nVERSION:2.0\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics).contains("TZID:Asia/Shanghai", "DTSTAMP:20261015T040000Z");
        // 有时刻的：带时区，没有结束时间就不写 DTEND
        assertThat(ics).contains("UID:node-1@jingda\r\n", "DTSTART;TZID=Asia/Shanghai:20261015T235959\r\n");
        String first = ics.substring(ics.indexOf("UID:node-1"), ics.indexOf("END:VEVENT"));
        assertThat(first).doesNotContain("DTEND").contains("BEGIN:VALARM", "TRIGGER:-P1D");
        // 全天的：结束日期不含当天，所以 26 号结束写成 27 号
        String second = ics.substring(ics.indexOf("UID:node-2"));
        assertThat(second).contains("DTSTART;VALUE=DATE:20261023\r\n", "DTEND;VALUE=DATE:20261027\r\n")
                .doesNotContain("VALARM");
        // 每一行都以 CRLF 结尾，不会有单独的 LF
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n");
    }

    @Test
    void escapesSpecialCharacters() {
        assertThat(IcsWriter.escape("A,B;C\\D\n第二行")).isEqualTo("A\\,B\\;C\\\\D\\n第二行");
        assertThat(IcsWriter.escape(null)).isEmpty();
    }

    @Test
    void longLinesFoldWithoutSplittingCharacters() {
        String title = "全国大学生数学建模竞赛".repeat(5);
        String ics = new IcsWriter("x", NOW).event("e", title, null, LocalDateTime.of(2026, 10, 1, 9, 0), null, false, false).finish();
        String[] lines = ics.split("\r\n");
        assertThat(Arrays.stream(lines).mapToInt(l -> l.getBytes(StandardCharsets.UTF_8).length).max().orElse(0))
                .as("每行不超过 75 字节").isLessThanOrEqualTo(75);
        // 去掉折行（CRLF + 空格）后还原出完整标题
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + title + "\r\n");
    }

    @Test
    void summaryMarksSourceAndState() {
        assertThat(CalendarFeedService.summary(event(Layer.OFFICIAL, "国赛", "报名截止", true, null))).isEqualTo("国赛 · 报名截止（截止）");
        assertThat(CalendarFeedService.summary(event(Layer.TEAM, "数模冲刺队", "交初稿", true, true))).isEqualTo("数模冲刺队 · 交初稿（已完成）");
        assertThat(CalendarFeedService.summary(event(Layer.PERSONAL, "个人", "组会", false, null))).isEqualTo("组会");
    }

    private static CalendarEvent event(Layer layer, String layerName, String title, boolean deadline, Boolean done) {
        return new CalendarEvent("x", layer, "k", layerName, title, LocalDateTime.of(2026, 10, 1, 9, 0), null, false, false,
                false, deadline, true, done, null, null, null, null);
    }
}
