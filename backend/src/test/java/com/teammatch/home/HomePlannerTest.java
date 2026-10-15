package com.teammatch.home;

import com.teammatch.competition.CompetitionNode;
import com.teammatch.home.HomeDtos.Stage;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HomePlannerTest {

    /** 2026-10-12 是周一 */
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    @Test
    void semesterRanges() {
        assertThat(HomePlanner.semester(LocalDate.of(2026, 10, 15)))
                .containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));
        assertThat(HomePlanner.semester(LocalDate.of(2027, 1, 10)))
                .as("一月还在上一年的秋季学期").containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31));
        assertThat(HomePlanner.semester(LocalDate.of(2027, 7, 20)))
                .as("暑期算进春季学期").containsExactly(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 8, 31));
        assertThat(HomePlanner.monday(LocalDate.of(2026, 10, 18))).isEqualTo(MONDAY);
        assertThat(HomePlanner.monday(MONDAY)).isEqualTo(MONDAY);
    }

    @Test
    void freeHoursSubtractBusyTime() {
        // 周一晚上（19–22，3 小时）+ 周三下午（14–18，4 小时）
        int slots = bit(0, 2) | bit(2, 1);
        assertThat(HomePlanner.freeHours(slots, MONDAY, List.of())).isEqualTo(7.0);

        // 周一 20:00–21:30 有个组会：晚上只剩 1.5 小时
        List<HomePlanner.Interval> meeting = List.of(span(MONDAY, 20, 0, MONDAY, 21, 30));
        assertThat(HomePlanner.freeHours(slots, MONDAY, meeting)).isEqualTo(5.5);

        // 两个安排重叠（20–21 和 20:30–22）只扣一次：晚上全没了
        List<HomePlanner.Interval> overlap = List.of(span(MONDAY, 20, 0, MONDAY, 21, 0), span(MONDAY, 20, 30, MONDAY, 22, 0),
                span(MONDAY, 19, 0, MONDAY, 20, 0));
        assertThat(HomePlanner.freeHours(slots, MONDAY, overlap)).isEqualTo(4.0);

        // 周三一整天在比赛（全天安排已经换算成 0 点到次日 0 点）
        List<HomePlanner.Interval> contest = List.of(span(MONDAY.plusDays(2), 0, 0, MONDAY.plusDays(3), 0, 0));
        assertThat(HomePlanner.freeHours(slots, MONDAY, contest)).isEqualTo(3.0);

        // 别的周的安排不影响这一周
        List<HomePlanner.Interval> nextWeek = List.of(span(MONDAY.plusWeeks(1), 0, 0, MONDAY.plusWeeks(1).plusDays(7), 0, 0));
        assertThat(HomePlanner.freeHours(slots, MONDAY, nextWeek)).isEqualTo(7.0);
    }

    @Test
    void warnWhenManyDeadlinesAndLittleTime() {
        assertThat(HomePlanner.warn(2, 4.0)).isTrue();
        assertThat(HomePlanner.warn(2, 6.0)).as("空闲正好 6 小时不提醒").isFalse();
        assertThat(HomePlanner.warn(1, 0.0)).as("只有一个截止不提醒").isFalse();
        assertThat(HomePlanner.warn(3, null)).as("没填空闲时段不判断").isFalse();
    }

    @Test
    void progressBetweenNodes() {
        LocalDateTime prev = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime next = LocalDateTime.of(2026, 10, 21, 0, 0);
        assertThat(HomePlanner.progress(prev, next, LocalDateTime.of(2026, 10, 11, 0, 0))).isEqualTo(50);
        assertThat(HomePlanner.progress(prev, next, LocalDateTime.of(2026, 9, 1, 0, 0))).isZero();
        // 没有上一个节点：按下一个节点前 30 天算
        assertThat(HomePlanner.progress(null, next, LocalDateTime.of(2026, 10, 6, 0, 0))).isEqualTo(50);
    }

    @Test
    void stagesMarkDoneCurrentTodo() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 15, 12, 0);
        List<Stage> stages = HomeService.stages(List.of(
                node("报名截止", LocalDateTime.of(2026, 10, 10, 23, 59), null),
                node("比赛", LocalDateTime.of(2026, 10, 14, 8, 0), LocalDateTime.of(2026, 10, 17, 20, 0)),
                node("成绩公布", LocalDateTime.of(2026, 11, 15, 0, 0), null)), now);
        assertThat(stages).extracting(Stage::state).containsExactly("DONE", "CURRENT", "TODO");
    }

    private static int bit(int day, int seg) {
        return 1 << (day * 3 + seg);
    }

    private static HomePlanner.Interval span(LocalDate d1, int h1, int m1, LocalDate d2, int h2, int m2) {
        return new HomePlanner.Interval(d1.atTime(h1, m1), d2.atTime(h2, m2));
    }

    private static CompetitionNode node(String title, LocalDateTime start, LocalDateTime end) {
        CompetitionNode n = new CompetitionNode();
        n.setTitle(title);
        n.setStartAt(start);
        n.setEndAt(end);
        n.setEndMode(CompetitionNode.EndMode.FIXED);
        return n;
    }
}
