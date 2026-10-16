package com.teammatch.calendar;

import com.teammatch.calendar.CalendarEntities.RepeatType;
import com.teammatch.competition.CompetitionDtos.NodeView;
import com.teammatch.competition.CompetitionNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CalendarDtos {

    private CalendarDtos() {
    }

    /** OFFICIAL 比赛官方节点 / TEAM 队伍里程碑 / PERSONAL 个人日程 */
    public enum Layer { OFFICIAL, TEAM, PERSONAL }

    /**
     * 日历上的一条。
     *
     * @param id             在整个日历里唯一，比如 node-3、milestone-5、event-7@2026-10-12
     * @param layerKey       图层的细分，比如 edition-1、team-2，前端按它分组开关
     * @param end            显示到什么时候：结束是区间时取最晚的那个，没有结束时间为空
     * @param fuzzy          时间说不准（开始或结束是一个区间、或者没有结束时间）
     * @param deadline       截止类节点：报名截止、作品提交、队伍里程碑
     * @param joined         官方节点：这一届我已经组队参加了（光关注的为 false）
     * @param conflictWith   撞期时，撞上的是哪一条
     * @param occurrenceDate 个人重复日程这一次的日期，"只删这一次"用
     */
    public record CalendarEvent(String id, Layer layer, String layerKey, String layerName, String title,
                                LocalDateTime start, LocalDateTime end, boolean allDay, boolean fuzzy, boolean busy,
                                boolean deadline, boolean joined, Boolean done, String conflictWith, String refType,
                                Long refId,
                                LocalDate occurrenceDate) {
    }

    public record Conflict(String first, String second, LocalDateTime start) {
    }

    /** 一段占用的时间 [start, end) */
    public record Period(LocalDateTime start, LocalDateTime end) {
    }

    public record CalendarView(List<CalendarEvent> events, List<Conflict> conflicts) {
    }

    public record EventForm(
            @NotBlank(message = "请填写标题") @Size(max = 64, message = "标题最多 64 个字") String title,
            @Size(max = 1000, message = "备注最多 1000 个字") String notes,
            @Size(max = 16) String color,
            @NotNull(message = "请填写开始时间") LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            RepeatType repeatType,
            /* WEEKLY 时的星期，1 = 周一 … 7 = 周日 */
            List<Integer> repeatWeekdays,
            LocalDate repeatUntil,
            boolean busy,
            /* 时间说不准（沿用简程的模型）：最晚哪天开始；结束是确定的、一个范围（endAt 到 endLatestAt），还是做完为止 */
            LocalDateTime startLatestAt,
            CompetitionNode.EndMode endMode,
            LocalDateTime endLatestAt) {
    }

    public record EventView(Long id, String title, String notes, String color, LocalDateTime startAt,
                            LocalDateTime endAt, boolean allDay, RepeatType repeatType, List<Integer> repeatWeekdays,
                            LocalDate repeatUntil, boolean busy, LocalDateTime startLatestAt,
                            CompetitionNode.EndMode endMode, LocalDateTime endLatestAt) {
    }

    public record MilestoneForm(
            @NotBlank(message = "请填写节点名称") @Size(max = 64, message = "名称最多 64 个字") String title,
            @Size(max = 500, message = "说明最多 500 个字") String note,
            @NotNull(message = "请填写时间") LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            CompetitionNode.EndMode endMode,
            LocalDateTime endLatestAt) {
    }

    /** tasks：这个节点下的分工待办 */
    public record MilestoneView(Long id, String title, String note, LocalDateTime startAt, LocalDateTime endAt,
                                boolean allDay, CompetitionNode.EndMode endMode, LocalDateTime endLatestAt,
                                LocalDateTime doneAt, List<TaskView> tasks) {
    }

    /**
     * 队伍页的时间线：官方节点 + 队伍里程碑。里程碑只有队员能看到。
     *
     * @param members 现在的队员，分任务时选人用（只给队员）
     */
    public record TeamTimeline(List<NodeView> official, List<MilestoneView> milestones, boolean member,
                               boolean canEdit, List<MemberOption> members) {
    }

    public record MemberOption(Long id, String nickname) {
    }

    /** assigneeId 为空表示先不分配 */
    public record TaskForm(
            @NotBlank(message = "请填写任务") @Size(max = 64, message = "任务最多 64 个字") String title,
            Long assigneeId) {
    }

    /**
     * 一条分工待办。
     *
     * @param assigneeLeft 负责人已经离队：任务算没人负责，谁都可以认领
     */
    public record TaskView(Long id, String title, Long assigneeId, String assigneeName, boolean assigneeLeft,
                           LocalDateTime doneAt) {
    }

    /** "我的队伍"页的我的待办：分给我、还没做完的任务 */
    public record MyTask(Long id, String title, Long teamId, String teamName, Long milestoneId, String milestoneTitle,
                         LocalDateTime startAt, LocalDateTime endAt, Boolean allDay) {
    }

    public record DoneForm(boolean done) {
    }
}
