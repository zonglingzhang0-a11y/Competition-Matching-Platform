package com.teammatch.calendar;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teammatch.competition.CompetitionNode;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日程相关的表。时间字段和比赛节点一样，沿用简程的模型（见 docs/数据库设计.md）。
 */
public final class CalendarEntities {

    private CalendarEntities() {
    }

    @Data
    @TableName("personal_event")
    public static class PersonalEvent {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long userId;
        private String title;
        private String notes;
        private String color;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Boolean allDay;
        private LocalDateTime startLatestAt;
        private CompetitionNode.EndMode endMode;
        private LocalDateTime endLatestAt;
        private RepeatType repeatType;
        /** WEEKLY 时的星期，如 "1,3,5"（1 = 周一） */
        private String repeatWeekdays;
        private LocalDate repeatUntil;
        /** 被单独删掉的日期，JSON 数组 ["2026-10-12"] */
        private String exceptions;
        /** 参与撞期检测 */
        private Boolean busy;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Integer deleted;
    }

    public enum RepeatType { NONE, DAILY, WEEKDAYS, WEEKLY }

    @Data
    @TableName("team_milestone")
    public static class TeamMilestone {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long teamId;
        private String title;
        private String note;
        private LocalDateTime startAt;
        private LocalDateTime endAt;
        private Boolean allDay;
        private LocalDateTime startLatestAt;
        private CompetitionNode.EndMode endMode;
        private LocalDateTime endLatestAt;
        private Long createdBy;
        private LocalDateTime doneAt;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private Integer deleted;
    }

    @Mapper
    public interface PersonalEventMapper extends BaseMapper<PersonalEvent> {
    }

    @Mapper
    public interface MilestoneMapper extends BaseMapper<TeamMilestone> {
    }

    /** 里程碑下的分工待办 */
    @Data
    @TableName("team_task")
    public static class TeamTask {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long milestoneId;
        private String title;
        /** 负责人；空表示还没人认领 */
        private Long assigneeId;
        private LocalDateTime doneAt;
        private Integer sort;
        private LocalDateTime createdAt;
    }

    @Mapper
    public interface TaskMapper extends BaseMapper<TeamTask> {

        /** 分给我、还没做完的任务（只算我还在的队伍、没删掉的里程碑），按里程碑时间排 */
        @Select("""
                SELECT t.id, t.title, m.team_id AS teamId, tm.name AS teamName, m.id AS milestoneId,
                       m.title AS milestoneTitle, m.start_at AS startAt, m.end_at AS endAt, m.all_day AS allDay
                FROM team_task t
                JOIN team_milestone m ON m.id = t.milestone_id AND m.deleted = 0
                JOIN team tm ON tm.id = m.team_id
                JOIN team_member mb ON mb.team_id = m.team_id AND mb.user_id = #{userId} AND mb.status = 'ACTIVE'
                WHERE t.assignee_id = #{userId} AND t.done_at IS NULL
                ORDER BY m.start_at, t.sort, t.id
                """)
        List<CalendarDtos.MyTask> mine(@Param("userId") long userId);

        /** 这个人在某个里程碑下还没做完的任务名，提醒时用 */
        @Select("""
                SELECT title FROM team_task
                WHERE milestone_id = #{milestoneId} AND assignee_id = #{userId} AND done_at IS NULL
                ORDER BY sort, id
                """)
        List<String> openTitles(@Param("milestoneId") long milestoneId, @Param("userId") long userId);
    }

    @Mapper
    public interface ReminderMapper {

        /** 记一笔"已提醒"；返回 1 说明是第一次，返回 0 说明之前提醒过了 */
        @Insert("""
                INSERT IGNORE INTO reminder_sent (user_id, kind, ref_id, offset_hours)
                VALUES (#{userId}, #{kind}, #{refId}, #{offsetHours})
                """)
        int markSent(@Param("userId") long userId, @Param("kind") String kind, @Param("refId") long refId,
                     @Param("offsetHours") int offsetHours);
    }
}
