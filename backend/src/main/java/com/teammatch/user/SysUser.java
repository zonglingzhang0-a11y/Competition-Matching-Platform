package com.teammatch.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private UserRole role;
    private UserStatus status;
    private Long schoolId;
    private VerifyStatus verifyStatus;
    private LocalDate verifiedUntil;
    private LocalDateTime lastLoginAt;
    /** 日历订阅链接里的密钥，见 CalendarFeedService */
    private String calendarToken;
    /** 不在网站上时，把没看的新消息汇总发到邮箱，见 NotificationMailJob */
    private Boolean emailNotify;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum UserRole { STUDENT, TEACHER, ADMIN }

    public enum UserStatus { ACTIVE, BANNED }

    /** NONE 未认证 / PENDING 邮箱已验证、学校待确认 / VERIFIED 已认证 */
    public enum VerifyStatus { NONE, PENDING, VERIFIED }
}
