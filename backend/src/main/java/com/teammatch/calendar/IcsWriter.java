package com.teammatch.calendar;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * 拼 iCalendar（RFC 5545）文本，给手机和电脑的日历订阅用。
 * 时间一律按北京时间（Asia/Shanghai，没有夏令时）写，全天日程用日期格式；行尾 CRLF，超过 75 字节的行折行。
 */
final class IcsWriter {

    static final String TZID = "Asia/Shanghai";
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final StringBuilder sb = new StringBuilder();
    private final String stamp;

    IcsWriter(String calendarName, Instant now) {
        this.stamp = UTC.format(now);
        line("BEGIN:VCALENDAR");
        line("VERSION:2.0");
        line("PRODID:-//JingDa//Competition Team Matching//ZH");
        line("CALSCALE:GREGORIAN");
        line("METHOD:PUBLISH");
        line("X-WR-CALNAME:" + escape(calendarName));
        line("X-WR-TIMEZONE:" + TZID);
        // 订阅的日历应用多久来取一次
        line("REFRESH-INTERVAL;VALUE=DURATION:PT6H");
        line("X-PUBLISHED-TTL:PT6H");
        line("BEGIN:VTIMEZONE");
        line("TZID:" + TZID);
        line("BEGIN:STANDARD");
        line("DTSTART:19700101T000000");
        line("TZOFFSETFROM:+0800");
        line("TZOFFSETTO:+0800");
        line("TZNAME:CST");
        line("END:STANDARD");
        line("END:VTIMEZONE");
    }

    /**
     * 一条日程。
     *
     * @param end       为空表示一个时间点；全天日程的 end 是最后一天（含）
     * @param remindDay 提前一天提醒（截止类）
     */
    IcsWriter event(String uid, String summary, String description, LocalDateTime start, LocalDateTime end,
                    boolean allDay, boolean remindDay) {
        line("BEGIN:VEVENT");
        line("UID:" + escape(uid) + "@jingda");
        line("DTSTAMP:" + stamp);
        if (allDay) {
            line("DTSTART;VALUE=DATE:" + DATE.format(start.toLocalDate()));
            // 全天日程的结束日期不含当天
            line("DTEND;VALUE=DATE:" + DATE.format((end == null ? start : end).toLocalDate().plusDays(1)));
        } else {
            line("DTSTART;TZID=" + TZID + ":" + LOCAL.format(start));
            if (end != null && end.isAfter(start)) {
                line("DTEND;TZID=" + TZID + ":" + LOCAL.format(end));
            }
        }
        line("SUMMARY:" + escape(summary));
        if (description != null && !description.isBlank()) {
            line("DESCRIPTION:" + escape(description));
        }
        if (remindDay) {
            line("BEGIN:VALARM");
            line("ACTION:DISPLAY");
            line("DESCRIPTION:" + escape(summary));
            line("TRIGGER:-P1D");
            line("END:VALARM");
        }
        line("END:VEVENT");
        return this;
    }

    String finish() {
        line("END:VCALENDAR");
        return sb.toString();
    }

    /** 文本里的反斜杠、分号、逗号、换行要转义 */
    static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n").replace("\r", "\\n");
    }

    /** 写一行：超过 75 字节就折行（续行以空格开头），不能把一个汉字拆开 */
    private void line(String content) {
        int bytes = 0;
        for (int i = 0; i < content.length(); ) {
            int cp = content.codePointAt(i);
            int len = new String(Character.toChars(cp)).getBytes(StandardCharsets.UTF_8).length;
            if (bytes + len > 75) {
                sb.append("\r\n ");
                // 续行开头的空格也算一个字节
                bytes = 1;
            }
            sb.appendCodePoint(cp);
            bytes += len;
            i += Character.charCount(cp);
        }
        sb.append("\r\n");
    }
}
