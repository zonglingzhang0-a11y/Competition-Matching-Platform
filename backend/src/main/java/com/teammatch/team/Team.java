package com.teammatch.team;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("team")
public class Team {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long editionId;
    private String name;
    private Long captainId;
    private String intro;
    private Status status;
    private Integer maxMembers;
    private MeetingMode meetingMode;
    /** 例会时段位图：第 day*3+seg 位，day 0 = 周一，seg 0/1/2 = 上午/下午/晚上 */
    private Integer meetingSlots;
    private String award;
    private LocalDateTime awardRecordedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;

    /**
     * RECRUITING 招募中 / FULL 已满员 / CLOSED 队长停止招募 / FINISHED 比赛结束 / DISBANDED 已解散。
     * RECRUITING 和 FULL 之间按人数自动切换。
     */
    public enum Status { RECRUITING, FULL, CLOSED, FINISHED, DISBANDED }

    public enum MeetingMode { OFFLINE, ONLINE, MIXED }

    public boolean isActive() {
        return status == Status.RECRUITING || status == Status.FULL || status == Status.CLOSED;
    }
}
