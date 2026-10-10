package com.teammatch.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notification")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Type type;
    private String title;
    private String content;
    /** 点开后跳到哪类页面，比如 TEAM */
    private String refType;
    private Long refId;
    private LocalDateTime readAt;
    private LocalDateTime createdAt;

    public enum Type {
        APPLY_RECEIVED, APPLY_RESULT, INVITE_RECEIVED, INVITE_RESULT,
        MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED, CAPTAIN_CHANGED, TEAM_DISBANDED,
        DEADLINE, MILESTONE, VERIFY_RESULT, SYSTEM
    }
}
