package com.teammatch.profile;

import com.teammatch.profile.ProfileDtos.SkillLevel;
import com.teammatch.profile.UserPageAccess.HistoryVisibility;
import com.teammatch.profile.UserPageAccess.Level;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.user.UserBriefs.Contact;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人资料页（点开别人的名字看到的资料）。看不到的部分为空，前端据此显示"仅队友可见"之类的提示。
 */
public final class UserPageDtos {

    private UserPageDtos() {
    }

    /**
     * @param credit    互评信用，和用户卡片上显示的一样，谁都能看
     * @param selfIntro 问卷开放题"我能给队伍带来的三点"
     * @param weakness  问卷开放题"希望队友包容我的一个短板"，只给队友、主动联系过的人和管理员看
     * @param highlight 最高荣誉：优先从平台记录和已核验的履历里挑，没有才用自己填的
     */
    public record UserPage(Long id, String nickname, String schoolName, boolean verified, String major,
                           Integer enrollYear, String degree, String bio, LocalDate joinedAt, List<SkillLevel> skills,
                           Radar radar,
                           Credit credit, String selfIntro, String weakness, Stats stats, ExperienceView highlight,
                           List<TeamRecord> teams, List<ExperienceView> experiences, Mutual mutual, Contact contact,
                           Access access) {
    }

    /** score 为空表示还没有互评记录（新用户） */
    public record Credit(Double score, int ratedRounds) {
    }

    /** 像游戏里的生涯数据 */
    public record Stats(int teams, int finished, int captainTimes, int awards, long memberDays) {
    }

    /** IN_TEAM 还在队里 / FINISHED 比赛已结束 / LEFT 已离队（不区分主动退出还是被移出）/ DISBANDED 队伍已解散 */
    public enum RecordState { IN_TEAM, FINISHED, LEFT, DISBANDED }

    /**
     * 一条平台内的组队记录，类似游戏库里的一款游戏。
     *
     * @param award 比赛结束时队长录入的获奖
     * @param days  在队天数，类似游戏时长；比赛结束了又没记下结束时间的为空
     */
    public record TeamRecord(Long teamId, String teamName, Long competitionId, String competitionName,
                             String editionTitle, String roleName, boolean captain, RecordState state, String award,
                             LocalDateTime joinedAt, Long days) {
    }

    /** source + verified 决定页面上的来源标记：平台记录 / 已核验 / 自己填写 */
    /** proofUrl 只给队友、主动联系过的人和管理员；其他人只知道有没有证明（hasProof） */
    public record ExperienceView(Long id, String title, Integer year, String award, String proofUrl, boolean hasProof,
                                 UserExperience.Source source, boolean verified) {
    }

    /** 两人的共同点：同校、都关注了的比赛、一起待过的队伍 */
    public record Mutual(boolean sameSchool, List<SharedEdition> editions, List<SharedTeam> teams) {
    }

    public record SharedEdition(Long competitionId, String competitionName, String editionTitle) {
    }

    /** current：现在还是队友 */
    public record SharedTeam(Long teamId, String teamName, String competitionName, boolean current) {
    }

    /**
     * @param notice            有内容被隐藏时的说明；没有隐藏为空
     * @param historyVisibility 本人的可见范围设置，只有看自己的资料页时才返回
     * @param preview           是不是自己在预览"别人看到的我"
     */
    public record Access(Level level, boolean historyVisible, boolean contactVisible, String notice,
                         HistoryVisibility historyVisibility, boolean preview) {
    }

    public record PrivacyForm(@NotNull(message = "请选择可见范围") HistoryVisibility historyVisibility) {
    }
}
