package com.teammatch.home;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 我的主页（B 版三栏）一次要用的数据：顶部的下一个节点和本周时间、左栏的学期赛程带和每周负荷、
 * 中栏的我参加的比赛和适合报名的比赛、右栏的待处理和推荐队友。日历本身仍走 /api/calendar。
 */
public final class HomeDtos {

    private HomeDtos() {
    }

    /**
     * @param next          离现在最近的一个节点（比赛官方节点或自己队伍的里程碑），没有为空
     * @param after         再往后的两个
     * @param weekFreeHours 本周还能投入几小时：问卷勾的空闲时段扣掉忙碌安排；没填空闲时段为空
     * @param weekDeadlines 本周有几个截止
     */
    public record HomeView(LocalDate today, Upcoming next, List<Upcoming> after, Double weekFreeHours,
                           int weekDeadlines, Semester semester, List<MyCompetition> mine,
                           List<Suggestion> suggestions, List<Pending> pending, List<Teammate> teammates) {
    }

    /**
     * 一个快到的节点。
     *
     * @param label    比赛简称，或者队伍名（队伍里程碑）
     * @param team     是队伍里程碑
     * @param progress 本阶段已经过了百分之几（上一个节点到这个节点之间）
     * @param refType  点了跳到哪：COMPETITION / TEAM
     */
    public record Upcoming(String title, String label, LocalDateTime at, long daysLeft, boolean deadline,
                           boolean team, Integer progress, String refType, Long refId) {
    }

    /** 学期赛程带：每个比赛一行，下面是每周的空闲和截止 */
    public record Semester(LocalDate start, LocalDate end, List<Lane> lanes, List<WeekLoad> weeks) {
    }

    /** 一行：一个比赛，连同自己队伍在这个比赛下的里程碑。joined：已经组队参加 */
    public record Lane(Long editionId, Long competitionId, String label, boolean joined, List<Mark> marks) {
    }

    /** end 为空表示一个时间点 */
    public record Mark(String title, LocalDateTime start, LocalDateTime end, boolean deadline, boolean team,
                       boolean fuzzy) {
    }

    /** warn：截止多、空闲少，提前提醒 */
    public record WeekLoad(LocalDate start, Double freeHours, int deadlines, boolean warn) {
    }

    /**
     * 我参加的比赛（每支所在的队伍一条）。
     *
     * @param stages       这一届的官方节点，标出哪些过了、现在在哪一步
     * @param nextDeadline 这个比赛里下一个截止（官方或自己队伍的）
     */
    public record MyCompetition(Long teamId, String teamName, Long editionId, Long competitionId,
                                String competitionName, String level, String editionTitle, List<String> members,
                                int memberCount, Integer maxMembers, int openNeeds, boolean captain,
                                String officialUrl, String noticeUrl, List<Stage> stages, Upcoming nextDeadline) {
    }

    /** state：DONE 已经过了 / CURRENT 正在进行或下一个要到的 / TODO 还没到 */
    public record Stage(String title, LocalDateTime at, String state) {
    }

    /** 适合报名的比赛：正在报名、还没参加。solo：个人赛，不用组队 */
    public record Suggestion(Long editionId, Long competitionId, String competitionName, String level,
                             String editionTitle, LocalDateTime registrationEnd, Long daysLeft, boolean solo,
                             int recruitingTeams, boolean followed, String officialUrl, String noticeUrl) {
    }

    /**
     * 待你处理。
     *
     * @param kind        INVITE 我收到的邀请 / APPLY 有人申请加入我当队长的队
     * @param who         邀请我的队长，或者申请人
     * @param consequence 处理之后会怎样，比如"接受后 10 月会有 3 个比赛同时进行"
     */
    public record Pending(String kind, Long applicationId, Long teamId, String teamName, String competitionName,
                          String who, String roleName, String message, LocalDateTime createdAt, String consequence) {
    }

    /** 给我当队长、还在招人的队推荐的队员 */
    public record Teammate(Long teamId, String teamName, Long userId, String nickname, String major,
                           Integer enrollYear, double score, String reason, boolean invited) {
    }
}
