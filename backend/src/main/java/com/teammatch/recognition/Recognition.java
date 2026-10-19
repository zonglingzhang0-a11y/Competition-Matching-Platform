package com.teammatch.recognition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 某个学校对某个比赛的加分认定，由同校同学标注。一人对一个比赛只能提交一条，别的同学可以确认。
 */
@Data
@TableName("school_competition_recognition")
public class Recognition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long schoolId;
    private Long competitionId;
    /** 学校自己的分级，比如"A 类"、"一类竞赛" */
    private String gradeLabel;
    private String note;
    /** 学校文件的链接 */
    private String sourceUrl;
    private Long submittedBy;
    /** 确认的人数，不算提交人自己 */
    private Integer confirmCount;
    private Status status;
    private LocalDateTime createdAt;

    /** PENDING 确认的人还不够 / CONFIRMED 够了，对外显示 / REJECTED 管理员驳回 */
    public enum Status { PENDING, CONFIRMED, REJECTED }
}
