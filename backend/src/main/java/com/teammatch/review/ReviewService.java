package com.teammatch.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.match.MatchConfig;
import com.teammatch.match.MatchConfigService;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.profile.ProfileMappers.ExperienceMapper;
import com.teammatch.profile.UserExperience;
import com.teammatch.review.CreditCalculator.Pair;
import com.teammatch.review.CreditCalculator.Rating;
import com.teammatch.review.ReviewDtos.CreditView;
import com.teammatch.review.ReviewDtos.ReviewInput;
import com.teammatch.review.ReviewDtos.RoundForm;
import com.teammatch.review.ReviewDtos.RoundSummary;
import com.teammatch.review.ReviewDtos.SubmitResult;
import com.teammatch.review.ReviewDtos.Target;
import com.teammatch.review.ReviewEntities.CreditMapper;
import com.teammatch.review.ReviewEntities.CreditMapper.Credit;
import com.teammatch.review.ReviewEntities.PeerReview;
import com.teammatch.review.ReviewEntities.PeerReviewMapper;
import com.teammatch.review.ReviewEntities.ReviewRound;
import com.teammatch.review.ReviewEntities.RoundMapper;
import com.teammatch.review.ReviewEntities.RoundStatus;
import com.teammatch.team.Team;
import com.teammatch.team.TeamDtos.EditionRef;
import com.teammatch.team.TeamMappers.TeamMapper;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserBriefs;
import com.teammatch.user.UserBriefs.UserBrief;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 赛后：队长标记比赛结束 → 开一轮互评 → 队员互相打分 → 所有人交齐或到期后统一揭晓 → 更新信用分。
 * <p>
 * 信用分影响推荐里的承诺系数；失联或不交付在两场比赛里都被评为"经常"，暂停推荐 30 天并通知管理员复核。
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    /** 互评开放多久 */
    static final Duration WINDOW = Duration.ofDays(7);
    /** 累计到这么多场比赛或这么多个评分人，才显示各项行为的分数 */
    static final int VISIBLE_ROUNDS = 2;
    static final int VISIBLE_RATERS = 4;
    /** 失联、不交付被评为"经常"的比赛达到这么多场，暂停推荐 */
    static final int SEVERE_ROUNDS = 2;
    static final Duration SUSPEND = Duration.ofDays(30);

    public static final String REF_REVIEW = "REVIEW";
    public static final String REF_CREDIT = "CREDIT";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm");
    private static final TypeReference<List<Integer>> ANSWERS = new TypeReference<>() {
    };

    private final RoundMapper roundMapper;
    private final PeerReviewMapper reviewMapper;
    private final CreditMapper creditMapper;
    private final TeamMapper teamMapper;
    private final EditionMapper editionMapper;
    private final CompetitionMapper competitionMapper;
    private final ExperienceMapper experienceMapper;
    private final SysUserMapper userMapper;
    private final TeamSupport teamSupport;
    private final UserBriefs userBriefs;
    private final NotificationService notifications;
    private final MatchConfigService configService;
    private final JsonMapper json;

    // ---------- 比赛结束 ----------

    /**
     * 队长标记比赛结束：队伍归档、记下奖项、给每个队员写一条参赛履历；两人及以上的队开一轮互评。
     *
     * @return 新开的互评轮次 id；单人队没有互评，返回 null
     */
    @Transactional
    public Long finishTeam(long captainId, long teamId, String award) {
        teamSupport.requireCaptain(captainId, teamId);
        Team t = teamMapper.selectForUpdate(teamId);
        if (t == null || !t.isActive()) {
            throw new BizException(409, "这支队伍已经标记过比赛结束了");
        }
        LocalDateTime now = teamSupport.now();
        String awardText = StringUtils.hasText(award) ? award.trim() : null;
        teamMapper.update(new LambdaUpdateWrapper<Team>()
                .eq(Team::getId, teamId)
                .set(Team::getStatus, Team.Status.FINISHED)
                .set(Team::getAward, awardText)
                .set(Team::getAwardRecordedAt, awardText == null ? null : now));
        teamSupport.expirePendingOfTeam(teamId);

        List<TeamMember> members = teamSupport.activeMembers(teamId);
        recordExperiences(t, members, awardText);
        if (members.size() < 2) {
            return null;
        }
        ReviewRound round = new ReviewRound();
        round.setTeamId(teamId);
        round.setOpensAt(now);
        round.setClosesAt(now.plus(WINDOW));
        round.setStatus(RoundStatus.OPEN);
        roundMapper.insert(round);
        for (TeamMember m : members) {
            notifications.send(m.getUserId(), Notification.Type.REVIEW_OPEN, "比赛结束了，给队友做个互评吧",
                    "「" + t.getName() + "」的互评开放到 " + WHEN.format(round.getClosesAt())
                            + "。每位队友 6 个问题，一分钟左右。所有人交齐或到截止时间后统一揭晓，你看不到别人具体给你打了什么分。",
                    REF_REVIEW, round.getId());
        }
        return round.getId();
    }

    /** 平台内组队参赛自动记进履历；同一个比赛同一年已经有记录的不重复写 */
    private void recordExperiences(Team t, List<TeamMember> members, String award) {
        CompetitionEdition e = editionMapper.selectById(t.getEditionId());
        Competition c = e == null ? null : competitionMapper.selectById(e.getCompetitionId());
        if (c == null) {
            return;
        }
        for (TeamMember m : members) {
            boolean exists = experienceMapper.exists(new LambdaQueryWrapper<UserExperience>()
                    .eq(UserExperience::getUserId, m.getUserId())
                    .eq(UserExperience::getCompetitionId, c.getId())
                    .eq(UserExperience::getYear, e.getYear()));
            if (exists) {
                continue;
            }
            UserExperience x = new UserExperience();
            x.setUserId(m.getUserId());
            x.setCompetitionId(c.getId());
            x.setTeamId(t.getId());
            x.setTitle(c.getName());
            x.setYear(e.getYear());
            x.setAward(award);
            x.setSource(UserExperience.Source.PLATFORM);
            x.setVerified(true);
            experienceMapper.insert(x);
        }
    }

    // ---------- 互评 ----------

    public List<RoundSummary> mine(long userId) {
        List<ReviewRound> rounds = roundMapper.ofUser(userId);
        return summaries(rounds, userId);
    }

    public RoundForm form(long userId, long roundId) {
        ReviewRound round = requireRound(roundId);
        List<Long> participants = participants(round);
        if (!participants.contains(userId)) {
            throw new BizException(403, "你不在这支队伍里");
        }
        Map<Long, List<Integer>> mine = reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                        .eq(PeerReview::getRoundId, roundId)
                        .eq(PeerReview::getRaterId, userId))
                .stream().collect(Collectors.toMap(PeerReview::getRateeId, r -> parse(r.getAnswers())));
        Map<Long, UserBrief> briefs = userBriefs.load(participants);
        // 队友在前，自评放最后
        List<Target> targets = new ArrayList<>();
        participants.stream().filter(id -> id != userId)
                .forEach(id -> targets.add(new Target(briefs.get(id), false, mine.get(id))));
        targets.add(new Target(briefs.get(userId), true, mine.get(userId)));
        return new RoundForm(summaries(List.of(round), userId).getFirst(), CreditCalculator.ITEMS,
                CreditCalculator.FREQUENCIES, targets, editable(round));
    }

    /**
     * 提交（或修改）我的互评：必须给每位队友都打分，自评选填。揭晓前可以改。
     * 所有人都交了就马上揭晓。
     */
    @Transactional
    public SubmitResult submit(long userId, long roundId, List<ReviewInput> inputs) {
        ReviewRound round = roundMapper.selectForUpdate(roundId);
        if (round == null) {
            throw new BizException(404, "这轮互评不存在");
        }
        if (!editable(round)) {
            throw new BizException(409, "这轮互评已经结束了");
        }
        List<Long> participants = participants(round);
        if (!participants.contains(userId)) {
            throw new BizException(403, "你不在这支队伍里");
        }
        Map<Long, List<Integer>> byRatee = new LinkedHashMap<>();
        for (ReviewInput in : inputs) {
            if (!participants.contains(in.rateeId())) {
                throw new BizException("只能评价这支队伍的队员");
            }
            if (!CreditCalculator.valid(in.answers())) {
                throw new BizException("每个人的 6 个问题都要选一项");
            }
            if (byRatee.put(in.rateeId(), in.answers()) != null) {
                throw new BizException("同一个人只能评一次");
            }
        }
        if (!byRatee.keySet().containsAll(participants.stream().filter(id -> id != userId).toList())) {
            throw new BizException("每位队友都要评，评完才能提交");
        }
        LocalDateTime now = teamSupport.now();
        byRatee.forEach((ratee, answers) -> reviewMapper.upsert(roundId, userId, ratee, json.writeValueAsString(answers), now));

        int raters = raterCount(roundId);
        boolean published = raters >= participants.size() && publish(round, participants);
        return new SubmitResult(published, raters, participants.size());
    }

    // ---------- 揭晓 ----------

    /** 定时任务调用：到期的互评揭晓 */
    @Transactional
    public boolean publishIfDue(long roundId) {
        ReviewRound round = roundMapper.selectForUpdate(roundId);
        if (round == null || round.getStatus() != RoundStatus.OPEN || round.getClosesAt().isAfter(teamSupport.now())) {
            return false;
        }
        return publish(round, participants(round));
    }

    /** 调用方已经锁住了这一轮 */
    private boolean publish(ReviewRound round, List<Long> participants) {
        if (round.getStatus() != RoundStatus.OPEN) {
            return false;
        }
        LocalDateTime now = teamSupport.now();
        List<Rating> ratings = reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                .eq(PeerReview::getRoundId, round.getId())).stream().map(this::rating).toList();
        CreditCalculator.Result result = CreditCalculator.evaluate(ratings);
        for (Pair p : result.flagged()) {
            reviewMapper.update(new LambdaUpdateWrapper<PeerReview>()
                    .eq(PeerReview::getRoundId, round.getId())
                    .eq(PeerReview::getRaterId, p.raterId())
                    .eq(PeerReview::getRateeId, p.rateeId())
                    .set(PeerReview::getWeight, BigDecimal.valueOf(CreditCalculator.FLAGGED_WEIGHT))
                    .set(PeerReview::getFlagged, true));
        }
        result.scores().forEach(creditMapper::addRound);
        roundMapper.update(new LambdaUpdateWrapper<ReviewRound>()
                .eq(ReviewRound::getId, round.getId())
                .set(ReviewRound::getStatus, RoundStatus.PUBLISHED)
                .set(ReviewRound::getPublishedAt, now));
        round.setStatus(RoundStatus.PUBLISHED);

        Map<Long, List<Rating>> byRatee = ratings.stream().collect(Collectors.groupingBy(Rating::rateeId));
        Team t = teamMapper.selectById(round.getTeamId());
        for (Long uid : participants) {
            notifications.send(uid, Notification.Type.REVIEW_RESULT, "「" + t.getName() + "」的互评揭晓了",
                    result.scores().containsKey(uid) ? "队友的评价已经计入你的信用分，去看看吧。" : "这轮没有人评价你，信用分不变。",
                    REF_CREDIT, null);
            // 这一轮又被多数队友评为经常失联或不交付，才去看要不要暂停
            if (CreditCalculator.severe(byRatee.getOrDefault(uid, List.of()))) {
                checkSevere(uid, now);
            }
        }
        return true;
    }

    /**
     * 失联或不交付在多场比赛里都被评为"经常"：暂停推荐，并请管理员复核。
     * <p>
     * 只数上次暂停结束（到期或管理员解除）之后揭晓的场次。不然解除或到期以后，
     * 以前那两场还在，下一轮一揭晓又会被暂停。
     */
    private void checkSevere(long userId, LocalDateTime now) {
        Credit credit = creditMapper.find(userId);
        LocalDateTime lastSuspension = credit == null ? null : credit.suspendedUntil();
        if (lastSuspension != null && lastSuspension.isAfter(now)) {
            return;
        }
        List<PeerReview> received = lastSuspension == null ? reviewMapper.publishedFor(userId)
                : reviewMapper.publishedSince(userId, lastSuspension);
        long severeRounds = received.stream()
                .collect(Collectors.groupingBy(PeerReview::getRoundId)).values().stream()
                .filter(list -> CreditCalculator.severe(list.stream().map(this::rating).toList()))
                .count();
        if (severeRounds < SEVERE_ROUNDS) {
            return;
        }
        LocalDateTime until = now.plus(SUSPEND);
        creditMapper.suspend(userId, until);
        notifications.send(userId, Notification.Type.CREDIT, "你被暂停推荐 30 天",
                "你在 " + severeRounds + " 场比赛的互评里都被多数队友评为经常失联或不按时交付，暂停出现在推荐里到 "
                        + WHEN.format(until) + "。管理员会复核，如有误会可以通过举报说明情况。",
                REF_CREDIT, null);
        String nickname = userMapper.selectById(userId).getNickname();
        for (SysUser admin : admins()) {
            notifications.send(admin.getId(), Notification.Type.SYSTEM, "互评复核：" + nickname + " 被暂停推荐",
                    nickname + " 在 " + severeRounds + " 场比赛里被评为经常失联或不交付，系统已暂停推荐 30 天，请到「举报与信用」里复核。",
                    "ADMIN_CREDIT", userId);
        }
    }

    // ---------- 信用 ----------

    public CreditView myCredit(long userId) {
        Credit credit = creditMapper.find(userId);
        Double score = credit == null || credit.score() == null ? null : credit.score().doubleValue();
        int ratedRounds = credit == null || credit.ratedRounds() == null ? 0 : credit.ratedRounds();
        LocalDateTime now = teamSupport.now();
        LocalDateTime suspended = credit != null && credit.suspendedUntil() != null
                && credit.suspendedUntil().isAfter(now) ? credit.suspendedUntil() : null;

        List<PeerReview> received = reviewMapper.publishedFor(userId);
        List<Rating> ratings = received.stream().map(this::rating).toList();
        Map<Pair, Double> weights = new HashMap<>();
        received.forEach(r -> weights.put(new Pair(r.getRaterId(), r.getRateeId()),
                r.getWeight() == null ? 1.0 : r.getWeight().doubleValue()));
        Set<Long> raters = received.stream().map(PeerReview::getRaterId).filter(id -> id != userId)
                .collect(Collectors.toSet());
        boolean visible = ratedRounds >= VISIBLE_ROUNDS || raters.size() >= VISIBLE_RATERS;
        List<Double> items = visible ? CreditCalculator.itemAverages(ratings, weights) : null;
        List<Double> selfItems = null;
        if (visible) {
            List<Rating> selfRatings = ratings.stream().filter(r -> r.raterId() == r.rateeId()).toList();
            if (!selfRatings.isEmpty()) {
                selfItems = new ArrayList<>();
                for (int i = 0; i < CreditCalculator.ITEMS.size(); i++) {
                    final int idx = i;
                    selfItems.add(Math.round(selfRatings.stream().mapToInt(r -> r.answers().get(idx)).average()
                            .orElse(0) * 100) / 100.0);
                }
            }
        }
        List<ReviewRound> published = roundMapper.ofUser(userId).stream()
                .filter(r -> r.getStatus() == RoundStatus.PUBLISHED).toList();
        return new CreditView(score, ratedRounds, factor(score), suspended, visible, CreditCalculator.ITEMS, items,
                selfItems, raters.size(), summaries(published, userId));
    }

    /** 和匹配引擎用同一套配置：新用户 newUser，有分的按 min–max 线性映射 */
    private double factor(Double score) {
        MatchConfig.Commitment c = configService.forPreset(Competition.MatchPreset.DEFAULT).commitment();
        if (c == null || c.history() == null) {
            return 1.0;
        }
        MatchConfig.History h = c.history();
        double k = score == null ? h.newUser() : h.min() + (h.max() - h.min()) * score / 100;
        return Math.round(k * 1000) / 1000.0;
    }

    // ---------- 内部 ----------

    private List<RoundSummary> summaries(List<ReviewRound> rounds, long userId) {
        if (rounds.isEmpty()) {
            return List.of();
        }
        List<Long> roundIds = rounds.stream().map(ReviewRound::getId).toList();
        Map<Long, Team> teams = teamMapper.selectByIds(rounds.stream().map(ReviewRound::getTeamId).toList()).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
        Map<Long, EditionRef> editions = teamSupport.editionRefs(
                teams.values().stream().map(Team::getEditionId).collect(Collectors.toSet()));
        List<PeerReview> reviews = reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                .select(PeerReview::getRoundId, PeerReview::getRaterId, PeerReview::getRateeId)
                .in(PeerReview::getRoundId, roundIds));
        Map<Long, Set<Long>> ratersByRound = new HashMap<>();
        for (PeerReview r : reviews) {
            if (!r.getRaterId().equals(r.getRateeId())) {
                ratersByRound.computeIfAbsent(r.getRoundId(), k -> new HashSet<>()).add(r.getRaterId());
            }
        }
        Map<Long, Integer> sizes = new HashMap<>();
        for (ReviewRound r : rounds) {
            sizes.put(r.getId(), participants(r).size());
        }
        return rounds.stream().map(r -> {
            Team t = teams.get(r.getTeamId());
            EditionRef e = t == null ? null : editions.get(t.getEditionId());
            Set<Long> done = ratersByRound.getOrDefault(r.getId(), Set.of());
            return new RoundSummary(r.getId(), r.getTeamId(), t == null ? null : t.getName(),
                    e == null ? null : e.competitionName(), e == null ? null : e.editionTitle(), r.getStatus(),
                    r.getClosesAt(), r.getPublishedAt(), done.contains(userId), done.size(), sizes.get(r.getId()));
        }).toList();
    }

    /** 参与互评的人：比赛结束时还在队里的人（结束后队伍成员不再变动） */
    private List<Long> participants(ReviewRound round) {
        return teamSupport.activeMembers(round.getTeamId()).stream().map(TeamMember::getUserId).toList();
    }

    private int raterCount(long roundId) {
        return Math.toIntExact(reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                        .select(PeerReview::getRaterId, PeerReview::getRateeId)
                        .eq(PeerReview::getRoundId, roundId))
                .stream().filter(r -> !r.getRaterId().equals(r.getRateeId()))
                .map(PeerReview::getRaterId).distinct().count());
    }

    private boolean editable(ReviewRound round) {
        return round.getStatus() == RoundStatus.OPEN && round.getClosesAt().isAfter(teamSupport.now());
    }

    private ReviewRound requireRound(long roundId) {
        ReviewRound round = roundMapper.selectById(roundId);
        if (round == null) {
            throw new BizException(404, "这轮互评不存在");
        }
        return round;
    }

    private Rating rating(PeerReview r) {
        return new Rating(r.getRaterId(), r.getRateeId(), parse(r.getAnswers()));
    }

    private List<Integer> parse(String answers) {
        return json.readValue(answers, ANSWERS);
    }

    List<SysUser> admins() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, SysUser.UserRole.ADMIN)
                .eq(SysUser::getStatus, SysUser.UserStatus.ACTIVE));
    }

    /** 给 ReviewJob 用：还开着、快截止的轮次里还没交的人 */
    List<Long> notSubmitted(ReviewRound round) {
        Set<Long> done = reviewMapper.selectList(new LambdaQueryWrapper<PeerReview>()
                        .select(PeerReview::getRaterId, PeerReview::getRateeId)
                        .eq(PeerReview::getRoundId, round.getId()))
                .stream().filter(r -> !r.getRaterId().equals(r.getRateeId()))
                .map(PeerReview::getRaterId).collect(Collectors.toSet());
        return participants(round).stream().filter(id -> !done.contains(id)).toList();
    }

    String teamName(long teamId) {
        Team t = teamMapper.selectById(teamId);
        return t == null ? "" : t.getName();
    }
}
