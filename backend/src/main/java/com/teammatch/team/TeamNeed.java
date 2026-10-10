package com.teammatch.team;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 招募需求：缺什么角色、几个人、要什么技能。
 */
@Data
@TableName("team_need")
public class TeamNeed {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    private String roleName;
    private Integer headcount;
    private Integer filled;
    private String note;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** OPEN 还在招 / FILLED 招满了 / CANCELLED 队长取消了 */
    public enum Status { OPEN, FILLED, CANCELLED }
}
