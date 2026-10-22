package com.teammatch.team;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.team.TeamDtos.ApplicationView;
import com.teammatch.team.TeamDtos.ApplyRequest;
import com.teammatch.team.TeamDtos.EditionRef;
import com.teammatch.team.TeamDtos.InviteRequest;
import com.teammatch.team.TeamDtos.MyApplications;
import com.teammatch.team.TeamDtos.TeamRef;
import com.teammatch.team.TeamMappers.ApplicationMapper;
import com.teammatch.team.TeamMappers.MemberMapper;
import com.teammatch.team.TeamMappers.NeedMapper;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserBriefs;
import com.teammatch.user.UserBriefs.UserBrief;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 入队申请（学生 → 队长处理）和邀请（队长 → 学生处理）。两个方向都要对方确认才算加入。
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationMapper applicationMapper;
    private final TeamMapper teamMapper;
    private final MemberMapper memberMapper;
    private final NeedMapper needMapper;
    private final TeamSupport support;
    private final UserService userService;
    private final UserBriefs userBriefs;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    @Transactional
    public long apply(long userId, long teamId, ApplyRequest req) {
        SysUser user = userService.requireVerified(userId);
        Team t = support.requireTeam(teamId);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        checkNoPending(teamId, userId, true);
        support.checkCanJoin(t, e, user, true);
        TeamNeed need = checkNeed(teamId, req.needId());

        TeamApplication app = create(teamId, userId, TeamApplication.Direction.APPLY, need, req.message());
        notifications.send(t.getCaptainId(), Notification.Type.APPLY_RECEIVED, "新的入队申请",
                user.getNickname() + " 申请加入「" + t.getName() + "」" + roleSuffix(need) + "，去队伍页处理吧。",
                NotificationService.REF_TEAM, teamId);
        return app.getId();
    }

    @Transactional
    public long invite(long captainId, long teamId, InviteRequest req) {
        Team t = support.requireCaptain(captainId, teamId);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        SysUser target = userService.getById(req.userId());
        checkNoPending(teamId, target.getId(), false);
        support.checkCanJoin(t, e, target, false);
        TeamNeed need = checkNeed(teamId, req.needId());

        TeamApplication app = create(teamId, target.getId(), TeamApplication.Direction.INVITE, need, req.message());
        String captain = userService.getById(captainId).getNickname();
        notifications.send(target.getId(), Notification.Type.INVITE_RECEIVED, "有队伍邀请你加入",
                captain + " 邀请你加入「" + t.getName() + "」" + roleSuffix(need) + "。",
                NotificationService.REF_TEAM, teamId);
        return app.getId();
    }

    /**
     * 接受：申请由队长接受，邀请由被邀请人接受。
     * 先锁入队的人、再锁队伍，拿到锁之后重新检查一遍，防止并发时超员或者一人进两队。
     */
    @Transactional
    public void accept(long userId, long applicationId) {
        support.lockUser(requirePending(applicationId).getUserId());
        // 等锁期间这条记录可能已经被处理或撤回了，拿到锁后重新读
        TeamApplication app = requirePending(applicationId);
        Team t = teamMapper.selectForUpdate(app.getTeamId());
        if (t == null || t.getStatus() == Team.Status.DISBANDED) {
            throw new BizException(404, "这支队伍不存在或已解散");
        }
        checkHandler(userId, app, t);
        CompetitionEdition e = support.requireEdition(t.getEditionId());
        boolean joinerIsActor = app.getDirection() == TeamApplication.Direction.INVITE;
        SysUser joiner = userService.getById(app.getUserId());
        support.checkCanJoin(t, e, joiner, joinerIsActor);

        TeamNeed need = app.getNeedId() == null ? null : needMapper.selectById(app.getNeedId());
        TeamMember m = new TeamMember();
        m.setTeamId(t.getId());
        m.setEditionId(e.getId());
        m.setUserId(joiner.getId());
        m.setRoleName(need != null && need.getStatus() == TeamNeed.Status.OPEN ? need.getRoleName() : null);
        m.setIsCaptain(false);
        m.setStatus(TeamMember.Status.ACTIVE);
        m.setJoinedAt(support.now());
        try {
            memberMapper.insert(m);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, (joinerIsActor ? "你" : "对方") + "已经在这届比赛的其他队伍里了");
        }

        close(app, TeamApplication.Status.ACCEPTED, userId);
        if (m.getRoleName() != null) {
            support.fillNeed(need.getId());
        }
        support.expirePendingOfUser(joiner.getId(), e.getId());
        if (support.recomputeStatus(t) == Team.Status.FULL) {
            support.expirePendingOfTeam(t.getId());
        }

        if (app.getDirection() == TeamApplication.Direction.APPLY) {
            notifications.send(joiner.getId(), Notification.Type.APPLY_RESULT, "申请通过了",
                    "你已经加入「" + t.getName() + "」，去队伍页看看队友吧。", NotificationService.REF_TEAM, t.getId());
        } else {
            notifications.send(t.getCaptainId(), Notification.Type.INVITE_RESULT, "邀请被接受了",
                    joiner.getNickname() + " 接受了邀请，加入了「" + t.getName() + "」。", NotificationService.REF_TEAM, t.getId());
        }
    }

    @Transactional
    public void reject(long userId, long applicationId) {
        TeamApplication app = requirePending(applicationId);
        Team t = support.requireTeam(app.getTeamId());
        checkHandler(userId, app, t);
        close(app, TeamApplication.Status.REJECTED, userId);
        if (app.getDirection() == TeamApplication.Direction.APPLY) {
            notifications.send(app.getUserId(), Notification.Type.APPLY_RESULT, "申请没有通过",
                    "「" + t.getName() + "」的队长婉拒了你的申请，看看别的队伍吧。", NotificationService.REF_TEAM, t.getId());
        } else {
            String nickname = userService.getById(app.getUserId()).getNickname();
            notifications.send(t.getCaptainId(), Notification.Type.INVITE_RESULT, "邀请被婉拒了",
                    nickname + " 婉拒了加入「" + t.getName() + "」的邀请。", NotificationService.REF_TEAM, t.getId());
        }
    }

    /** 撤回：申请由申请人撤回，邀请由队长撤回 */
    public void withdraw(long userId, long applicationId) {
        TeamApplication app = requirePending(applicationId);
        Team t = support.requireTeam(app.getTeamId());
        boolean initiator = app.getDirection() == TeamApplication.Direction.APPLY
                ? app.getUserId().equals(userId)
                : t.getCaptainId().equals(userId);
        if (!initiator) {
            throw new BizException(403, "只有发起人能撤回");
        }
        close(app, TeamApplication.Status.WITHDRAWN, userId);
    }

    /** 我发出的申请和我收到的邀请 */
    public MyApplications mine(long userId) {
        List<TeamApplication> apps = applicationMapper.selectList(new LambdaQueryWrapper<TeamApplication>()
                .eq(TeamApplication::getUserId, userId)
                .orderByDesc(TeamApplication::getId)
                .last("LIMIT 100"));
        List<ApplicationView> views = views(apps);
        return new MyApplications(
                views.stream().filter(v -> v.direction() == TeamApplication.Direction.APPLY).toList(),
                views.stream().filter(v -> v.direction() == TeamApplication.Direction.INVITE).toList());
    }

    /** 队长看自己队伍收到的申请和发出的邀请，待处理的排前面 */
    public List<ApplicationView> ofTeam(long captainId, long teamId) {
        support.requireCaptain(captainId, teamId);
        List<TeamApplication> apps = applicationMapper.selectList(new LambdaQueryWrapper<TeamApplication>()
                .eq(TeamApplication::getTeamId, teamId)
                .orderByDesc(TeamApplication::getId)
                .last("LIMIT 100"));
        return views(apps).stream()
                .sorted(Comparator.comparing(v -> v.status() != TeamApplication.Status.PENDING))
                .toList();
    }

    // ---------- 内部 ----------

    private TeamApplication create(long teamId, long userId, TeamApplication.Direction direction, TeamNeed need,
                                   String message) {
        TeamApplication app = new TeamApplication();
        app.setTeamId(teamId);
        app.setUserId(userId);
        app.setDirection(direction);
        app.setNeedId(need == null ? null : need.getId());
        app.setMessage(message == null || message.isBlank() ? null : message.trim());
        app.setStatus(TeamApplication.Status.PENDING);
        try {
            applicationMapper.insert(app);
        } catch (DuplicateKeyException ex) {
            // 两个请求同时到达时，由唯一索引兜底
            throw new BizException(409, "已经有一条待处理的申请或邀请了");
        }
        // 匹配模块在事务提交后记下当时的匹配分（看板"推荐准不准"用），算分出错不影响申请
        events.publishEvent(new ApplicationCreated(app.getId(), userId, teamId, direction == TeamApplication.Direction.APPLY));
        return app;
    }

    /** 同一支队和同一个人之间只能有一条待处理的记录，给出具体该怎么做的提示 */
    private void checkNoPending(long teamId, long userId, boolean byApplicant) {
        TeamApplication pending = applicationMapper.selectOne(new LambdaQueryWrapper<TeamApplication>()
                .eq(TeamApplication::getTeamId, teamId)
                .eq(TeamApplication::getUserId, userId)
                .eq(TeamApplication::getStatus, TeamApplication.Status.PENDING));
        if (pending == null) {
            return;
        }
        boolean isApply = pending.getDirection() == TeamApplication.Direction.APPLY;
        if (byApplicant) {
            throw new BizException(409, isApply ? "你已经申请过了，等队长处理吧" : "这支队伍已经邀请你了，直接接受邀请就行");
        }
        throw new BizException(409, isApply ? "对方已经申请加入你的队伍了，直接处理申请就行" : "已经邀请过对方了，等对方回复吧");
    }

    private TeamNeed checkNeed(long teamId, Long needId) {
        if (needId == null) {
            return null;
        }
        TeamNeed need = needMapper.selectById(needId);
        if (need == null || !need.getTeamId().equals(teamId)) {
            throw new BizException("选的招募需求不属于这支队伍");
        }
        if (need.getStatus() != TeamNeed.Status.OPEN) {
            throw new BizException("这个角色已经招满或取消了");
        }
        return need;
    }

    private TeamApplication requirePending(long id) {
        TeamApplication app = applicationMapper.selectById(id);
        if (app == null) {
            throw new BizException(404, "这条申请或邀请不存在");
        }
        if (app.getStatus() != TeamApplication.Status.PENDING) {
            throw new BizException(409, "这条已经处理过了");
        }
        return app;
    }

    /** 申请由队长处理，邀请由被邀请人处理 */
    private static void checkHandler(long userId, TeamApplication app, Team t) {
        boolean ok = app.getDirection() == TeamApplication.Direction.APPLY
                ? t.getCaptainId().equals(userId)
                : app.getUserId().equals(userId);
        if (!ok) {
            throw new BizException(403, app.getDirection() == TeamApplication.Direction.APPLY
                    ? "只有队长能处理入队申请" : "只有被邀请的人能处理邀请");
        }
    }

    private void close(TeamApplication app, TeamApplication.Status status, long handledBy) {
        TeamApplication update = new TeamApplication();
        update.setId(app.getId());
        update.setStatus(status);
        update.setHandledBy(handledBy);
        update.setHandledAt(support.now());
        applicationMapper.updateById(update);
    }

    private List<ApplicationView> views(List<TeamApplication> apps) {
        if (apps.isEmpty()) {
            return List.of();
        }
        Map<Long, Team> teams = teamMapper.selectByIds(apps.stream().map(TeamApplication::getTeamId).distinct().toList())
                .stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, EditionRef> editions = support.editionRefs(teams.values().stream().map(Team::getEditionId).distinct().toList());
        Map<Long, UserBrief> users = userBriefs.load(apps.stream().map(TeamApplication::getUserId).distinct().toList());
        List<Long> needIds = apps.stream().map(TeamApplication::getNeedId).filter(id -> id != null).distinct().toList();
        Map<Long, String> roles = needIds.isEmpty() ? Map.of() : needMapper.selectByIds(needIds).stream()
                .collect(Collectors.toMap(TeamNeed::getId, TeamNeed::getRoleName));
        return apps.stream().filter(a -> teams.containsKey(a.getTeamId())).map(a -> {
            Team t = teams.get(a.getTeamId());
            EditionRef e = editions.get(t.getEditionId());
            TeamRef ref = new TeamRef(t.getId(), t.getName(), e == null ? "" : e.competitionName(),
                    e == null ? "" : e.editionTitle());
            return new ApplicationView(a.getId(), a.getDirection(), a.getStatus(), a.getMessage(),
                    a.getNeedId() == null ? null : roles.get(a.getNeedId()), a.getCreatedAt(), a.getHandledAt(),
                    ref, users.get(a.getUserId()));
        }).toList();
    }

    private static String roleSuffix(TeamNeed need) {
        return need == null ? "" : "（" + need.getRoleName() + "）";
    }
}
