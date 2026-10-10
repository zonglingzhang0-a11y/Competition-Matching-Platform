package com.teammatch.competition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 比赛官方时间节点。时间字段沿用简程的模型，见 docs/数据库设计.md。
 */
@Data
@TableName("competition_node")
public class CompetitionNode {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long editionId;
    private String title;
    private NodeType nodeType;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Boolean allDay;
    /** 开始时间说不准时，最晚可能的开始时间 */
    private LocalDateTime startLatestAt;
    private EndMode endMode;
    /** endMode = RANGE 时，结束时间区间的上限 */
    private LocalDateTime endLatestAt;
    /** 这段时间被比赛占满，参与撞期检测 */
    private Boolean busy;
    private String note;
    private Integer sort;

    public enum NodeType { REGISTRATION, CONTEST, SUBMISSION, RESULT, OTHER }

    /** FIXED 确定 / RANGE 结束在一个区间里 / OPEN 还没有结束时间 */
    public enum EndMode { FIXED, RANGE, OPEN }
}
