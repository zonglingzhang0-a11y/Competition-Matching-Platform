package com.teammatch.competition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("role_template_item")
public class RoleTemplateItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long templateId;
    private String name;
    private Integer sort;
}
