package com.teammatch.school;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("school_email_domain")
public class SchoolEmailDomain {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long schoolId;
    private String domain;
    private LocalDateTime createdAt;
}
