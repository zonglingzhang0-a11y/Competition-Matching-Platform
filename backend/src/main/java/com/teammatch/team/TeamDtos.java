package com.teammatch.team;

import com.teammatch.competition.EditionPhase;
import com.teammatch.user.UserBriefs.Contact;
import com.teammatch.user.UserBriefs.UserBrief;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class TeamDtos {

    private TeamDtos() {
    }

    // ---------- 请求 ----------

    public record CreateTeamRequest(
            @NotNull(message = "请选择比赛届次") Long editionId,
            @NotBlank(message = "请填写队名") @Size(max = 32, message = "队名最多 32 个字") String name,
            @Size(max = 1000, message = "简介最多 1000 个字") String intro,
            @Min(value = 1, message = "人数至少 1 人") @Max(value = 30, message = "人数太多了") Integer maxMembers,
            Team.MeetingMode meetingMode) {
    }

    public record UpdateTeamRequest(
            @NotBlank(message = "请填写队名") @Size(max = 32, message = "队名最多 32 个字") String name,
            @Size(max = 1000, message = "简介最多 1000 个字") String intro,
            @NotNull(message = "请填写队伍人数") @Min(value = 1, message = "人数至少 1 人") @Max(value = 30, message = "人数太多了")
            Integer maxMembers,
            Team.MeetingMode meetingMode) {
    }

    public record RecruitingRequest(boolean open) {
    }

    public record TransferCaptainRequest(@NotNull(message = "请选择新队长") Long userId) {
    }

    public record NeedSkillInput(
            @NotNull(message = "技能不能为空") Long skillId,
            @NotNull(message = "请选择最低熟练度") @Min(value = 1, message = "熟练度是 1 到 3") @Max(value = 3, message = "熟练度是 1 到 3")
            Integer minLevel) {
    }

    public record NeedForm(
            @NotBlank(message = "请填写缺的角色") @Size(max = 32, message = "角色名最多 32 个字") String roleName,
            @NotNull(message = "请填写人数") @Min(value = 1, message = "至少招 1 人") @Max(value = 10, message = "人数太多了")
            Integer headcount,
            @Size(max = 255, message = "要求最多 255 个字") String note,
            @NotNull @Valid @Size(max = 10, message = "技能要求最多 10 项") List<NeedSkillInput> skills) {
    }

    public record ApplyRequest(Long needId, @Size(max = 500, message = "留言最多 500 个字") String message) {
    }

    public record InviteRequest(
            @NotNull(message = "请选择要邀请的人") Long userId,
            Long needId,
            @Size(max = 500, message = "留言最多 500 个字") String message) {
    }

    // ---------- 返回 ----------

    public record EditionRef(Long editionId, Long competitionId, String competitionName, String editionTitle,
                             EditionPhase phase, Integer teamMin, Integer teamMax, boolean sameSchoolRequired) {
    }

    public record NeedSkillView(Long skillId, String name, Integer minLevel) {
    }

    public record NeedView(Long id, String roleName, Integer headcount, Integer filled, String note,
                           TeamNeed.Status status, List<NeedSkillView> skills) {
    }

    /** contact 只有队友才能看到，其他人为空 */
    public record MemberView(UserBrief user, String roleName, boolean captain, LocalDateTime joinedAt, Contact contact) {
    }

    public record AppBrief(Long id, TeamApplication.Direction direction, LocalDateTime createdAt) {
    }

    /**
     * 当前用户和这支队伍的关系。applyBlockedReason 为空表示可以申请，否则是不能申请的原因。
     */
    public record MyRelation(boolean member, boolean captain, AppBrief pendingApplication,
                             AppBrief pendingInvitation, String applyBlockedReason) {
    }

    public record TeamDetail(Long id, String name, String intro, Team.Status status, Integer maxMembers,
                             Team.MeetingMode meetingMode, int memberCount, EditionRef edition, Long captainId,
                             List<MemberView> members, List<NeedView> needs, MyRelation me, LocalDateTime createdAt,
                             /* 比赛结束时队长录入的奖项，没录为空 */
                             String award) {
    }

    public record TeamSummary(Long id, String name, String intro, Team.Status status, Integer maxMembers,
                              int memberCount, UserBrief captain, List<NeedView> openNeeds, EditionRef edition) {
    }

    public record TeamRef(Long id, String name, String competitionName, String editionTitle) {
    }

    public record ApplicationView(Long id, TeamApplication.Direction direction, TeamApplication.Status status,
                                  String message, String needRoleName, LocalDateTime createdAt,
                                  LocalDateTime handledAt, TeamRef team, UserBrief user) {
    }

    /** sent：我发出的申请 / received：我收到的邀请 */
    public record MyApplications(List<ApplicationView> sent, List<ApplicationView> received) {
    }

    /** 关注了这一届、还没组队的人。invited：这支队已经邀请过、对方还没处理 */
    public record Seeker(UserBrief user, boolean invited) {
    }
}
