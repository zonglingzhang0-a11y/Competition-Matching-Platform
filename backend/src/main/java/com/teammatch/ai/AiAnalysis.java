package com.teammatch.ai;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ai_analysis 表：每个人 / 每支队伍留一份最近的分析结果（结构见 {@link AiDtos}）。
 * 页面打开先读这份，点"重新分析"才重新请求模型。
 */
@Data
@TableName("ai_analysis")
public class AiAnalysis {

    public static final String KIND_PROFILE = "PROFILE";
    public static final String KIND_TEAM = "TEAM";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String kind;
    private Long subjectId;
    private String payload;
    private String source;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
