package com.teammatch.team;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.competition.EditionPhase;
import com.teammatch.team.TeamDtos.EditionRef;
import com.teammatch.team.TeamMappers.ApplicationMapper;
import com.teammatch.team.TeamMappers.MemberMapper;
import com.teammatch.team.TeamMappers.NeedMapper;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 队伍管理和申请邀请共用的规则，集中放在这里，两边的判断才不会不一致。
 */
@Component
@RequiredArgsConstructor
public class TeamSupport {

    private final TeamMapper teamMapper;
    private final MemberMapper memberMapper;
    private final NeedMapper needMapper;
    private final ApplicationMapper applicationMapper;
    private final EditionMapper editionMapper;
    private final CompetitionMapper competitionMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final Clock clock;

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /**
     * 入队相关的事务统一按"先锁人、再锁队伍"的顺序加锁。
     * 同一个人同时被两支队接受时，两个事务会在这里排队，而不是各锁一支队、再互相等对方而死锁。
     * 锁的是档案行，原因见 {@link UserProfileMapper#lockByUserId}。
     */
    void lockUser(long userId) {
        if (profileMapper.lockByUserId(userId) == null) {
            // 老账号可能没有档案行，补一行再锁
            UserProfile p = new UserProfile();
            p.setUserId(userId);
            try {
                profileMapper.insert(p);
            } catch (DuplicateKeyException ignored) {
                // 别的请求刚好补上了
            }
            profileMapper.lockByUserId(userId);
        }
    }

    public Team requireTeam(long teamId) {
        Team t = teamMapper.selectById(teamId);
        if (t == null || t.getStatus() == Team.Status.DISBANDED) {
            throw new BizException(404, "这支队伍不存在或已解散");
        }
        return t;
    }

    public Team requireCaptain(long userId, long teamId) {
        Team t = requireTeam(teamId);
        if (!t.getCaptainId().equals(userId)) {
            throw new BizException(403, "只有队长能这样操作");
        }
        return t;
    }

    public CompetitionEdition requireEdition(long editionId) {
        CompetitionEdition e = editionMapper.selectById(editionId);
        if (e == null || e.getStatus() != CompetitionEdition.Status.PUBLISHED) {
            throw new BizException(404, "这一届比赛不存在或还没发布");
        }
        return e;
    }

    public EditionPhase phase(CompetitionEdition e) {
        return EditionPhase.of(e.getRegistrationStart(), e.getRegistrationEnd(), now());
    }

    /** 这个人在这一届里正在哪支队（没有就返回 null） */
    public TeamMember activeMembership(long userId, long editionId) {
        return memberMapper.selectOne(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getUserId, userId)
                .eq(TeamMember::getEditionId, editionId)
                .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE));
    }

    public List<TeamMember> activeMembers(long teamId) {
        return memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE)
                .orderByDesc(TeamMember::getIsCaptain)
                .orderByAsc(TeamMember::getJoinedAt));
    }

    int activeCount(long teamId) {
        return Math.toIntExact(memberMapper.selectCount(new LambdaQueryWrapper<TeamMember>()
                .eq(TeamMember::getTeamId, teamId)
                .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE)));
    }

    Map<Long, Integer> activeCounts(Collection<Long> teamIds) {
        if (teamIds.isEmpty()) {
            return Map.of();
        }
        return memberMapper.countActive(teamIds).stream()
                .collect(Collectors.toMap(MemberMapper.MemberCount::teamId, c -> Math.toIntExact(c.members())));
    }

    /** 招募中和已满员之间按人数自动切换；队长关掉的、已结束的不动 */
    Team.Status recomputeStatus(Team t) {
        if (t.getStatus() == Team.Status.RECRUITING || t.getStatus() == Team.Status.FULL) {
            Team.Status next = activeCount(t.getId()) >= t.getMaxMembers() ? Team.Status.FULL : Team.Status.RECRUITING;
            if (next != t.getStatus()) {
                t.setStatus(next);
                Team update = new Team();
                update.setId(t.getId());
                update.setStatus(next);
                teamMapper.updateById(update);
            }
        }
        return t.getStatus();
    }

    /**
     * 这个人现在不能加入这支队的原因；能加入返回 null。
     * self：是不是本人在操作，决定提示里说"你"还是"对方"。
     */
    public String joinBlockReason(Team t, CompetitionEdition e, SysUser user, boolean self) {
        return joinBlockReason(t, e, user, self, true);
    }

    /** checkVerified = false：推荐时先不看认证，没认证的人也能看到推荐，申请时再拦 */
    public String joinBlockReason(Team t, CompetitionEdition e, SysUser user, boolean self, boolean checkVerified) {
        String who = self ? "你" : "对方";
        if (t.getStatus() == Team.Status.FULL) {
            return "队伍已经满员了";
        }
        if (t.getStatus() != Team.Status.RECRUITING) {
            return "队伍已经停止招募";
        }
        if (phase(e) == EditionPhase.CLOSED) {
            return "这届比赛报名已截止";
        }
        if (checkVerified && !userService.isVerified(user)) {
            return self ? "请先完成学校认证" : "对方还没完成学校认证";
        }
        TeamMember current = activeMembership(user.getId(), e.getId());
        if (current != null) {
            return current.getTeamId().equals(t.getId()) ? who + "已经在这支队伍里了" : who + "已经在这届比赛的其他队伍里了";
        }
        if (Boolean.TRUE.equals(e.getSameSchoolRequired())) {
            SysUser captain = userService.getById(t.getCaptainId());
            if (!Objects.equals(captain.getSchoolId(), user.getSchoolId())) {
                return "这届比赛要求同校组队，" + who + "和队长不是同一所学校";
            }
        }
        return null;
    }

    void checkCanJoin(Team t, CompetitionEdition e, SysUser user, boolean self) {
        String reason = joinBlockReason(t, e, user, self);
        if (reason != null) {
            throw new BizException(reason);
        }
    }

    /** 这个人进了某支队之后，他在这一届里其他待处理的申请和邀请都作废 */
    void expirePendingOfUser(long userId, long editionId) {
        applicationMapper.update(new LambdaUpdateWrapper<TeamApplication>()
                .eq(TeamApplication::getUserId, userId)
                .eq(TeamApplication::getStatus, TeamApplication.Status.PENDING)
                .inSql(TeamApplication::getTeamId, "SELECT id FROM team WHERE edition_id = " + editionId)
                .set(TeamApplication::getStatus, TeamApplication.Status.EXPIRED)
                .set(TeamApplication::getHandledAt, now()));
    }

    /** 队伍满员、停止招募或解散时，发给这支队、和这支队发出的待处理记录都作废 */
    public void expirePendingOfTeam(long teamId) {
        applicationMapper.update(new LambdaUpdateWrapper<TeamApplication>()
                .eq(TeamApplication::getTeamId, teamId)
                .eq(TeamApplication::getStatus, TeamApplication.Status.PENDING)
                .set(TeamApplication::getStatus, TeamApplication.Status.EXPIRED)
                .set(TeamApplication::getHandledAt, now()));
    }

    /** 有人通过某个招募需求入队：已招人数 +1，招满了就标记 FILLED */
    void fillNeed(Long needId) {
        if (needId == null) {
            return;
        }
        TeamNeed need = needMapper.selectById(needId);
        if (need == null || need.getStatus() != TeamNeed.Status.OPEN) {
            return;
        }
        need.setFilled(need.getFilled() + 1);
        if (need.getFilled() >= need.getHeadcount()) {
            need.setStatus(TeamNeed.Status.FILLED);
        }
        needMapper.updateById(need);
    }

    /** 有人退队或被移出：把他占的那个名额还回去 */
    void releaseNeed(long teamId, String roleName) {
        if (roleName == null) {
            return;
        }
        TeamNeed need = needMapper.selectOne(new LambdaQueryWrapper<TeamNeed>()
                .eq(TeamNeed::getTeamId, teamId)
                .eq(TeamNeed::getRoleName, roleName)
                .in(TeamNeed::getStatus, List.of(TeamNeed.Status.OPEN, TeamNeed.Status.FILLED))
                .gt(TeamNeed::getFilled, 0)
                .last("LIMIT 1"));
        if (need != null) {
            need.setFilled(need.getFilled() - 1);
            need.setStatus(TeamNeed.Status.OPEN);
            needMapper.updateById(need);
        }
    }

    EditionRef editionRef(CompetitionEdition e) {
        Competition c = competitionMapper.selectById(e.getCompetitionId());
        return toRef(e, c);
    }

    public Map<Long, EditionRef> editionRefs(Collection<Long> editionIds) {
        if (editionIds.isEmpty()) {
            return Map.of();
        }
        List<CompetitionEdition> editions = editionMapper.selectByIds(editionIds);
        Map<Long, Competition> competitions = competitionMapper.selectByIds(
                        editions.stream().map(CompetitionEdition::getCompetitionId).distinct().toList())
                .stream().collect(Collectors.toMap(Competition::getId, Function.identity()));
        return editions.stream().collect(Collectors.toMap(CompetitionEdition::getId,
                e -> toRef(e, competitions.get(e.getCompetitionId()))));
    }

    private EditionRef toRef(CompetitionEdition e, Competition c) {
        return new EditionRef(e.getId(), e.getCompetitionId(), c == null ? "" : c.getName(), e.getTitle(), phase(e),
                e.getTeamMin(), e.getTeamMax(), Boolean.TRUE.equals(e.getSameSchoolRequired()));
    }
}
