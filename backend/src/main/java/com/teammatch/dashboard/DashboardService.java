package com.teammatch.dashboard;

import com.teammatch.competition.EditionPhase;
import com.teammatch.dashboard.DashboardMapper.BucketRow;
import com.teammatch.dashboard.DashboardMapper.EditionRow;
import com.teammatch.dashboard.DashboardMapper.FunnelRow;
import com.teammatch.dashboard.DashboardMapper.KeyCount;
import com.teammatch.dashboard.DashboardMapper.SkillCount;
import com.teammatch.dashboard.DashboardMapper.SkillLevelCount;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.Skill;
import com.teammatch.verify.VerifyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据看板：平台概况、各届组队情况（风险图用同一份数据）、组队漏斗、推荐准不准、技能供需缺口。
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DashboardMapper mapper;
    private final SkillMapper skillMapper;
    private final VerifyReviewService reviewService;
    private final Clock clock;

    /**
     * 平台概况。teams、applications 是按状态的计数，比如 {"RECRUITING": 3, "FULL": 1}。
     */
    public record Overview(long users, long verifiedUsers, long newUsers7d, long pendingVerify, long surveyDone,
                           Map<String, Long> teams, long openSlots, Map<String, Long> applications,
                           long competitions, long openEditions) {
    }

    /**
     * 一届比赛的组队情况。
     *
     * @param openSlots 还在招的队伍一共还差几个人
     * @param seekers   关注了这一届、已认证、还没进队的人数
     */
    public record EditionStat(Long editionId, String competitionName, String editionTitle, EditionPhase phase,
                              LocalDateTime registrationEnd, long recruitingTeams, long formedTeams, long openSlots,
                              long seekers, long followers) {
    }

    /**
     * 一种技能的供需。
     *
     * @param demand        队伍还需要几个会这项技能的人
     * @param supply        还在找队的人里会这项技能的人数
     * @param supplyByLevel 按熟练度分：[入门, 熟练, 精通]
     * @param gap           demand − supply，大于 0 表示缺人
     */
    public record SkillGap(Long skillId, String name, String category, long demand, long supply,
                           List<Long> supplyByLevel, long gap) {
    }

    /**
     * 一届比赛的组队漏斗：关注或在队里 → 填了问卷 → 申请或邀请过（含建队）→ 进了队 → 队伍组好了。
     * 看大家卡在哪一步：没填问卷的多就推问卷，申请发了没结果的多就催队长。
     */
    public record Funnel(long editionId, long people, long surveyed, long engaged, long joined, long formed) {
    }

    public Funnel funnel(long editionId) {
        FunnelRow r = mapper.funnel(editionId);
        return new Funnel(editionId, n(r.people()), n(r.surveyed()), n(r.engaged()), n(r.joined()), n(r.formed()));
    }

    private static long n(Long v) {
        return v == null ? 0 : v;
    }

    /**
     * 推荐准不准：按发出时的匹配分分段，每段处理完的申请和邀请有几条、成了几条。分越高成的比例越高，说明推荐靠谱。
     *
     * @param unscored 处理完了、但发出时还没开始记匹配分的（老记录），不在图里
     */
    public record Calibration(List<ScoreBucket> buckets, long unscored) {
    }

    /** from–to 分这一段：处理完 total 条，同意的 accepted 条 */
    public record ScoreBucket(int from, int to, long total, long accepted) {
    }

    public Calibration calibration() {
        Map<Integer, BucketRow> rows = mapper.calibration().stream()
                .collect(Collectors.toMap(BucketRow::bucket, Function.identity()));
        List<ScoreBucket> buckets = new ArrayList<>();
        for (int b = 0; b < 5; b++) {
            BucketRow r = rows.get(b);
            buckets.add(new ScoreBucket(b * 20, b * 20 + 20, r == null ? 0 : n(r.total()), r == null ? 0 : n(r.accepted())));
        }
        return new Calibration(buckets, mapper.unscoredHandled());
    }

    // ---------- 运营：每块都对应一件该做的事 ----------

    /** 等了超过这么多天算积压 */
    private static final int BACKLOG_DAYS = 3;
    /** 处理时长按最近这么多天算 */
    private static final int RECENT_DAYS = 30;
    /** 开着的来源超过这么多天没抓过，提醒看看定时任务 */
    private static final int STALE_DAYS = 3;

    public record Ops(ApplicationOps applications, VerifyOps verifications, List<SourceHealth> sources, ReportOps reports) {
    }

    /**
     * @param medianHours 最近 30 天处理完的申请和邀请，等待时长的中位数（小时）；没有记录时为空
     * @param backlog     手上压着等了 3 天以上的人：申请等队长处理，邀请等被邀请的人处理
     */
    public record ApplicationOps(long pending, long overdue, Double medianHours, long handled30d, long expired30d,
                                 List<Backlog> backlog) {
    }

    public record Backlog(long userId, String nickname, String direction, long count, long oldestDays) {
    }

    /** groups：按学校分；unknownDomain 的是还没收录的邮箱后缀，收录以后同校的人自动通过 */
    public record VerifyOps(long pending, Long oldestDays, List<VerifyGroup> groups) {
    }

    public record VerifyGroup(String label, boolean unknownDomain, long count, long oldestDays) {
    }

    /** status：OK 正常 / ERROR 上次出错 / STALE 好几天没抓 / NEVER 还没抓过 / DISABLED 停用 */
    public record SourceHealth(long id, String name, String status, LocalDateTime lastRunAt, String lastResult, long toConfirm) {
    }

    public record ReportOps(long pending, Long oldestDays, Double medianHours, long suspendedUsers) {
    }

    public Ops ops() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime overdueBefore = now.minusDays(BACKLOG_DAYS);
        LocalDateTime recent = now.minusDays(RECENT_DAYS);
        Map<String, Long> apps = toMap(mapper.applicationsByStatus());
        List<Long> waits = mapper.applicationWaitMinutes(recent);
        ApplicationOps applications = new ApplicationOps(apps.getOrDefault("PENDING", 0L),
                mapper.pendingApplicationsBefore(overdueBefore), medianHours(waits), waits.size(),
                mapper.expiredApplicationsSince(recent),
                mapper.applicationBacklog(overdueBefore).stream()
                        .map(b -> new Backlog(b.userId(), b.nickname(), b.direction(), n(b.cnt()), days(b.oldest(), now)))
                        .toList());

        List<VerifyGroup> groups = mapper.pendingVerificationGroups().stream()
                .map(g -> new VerifyGroup(g.label(), Boolean.TRUE.equals(g.unknownDomain()), n(g.cnt()), days(g.oldest(), now)))
                .toList();
        LocalDateTime oldestVerify = mapper.oldestPendingVerification();
        VerifyOps verifications = new VerifyOps(reviewService.pendingCount(),
                oldestVerify == null ? null : days(oldestVerify, now), groups);

        List<SourceHealth> sources = mapper.crawlSources().stream()
                .map(s -> new SourceHealth(s.id(), s.name(), sourceStatus(s, now), s.lastRunAt(), s.lastResult(), n(s.toConfirm())))
                .toList();

        LocalDateTime oldestReport = mapper.oldestPendingReport();
        ReportOps reports = new ReportOps(mapper.pendingReports(), oldestReport == null ? null : days(oldestReport, now),
                medianHours(mapper.reportHandleMinutes(now.minusDays(90))), mapper.suspendedUsers(now));
        return new Ops(applications, verifications, sources, reports);
    }

    private static String sourceStatus(DashboardMapper.SourceRow s, LocalDateTime now) {
        if (!Boolean.TRUE.equals(s.enabled())) {
            return "DISABLED";
        }
        if (s.lastRunAt() == null) {
            return "NEVER";
        }
        if (s.lastResult() != null && s.lastResult().startsWith("出错")) {
            return "ERROR";
        }
        return s.lastRunAt().isBefore(now.minusDays(STALE_DAYS)) ? "STALE" : "OK";
    }

    /** 中位数，分钟换成小时，保留一位小数 */
    static Double medianHours(List<Long> minutes) {
        List<Long> sorted = minutes.stream().filter(m -> m != null && m >= 0).sorted().toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int mid = sorted.size() / 2;
        double median = sorted.size() % 2 == 1 ? sorted.get(mid) : (sorted.get(mid - 1) + sorted.get(mid)) / 2.0;
        return Math.round(median / 6.0) / 10.0;
    }

    private static long days(LocalDateTime from, LocalDateTime now) {
        return from == null ? 0 : Math.max(0, Duration.between(from, now).toDays());
    }

    public Overview overview() {
        LocalDateTime now = LocalDateTime.now(clock);
        return new Overview(
                mapper.users(),
                mapper.verifiedUsers(now.toLocalDate()),
                mapper.usersSince(now.minusDays(7)),
                reviewService.pendingCount(),
                mapper.surveyDone(),
                toMap(mapper.teamsByStatus()),
                mapper.openSlots(),
                toMap(mapper.applicationsByStatus()),
                mapper.competitions(),
                mapper.openEditions(now));
    }

    /** 报名中的排前面（截止早的在前），已截止的排后面（最近截止的在前） */
    public List<EditionStat> editions() {
        LocalDateTime now = LocalDateTime.now(clock);
        Comparator<EditionStat> order = Comparator
                .comparing((EditionStat e) -> e.phase() == EditionPhase.CLOSED)
                .thenComparing(e -> e.phase() == EditionPhase.CLOSED ? null : e.registrationEnd(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EditionStat::registrationEnd, Comparator.nullsLast(Comparator.reverseOrder()));
        return mapper.editions(now.toLocalDate()).stream()
                .map(r -> stat(r, now))
                .sorted(order)
                .toList();
    }

    /**
     * 技能供需缺口，缺得最多的排前面。editionId 为空时汇总所有报名还没截止的届次。
     * 只列出有人需要或有人会的技能。
     */
    public List<SkillGap> skillGap(Long editionId) {
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, Long> demand = mapper.skillDemand(editionId, now).stream()
                .collect(Collectors.toMap(SkillCount::skillId, SkillCount::cnt));
        Map<Long, long[]> supply = new HashMap<>();
        for (SkillLevelCount c : mapper.skillSupply(editionId, now, now.toLocalDate())) {
            int level = Math.clamp(c.level(), 1, 3);
            supply.computeIfAbsent(c.skillId(), id -> new long[3])[level - 1] += c.cnt();
        }
        Set<Long> ids = new HashSet<>(demand.keySet());
        ids.addAll(supply.keySet());
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Skill> skills = skillMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        return ids.stream().filter(skills::containsKey).map(id -> {
                    Skill s = skills.get(id);
                    long d = demand.getOrDefault(id, 0L);
                    long[] levels = supply.getOrDefault(id, new long[3]);
                    long total = levels[0] + levels[1] + levels[2];
                    return new SkillGap(id, s.getName(), s.getCategory(), d, total,
                            List.of(levels[0], levels[1], levels[2]), d - total);
                })
                .sorted(Comparator.comparingLong(SkillGap::gap).reversed()
                        .thenComparing(Comparator.comparingLong(SkillGap::demand).reversed())
                        .thenComparing(SkillGap::skillId))
                .toList();
    }

    private static EditionStat stat(EditionRow r, LocalDateTime now) {
        return new EditionStat(r.editionId(), r.competitionName(), r.editionTitle(),
                EditionPhase.of(r.registrationStart(), r.registrationEnd(), now), r.registrationEnd(),
                nz(r.recruitingTeams()), nz(r.formedTeams()), nz(r.openSlots()), nz(r.seekers()), nz(r.followers()));
    }

    private static Map<String, Long> toMap(List<KeyCount> rows) {
        return rows.stream().collect(Collectors.toMap(KeyCount::k, KeyCount::v));
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
