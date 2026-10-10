package com.teammatch.survey;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

/**
 * 问卷相关的表。JSON 字段先按字符串读出来，用的时候再解析。
 */
public final class SurveyEntities {

    private SurveyEntities() {
    }

    @Data
    @TableName("survey_version")
    public static class SurveyVersion {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String code;
        private String name;
        private Boolean active;
        private LocalDateTime createdAt;
    }

    @Data
    @TableName("survey_question")
    public static class SurveyQuestion {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long versionId;
        private String code;
        /** GENERAL 通用部分 / COMPETITION 比赛级部分 */
        private String part;
        private String dimension;
        /** SINGLE / MULTI / RANKING / SLIDER / SLOT_GRID / TEXT / PLEDGE */
        private String type;
        private String title;
        private String note;
        private String options;
        private String settings;
        private Boolean required;
        private Boolean inQuick;
        private Integer sort;
    }

    @Data
    @TableName("survey_response")
    public static class SurveyResponse {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long userId;
        private Long versionId;
        /** 通用部分为空 */
        private Long editionId;
        @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
        private Long editionKey;
        private String answers;
        private Boolean quick;
        private LocalDateTime pledgedAt;
        private LocalDateTime submittedAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @TableName("match_config")
    public static class MatchConfigRow {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long versionId;
        private String preset;
        private String config;
        private Long updatedBy;
        private LocalDateTime updatedAt;
    }

    @Mapper
    public interface VersionMapper extends BaseMapper<SurveyVersion> {
    }

    @Mapper
    public interface QuestionMapper extends BaseMapper<SurveyQuestion> {
    }

    @Mapper
    public interface ResponseMapper extends BaseMapper<SurveyResponse> {
    }

    @Mapper
    public interface MatchConfigMapper extends BaseMapper<MatchConfigRow> {
    }
}
