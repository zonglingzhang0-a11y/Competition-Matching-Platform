package com.teammatch.competition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("competition")
public class Competition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String shortName;
    private String organizer;
    private String category;
    private Level level;
    /** 是否在中国高等教育学会的竞赛目录里 */
    private Boolean inCaheList;
    private String officialUrl;
    private String description;
    private Long roleTemplateId;
    private MatchPreset matchPreset;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public enum Level { INTERNATIONAL, NATIONAL, PROVINCIAL, SCHOOL }

    /** 匹配权重预设，对应 match_config 表 */
    public enum MatchPreset { DEFAULT, SHORT_INTENSIVE, LONG_TERM }
}
