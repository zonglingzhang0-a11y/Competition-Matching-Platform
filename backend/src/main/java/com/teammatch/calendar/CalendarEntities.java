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

import java.time.LocalDate;
import java.time.LocalDateTime;

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
