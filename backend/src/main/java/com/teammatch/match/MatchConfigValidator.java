package com.teammatch.match;

import com.teammatch.common.BizException;
import com.teammatch.match.MatchConfig.Dimension;
import com.teammatch.match.MatchConfig.HardFilter;
import com.teammatch.match.MatchConfig.Item;
import com.teammatch.match.MatchConfig.MatrixOverride;
import com.teammatch.match.MatchConfig.TeamRule;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员保存匹配配置前的检查。
 * <p>
 * 引擎假定配置是完整的，缺了必填字段会直接空指针，所以这里按打分器逐个检查要用到的字段，
 * 提示写成管理员能看懂的话。
 */
public final class MatchConfigValidator {

    public static final List<String> SCORERS = List.of("single", "slider", "tags", "ranking", "setIntersect",
            "slotOverlap", "skillComplement", "roleComplement", "roleDiversity", "mentorResource");

    /** 技能互补那道"题"读的是个人档案里的技能，不在问卷里 */
    public static final String SKILLS = "SKILLS";

    private static final Set<String> HARD_FILTERS = Set.of("answerPair", "competitionTimeConflict", "editionRules",
            "platformBlock");
    private static final Set<String> TEAM_RULES = Set.of("onlineOnlyVsOffline", "englishRequired");

    private MatchConfigValidator() {
    }

    /**
     * 检查默认配置。
     *
     * @param questionCodes 当前问卷版本里的题目代码
     */
    public static void validateDefault(MatchConfig c, Set<String> questionCodes) {
        need(c.base() == null && c.dimensionWeights() == null && c.matrixOverrides() == null,
                "默认配置里不能写 extends、dimensionWeights、matrixOverrides，这几项是预设用的");
        need(c.dimensions() != null && !c.dimensions().isEmpty(), "至少要有一个维度");
        Set<String> keys = new HashSet<>();
        double total = 0;
        for (Dimension d : c.dimensions()) {
            need(d.key() != null && !d.key().isBlank(), "有个维度缺少 key");
            String where = "维度「" + (d.name() == null || d.name().isBlank() ? d.key() : d.name()) + "」";
            need(keys.add(d.key()), "维度 key「" + d.key() + "」重复了");
            need(d.name() != null && !d.name().isBlank(), where + "缺少名称");
            weight(d.weight(), where + "的权重");
            total += d.weight();
            need(d.items() != null && !d.items().isEmpty(), where + "里至少要有一道题");
            Set<String> qs = new HashSet<>();
            for (Item item : d.items()) {
                validateItem(where, item, questionCodes);
                need(qs.add(item.q()), where + "里 " + item.q() + " 出现了两次");
            }
        }
        need(total > 0, "维度权重不能全是 0");
        validateCommitment(c.commitment());
        validateHardFilters(c.hardFilters(), questionCodes);
        validateTeamRules(c.teamRules(), questionCodes);
    }

    /** 检查预设：只能改维度权重和相容矩阵里的格子，而且要对得上默认配置 */
    public static void validatePreset(MatchConfig defaults, MatchConfig preset) {
        need(preset.dimensions() == null && preset.commitment() == null && preset.hardFilters() == null
                        && preset.teamRules() == null,
                "预设只能写 dimensionWeights（维度权重）和 matrixOverrides（相容矩阵里要改的格子），其他沿用默认配置");
        Map<String, Double> weights = preset.dimensionWeights() == null ? Map.of() : preset.dimensionWeights();
        Set<String> keys = new HashSet<>();
        defaults.dimensions().forEach(d -> keys.add(d.key()));
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            need(keys.contains(e.getKey()), "默认配置里没有维度「" + e.getKey() + "」");
            need(e.getValue() != null, "维度「" + e.getKey() + "」的权重没填");
            weight(e.getValue(), "维度「" + e.getKey() + "」的权重");
        }
        double total = 0;
        for (Dimension d : defaults.dimensions()) {
            total += weights.getOrDefault(d.key(), d.weight());
        }
        need(total > 0, "维度权重不能全是 0");

        Map<String, List<MatrixOverride>> overrides = preset.matrixOverrides() == null ? Map.of() : preset.matrixOverrides();
        for (Map.Entry<String, List<MatrixOverride>> e : overrides.entrySet()) {
            Item item = defaults.dimensions().stream().flatMap(d -> d.items().stream())
                    .filter(i -> i.q().equals(e.getKey()) && "single".equals(i.scorer()))
                    .findFirst().orElse(null);
            need(item != null, e.getKey() + " 不是用相容矩阵打分的题，不能改矩阵");
            need(e.getValue() != null, e.getKey() + " 要改的格子没填");
            for (MatrixOverride o : e.getValue()) {
                need(o != null && item.options().contains(o.row()) && item.options().contains(o.col()),
                        e.getKey() + " 的选项只有 " + String.join("、", item.options()));
                unit(o.value(), e.getKey() + " 矩阵里的分数");
            }
        }
    }

    private static void validateItem(String where, Item item, Set<String> questionCodes) {
        need(item.q() != null && !item.q().isBlank(), where + "里有道题缺少题目代码 q");
        String at = where + "里的 " + item.q();
        need(SKILLS.equals(item.q()) || questionCodes.contains(item.q()), at + "：问卷里没有这道题");
        need(item.scorer() != null && SCORERS.contains(item.scorer()),
                at + "：打分方式只能是 " + String.join("、", SCORERS));
        weight(item.weight(), at + " 的权重");
        switch (item.scorer()) {
            case "single" -> {
                List<String> options = item.options();
                need(options != null && !options.isEmpty() && new HashSet<>(options).size() == options.size(),
                        at + "：要列出选项，而且不能重复");
                int n = options.size();
                need(item.matrix() != null && item.matrix().size() == n
                                && item.matrix().stream().allMatch(row -> row != null && row.size() == n),
                        at + "：相容矩阵应该是 " + n + "×" + n + " 的");
                item.matrix().forEach(row -> row.forEach(v -> unit(v, at + " 矩阵里的分数")));
                need(item.compareWith() == null || "captain".equals(item.compareWith()),
                        at + "：compareWith 只能是 captain 或不写");
            }
            case "slider" -> {
                need(item.tolerance() != null && item.tolerance() > 0, at + "：容忍差距 tolerance 要大于 0");
                if (item.slowPenalty() != null) {
                    need(item.slowPenalty().whenTeamGoal() != null, at + "：slowPenalty 要写 whenTeamGoal");
                    unit(item.slowPenalty().factor(), at + " 的 slowPenalty 系数");
                }
            }
            case "ranking" -> need(item.k() == null || item.k() >= 1, at + "：k 至少是 1");
            case "setIntersect" -> {
                need(item.hit() != null && item.miss() != null, at + "：要写 hit 和 miss 两个分数");
                unit(item.hit(), at + " 的 hit");
                unit(item.miss(), at + " 的 miss");
            }
            case "slotOverlap" -> need(item.meetingSlotWeight() == null || item.meetingSlotWeight() >= 0,
                    at + "：meetingSlotWeight 不能是负数");
            case "roleComplement" -> {
                need(item.rankScores() != null && !item.rankScores().isEmpty(), at + "：要写 rankScores");
                item.rankScores().forEach(v -> unit(v, at + " 的 rankScores"));
            }
            case "roleDiversity" -> need(item.base() != null && item.perNewRole() != null
                            && item.perDuplicateScarce() != null && item.scarce() != null,
                    at + "：要写 base、perNewRole、perDuplicateScarce、scarce");
            case "mentorResource" -> {
                need(item.canMentor() != null && item.canMentor().answers() != null && item.wantsMentor() != null,
                        at + "：要写 canMentor 和 wantsMentor");
                need(item.scores() != null && item.scores().keySet().containsAll(List.of("matched", "noneNeeded", "unmet")),
                        at + "：scores 要有 matched、noneNeeded、unmet 三项");
                item.scores().values().forEach(v -> unit(v, at + " 的 scores"));
            }
            default -> {
                // tags、skillComplement 没有额外参数
            }
        }
    }

    private static void validateCommitment(MatchConfig.Commitment c) {
        if (c == null) {
            return;
        }
        need(c.history() != null && c.constraint() != null, "承诺系数 commitment 要同时写 history 和 constraint");
        var h = c.history();
        unit(h.newUser(), "承诺系数里新用户的值");
        unit(h.min(), "承诺系数的下限");
        unit(h.max(), "承诺系数的上限");
        need(h.min() <= h.max(), "承诺系数的下限不能大于上限");
        var con = c.constraint();
        need(con.q() != null && con.timeTags() != null && con.highGoals() != null,
                "承诺系数的 constraint 要写 q、timeTags、highGoals");
        unit(con.floor(), "承诺系数的最低值 floor");
        unit(con.perHitHighGoal(), "perHitHighGoal");
        unit(con.perHitOther(), "perHitOther");
    }

    private static void validateHardFilters(List<HardFilter> filters, Set<String> questionCodes) {
        if (filters == null) {
            return;
        }
        for (HardFilter f : filters) {
            need(f != null && HARD_FILTERS.contains(f.type()), "一票否决的类型只能是 " + String.join("、", HARD_FILTERS));
            if ("answerPair".equals(f.type())) {
                need(f.q() != null && questionCodes.contains(f.q()), "一票否决 answerPair：问卷里没有题目 " + f.q());
                need(f.pairs() != null && f.pairs().stream().allMatch(p -> p != null && p.size() == 2),
                        "一票否决 answerPair：pairs 里每一组要正好两个选项");
            }
        }
    }

    private static void validateTeamRules(List<TeamRule> rules, Set<String> questionCodes) {
        if (rules == null) {
            return;
        }
        for (TeamRule r : rules) {
            need(r != null && TEAM_RULES.contains(r.type()), "队伍规则的类型只能是 " + String.join("、", TEAM_RULES));
            need(r.q() != null && questionCodes.contains(r.q()) && r.tag() != null,
                    "队伍规则 " + r.type() + " 要写问卷里有的题目 q 和选项 tag");
            if (r.factor() != null) {
                unit(r.factor(), "队伍规则 " + r.type() + " 的系数");
            }
        }
    }

    /** 权重：不小于 0 的有限数。各项权重会按比例换算，不要求加起来等于 1 */
    private static void weight(double v, String what) {
        need(Double.isFinite(v) && v >= 0 && v <= 100, what + "要在 0 到 100 之间");
    }

    /** 分数、系数：0 到 1 */
    private static void unit(Double v, String what) {
        need(v != null && Double.isFinite(v) && v >= 0 && v <= 1, what + "要在 0 到 1 之间");
    }

    private static void need(boolean ok, String message) {
        if (!ok) {
            throw new BizException(message);
        }
    }
}
