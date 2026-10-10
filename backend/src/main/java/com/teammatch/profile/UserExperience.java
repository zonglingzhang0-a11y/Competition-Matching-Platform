package com.teammatch.profile;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_experience")
public class UserExperience {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long competitionId;
    private Long teamId;
    private String title;
    private Integer year;
    private String award;
    private String proofUrl;
    private Source source;
    private Boolean verified;
    private LocalDateTime createdAt;

    /** MANUAL 自己填的 / PLATFORM 平台内组队、赛后录入成绩时生成 */
    public enum Source { MANUAL, PLATFORM }
}
