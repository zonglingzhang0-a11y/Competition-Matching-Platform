package com.teammatch.team;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.EditionPhase;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.Skill;
import com.teammatch.team.TeamDtos.AppBrief;
import com.teammatch.team.TeamDtos.CreateTeamRequest;
import com.teammatch.team.TeamDtos.EditionRef;
import com.teammatch.team.TeamDtos.MemberView;
import com.teammatch.team.TeamDtos.MyRelation;
import com.teammatch.team.TeamDtos.NeedForm;
import com.teammatch.team.TeamDtos.NeedSkillInput;
import com.teammatch.team.TeamDtos.NeedSkillView;
import com.teammatch.team.TeamDtos.NeedView;
import com.teammatch.team.TeamDtos.Seeker;
import com.teammatch.team.TeamDtos.TeamDetail;
import com.teammatch.team.TeamDtos.TeamSummary;
import com.teammatch.team.TeamDtos.UpdateTeamRequest;
import com.teammatch.team.TeamMappers.ApplicationMapper;
import com.teammatch.team.TeamMappers.MemberMapper;
import com.teammatch.team.TeamMappers.NeedMapper;
import com.teammatch.team.TeamMappers.NeedSkillMapper;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserBriefs;
import com.teammatch.user.UserBriefs.Contact;
import com.teammatch.user.UserBriefs.UserBrief;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 队伍：建队、改信息、招募需求、退队、移出队员、转让队长、解散，以及各种查询。
 * 入队申请和邀请见 {@link ApplicationService}。
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final String ALREADY_IN_TEAM = "你已经在这届比赛的一支队伍里了，一届只能参加一支队";

    private final TeamMapper teamMapper;
    private final MemberMapper memberMapper;
    private final NeedMapper needMapper;
    private final NeedSkillMapper needSkillMapper;
    private final ApplicationMapper applicationMapper;
    private final SkillMapper skillMapper;
    private final SeekerMapper seekerMapper;
    private final TeamSupport support;
    private final UserService userService;
    private final UserBriefs userBriefs;
    private final NotificationService notifications;

    // ---------- 建队和修改 ----------

    @Transactional
    public long create(long userId, CreateTeamRequest req) {
        userService.requireVerified(userId);
        // 和接受申请一样先锁人，避免"建队"和"被别的队接受"同时发生
        support.lockUser(userId);
        CompetitionEdition e = support.requireEdition(req.editionId());
        if (support.phase(e) == EditionPhase.CLOSED) {
            throw new BizException("这届比赛报名已截止，不能再建队了");
        }
        int max = req.maxMembers() == null ? e.getTeamMax() : req.maxMembers();
        checkTeamSize(max, e, 1);
        if (support.activeMembership(userId, e.getId()) != null) {
            throw new BizException(409, ALREADY_IN_TEAM);
        }

        Team t = new Team();
        t.setEditionId(e.getId());
        t.setName(req.name().trim());
        t.setCaptainId(userId);
        t.setIntro(blankToNull(req.intro()));
        t.setStatus(Team.Status.RECRUITING);
        t.setMaxMembers(max);
        t.setMeetingMode(req.meetingMode() == null ? Team.MeetingMode.MIXED : req.meetingMode());
        t.setMeetingSlots(0);
        teamMapper.insert(t);

        TeamMember captain = new TeamMember();
        captain.setTeamId(t.getId());
        captain.setEditionId(e.getId());
        captain.setUserId(userId);
        captain.setIsCaptain(true);
        captain.setStatus(TeamMember.Status.ACTIVE);
        captain.setJoinedAt(support.now());
        try {
            memberMapper.insert(captain);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, ALREADY_IN_TEAM);
        }
        support.expirePendingOfUser(userId, e.getId());
        support.recomputeStatus(t);
        return t.getId();
    }

    @Transactional
    public void update(long userId, long teamId, UpdateTeamRequest req) {
        Team t = support.requireCaptain(userId, teamId);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        checkTeamSize(req.maxMembers(), e, support.activeCount(teamId));
        t.setName(req.name().trim());
        t.setIntro(blankToNull(req.intro()));
        t.setMaxMembers(req.maxMembers());
        if (req.meetingMode() != null) {
            t.setMeetingMode(req.meetingMode());
        }
        teamMapper.update(new LambdaUpdateWrapper<Team>()
                .eq(Team::getId, teamId)
                .set(Team::getName, t.getName())
                .set(Team::getIntro, t.getIntro())
                .set(Team::getMaxMembers, t.getMaxMembers())
                .set(Team::getMeetingMode, t.getMeetingMode()));
        if (support.recomputeStatus(t) == Team.Status.FULL) {
            support.expirePendingOfTeam(teamId);
        }
    }

    /** 队长手动停止招募或重新开放 */
    @Transactional
    public void setRecruiting(long userId, long teamId, boolean open) {
        Team t = support.requireCaptain(userId, teamId);
        if (!t.isActive()) {
            throw new BizException("比赛已经结束，不能再改招募状态");
        }
        Team update = new Team();
        update.setId(teamId);
        if (open) {
            update.setStatus(support.activeCount(teamId) >= t.getMaxMembers() ? Team.Status.FULL : Team.Status.RECRUITING);
        } else {
            update.setStatus(Team.Status.CLOSED);
            support.expirePendingOfTeam(teamId);
        }
        teamMapper.updateById(update);
    }

    @Transactional
    public void disband(long userId, long teamId) {
        Team t = support.requireCaptain(userId, teamId);
        List<TeamMember> members = support.activeMembers(teamId);
        memberMapper.update(new LambdaUpdateWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE)
                .set(TeamMember::getStatus, TeamMember.Status.LEFT)
                .set(TeamMember::getLeftAt, support.now()));
        support.expirePendingOfTeam(teamId);
        Team update = new Team();
        update.setId(teamId);
        update.setStatus(Team.Status.DISBANDED);
        teamMapper.updateById(update);
        for (TeamMember m : members) {
            if (!m.getUserId().equals(userId)) {
                notifications.send(m.getUserId(), Notification.Type.TEAM_DISBANDED, "队伍已解散",
                        "队长解散了「" + t.getName() + "」，你可以重新找队伍了。", null, null);
            }
        }
    }

    @Transactional
    public void leave(long userId, long teamId) {
        Team t = support.requireTeam(teamId);
        if (t.getCaptainId().equals(userId)) {
            throw new BizException("队长不能直接退出：先把队长转给其他队员，或者解散队伍");
        }
        TeamMember m = requireActiveMember(teamId, userId, "你不在这支队伍里");
        markGone(m, TeamMember.Status.LEFT);
        support.releaseNeed(teamId, m.getRoleName());
        support.recomputeStatus(t);
        String nickname = userService.getById(userId).getNickname();
        notifications.send(t.getCaptainId(), Notification.Type.MEMBER_LEFT, "有队员退出了",
                nickname + " 退出了「" + t.getName() + "」。", NotificationService.REF_TEAM, teamId);
    }

    @Transactional
    public void removeMember(long captainId, long teamId, long memberUserId) {
        Team t = support.requireCaptain(captainId, teamId);
        if (memberUserId == captainId) {
            throw new BizException("不能把自己移出队伍");
        }
        TeamMember m = requireActiveMember(teamId, memberUserId, "对方不在这支队伍里");
        markGone(m, TeamMember.Status.REMOVED);
        support.releaseNeed(teamId, m.getRoleName());
        support.recomputeStatus(t);
        notifications.send(memberUserId, Notification.Type.MEMBER_REMOVED, "你被移出了队伍",
                "队长把你移出了「" + t.getName() + "」，你可以重新找队伍了。", null, null);
    }

    @Transactional
    public void transferCaptain(long captainId, long teamId, long newCaptainId) {
        Team t = support.requireCaptain(captainId, teamId);
        if (newCaptainId == captainId) {
            throw new BizException("你已经是队长了");
        }
        TeamMember next = requireActiveMember(teamId, newCaptainId, "新队长必须是这支队伍的队员");
        TeamMember current = requireActiveMember(teamId, captainId, "你不在这支队伍里");
        current.setIsCaptain(false);
        memberMapper.updateById(current);
        next.setIsCaptain(true);
        memberMapper.updateById(next);
        Team update = new Team();
        update.setId(teamId);
        update.setCaptainId(newCaptainId);
        teamMapper.updateById(update);
        notifications.send(newCaptainId, Notification.Type.CAPTAIN_CHANGED, "你成为了队长",
                "你现在是「" + t.getName() + "」的队长，可以处理入队申请、管理招募需求了。", NotificationService.REF_TEAM, teamId);
    }

    // ---------- 招募需求 ----------

    @Transactional
    public long addNeed(long userId, long teamId, NeedForm form) {
        support.requireCaptain(userId, teamId);
        checkSkills(form.skills());
        TeamNeed need = new TeamNeed();
        need.setTeamId(teamId);
        need.setRoleName(form.roleName().trim());
        need.setHeadcount(form.headcount());
        need.setFilled(0);
        need.setNote(blankToNull(form.note()));
        need.setStatus(TeamNeed.Status.OPEN);
        needMapper.insert(need);
        if (!form.skills().isEmpty()) {
            needSkillMapper.insertAll(need.getId(), form.skills());
        }
        return need.getId();
    }

    @Transactional
    public void updateNeed(long userId, long needId, NeedForm form) {
        TeamNeed need = requireNeed(needId);
        support.requireCaptain(userId, need.getTeamId());
        if (need.getStatus() == TeamNeed.Status.CANCELLED) {
            throw new BizException("这条招募需求已经取消了");
        }
        if (form.headcount() < need.getFilled()) {
            throw new BizException("已经招到 " + need.getFilled() + " 人了，人数不能再少");
        }
        checkSkills(form.skills());
        need.setRoleName(form.roleName().trim());
        need.setHeadcount(form.headcount());
        need.setStatus(need.getFilled() >= form.headcount() ? TeamNeed.Status.FILLED : TeamNeed.Status.OPEN);
        needMapper.update(new LambdaUpdateWrapper<TeamNeed>()
                .eq(TeamNeed::getId, needId)
                .set(TeamNeed::getRoleName, need.getRoleName())
                .set(TeamNeed::getHeadcount, need.getHeadcount())
                .set(TeamNeed::getNote, blankToNull(form.note()))
                .set(TeamNeed::getStatus, need.getStatus()));
        needSkillMapper.deleteByNeed(needId);
        if (!form.skills().isEmpty()) {
            needSkillMapper.insertAll(needId, form.skills());
        }
    }

    /** 取消而不是删除：已经有申请引用了这条需求 */
    public void cancelNeed(long userId, long needId) {
        TeamNeed need = requireNeed(needId);
        support.requireCaptain(userId, need.getTeamId());
        TeamNeed update = new TeamNeed();
        update.setId(needId);
        update.setStatus(TeamNeed.Status.CANCELLED);
        needMapper.updateById(update);
    }

    // ---------- 查询 ----------

    public TeamDetail detail(long viewerId, long teamId) {
        Team t = support.requireTeam(teamId);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        List<TeamMember> members = support.activeMembers(teamId);
        Set<Long> memberIds = members.stream().map(TeamMember::getUserId).collect(Collectors.toSet());
        boolean viewerIsMember = memberIds.contains(viewerId);

        Map<Long, UserBrief> briefs = userBriefs.load(memberIds);
        Map<Long, Contact> contacts = viewerIsMember ? userBriefs.contacts(memberIds) : Map.of();
        List<MemberView> memberViews = members.stream().map(m -> new MemberView(briefs.get(m.getUserId()),
                m.getRoleName(), Boolean.TRUE.equals(m.getIsCaptain()), m.getJoinedAt(),
                contacts.get(m.getUserId()))).toList();

        List<NeedView> needs = needViews(List.of(teamId), false).getOrDefault(teamId, List.of());
        return new TeamDetail(t.getId(), t.getName(), t.getIntro(), t.getStatus(), t.getMaxMembers(),
                t.getMeetingMode(), members.size(), support.editionRef(e), t.getCaptainId(), memberViews, needs,
                relation(viewerId, t, e, viewerIsMember), t.getCreatedAt());
    }

    /** 某一届的队伍，招募中的排前面 */
    public List<TeamSummary> listByEdition(long editionId) {
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                .eq(Team::getEditionId, editionId)
                .in(Team::getStatus, List.of(Team.Status.RECRUITING, Team.Status.FULL, Team.Status.CLOSED))
                .orderByDesc(Team::getCreatedAt));
        teams.sort(Comparator.comparing(t -> t.getStatus() != Team.Status.RECRUITING));
        return summaries(teams);
    }

    /** 我正在参加的队伍 */
    public List<TeamSummary> mine(long userId) {
        List<Long> teamIds = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .eq(TeamMember::getUserId, userId)
                        .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE))
                .stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return List.of();
        }
        List<Team> teams = teamMapper.selectByIds(teamIds);
        teams.sort(Comparator.comparing(Team::getCreatedAt).reversed());
        return summaries(teams);
    }

    /** 关注了这一届、已认证、还没组队的人，队长从这里挑人邀请 */
    public List<Seeker> seekers(long captainId, long teamId) {
        Team t = support.requireCaptain(captainId, teamId);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        Long schoolId = Boolean.TRUE.equals(e.getSameSchoolRequired())
                ? userService.getById(t.getCaptainId()).getSchoolId() : null;
        List<Long> ids = seekerMapper.seekers(e.getId(), schoolId);
        Set<Long> invited = applicationMapper.selectList(new LambdaQueryWrapper<TeamApplication>()
                        .eq(TeamApplication::getTeamId, teamId)
                        .eq(TeamApplication::getStatus, TeamApplication.Status.PENDING))
                .stream().map(TeamApplication::getUserId).collect(Collectors.toSet());
        Map<Long, UserBrief> briefs = userBriefs.load(ids);
        return ids.stream().filter(briefs::containsKey)
                .map(id -> new Seeker(briefs.get(id), invited.contains(id))).toList();
    }

    // ---------- 内部 ----------

    /** 推荐列表也用这个，保证和队伍列表显示的一样 */
    public List<TeamSummary> summaries(List<Team> teams) {
        if (teams.isEmpty()) {
            return List.of();
        }
        List<Long> ids = teams.stream().map(Team::getId).toList();
        Map<Long, Integer> counts = support.activeCounts(ids);
        Map<Long, List<NeedView>> needs = needViews(ids, true);
        Map<Long, UserBrief> captains = userBriefs.load(teams.stream().map(Team::getCaptainId).collect(Collectors.toSet()));
        Map<Long, EditionRef> editions = support.editionRefs(teams.stream().map(Team::getEditionId).collect(Collectors.toSet()));
        return teams.stream().map(t -> new TeamSummary(t.getId(), t.getName(), t.getIntro(), t.getStatus(),
                t.getMaxMembers(), counts.getOrDefault(t.getId(), 0), captains.get(t.getCaptainId()),
                needs.getOrDefault(t.getId(), List.of()), editions.get(t.getEditionId()))).toList();
    }

    /** openOnly：列表里只显示还在招的需求；详情页显示全部（不含已取消的） */
    private Map<Long, List<NeedView>> needViews(Collection<Long> teamIds, boolean openOnly) {
        List<TeamNeed> needs = needMapper.selectList(new LambdaQueryWrapper<TeamNeed>()
                .in(TeamNeed::getTeamId, teamIds)
                .eq(openOnly, TeamNeed::getStatus, TeamNeed.Status.OPEN)
                .ne(!openOnly, TeamNeed::getStatus, TeamNeed.Status.CANCELLED)
                .orderByAsc(TeamNeed::getId));
        if (needs.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<NeedSkillView>> skills = needSkillMapper.listByNeeds(needs.stream().map(TeamNeed::getId).toList())
                .stream().collect(Collectors.groupingBy(NeedSkillMapper.NeedSkillRow::needId,
                        Collectors.mapping(r -> new NeedSkillView(r.skillId(), r.name(), r.minLevel()), Collectors.toList())));
        return needs.stream().collect(Collectors.groupingBy(TeamNeed::getTeamId,
                Collectors.mapping(n -> new NeedView(n.getId(), n.getRoleName(), n.getHeadcount(), n.getFilled(),
                        n.getNote(), n.getStatus(), skills.getOrDefault(n.getId(), List.of())), Collectors.toList())));
    }

    private MyRelation relation(long viewerId, Team t, CompetitionEdition e, boolean member) {
        boolean captain = t.getCaptainId().equals(viewerId);
        TeamApplication pending = applicationMapper.selectOne(new LambdaQueryWrapper<TeamApplication>()
                .eq(TeamApplication::getTeamId, t.getId())
                .eq(TeamApplication::getUserId, viewerId)
                .eq(TeamApplication::getStatus, TeamApplication.Status.PENDING));
        AppBrief application = pending != null && pending.getDirection() == TeamApplication.Direction.APPLY
                ? new AppBrief(pending.getId(), pending.getDirection(), pending.getCreatedAt()) : null;
        AppBrief invitation = pending != null && pending.getDirection() == TeamApplication.Direction.INVITE
                ? new AppBrief(pending.getId(), pending.getDirection(), pending.getCreatedAt()) : null;
        String blocked = member ? "你已经在这支队伍里了"
                : pending != null ? null
                : support.joinBlockReason(t, e, userService.getById(viewerId), true);
        return new MyRelation(member, captain, application, invitation, blocked);
    }

    private void checkTeamSize(int max, CompetitionEdition e, int currentMembers) {
        if (max < e.getTeamMin() || max > e.getTeamMax()) {
            throw new BizException("这届比赛每队 " + e.getTeamMin() + " 到 " + e.getTeamMax() + " 人，队伍人数要在这个范围内");
        }
        if (max < currentMembers) {
            throw new BizException("队里已经有 " + currentMembers + " 人了，人数上限不能比这少");
        }
    }

    private void checkSkills(List<NeedSkillInput> skills) {
        Set<Long> ids = new HashSet<>();
        for (NeedSkillInput s : skills) {
            if (!ids.add(s.skillId())) {
                throw new BizException("同一项技能填了两次");
            }
        }
        if (!ids.isEmpty() && skillMapper.selectCount(new LambdaQueryWrapper<Skill>().in(Skill::getId, ids)) != ids.size()) {
            throw new BizException("有技能不在技能列表里，请刷新页面后重试");
        }
    }

    private TeamNeed requireNeed(long needId) {
        TeamNeed need = needMapper.selectById(needId);
        if (need == null) {
            throw new BizException(404, "这条招募需求不存在");
        }
        return need;
    }

    private TeamMember requireActiveMember(long teamId, long userId, String message) {
        TeamMember m = memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE));
        if (m == null) {
            throw new BizException(message);
        }
        return m;
    }

    private void markGone(TeamMember m, TeamMember.Status status) {
        TeamMember update = new TeamMember();
        update.setId(m.getId());
        update.setStatus(status);
        update.setLeftAt(support.now());
        memberMapper.updateById(update);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Mapper
    public interface SeekerMapper {

        @Select("""
                <script>
                SELECT u.id FROM competition_follow f
                JOIN sys_user u ON u.id = f.user_id
                WHERE f.edition_id = #{editionId}
                  AND u.status = 'ACTIVE'
                  AND u.verify_status = 'VERIFIED'
                  AND (u.verified_until IS NULL OR u.verified_until &gt;= CURDATE())
                  <if test="schoolId != null">AND u.school_id = #{schoolId}</if>
                  AND NOT EXISTS (SELECT 1 FROM team_member m
                                  WHERE m.edition_id = #{editionId} AND m.user_id = u.id AND m.status = 'ACTIVE')
                ORDER BY f.created_at DESC
                LIMIT 50
                </script>
                """)
        List<Long> seekers(@Param("editionId") long editionId, @Param("schoolId") Long schoolId);
    }
}
