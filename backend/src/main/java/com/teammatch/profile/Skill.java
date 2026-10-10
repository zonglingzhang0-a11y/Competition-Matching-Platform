package com.teammatch.profile;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("skill")
public class Skill {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String category;
    private Integer sort;
    private LocalDateTime createdAt;
}
