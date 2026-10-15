package com.teammatch.home;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 主页上和时间有关的计算，都是纯函数：
 * <ul>
 *   <li>学期范围：秋季 9/1 – 次年 1/31，春季 2/1 – 8/31（暑期算进春季，不少比赛在暑假）</li>
 *   <li>每周空闲：问卷 G4 勾的时段（上午 8–12、下午 14–18、晚上 19–22），扣掉日历里忙碌的安排</li>
 *   <li>冲突预警：一周里截止 ≥ 2 个、空闲 &lt; 6 小时</li>
 *   <li>阶段进度：上一个节点到下一个节点之间，现在走到了哪</li>
 * </ul>
 */
final class HomePlanner {

    /** 一天里的三个时段（小时），和问卷 G4 的上午 / 下午 / 晚上对应 */
    static final int[][] SEGMENTS = {{8, 12}, {14, 18}, {19, 22}};
    static final int WARN_DEADLINES = 2;
    static final double WARN_FREE_HOURS = 6;

    private HomePlanner() {
    }

    /** 一段忙碌的时间，[start, end) */
    record Interval(LocalDateTime start, LocalDateTime end) {
    }

    /** 今天所在学期的第一天和最后一天 */
    static LocalDate[] semester(LocalDate today) {
        int y = today.getYear();
        int m = today.getMonthValue();
        if (m >= 9) {
            return new LocalDate[]{LocalDate.of(y, 9, 1), LocalDate.of(y + 1, 1, 31)};
        }
        if (m == 1) {
            return new LocalDate[]{LocalDate.of(y - 1, 9, 1), LocalDate.of(y, 1, 31)};
        }
        return new LocalDate[]{LocalDate.of(y, 2, 1), LocalDate.of(y, 8, 31)};
    }

    /** 这一天所在周的周一 */
    static LocalDate monday(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /**
     * 从 weekStart（周一）起的 7 天里，问卷勾的空闲时段扣掉忙碌安排后还剩几小时（保留一位小数）。
     * slots 是问卷 G4 的位图：第 day*3+seg 位表示那天那个时段有空，day 0 = 周一。
     */
    static double freeHours(int slots, LocalDate weekStart, List<Interval> busy) {
        double minutes = 0;
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            for (int seg = 0; seg < SEGMENTS.length; seg++) {
                if ((slots & (1 << (day * 3 + seg))) == 0) {
                    continue;
                }
                LocalDateTime from = date.atTime(SEGMENTS[seg][0], 0);
                LocalDateTime to = date.atTime(SEGMENTS[seg][1], 0);
                minutes += Duration.between(from, to).toMinutes() - busyMinutes(from, to, busy);
            }
        }
        return Math.round(minutes / 6.0) / 10.0;
    }

    /** [from, to) 里被忙碌安排占掉的分钟数；安排之间重叠的部分只算一次 */
    static long busyMinutes(LocalDateTime from, LocalDateTime to, List<Interval> busy) {
        List<Interval> clipped = new ArrayList<>();
        for (Interval b : busy) {
            LocalDateTime s = b.start().isAfter(from) ? b.start() : from;
            LocalDateTime e = b.end().isBefore(to) ? b.end() : to;
            if (s.isBefore(e)) {
                clipped.add(new Interval(s, e));
            }
        }
        clipped.sort(Comparator.comparing(Interval::start));
        long total = 0;
        LocalDateTime curStart = null;
        LocalDateTime curEnd = null;
        for (Interval c : clipped) {
            if (curEnd == null || c.start().isAfter(curEnd)) {
                if (curEnd != null) {
                    total += Duration.between(curStart, curEnd).toMinutes();
                }
                curStart = c.start();
                curEnd = c.end();
            } else if (c.end().isAfter(curEnd)) {
                curEnd = c.end();
            }
        }
        if (curEnd != null) {
            total += Duration.between(curStart, curEnd).toMinutes();
        }
        return total;
    }

    /** 这一周要不要提醒：截止多、空闲少。没填空闲时段的不判断 */
    static boolean warn(int deadlines, Double freeHours) {
        return freeHours != null && deadlines >= WARN_DEADLINES && freeHours < WARN_FREE_HOURS;
    }

    /**
     * 本阶段已经过了百分之几：从上一个节点到下一个节点。没有上一个节点时，按下一个节点前 30 天算。
     */
    static int progress(LocalDateTime previous, LocalDateTime next, LocalDateTime now) {
        LocalDateTime start = previous != null && previous.isBefore(next) ? previous : next.minusDays(30);
        long total = Duration.between(start, next).toMinutes();
        long passed = Duration.between(start, now).toMinutes();
        if (total <= 0) {
            return 100;
        }
        return (int) Math.max(0, Math.min(100, Math.round(passed * 100.0 / total)));
    }
}
