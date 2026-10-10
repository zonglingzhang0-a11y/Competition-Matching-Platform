package com.teammatch.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.INPUT)
    private Long userId;
    private String realName;
    private String gender;
    private String major;
    private Integer enrollYear;
    private String degree;
    private String bio;
    private String contactWechat;
    private String contactQq;
    private LocalDateTime updatedAt;
}
