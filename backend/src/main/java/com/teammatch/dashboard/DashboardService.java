package com.teammatch.dashboard;

import com.teammatch.competition.EditionPhase;
import com.teammatch.dashboard.DashboardMapper.EditionRow;
import com.teammatch.dashboard.DashboardMapper.KeyCount;
import com.teammatch.dashboard.DashboardMapper.SkillCount;
import com.teammatch.dashboard.DashboardMapper.SkillLevelCount;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.Skill;
import com.teammatch.verify.VerifyReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 数据看板：平台概况、各届组队情况、技能供需缺口。
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
