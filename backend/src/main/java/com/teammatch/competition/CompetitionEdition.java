package com.teammatch.competition;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("competition_edition")
public class CompetitionEdition {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long competitionId;
    private Integer year;
    private String title;
    private Integer teamMin;
    private Integer teamMax;
    private Boolean sameSchoolRequired;
    private Boolean advisorRequired;
    private Boolean englishRequired;
    private LocalDateTime registrationStart;
    private LocalDateTime registrationEnd;
    private String noticeUrl;
    private Source source;
    private Status status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    public enum Source { MANUAL, CRAWLER }

    /** DRAFT 草稿，学生看不到 / PUBLISHED 已发布 / ARCHIVED 已归档 */
    public enum Status { DRAFT, PUBLISHED, ARCHIVED }
}
