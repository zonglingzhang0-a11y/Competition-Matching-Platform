package com.teammatch.match;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.calendar.CalendarEntities.PersonalEvent;
import com.teammatch.calendar.Occurrences;
import com.teammatch.calendar.PersonalEventService;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.match.MatchInputs.Facts;
import com.teammatch.match.MatchInputs.Labels;
import com.teammatch.match.MatchInputs.Need;
import com.teammatch.match.MatchInputs.Person;
import com.teammatch.match.MatchInputs.TeamContext;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.Skill;
import com.teammatch.survey.SurveyDtos.Option;
import com.teammatch.survey.SurveyDtos.Question;
import com.teammatch.survey.SurveyService;
import com.teammatch.team.Team;
import com.teammatch.team.TeamDtos.EditionRef;
import com.teammatch.team.TeamDtos.Seeker;
import com.teammatch.team.TeamDtos.TeamSummary;
import com.teammatch.team.TeamMappers.MemberMapper;
import com.teammatch.team.TeamMappers.NeedMapper;
import com.teammatch.team.TeamMappers.NeedSkillMapper;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamNeed;
import com.teammatch.team.TeamService;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.SysUser;
import com.teammatch.user.UserBriefs;
import com.teammatch.user.UserBriefs.UserBrief;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 推荐：给学生推荐队伍、给队长推荐可以邀请的人。
 * 负责把数据库里的数据整理成引擎要的输入，打分交给 {@link MatchEngine}。
 * 现在的数据量每次实时算就够快；以后人多了再把结果缓存进 match_score 表。
 */
@Service
@RequiredArgsConstructor
public class RecommendService {

    private static final int HOME_EDITIONS = 3;
    private static final int HOME_TEAMS_PER_EDITION = 3;

    private final TeamMapper teamMapper;
    private final MemberMapper memberMapper;
    private final NeedMapper needMapper;
    private final NeedSkillMapper needSkillMapper;
    private final CompetitionMapper competitionMapper;
    private final SkillMapper skillMapper;
    private final MatchMapper matchMapper;
    private final TeamSupport teamSupport;
    private final TeamService teamService;
    private final SurveyService surveyService;
    private final MatchConfigService configService;
    private final UserService userService;
    private final UserBriefs userBriefs;
    private final PersonalEventService eventService;
    private final Clock clock;

    public record RecommendedTeam(TeamSummary team, MatchResult match) {
    }

    /**
     * @param currentTeamId 已经在这一届的某支队里时，是那支队的 id，此时不再推荐
     * @param note          整体上不能推荐的原因，比如和已参加的比赛撞期
     */
    public record TeamRecommendations(Long editionId, Long currentTeamId, boolean generalSurveyDone,
                                      boolean editionSurveyDone, List<RecommendedTeam> items, String note) {
    }

    public record RecommendedUser(UserBrief user, boolean invited, MatchResult match) {
    }

    public record HomeRecommendation(EditionRef edition, List<RecommendedTeam> teams) {
    }

    // ---------- 给学生推荐队伍 ----------

    public TeamRecommendations teamsFor(long userId, long editionId) {
        CompetitionEdition e = teamSupport.requireEdition(editionId);
        SysUser user = userService.getById(userId);
        boolean generalDone = !surveyService.generalAnswers(List.of(userId)).isEmpty();
        boolean editionDone = !surveyService.editionAnswers(List.of(userId), editionId).isEmpty();
        TeamMember current = teamSupport.activeMembership(userId, editionId);
        if (current != null) {
            return new TeamRecommendations(editionId, current.getTeamId(), generalDone, editionDone, List.of(), null);
        }

        // 推荐一支队等于替队长招人，所以队长被停用或暂停推荐的队伍也不推荐
        List<Team> teams = teamMapper.selectList(new LambdaQueryWrapper<Team>()
                        .eq(Team::getEditionId, editionId)
                        .eq(Team::getStatus, Team.Status.RECRUITING)
                        .notInSql(Team::getCaptainId, "SELECT id FROM sys_user WHERE status = 'BANNED'")
                        .notInSql(Team::getCaptainId, "SELECT user_id FROM user_credit WHERE suspended_until > NOW()"))
                .stream()
                .filter(t -> teamSupport.joinBlockReason(t, e, user, true, false) == null)
                .toList();
        if (teams.isEmpty()) {
            return new TeamRecommendations(editionId, null, generalDone, editionDone, List.of(), null);
        }

        Map<Long, TeamContext> contexts = contexts(teams, e);
        Person me = persons(List.of(userId), editionId).get(userId);
        Facts facts = facts(List.of(userId), e).get(userId);
        MatchEngine engine = engine(e);
        Map<Long, TeamSummary> summaries = teamService.summaries(teams).stream()
                .collect(Collectors.toMap(TeamSummary::id, Function.identity()));

        List<RecommendedTeam> scored = teams.stream()
                .map(t -> new RecommendedTeam(summaries.get(t.getId()), engine.score(me, contexts.get(t.getId()), facts, true)))
                .toList();
        String note = scored.stream().map(r -> r.match().veto()).filter(Objects::nonNull)
                .filter(v -> v.startsWith("比赛时间")).findFirst().orElse(null);
        List<RecommendedTeam> items = scored.stream()
                .filter(r -> r.match().veto() == null)
                .sorted(Comparator.comparingDouble((RecommendedTeam r) -> r.match().score()).reversed())
                .toList();
        return new TeamRecommendations(editionId, null, generalDone, editionDone, items, note);
    }

    /** 首页：我关注的几届比赛，每届推荐几支队伍 */
    public List<HomeRecommendation> home(long userId) {
        List<Long> editionIds = matchMapper.followedOpenEditions(userId, LocalDateTime.now(clock), HOME_EDITIONS);
        Map<Long, EditionRef> refs = teamSupport.editionRefs(editionIds);
        List<HomeRecommendation> out = new ArrayList<>();
        for (Long id : editionIds) {
            TeamRecommendations r = teamsFor(userId, id);
            if (r.currentTeamId() == null && refs.containsKey(id)) {
                out.add(new HomeRecommendation(refs.get(id), r.items().stream().limit(HOME_TEAMS_PER_EDITION).toList()));
            }
        }
        return out;
    }

    // ---------- 给队长推荐人 ----------

    /** 可以邀请的人按匹配度排序；被一票否决的排在最后，并写明原因 */
    public List<RecommendedUser> usersFor(long captainId, long teamId) {
        List<Seeker> seekers = teamService.seekers(captainId, teamId);
        if (seekers.isEmpty()) {
            return List.of();
        }
        Team t = teamSupport.requireTeam(teamId);
        CompetitionEdition e = teamSupport.requireEdition(t.getEditionId());
        TeamContext ctx = contexts(List.of(t), e).get(teamId);
        List<Long> ids = seekers.stream().map(s -> s.user().id()).toList();
        Map<Long, Person> people = persons(ids, e.getId());
        Map<Long, Facts> facts = facts(ids, e);
        MatchEngine engine = engine(e);
        return seekers.stream()
                .map(s -> new RecommendedUser(s.user(), s.invited(),
                        engine.score(people.get(s.user().id()), ctx, facts.get(s.user().id()), false)))
                .sorted(Comparator.comparing((RecommendedUser r) -> r.match().veto() != null)
                        .thenComparing(Comparator.comparingDouble((RecommendedUser r) -> r.match().score()).reversed()))
                .toList();
    }

    // ---------- 准备引擎的输入 ----------

    private MatchEngine engine(CompetitionEdition e) {
        Competition c = competitionMapper.selectById(e.getCompetitionId());
        MatchConfig config = configService.forPreset(c == null ? null : c.getMatchPreset());
        return new MatchEngine(config, labels());
    }

    private Map<Long, Person> persons(Collection<Long> userIds, long editionId) {
        Map<Long, Map<String, Object>> general = surveyService.generalAnswers(userIds);
        Map<Long, Map<String, Object>> edition = surveyService.editionAnswers(userIds, editionId);
        Map<Long, UserBrief> briefs = userBriefs.load(userIds);
        Map<Long, Long> experiences = matchMapper.experienceCounts(userIds).stream()
                .collect(Collectors.toMap(MatchMapper.CountRow::userId, MatchMapper.CountRow::cnt));
        Map<Long, Double> credits = matchMapper.credits(userIds).stream().filter(c -> c.score() != null)
                .collect(Collectors.toMap(MatchMapper.CreditRow::userId, MatchMapper.CreditRow::score));
        return userIds.stream().filter(briefs::containsKey).collect(Collectors.toMap(Function.identity(), id -> {
            UserBrief b = briefs.get(id);
            Map<Long, Integer> skills = b.skills().stream()
                    .collect(Collectors.toMap(s -> s.skillId(), s -> s.level(), (a, x) -> a));
            return new Person(id, b.nickname(), general.getOrDefault(id, Map.of()), edition.getOrDefault(id, Map.of()),
                    skills, Math.toIntExact(experiences.getOrDefault(id, 0L)), credits.get(id));
        }));
    }

    private Map<Long, TeamContext> contexts(List<Team> teams, CompetitionEdition e) {
        List<Long> teamIds = teams.stream().map(Team::getId).toList();
        Map<Long, List<Long>> memberIds = memberMapper.selectList(new LambdaQueryWrapper<TeamMember>()
                        .in(TeamMember::getTeamId, teamIds)
                        .eq(TeamMember::getStatus, TeamMember.Status.ACTIVE))
                .stream().collect(Collectors.groupingBy(TeamMember::getTeamId,
                        Collectors.mapping(TeamMember::getUserId, Collectors.toList())));
        Set<Long> everyone = new HashSet<>();
        memberIds.values().forEach(everyone::addAll);
        Map<Long, Person> people = persons(everyone, e.getId());

        List<TeamNeed> needs = needMapper.selectList(new LambdaQueryWrapper<TeamNeed>()
                .in(TeamNeed::getTeamId, teamIds)
                .eq(TeamNeed::getStatus, TeamNeed.Status.OPEN));
        Map<Long, Map<Long, Integer>> needSkills = needs.isEmpty() ? Map.of()
                : needSkillMapper.listByNeeds(needs.stream().map(TeamNeed::getId).toList()).stream()
                .collect(Collectors.groupingBy(NeedSkillMapper.NeedSkillRow::needId,
                        Collectors.toMap(NeedSkillMapper.NeedSkillRow::skillId, NeedSkillMapper.NeedSkillRow::minLevel)));
        Map<Long, List<Need>> needsByTeam = needs.stream().collect(Collectors.groupingBy(TeamNeed::getTeamId,
                Collectors.mapping(n -> new Need(n.getRoleName(), n.getHeadcount() - n.getFilled(),
                        needSkills.getOrDefault(n.getId(), Map.of())), Collectors.toList())));

        return teams.stream().collect(Collectors.toMap(Team::getId, t -> new TeamContext(t.getId(), t.getCaptainId(),
                memberIds.getOrDefault(t.getId(), List.of()).stream().map(people::get).filter(Objects::nonNull).toList(),
                needsByTeam.getOrDefault(t.getId(), List.of()),
                t.getMeetingSlots() == null ? 0 : t.getMeetingSlots(),
                t.getMeetingMode() == null ? "MIXED" : t.getMeetingMode().name(),
                Boolean.TRUE.equals(e.getEnglishRequired()))));
    }

    /** 撞期和暂停推荐：看这些人已经参加的其他比赛，有没有和这一届的比赛时段重叠 */
    private Map<Long, Facts> facts(Collection<Long> userIds, CompetitionEdition e) {
        List<MatchMapper.BusyRow> mine = matchMapper.busyNodes(e.getId());
        Map<Long, String> conflicts = mine.isEmpty() ? Map.of()
                : matchMapper.otherBusyNodes(userIds, e.getId()).stream()
                .filter(o -> mine.stream().anyMatch(m -> overlaps(m.startAt(), m.endAt(), o.startAt(), o.endAt())))
                .collect(Collectors.toMap(MatchMapper.UserBusyRow::userId, MatchMapper.UserBusyRow::competitionName, (a, b) -> a));
        LocalDateTime now = LocalDateTime.now(clock);
        Set<Long> suspended = matchMapper.credits(userIds).stream()
                .filter(c -> c.suspendedUntil() != null && c.suspendedUntil().isAfter(now))
                .map(MatchMapper.CreditRow::userId).collect(Collectors.toSet());
        Map<Long, String> personal = personalConflicts(userIds, mine);
        return userIds.stream().collect(Collectors.toMap(Function.identity(),
                id -> new Facts(conflicts.get(id), suspended.contains(id), personal.get(id))));
    }

    /** 这些人的个人日程（标了参与撞期检测的）里，有没有和这一届比赛期间重叠的；有就记下日程标题 */
    private Map<Long, String> personalConflicts(Collection<Long> userIds, List<MatchMapper.BusyRow> contest) {
        if (contest.isEmpty()) {
            return Map.of();
        }
        LocalDateTime from = contest.stream().map(MatchMapper.BusyRow::startAt).min(Comparator.naturalOrder()).orElseThrow();
        LocalDateTime to = contest.stream().map(b -> b.endAt() == null ? b.startAt() : b.endAt())
                .max(Comparator.naturalOrder()).orElseThrow().plusSeconds(1);
        Map<Long, String> out = new java.util.HashMap<>();
        eventService.busyOf(userIds).forEach((uid, events) -> {
            for (PersonalEvent ev : events) {
                boolean clash = Occurrences.between(eventService.rule(ev), from, to).stream().anyMatch(o ->
                        contest.stream().anyMatch(b -> Occurrences.overlaps(o.start(), o.end(), b.startAt(), b.endAt())));
                if (clash) {
                    out.putIfAbsent(uid, ev.getTitle());
                    return;
                }
            }
        });
        return out;
    }

    /** 两个时间段有没有重叠；没有结束时间的当作一个时间点 */
    static boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
        LocalDateTime ae = aEnd == null ? aStart : aEnd;
        LocalDateTime be = bEnd == null ? bStart : bEnd;
        return !aStart.isAfter(be) && !bStart.isAfter(ae);
    }

    /** 推荐理由里要用的题目标题、选项文字、技能名 */
    private Labels labels() {
        List<Question> questions = surveyService.questions(surveyService.activeVersion().getId());
        Map<String, Question> byCode = questions.stream().collect(Collectors.toMap(Question::code, Function.identity()));
        Map<Long, String> skills = skillMapper.selectList(new LambdaQueryWrapper<Skill>()).stream()
                .collect(Collectors.toMap(Skill::getId, Skill::getName));
        return new Labels() {
            @Override
            public String title(String code) {
                Question q = byCode.get(code);
                return q == null ? code : q.title().replaceAll("[？?]$", "");
            }

            @Override
            public String option(String code, String value) {
                Question q = byCode.get(code);
                if (q == null || q.options() == null) {
                    return value;
                }
                return q.options().stream().filter(o -> o.value().equals(value)).map(Option::label)
                        .findFirst().orElse(value);
            }

            @Override
            public String skill(long skillId) {
                return skills.getOrDefault(skillId, "技能");
            }
        };
    }
}
