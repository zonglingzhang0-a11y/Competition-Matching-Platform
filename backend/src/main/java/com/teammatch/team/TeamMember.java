package com.teammatch.team;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team_member")
public class TeamMember {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long teamId;
    /** 冗余自 team，数据库靠它保证"同一届一个人只能在一支队里" */
    private Long editionId;
    private Long userId;
    private String roleName;
    private Boolean isCaptain;
    private Status status;
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
    /** 数据库生成列，只读 */
    @TableField(insertStrategy = FieldStrategy.NEVER, updateStrategy = FieldStrategy.NEVER)
    private Integer activeKey;

    public enum Status { ACTIVE, LEFT, REMOVED }
}
