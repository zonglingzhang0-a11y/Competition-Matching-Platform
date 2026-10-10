package com.teammatch.calendar;

import com.teammatch.calendar.CalendarEntities.RepeatType;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 把一条（可能重复的）日程展开成某个时间范围里的每一次发生。规则和简程的 occursOn 一致：
 * 不重复 / 每天 / 工作日 / 每周指定几天，有截止日期，可以单独删掉某几天。
 */
public final class Occurrences {

    /** 没有结束时间的定时日程，默认占 1 小时 */
    static final Duration DEFAULT_LENGTH = Duration.ofHours(1);

    private Occurrences() {
    }

    /** 一次发生。date 是这次发生开始的那天，用来"只删这一次" */
    public record Occurrence(LocalDate date, LocalDateTime start, LocalDateTime end) {
    }

    /** 一条日程的规则 */
    public record Rule(LocalDateTime start, LocalDateTime end, boolean allDay, RepeatType repeat,
                       Set<DayOfWeek> weekdays, LocalDate until, Set<LocalDate> exceptions) {
    }

    /** "1,3,5" → 周一、周三、周五 */
    public static Set<DayOfWeek> parseWeekdays(String s) {
        if (s == null || s.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty())
                .map(x -> DayOfWeek.of(Integer.parseInt(x))).collect(Collectors.toSet());
    }

    /** 某一天有没有这条日程（只看开始那天） */
    static boolean occursOn(Rule r, LocalDate date) {
        LocalDate first = r.start().toLocalDate();
        if (date.isBefore(first) || (r.until() != null && date.isAfter(r.until())) || r.exceptions().contains(date)) {
            return false;
        }
        return switch (r.repeat()) {
            case NONE -> date.equals(first);
            case DAILY -> true;
            case WEEKDAYS -> date.getDayOfWeek().getValue() <= 5;
            case WEEKLY -> (r.weekdays().isEmpty() ? Set.of(first.getDayOfWeek()) : r.weekdays()).contains(date.getDayOfWeek());
        };
    }

    /** 一次发生的长度：全天的按整天算，定时的按 end − start，没有结束时间的按 1 小时 */
    static Duration length(Rule r) {
        if (r.allDay()) {
            LocalDate last = r.end() == null ? r.start().toLocalDate() : r.end().toLocalDate();
            return Duration.ofDays(Math.max(1, last.toEpochDay() - r.start().toLocalDate().toEpochDay() + 1));
        }
        if (r.end() == null || !r.end().isAfter(r.start())) {
            return DEFAULT_LENGTH;
        }
        return Duration.between(r.start(), r.end());
    }

    /** 和 [from, to) 有重叠的每一次发生 */
    public static List<Occurrence> between(Rule r, LocalDateTime from, LocalDateTime to) {
        Duration len = length(r);
        LocalTime startTime = r.allDay() ? LocalTime.MIDNIGHT : r.start().toLocalTime();
        // 跨天的日程可能在 from 之前开始、延续到 from 之后，所以往前多看几天
        LocalDate day = from.toLocalDate().minusDays(len.toDays() + 1);
        LocalDate last = to.toLocalDate();
        List<Occurrence> out = new ArrayList<>();
        for (; !day.isAfter(last); day = day.plusDays(1)) {
            if (!occursOn(r, day)) {
                continue;
            }
            LocalDateTime s = day.atTime(startTime);
            LocalDateTime e = s.plus(len);
            if (s.isBefore(to) && e.isAfter(from)) {
                out.add(new Occurrence(day, s, e));
            }
        }
        return out;
    }

    /** 两个时间段 [aStart, aEnd) 和 [bStart, bEnd) 是否重叠；结束为空的当作一个时间点 */
    public static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime ae = aEnd == null ? aStart : aEnd;
        LocalDateTime be = bEnd == null ? bStart : bEnd;
        if (ae.equals(aStart) || be.equals(bStart)) {
            return !aStart.isAfter(be) && !bStart.isAfter(ae);
        }
        return aStart.isBefore(be) && bStart.isBefore(ae);
    }
}
