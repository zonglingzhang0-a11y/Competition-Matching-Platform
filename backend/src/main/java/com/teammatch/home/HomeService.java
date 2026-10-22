package com.teammatch.home;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.calendar.CalendarEntities.MilestoneMapper;
import com.teammatch.calendar.CalendarEntities.TeamMilestone;
import com.teammatch.calendar.CalendarMapper;
import com.teammatch.calendar.CalendarService;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import com.teammatch.competition.CompetitionNode;
import com.teammatch.home.HomeDtos.HomeView;
import com.teammatch.home.HomeDtos.Lane;
import com.teammatch.home.HomeDtos.Mark;
import com.teammatch.home.HomeDtos.MyCompetition;
import com.teammatch.home.HomeDtos.Pending;
import com.teammatch.home.HomeDtos.Semester;
import com.teammatch.home.HomeDtos.Stage;
import com.teammatch.home.HomeDtos.Suggestion;
import com.teammatch.home.HomeDtos.Teammate;
import com.teammatch.home.HomeDtos.Upcoming;
import com.teammatch.home.HomeDtos.WeekLoad;
import com.teammatch.home.HomeMapper.EditionInfo;
import com.teammatch.match.RecommendService;
import com.teammatch.match.RecommendService.RecommendedUser;
import com.teammatch.survey.SurveyService;
import com.teammatch.team.ApplicationService;
import com.teammatch.team.Team;
import com.teammatch.team.TeamApplication;
import com.teammatch.team.TeamDtos.ApplicationView;
import com.teammatch.team.TeamDtos.TeamSummary;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamService;
import com.teammatch.team.TeamSupport;
import com.teammatch.recognition.RecognitionService;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 我的主页的数据，按 B 版三栏组织。时间相关的计算在 {@link HomePlanner}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeService {

    /** 还在进行的队伍（结束、解散的不算"我参加的"） */
    private static final Set<Team.Status> ACTIVE = Set.of(Team.Status.RECRUITING, Team.Status.FULL, Team.Status.CLOSED);
    private static final int SUGGESTIONS = 3;
    private static final int TEAMMATES = 3;

    private final HomeMapper homeMapper;
    private final CalendarMapper calendarMapper;
    private final CalendarService calendarService;
    private final NodeMapper nodeMapper;
    private final MilestoneMapper milestoneMapper;
    private final TeamService teamService;
    private final TeamSupport teamSupport;
    private final ApplicationService applicationService;
    private final RecommendService recommendService;
    private final SurveyService surveyService;
    private final SysUserMapper userMapper;
    private final RecognitionService recognitionService;
    private final Clock clock;

    /**
     * 主页上的一个时间点，官方节点和自己队伍的里程碑统一成这个，方便排序和分组。
     * editionId：属于哪一届（里程碑跟着队伍所在的届次）
     */
    record Point(String title, String label, LocalDateTime start, LocalDateTime end, boolean deadline, boolean team,
                 boolean fuzzy, boolean done, Long editionId, String refType, Long refId) {
    }

    public HomeView view(long userId) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        List<TeamSummary> teams = teamService.mine(userId).stream().filter(t -> ACTIVE.contains(t.status())).toList();
        Map<Long, TeamSummary> teamById = teams.stream().collect(Collectors.toMap(TeamSummary::id, Function.identity()));
        Set<Long> joined = teams.stream().map(t -> t.edition().editionId()).collect(Collectors.toSet());
        Set<Long> followed = new HashSet<>(calendarMapper.followedEditions(userId));
        Set<Long> editionIds = new LinkedHashSet<>(joined);
        editionIds.addAll(followed);
        Map<Long, EditionInfo> info = editionIds.isEmpty() ? Map.of()
                : homeMapper.editions(editionIds).stream().collect(Collectors.toMap(EditionInfo::editionId, Function.identity()));

        List<CompetitionNode> nodes = editionIds.isEmpty() ? List.of()
                : nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                        .in(CompetitionNode::getEditionId, editionIds)
                        .orderByAsc(CompetitionNode::getStartAt));
        List<TeamMilestone> milestones = teams.isEmpty() ? List.of()
                : milestoneMapper.selectList(new LambdaQueryWrapper<TeamMilestone>()
                        .in(TeamMilestone::getTeamId, teamById.keySet())
                        .orderByAsc(TeamMilestone::getStartAt));
        List<Point> points = points(nodes, milestones, info, teamById);

        // 下一个节点和之后的两个
        List<Point> upcoming = points.stream().filter(p -> !p.done() && !p.start().isBefore(now)).toList();
        Upcoming next = upcoming.isEmpty() ? null : upcoming(upcoming.getFirst(), points, now);
        List<Upcoming> after = upcoming.stream().skip(1).limit(2).map(p -> upcoming(p, null, now)).toList();

        // 学期赛程带和每周负荷
        LocalDate[] range = HomePlanner.semester(today);
        Integer slots = slots(userId);
        List<HomePlanner.Interval> busy = slots == null ? List.of()
                : calendarService.busyPeriods(userId, range[0].atStartOfDay(), range[1].plusDays(1).atStartOfDay()).stream()
                .map(p -> new HomePlanner.Interval(p.start(), p.end())).toList();
        List<WeekLoad> weeks = weeks(range, points, slots, busy);
        LocalDate thisMonday = HomePlanner.monday(today);
        WeekLoad thisWeek = weeks.stream().filter(w -> w.start().equals(thisMonday)).findFirst().orElse(null);
        Semester semester = new Semester(range[0], range[1], lanes(range, points, info, joined), weeks);

        return new HomeView(today, next, after, thisWeek == null ? null : thisWeek.freeHours(),
                thisWeek == null ? 0 : thisWeek.deadlines(), semester,
                mine(userId, teams, nodes, points, info, now), suggestions(userId, joined, followed, now),
                pending(userId, teams, joined, nodes), teammates(userId, teams));
    }

    // ---------- 节点 ----------

    private static List<Point> points(List<CompetitionNode> nodes, List<TeamMilestone> milestones,
                                      Map<Long, EditionInfo> info, Map<Long, TeamSummary> teamById) {
        List<Point> out = new ArrayList<>();
        for (CompetitionNode n : nodes) {
            EditionInfo e = info.get(n.getEditionId());
            out.add(new Point(n.getTitle(), e == null ? "比赛" : e.label(), n.getStartAt(),
                    CalendarService.displayEnd(n.getEndMode(), n.getEndAt(), n.getEndLatestAt()),
                    CalendarService.isDeadline(n.getNodeType()), false,
                    CalendarService.fuzzy(n.getEndMode(), n.getStartLatestAt()), false, n.getEditionId(),
                    "COMPETITION", e == null ? null : e.competitionId()));
        }
        for (TeamMilestone m : milestones) {
            TeamSummary t = teamById.get(m.getTeamId());
            if (t == null) {
                continue;
            }
            out.add(new Point(m.getTitle(), t.name(), m.getStartAt(),
                    CalendarService.displayEnd(m.getEndMode(), m.getEndAt(), m.getEndLatestAt()), true, true,
                    CalendarService.fuzzy(m.getEndMode(), m.getStartLatestAt()), m.getDoneAt() != null,
                    t.edition().editionId(), "TEAM", t.id()));
        }
        out.sort(Comparator.comparing(Point::start));
        return out;
    }

    /** 一个快到的节点；all 不为空时顺便算本阶段过了多少（同一届里上一个节点到它） */
    private static Upcoming upcoming(Point p, List<Point> all, LocalDateTime now) {
        Integer progress = null;
        if (all != null) {
            LocalDateTime previous = all.stream()
                    .filter(o -> Objects.equals(o.editionId(), p.editionId()) && o.start().isBefore(now))
                    .map(Point::start)
                    .max(Comparator.naturalOrder())
                    .orElse(null);
            progress = HomePlanner.progress(previous, p.start(), now);
        }
        return new Upcoming(p.title(), p.label(), p.start(), ChronoUnit.DAYS.between(now.toLocalDate(), p.start().toLocalDate()),
                p.deadline(), p.team(), progress, p.refType(), p.refId());
    }

    // ---------- 学期 ----------

    private static List<WeekLoad> weeks(LocalDate[] range, List<Point> points, Integer slots,
                                        List<HomePlanner.Interval> busy) {
        List<WeekLoad> out = new ArrayList<>();
        for (LocalDate monday = HomePlanner.monday(range[0]); !monday.isAfter(range[1]); monday = monday.plusWeeks(1)) {
            LocalDateTime from = monday.atStartOfDay();
            LocalDateTime to = monday.plusWeeks(1).atStartOfDay();
            int deadlines = (int) points.stream()
                    .filter(p -> p.deadline() && !p.start().isBefore(from) && p.start().isBefore(to))
                    .count();
            Double free = slots == null ? null : HomePlanner.freeHours(slots, monday, busy);
            out.add(new WeekLoad(monday, free, deadlines, HomePlanner.warn(deadlines, free)));
        }
        return out;
    }

    /** 每个比赛一行：参加了的在前，然后按最早的节点排 */
    private static List<Lane> lanes(LocalDate[] range, List<Point> points, Map<Long, EditionInfo> info, Set<Long> joined) {
        LocalDateTime from = range[0].atStartOfDay();
        LocalDateTime to = range[1].plusDays(1).atStartOfDay();
        Map<Long, List<Mark>> byEdition = new LinkedHashMap<>();
        for (Point p : points) {
            LocalDateTime end = p.end() == null ? p.start() : p.end();
            if (end.isBefore(from) || !p.start().isBefore(to)) {
                continue;
            }
            byEdition.computeIfAbsent(p.editionId(), k -> new ArrayList<>())
                    .add(new Mark(p.title(), p.start(), p.end(), p.deadline(), p.team(), p.fuzzy()));
        }
        return byEdition.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<Long, List<Mark>> e) -> !joined.contains(e.getKey())))
                .map(e -> {
                    EditionInfo i = info.get(e.getKey());
                    return new Lane(e.getKey(), i == null ? null : i.competitionId(), i == null ? "比赛" : i.label(),
                            joined.contains(e.getKey()), e.getValue());
                })
                .toList();
    }

    /** 问卷 G4 勾的空闲时段，没填为空 */
    private Integer slots(long userId) {
        Map<String, Object> answers = surveyService.generalAnswers(List.of(userId)).get(userId);
        Object g4 = answers == null ? null : answers.get("G4");
        return g4 instanceof Number n && n.intValue() != 0 ? n.intValue() : null;
    }

    // ---------- 中栏 ----------

    private List<MyCompetition> mine(long userId, List<TeamSummary> teams, List<CompetitionNode> nodes,
                                     List<Point> points, Map<Long, EditionInfo> info, LocalDateTime now) {
        Map<Long, List<Long>> memberIds = new HashMap<>();
        for (TeamSummary t : teams) {
            memberIds.put(t.id(), teamSupport.activeMembers(t.id()).stream().map(TeamMember::getUserId).toList());
        }
        Map<Long, String> names = nicknames(memberIds.values().stream().flatMap(List::stream).toList());
        List<MyCompetition> out = new ArrayList<>();
        for (TeamSummary t : teams) {
            long editionId = t.edition().editionId();
            EditionInfo i = info.get(editionId);
            List<Stage> stages = stages(nodes.stream().filter(n -> n.getEditionId() == editionId).toList(), now);
            Upcoming nextDeadline = points.stream()
                    .filter(p -> p.editionId() == editionId && p.deadline() && !p.done() && !p.start().isBefore(now)
                            && (!p.team() || Objects.equals(p.refId(), t.id())))
                    .findFirst().map(p -> upcoming(p, null, now)).orElse(null);
            out.add(new MyCompetition(t.id(), t.name(), editionId, t.edition().competitionId(),
                    t.edition().competitionName(), i == null ? null : i.level(), t.edition().editionTitle(),
                    memberIds.get(t.id()).stream().map(names::get).toList(), t.memberCount(), t.maxMembers(),
                    t.openNeeds().size(), t.captain() != null && t.captain().id() == userId,
                    i == null ? null : i.officialUrl(), i == null ? null : i.noticeUrl(), stages, nextDeadline));
        }
        // 下一个截止近的排前面
        out.sort(Comparator.comparing((MyCompetition m) -> m.nextDeadline() == null ? LocalDateTime.MAX : m.nextDeadline().at()));
        return out;
    }

    /** 已经过了的、正在进行或下一个要到的（第一个还没结束的）、还没到的 */
    static List<Stage> stages(List<CompetitionNode> nodes, LocalDateTime now) {
        List<Stage> out = new ArrayList<>();
        boolean currentFound = false;
        for (CompetitionNode n : nodes) {
            LocalDateTime end = CalendarService.displayEnd(n.getEndMode(), n.getEndAt(), n.getEndLatestAt());
            LocalDateTime over = end == null ? n.getStartAt() : end;
            String state;
            if (!currentFound && over.isBefore(now)) {
                state = "DONE";
            } else if (!currentFound) {
                state = "CURRENT";
                currentFound = true;
            } else {
                state = "TODO";
            }
            out.add(new Stage(n.getTitle(), n.getStartAt(), state));
        }
        return out;
    }

    /** 正在报名、还没参加的比赛：关注了的在前，其余按报名截止早晚 */
    private List<Suggestion> suggestions(long userId, Set<Long> joined, Set<Long> followed, LocalDateTime now) {
        List<EditionInfo> open = homeMapper.openEditions(now).stream()
                .filter(e -> !joined.contains(e.editionId()))
                .sorted(Comparator.comparing((EditionInfo e) -> !followed.contains(e.editionId())))
                .limit(SUGGESTIONS)
                .toList();
        if (open.isEmpty()) {
            return List.of();
        }
        Map<Long, Long> recruiting = homeMapper.recruitingTeams(open.stream().map(EditionInfo::editionId).toList()).stream()
                .collect(Collectors.toMap(HomeMapper.EditionCount::editionId, HomeMapper.EditionCount::cnt));
        SysUser me = userMapper.selectById(userId);
        Map<Long, String> recognitions = recognitionService.labelsForSchool(me == null ? null : me.getSchoolId(),
                open.stream().map(EditionInfo::competitionId).collect(Collectors.toSet()));
        return open.stream().map(e -> new Suggestion(e.editionId(), e.competitionId(), e.competitionName(), e.level(),
                e.editionTitle(), e.registrationEnd(),
                e.registrationEnd() == null ? null : ChronoUnit.DAYS.between(now.toLocalDate(), e.registrationEnd().toLocalDate()),
                e.teamMax() != null && e.teamMax() == 1, recruiting.getOrDefault(e.editionId(), 0L).intValue(),
                followed.contains(e.editionId()), e.officialUrl(), e.noticeUrl(), recognitions.get(e.competitionId()))).toList();
    }

    // ---------- 右栏 ----------

    /** 我收到的邀请 + 有人申请加入我当队长的队，都附上处理后的影响 */
    private List<Pending> pending(long userId, List<TeamSummary> teams, Set<Long> joined, List<CompetitionNode> myNodes) {
        List<Pending> out = new ArrayList<>();
        for (ApplicationView v : applicationService.mine(userId).received()) {
            if (v.status() != TeamApplication.Status.PENDING) {
                continue;
            }
            Team team = teamSupport.requireTeam(v.team().id());
            String captain = nicknames(List.of(team.getCaptainId())).get(team.getCaptainId());
            out.add(new Pending("INVITE", v.id(), team.getId(), team.getName(), v.team().competitionName(), captain,
                    team.getCaptainId(), v.needRoleName(), v.message(), v.createdAt(),
                    inviteConsequence(team, teams, joined, myNodes)));
        }
        for (TeamSummary t : teams) {
            if (t.captain() == null || t.captain().id() != userId) {
                continue;
            }
            for (ApplicationView v : applicationService.ofTeam(userId, t.id())) {
                if (v.status() != TeamApplication.Status.PENDING || v.direction() != TeamApplication.Direction.APPLY) {
                    continue;
                }
                int after = t.memberCount() + 1;
                String consequence = "同意后队伍 " + after + "/" + t.maxMembers() + " 人"
                        + (t.maxMembers() != null && after >= t.maxMembers() ? "，满员" : "");
                out.add(new Pending("APPLY", v.id(), t.id(), t.name(), t.edition().competitionName(),
                        v.user() == null ? null : v.user().nickname(), v.user() == null ? null : v.user().id(),
                        v.needRoleName(), v.message(), v.createdAt(), consequence));
            }
        }
        out.sort(Comparator.comparing(Pending::createdAt).reversed());
        return out;
    }

    /**
     * 接受邀请的影响：已经在这一届的别的队里就不能接受；否则看比赛期间（参与撞期检测的节点）
     * 那个月我已经参加的比赛有几个，算上这个超过 1 个就提醒。
     */
    private String inviteConsequence(Team team, List<TeamSummary> teams, Set<Long> joined, List<CompetitionNode> myNodes) {
        if (joined.contains(team.getEditionId())) {
            String current = teams.stream().filter(t -> t.edition().editionId().equals(team.getEditionId()))
                    .map(TeamSummary::name).findFirst().orElse("另一支队");
            return "你已经在这一届的「" + current + "」里，接受前要先退出";
        }
        CompetitionNode contest = nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                        .eq(CompetitionNode::getEditionId, team.getEditionId())
                        .eq(CompetitionNode::getBusy, true)
                        .orderByAsc(CompetitionNode::getStartAt))
                .stream().findFirst().orElse(null);
        if (contest == null) {
            return null;
        }
        int month = contest.getStartAt().getMonthValue();
        int year = contest.getStartAt().getYear();
        long parallel = myNodes.stream()
                .filter(n -> joined.contains(n.getEditionId()) && Boolean.TRUE.equals(n.getBusy()))
                .filter(n -> overlapsMonth(n, year, month))
                .map(CompetitionNode::getEditionId)
                .distinct()
                .count();
        return parallel > 0
                ? "接受后 " + month + " 月会有 " + (parallel + 1) + " 个比赛同时进行"
                : "比赛在 " + month + " 月，和你参加的其他比赛不冲突";
    }

    private static boolean overlapsMonth(CompetitionNode n, int year, int month) {
        LocalDateTime monthStart = LocalDate.of(year, month, 1).atStartOfDay();
        LocalDateTime monthEnd = monthStart.plusMonths(1);
        LocalDateTime end = CalendarService.displayEnd(n.getEndMode(), n.getEndAt(), n.getEndLatestAt());
        LocalDateTime realEnd = end == null ? n.getStartAt() : end;
        return n.getStartAt().isBefore(monthEnd) && !realEnd.isBefore(monthStart);
    }

    /** 我当队长、还在招人的队：每队取匹配分最高的几个（已经邀请过的也列出来，标上） */
    private List<Teammate> teammates(long userId, List<TeamSummary> teams) {
        List<Teammate> out = new ArrayList<>();
        for (TeamSummary t : teams) {
            if (out.size() >= TEAMMATES) {
                break;
            }
            if (t.captain() == null || t.captain().id() != userId || t.status() != Team.Status.RECRUITING
                    || t.openNeeds().isEmpty()) {
                continue;
            }
            List<RecommendedUser> users;
            try {
                users = recommendService.usersFor(userId, t.id());
            } catch (BizException e) {
                log.debug("主页推荐队友跳过队伍 {}：{}", t.id(), e.getMessage());
                continue;
            }
            for (RecommendedUser u : users) {
                if (out.size() >= TEAMMATES) {
                    break;
                }
                String reason = u.match().reasons().isEmpty() ? null : u.match().reasons().getFirst();
                out.add(new Teammate(t.id(), t.name(), u.user().id(), u.user().nickname(), u.user().major(),
                        u.user().enrollYear(), u.match().score(), reason, u.invited(), u.match()));
            }
        }
        return out;
    }

    private Map<Long, String> nicknames(Collection<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(distinct).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }
}
