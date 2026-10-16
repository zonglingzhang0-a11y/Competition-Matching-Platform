package com.teammatch.calendar;

import com.teammatch.calendar.CalendarDtos.Layer;
import com.teammatch.calendar.CalendarEntities.RepeatType;
import com.teammatch.calendar.Occurrences.Occurrence;
import com.teammatch.calendar.Occurrences.Rule;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OccurrencesTest {

    /** 2026-10-12 是周一 */
    private static final LocalDateTime MON_9 = LocalDateTime.of(2026, 10, 12, 9, 0);
    private static final LocalDateTime WEEK_START = LocalDate.of(2026, 10, 12).atStartOfDay();
    private static final LocalDateTime WEEK_END = LocalDate.of(2026, 10, 19).atStartOfDay();

    private static Rule rule(RepeatType repeat, Set<DayOfWeek> days, LocalDate until, Set<LocalDate> skip) {
        return new Rule(MON_9, MON_9.plusHours(2), false, repeat, days, until, skip);
    }

    private static List<LocalDate> dates(Rule r) {
        return Occurrences.between(r, WEEK_START, WEEK_END).stream().map(Occurrence::date).toList();
    }

    @Test
    void repeatRules() {
        assertThat(dates(rule(RepeatType.NONE, Set.of(), null, Set.of()))).containsExactly(LocalDate.of(2026, 10, 12));
        assertThat(dates(rule(RepeatType.DAILY, Set.of(), null, Set.of()))).hasSize(7);
        assertThat(dates(rule(RepeatType.WEEKDAYS, Set.of(), null, Set.of()))).hasSize(5);
        assertThat(dates(rule(RepeatType.WEEKLY, Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), null, Set.of())))
                .containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 16));
    }

    @Test
    void untilAndSkippedDaysAreRespected() {
        Rule r = rule(RepeatType.DAILY, Set.of(), LocalDate.of(2026, 10, 15), Set.of(LocalDate.of(2026, 10, 13)));
        assertThat(dates(r)).containsExactly(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 14), LocalDate.of(2026, 10, 15));
    }

    @Test
    void occurrenceKeepsItsLengthAndCrossesMidnight() {
        Rule late = new Rule(LocalDateTime.of(2026, 10, 11, 23, 0), LocalDateTime.of(2026, 10, 12, 1, 0), false,
                RepeatType.NONE, Set.of(), null, Set.of());
        List<Occurrence> os = Occurrences.between(late, WEEK_START, WEEK_END);
        assertThat(os).hasSize(1);
        assertThat(os.getFirst().end()).isEqualTo(LocalDateTime.of(2026, 10, 12, 1, 0));
    }

    @Test
    void allDayMultiDayEventsSpanWholeDays() {
        Rule trip = new Rule(MON_9, MON_9.plusDays(2), true, RepeatType.NONE, Set.of(), null, Set.of());
        Occurrence o = Occurrences.between(trip, WEEK_START, WEEK_END).getFirst();
        assertThat(o.start()).isEqualTo(WEEK_START);
        assertThat(o.end()).isEqualTo(WEEK_START.plusDays(3));
    }

    @Test
    void parseWeekdays() {
        assertThat(Occurrences.parseWeekdays("1, 3,5")).containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        assertThat(Occurrences.parseWeekdays(null)).isEmpty();
    }

    @Test
    void overlaps() {
        LocalDateTime t = MON_9;
        assertThat(Occurrences.overlaps(t, t.plusHours(2), t.plusHours(1), t.plusHours(3))).isTrue();
        assertThat(Occurrences.overlaps(t, t.plusHours(1), t.plusHours(1), t.plusHours(2))).as("首尾相接不算").isFalse();
        assertThat(Occurrences.overlaps(t.plusMinutes(30), null, t, t.plusHours(1))).as("时间点落在区间里").isTrue();
    }

    @Test
    void conflictsBetweenPersonalAndContest() {
        List<CalendarService.Item> items = new ArrayList<>(List.of(
                item("node-1", Layer.OFFICIAL, "edition-1", "国赛", "比赛进行", t(23, 18), t(26, 20), true, true),
                item("node-2", Layer.OFFICIAL, "edition-2", "计设", "现场决赛", t(24, 9), t(25, 18), true, false),
                item("event-1@x", Layer.PERSONAL, "personal", "个人", "社团换届", t(24, 14), t(24, 16), true, false),
                item("event-2@x", Layer.PERSONAL, "personal", "个人", "看电影", t(24, 19), t(24, 21), false, false)));
        var conflicts = CalendarService.markConflicts(items);
        // 社团换届和两场比赛都重叠；看电影不参与撞期检测；计设只是关注、没参加，所以两场比赛之间不算撞期
        assertThat(conflicts).hasSize(2);
        assertThat(items.get(2).conflictWith).isNotNull();
        assertThat(items.get(3).conflictWith).isNull();
    }

    @Test
    void allDayItemsCoverWholeDays() {
        // 全天的结束存的是最后一天的 0 点：10-23 一天的全天日程，和当天 18 点开始的比赛也算撞期
        List<CalendarService.Item> items = new ArrayList<>(List.of(
                item("node-1", Layer.OFFICIAL, "edition-1", "国赛", "比赛进行", t(23, 18), t(26, 20), true, true),
                allDay("event-1@x", "交报名表", t(23, 0), t(23, 0)),
                allDay("event-2@x", "社团招新", t(20, 0), t(22, 0))));
        var conflicts = CalendarService.markConflicts(items);
        assertThat(items.get(1).conflictWith).as("当天的全天日程").isNotNull();
        assertThat(items.get(2).conflictWith).as("前一天结束的不算").isNull();
        assertThat(conflicts).hasSize(1);
    }

    private static CalendarService.Item allDay(String id, String title, LocalDateTime start, LocalDateTime end) {
        return new CalendarService.Item(id, Layer.PERSONAL, "personal", "个人", title, start, end, true, false, true, null,
                null, null, null, false);
    }

    private static LocalDateTime t(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 0);
    }

    private static CalendarService.Item item(String id, Layer layer, String key, String name, String title,
                                             LocalDateTime start, LocalDateTime end, boolean busy, boolean joined) {
        return new CalendarService.Item(id, layer, key, name, title, start, end, false, false, busy, null, null, null,
                null, joined);
    }
}
