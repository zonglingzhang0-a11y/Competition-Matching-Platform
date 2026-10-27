package com.teammatch.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.review.ReviewDtos.Action;
import com.teammatch.review.ReviewDtos.FlaggedRow;
import com.teammatch.review.ReviewDtos.HandleForm;
import com.teammatch.review.ReviewDtos.MyReport;
import com.teammatch.review.ReviewDtos.ReportForm;
import com.teammatch.review.ReviewDtos.ReportRow;
import com.teammatch.review.ReviewDtos.SuspendedRow;
import com.teammatch.review.ReviewEntities.CreditMapper;
import com.teammatch.review.ReviewEntities.PeerReview;
import com.teammatch.review.ReviewEntities.PeerReviewMapper;
import com.teammatch.review.ReviewEntities.Report;
import com.teammatch.review.ReviewEntities.ReportMapper;
import com.teammatch.review.ReviewEntities.ReportStatus;
import com.teammatch.review.ReviewEntities.ReviewRound;
import com.teammatch.review.ReviewEntities.RoundMapper;
import com.teammatch.review.ReviewEntities.TargetType;
import com.teammatch.team.Team;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 举报（学生提交、管理员处理），以及管理端的信用复核：被暂停推荐的人、被标记异常的互评。
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** "不再出现在推荐里"就是把暂停时间设得足够远 */
    static final LocalDateTime FOREVER = LocalDateTime.of(9999, 12, 31, 0, 0);

    private static final Map<ReviewEntities.Reason, String> REASONS = Map.of(
            ReviewEntities.Reason.PLAGIARISM, "抄袭",
            ReviewEntities.Reason.FORGERY, "伪造材料",
            ReviewEntities.Reason.ABUSE, "辱骂骚扰",
            ReviewEntities.Reason.SPAM, "广告刷屏",
            ReviewEntities.Reason.OTHER, "其他");

    private static final Map<Action, String> ACTIONS = Map.of(
            Action.NONE, "记录在案",
            Action.SUSPEND_30, "暂停推荐 30 天",
            Action.BLOCK, "不再出现在推荐里",
            Action.BAN, "停用账号",
            Action.CLOSE_TEAM, "关闭队伍招募");

    private final ReportMapper reportMapper;
    private final CreditMapper creditMapper;
    private final RoundMapper roundMapper;
    private final PeerReviewMapper reviewMapper;
    private final SysUserMapper userMapper;
    private final TeamMapper teamMapper;
    private final TeamSupport teamSupport;
    private final UserAdminService userAdminService;
    private final NotificationService notifications;
    private final JsonMapper json;

    // ---------- 学生 ----------

    public long create(long reporterId, ReportForm form) {
        if (form.targetType() == TargetType.USER) {
            if (form.targetId() == reporterId) {
                throw new BizException("不能举报自己");
            }
            if (userMapper.selectById(form.targetId()) == null) {
                throw new BizException(404, "这个用户不存在");
            }
        } else if (teamMapper.selectById(form.targetId()) == null) {
            throw new BizException(404, "这支队伍不存在");
        }
        boolean pending = reportMapper.exists(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, reporterId)
                .eq(Report::getTargetType, form.targetType())
                .eq(Report::getTargetId, form.targetId())
                .eq(Report::getStatus, ReportStatus.PENDING));
        if (pending) {
            throw new BizException(409, "你已经举报过了，管理员处理后会通知你");
        }
        Report r = new Report();
        r.setReporterId(reporterId);
        r.setTargetType(form.targetType());
        r.setTargetId(form.targetId());
        r.setReason(form.reason());
        r.setDetail(StringUtils.hasText(form.detail()) ? form.detail().trim() : null);
        r.setStatus(ReportStatus.PENDING);
        r.setCreatedAt(teamSupport.now());
        reportMapper.insert(r);
        return r.getId();
    }

    public List<MyReport> mine(long userId) {
        List<Report> list = reportMapper.selectList(new LambdaQueryWrapper<Report>()
                .eq(Report::getReporterId, userId)
                .orderByDesc(Report::getId));
        Names names = names(list);
        return list.stream().map(r -> new MyReport(r.getId(), r.getTargetType(), r.getTargetId(),
                names.title(r), r.getReason(), r.getDetail(), r.getStatus(), r.getResultNote(), r.getCreatedAt(),
                r.getHandledAt())).toList();
    }

    // ---------- 管理端：举报 ----------

    /** 待处理的先来先处理，其他的最新的在前 */
    public PageResult<ReportRow> list(ReportStatus status, int page, int size) {
        boolean pending = status == ReportStatus.PENDING;
        Page<Report> p = reportMapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<Report>()
                .eq(status != null, Report::getStatus, status)
                .orderBy(true, pending, Report::getId));
        Names names = names(p.getRecords());
        Set<Long> people = new HashSet<>();
        p.getRecords().forEach(r -> {
            people.add(r.getReporterId());
            if (r.getHandledBy() != null) {
                people.add(r.getHandledBy());
            }
        });
        Map<Long, SysUser> users = people.isEmpty() ? Map.of()
                : userMapper.selectByIds(people).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return PageResult.of(p, r -> new ReportRow(r.getId(), nickname(users.get(r.getReporterId())), r.getTargetType(),
                r.getTargetId(), names.title(r), names.sub(r), r.getReason(), r.getDetail(), r.getStatus(),
                r.getHandledBy() == null ? null : nickname(users.get(r.getHandledBy())), r.getResultNote(),
                r.getCreatedAt(), r.getHandledAt(),
                reportMapper.selectCount(new LambdaQueryWrapper<Report>()
                        .eq(Report::getTargetType, r.getTargetType())
                        .eq(Report::getTargetId, r.getTargetId()))));
    }

    public long pendingCount() {
        return reportMapper.selectCount(new LambdaQueryWrapper<Report>().eq(Report::getStatus, ReportStatus.PENDING));
    }

    @Transactional
    public void handle(long adminId, long id, HandleForm form) {
        Report r = reportMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "这条举报不存在");
        }
        if (form.result() == ReportStatus.PENDING) {
            throw new BizException("请选择属实或不属实");
        }
        Action action = form.result() == ReportStatus.DISMISSED || form.action() == null ? Action.NONE : form.action();
        boolean forUser = r.getTargetType() == TargetType.USER;
        if (forUser ? action == Action.CLOSE_TEAM : action != Action.NONE && action != Action.CLOSE_TEAM) {
            throw new BizException(forUser ? "举报的是用户，不能关闭队伍" : "举报的是队伍，只能关闭它的招募");
        }
        LocalDateTime now = teamSupport.now();
        String note = form.note().trim();
        int updated = reportMapper.update(new LambdaUpdateWrapper<Report>()
                .eq(Report::getId, id)
                .eq(Report::getStatus, ReportStatus.PENDING)
                .set(Report::getStatus, form.result())
                .set(Report::getHandledBy, adminId)
                .set(Report::getHandledAt, now)
                .set(Report::getResultNote, note));
        if (updated == 0) {
            throw new BizException(409, "这条举报已经处理过了，请刷新");
        }
        apply(adminId, r, action, now);

        String target = names(List.of(r)).title(r);
        String content = form.result() == ReportStatus.CONFIRMED
                ? "你对「" + target + "」的举报经核实属实，处理结果：" + ACTIONS.get(action) + "。说明：" + note
                : "你对「" + target + "」的举报经核实暂不构成违规。说明：" + note;
        notifications.send(r.getReporterId(), Notification.Type.REPORT_RESULT, "你的举报已处理", content, null, null);
    }

    private void apply(long adminId, Report r, Action action, LocalDateTime now) {
        String reason = REASONS.get(r.getReason());
        switch (action) {
            case SUSPEND_30 -> {
                creditMapper.suspend(r.getTargetId(), now.plus(ReviewService.SUSPEND));
                notifications.send(r.getTargetId(), Notification.Type.CREDIT, "你被暂停推荐 30 天",
                        "有人举报你「" + reason + "」，经管理员核实属实，30 天内不会出现在推荐里。", ReviewService.REF_CREDIT, null);
            }
            case BLOCK -> {
                creditMapper.suspend(r.getTargetId(), FOREVER);
                notifications.send(r.getTargetId(), Notification.Type.CREDIT, "你不会再出现在推荐里",
                        "有人举报你「" + reason + "」，经管理员核实属实。你仍然可以自己找队伍、申请入队。", ReviewService.REF_CREDIT, null);
            }
            case BAN -> userAdminService.setStatus(adminId, r.getTargetId(), SysUser.UserStatus.BANNED);
            case CLOSE_TEAM -> {
                Team t = teamMapper.selectById(r.getTargetId());
                if (t != null && t.isActive()) {
                    teamMapper.update(new LambdaUpdateWrapper<Team>()
                            .eq(Team::getId, t.getId())
                            .set(Team::getStatus, Team.Status.CLOSED));
                    teamSupport.expirePendingOfTeam(t.getId());
                    notifications.send(t.getCaptainId(), Notification.Type.SYSTEM, "你的队伍被关闭了招募",
                            "「" + t.getName() + "」被举报「" + reason + "」，经管理员核实属实，已关闭招募。", "TEAM", t.getId());
                }
            }
            case NONE -> {
                // 只记录
            }
        }
    }

    // ---------- 管理端：信用复核 ----------

    public List<SuspendedRow> suspended() {
        var list = creditMapper.suspended(teamSupport.now());
        Map<Long, SysUser> users = list.isEmpty() ? Map.of()
                : userMapper.selectByIds(list.stream().map(CreditMapper.Credit::userId).toList()).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return list.stream().map(c -> {
            SysUser u = users.get(c.userId());
            return new SuspendedRow(c.userId(), nickname(u), u == null ? null : u.getEmail(),
                    c.score() == null ? null : c.score().doubleValue(), c.ratedRounds() == null ? 0 : c.ratedRounds(),
                    c.suspendedUntil(), !c.suspendedUntil().isBefore(FOREVER));
        }).toList();
    }

    public void lift(long userId) {
        if (creditMapper.lift(userId, teamSupport.now()) == 0) {
            throw new BizException(404, "这个人没有被暂停推荐");
        }
        notifications.send(userId, Notification.Type.CREDIT, "已恢复推荐", "管理员复核后，你重新出现在推荐里了。",
                ReviewService.REF_CREDIT, null);
    }

    /** 最近被判为异常、降了权重的评分，最新的在前 */
    public List<FlaggedRow> flagged() {
        List<PeerReview> list = reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                .eq(PeerReview::getFlagged, true)
                .orderByDesc(PeerReview::getId)
                .last("LIMIT 50"));
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> people = new HashSet<>();
        list.forEach(r -> {
            people.add(r.getRaterId());
            people.add(r.getRateeId());
        });
        Map<Long, SysUser> users = userMapper.selectByIds(people).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, ReviewRound> rounds = roundMapper.selectByIds(list.stream().map(PeerReview::getRoundId).distinct().toList())
                .stream().collect(Collectors.toMap(ReviewRound::getId, Function.identity()));
        Map<Long, String> teams = teamMapper.selectByIds(rounds.values().stream().map(ReviewRound::getTeamId).distinct().toList())
                .stream().collect(Collectors.toMap(Team::getId, Team::getName));
        return list.stream().map(r -> {
            ReviewRound round = rounds.get(r.getRoundId());
            return new FlaggedRow(r.getId(), r.getRoundId(), round == null ? null : teams.get(round.getTeamId()),
                    nickname(users.get(r.getRaterId())), nickname(users.get(r.getRateeId())),
                    json.readValue(r.getAnswers(), new TypeReference<List<Integer>>() {
                    }), r.getSubmittedAt());
        }).toList();
    }

    // ---------- 内部 ----------

    /** 批量查举报对象的名字：用户是昵称，队伍是队名 */
    private Names names(List<Report> reports) {
        Set<Long> userIds = reports.stream().filter(r -> r.getTargetType() == TargetType.USER)
                .map(Report::getTargetId).collect(Collectors.toSet());
        Set<Long> teamIds = reports.stream().filter(r -> r.getTargetType() == TargetType.TEAM)
                .map(Report::getTargetId).collect(Collectors.toSet());
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Team> teams = teamIds.isEmpty() ? Map.of()
                : teamMapper.selectByIds(teamIds).stream().collect(Collectors.toMap(Team::getId, Function.identity()));
        Set<Long> captainIds = teams.values().stream().map(Team::getCaptainId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, SysUser> captains = captainIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(captainIds).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return new Names(users, teams, captains);
    }

    private record Names(Map<Long, SysUser> users, Map<Long, Team> teams, Map<Long, SysUser> captains) {

        String title(Report r) {
            if (r.getTargetType() == TargetType.USER) {
                return nickname(users.get(r.getTargetId()));
            }
            Team t = teams.get(r.getTargetId());
            return t == null ? "已删除的队伍" : t.getName();
        }

        /** 管理员看的补充信息：用户的邮箱、队伍的队长 */
        String sub(Report r) {
            if (r.getTargetType() == TargetType.USER) {
                SysUser u = users.get(r.getTargetId());
                return u == null ? null : u.getEmail();
            }
            Team t = teams.get(r.getTargetId());
            return t == null ? null : "队长 " + nickname(captains.get(t.getCaptainId()));
        }
    }

    private static String nickname(SysUser u) {
        return u == null ? "已注销的用户" : u.getNickname();
    }
}
