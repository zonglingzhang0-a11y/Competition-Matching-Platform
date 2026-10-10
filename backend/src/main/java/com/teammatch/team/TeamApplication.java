package com.teammatch.team;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 入队申请和邀请放在一张表里，用 direction 区分。
 */
@Data
@TableName("team_application")
public class TeamApplication {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    /** 申请人 / 被邀请人 */
    private Long userId;
    private Direction direction;
    private Long needId;
    private String message;
    private Status status;
    private Long handledBy;
    private LocalDateTime handledAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 数据库生成列，只读 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer pendingKey;

    /** APPLY：学生申请，队长处理 / INVITE：队长邀请，学生处理 */
    public enum Direction { APPLY, INVITE }

    /** EXPIRED：对方已经进了别的队、或者队伍满了，系统自动作废 */
    public enum Status { PENDING, ACCEPTED, REJECTED, WITHDRAWN, EXPIRED }
}
