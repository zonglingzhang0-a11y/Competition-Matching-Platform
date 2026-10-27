package com.teammatch.match;

import com.teammatch.match.MatchConfig.Dimension;
import com.teammatch.match.MatchConfig.HardFilter;
import com.teammatch.match.MatchConfig.Item;
import com.teammatch.match.MatchConfig.TeamRule;
import com.teammatch.match.MatchInputs.Facts;
import com.teammatch.match.MatchInputs.Labels;
import com.teammatch.match.MatchInputs.Need;
import com.teammatch.match.MatchInputs.Person;
import com.teammatch.match.MatchInputs.TeamContext;
import com.teammatch.match.MatchResult.DimensionScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 匹配引擎：算一个人和一支队伍有多合适，并写出推荐理由。
 * <ol>
 *   <li>一票否决：撞期、被暂停推荐、目标差得太远</li>
 *   <li>逐题打分：【两人】题和每个队员比再取平均（有的只和队长比），【团队】题看加入后的队伍结构</li>
 *   <li>两级加权：题目分 → 维度分 → 总分；没数据的题和维度不计，权重按比例分给其他的</li>
 *   <li>乘上承诺系数 K 和队伍规则（只能线上 vs 线下为主等）</li>
 *   <li>得分最高的几题写成推荐理由，得分最低的维度写成"组队前先聊聊"</li>
 * </ol>
 * 不查数据库、不依赖 Spring，方便单元测试。
 */
public final class MatchEngine {

    /** 推荐理由只挑得分不低于这个的题 */
    private static final double GOOD = 0.7;
    /** 维度分低于这个，就提示"组队前先聊聊" */
    private static final double CONCERN = 0.6;
    /** 没有数据时的中间分 */
    private static final double NEUTRAL = 0.5;
    /** 比赛期间有个人日程时乘的系数 */
    private static final double PERSONAL_CONFLICT_FACTOR = 0.9;

    private final MatchConfig config;
    private final Labels labels;

    public MatchEngine(MatchConfig config, Labels labels) {
        this.config = config;
        this.labels = labels;
    }

    /**
     * @param candidateView true：给候选人本人看（"你 / 队伍"）；false：给队长看（"TA / 你们队"）
     */
    public MatchResult score(Person cand, TeamContext team, Facts facts, boolean candidateView) {
        Words w = candidateView ? new Words("你", "队伍") : new Words("TA", "你们队");
        if (facts.suspended()) {
            return MatchResult.vetoed("暂时不在推荐范围内");
        }
        if (facts.timeConflictWith() != null) {
            return MatchResult.vetoed("比赛时间和" + w.who + "已经参加的「" + facts.timeConflictWith() + "」重叠");
        }
        String veto = answerPairVeto(cand, team, w);
        if (veto != null) {
            return MatchResult.vetoed(veto);
        }

        List<Scored> all = new ArrayList<>();
        List<DimensionScore> dims = new ArrayList<>();
        double weighted = 0;
        double weightSum = 0;
        double completeness = 0;
        double allDimWeight = 0;
        Map<String, Double> dimScores = new LinkedHashMap<>();
        for (Dimension d : config.dimensions()) {
            allDimWeight += d.weight();
            double itemWeighted = 0;
            double itemWeightSum = 0;
            double itemWeightAll = 0;
            for (Item item : d.items()) {
                itemWeightAll += item.weight();
                Scored s = scoreItem(d, item, cand, team, w);
                if (s != null) {
                    all.add(s);
                    itemWeighted += item.weight() * s.score;
                    itemWeightSum += item.weight();
                }
            }
            if (itemWeightSum == 0) {
                dims.add(new DimensionScore(d.key(), d.name(), null));
                continue;
            }
            double ds = itemWeighted / itemWeightSum;
            dimScores.put(d.key(), ds);
            dims.add(new DimensionScore(d.key(), d.name(), round(ds * 100)));
            weighted += d.weight() * ds;
            weightSum += d.weight();
            completeness += d.weight() * itemWeightSum / itemWeightAll;
        }
        double raw = weightSum == 0 ? NEUTRAL : weighted / weightSum;
        double comp = allDimWeight == 0 ? 0 : completeness / allDimWeight;
        // 按完整度往中间收：数据越少越靠近 50 分，避免只填了一两题的人凭一题高分排到前面
        double base = NEUTRAL + (raw - NEUTRAL) * comp;

        List<String> warnings = new ArrayList<>();
        double k = commitment(cand, team, w, warnings);
        double factor = teamRules(cand, team, w, warnings);
        if (facts.personalConflictWith() != null) {
            // 个人日程和比赛撞了只是提醒、稍微降分，不一票否决：个人安排通常可以调整。
            // 日程标题是私事（"医院复查""面试"），只给本人看；给队长看只说有安排
            factor *= PERSONAL_CONFLICT_FACTOR;
            warnings.add("比赛期间" + w.who + (candidateView
                    ? "有个人日程「" + facts.personalConflictWith() + "」"
                    : "有个人安排"));
        }
        if (weightSum == 0) {
            warnings.add(cand.general().isEmpty() && cand.edition().isEmpty()
                    ? w.who + "还没填问卷，分数只供参考"
                    : "还没有可以比较的问卷答案，分数只供参考");
        }

        return new MatchResult(round(100 * base * k * factor), round(k, 3), round(comp, 2), dims,
                reasons(all), talkAbout(all, dimScores), warnings, null);
    }

    // ---------- 一票否决 ----------

    private String answerPairVeto(Person cand, TeamContext team, Words w) {
        if (config.hardFilters() == null) {
            return null;
        }
        for (HardFilter f : config.hardFilters()) {
            if (!"answerPair".equals(f.type())) {
                continue;
            }
            String mine = str(cand.answer(f.q()));
            if (mine == null) {
                continue;
            }
            for (Person m : team.members()) {
                String theirs = str(m.answer(f.q()));
                for (List<String> pair : f.pairs()) {
                    if (theirs != null && (pair.equals(List.of(mine, theirs)) || pair.equals(List.of(theirs, mine)))) {
                        return "目标差得太远：" + w.who + "想「" + labels.option(f.q(), mine) + "」，"
                                + w.team + "里有人想「" + labels.option(f.q(), theirs) + "」";
                    }
                }
            }
        }
        return null;
    }

    // ---------- 逐题打分 ----------

    private Scored scoreItem(Dimension d, Item item, Person c, TeamContext t, Words w) {
        return switch (item.scorer()) {
            case "single" -> single(d, item, c, t, w);
            case "slider" -> slider(d, item, c, t, w);
            case "tags" -> tags(d, item, c, t);
            case "ranking" -> ranking(d, item, c, t, w);
            case "setIntersect" -> setIntersect(d, item, c, t);
            case "slotOverlap" -> slots(d, item, c, t);
            case "skillComplement" -> skills(d, item, c, t);
            case "roleComplement" -> roles(d, item, c, t, w);
            case "roleDiversity" -> diversity(d, item, c, t, w);
            case "mentorResource" -> mentor(d, item, c, t);
            default -> null;
        };
    }

    /** 【两人】题和谁比：一般是全体队员，有的题只和队长比 */
    private List<Person> peers(Item item, TeamContext t) {
        boolean captainOnly = "captain".equals(item.compareWith())
                || (config.aggregate() != null && config.aggregate().captainOnly() != null
                && config.aggregate().captainOnly().contains(item.q()));
        if (captainOnly) {
            Person captain = t.captain();
            return captain == null ? List.of() : List.of(captain);
        }
        return t.members();
    }

    private Scored single(Dimension d, Item item, Person c, TeamContext t, Words w) {
        String mine = str(c.answer(item.q()));
        if (mine == null) {
            return null;
        }
        List<String> theirs = peers(item, t).stream().map(p -> str(p.answer(item.q()))).filter(Objects::nonNull).toList();
        List<Double> scores = theirs.stream().map(v -> Scorers.single(item, mine, v)).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        String majority = mostCommon(theirs);
        boolean captainOnly = peers(item, t).size() == 1 && t.members().size() > 1;
        String good = switch (item.q()) {
            case "P1" -> mine.equals(majority) ? "目标一致：都想「" + labels.option("P1", mine) + "」" : "目标相近";
            case "P3" -> "冲刺阶段能接受的强度差不多";
            case "G2" -> "推进任务的节奏合拍";
            case "G3" -> "作息差不多";
            case "G6" -> "希望同步进度的频率一致";
            case "G7" -> "做决定的方式和队长合得来";
            case "G8" -> "处理分歧的方式合得来";
            default -> "「" + labels.title(item.q()) + "」上合拍";
        };
        String concern = "「" + labels.title(item.q()) + "」" + w.who + "选了「" + labels.option(item.q(), mine) + "」，"
                + (captainOnly ? "队长" : w.team + "多数") + "选了「" + labels.option(item.q(), majority) + "」";
        return new Scored(d, item, mean(scores), good, concern);
    }

    private Scored slider(Dimension d, Item item, Person c, TeamContext t, Words w) {
        Double mine = num(c.answer(item.q()));
        if (mine == null) {
            return null;
        }
        List<Double> theirs = peers(item, t).stream().map(p -> num(p.answer(item.q()))).filter(Objects::nonNull).toList();
        if (theirs.isEmpty()) {
            return null;
        }
        double s = mean(theirs.stream().map(v -> Scorers.slider(mine, v, item.tolerance())).toList());
        var penalty = item.slowPenalty();
        if (penalty != null && penalty.whenTeamGoal().contains(teamGoal(t)) && mine > penalty.overHours()) {
            s *= penalty.factor();
        }
        String avg = fmt(mean(theirs));
        String m = fmt(mine);
        return switch (item.q()) {
            case "P2" -> new Scored(d, item, s, "每周投入时间相近（" + w.who + " " + m + " 小时，" + w.team + "平均 " + avg + " 小时）",
                    "每周投入时间差得多（" + w.who + " " + m + " 小时，" + w.team + "平均 " + avg + " 小时）");
            case "G5" -> new Scored(d, item, s, "回消息的速度差不多",
                    "回消息的速度差得多（" + w.who + "一般 " + m + " 小时内回，" + w.team + "平均 " + avg + " 小时）");
            default -> new Scored(d, item, s, "「" + labels.title(item.q()) + "」上差不多",
                    "「" + labels.title(item.q()) + "」差得多（" + w.who + " " + m + "，" + w.team + "平均 " + avg + "）");
        };
    }

    private Scored tags(Dimension d, Item item, Person c, TeamContext t) {
        List<String> mine = list(c.answer(item.q()));
        if (mine.isEmpty()) {
            return null;
        }
        List<Double> scores = peers(item, t).stream().map(p -> list(p.answer(item.q())))
                .filter(l -> !l.isEmpty()).map(l -> Scorers.jaccard(mine, l)).filter(Objects::nonNull).toList();
        if (scores.isEmpty()) {
            return null;
        }
        return new Scored(d, item, mean(scores), "参赛动机相近", "参赛动机不太一样，先聊聊各自想从比赛里得到什么");
    }

    private Scored ranking(Dimension d, Item item, Person c, TeamContext t, Words w) {
        List<String> mine = list(c.answer(item.q()));
        if (mine.isEmpty()) {
            return null;
        }
        List<List<String>> theirs = peers(item, t).stream().map(p -> list(p.answer(item.q()))).filter(l -> !l.isEmpty()).toList();
        if (theirs.isEmpty()) {
            return null;
        }
        double s = mean(theirs.stream().map(l -> Scorers.ranking(mine, l, item.k() == null ? 3 : item.k())).toList());
        String shared = mine.stream().filter(x -> theirs.stream().anyMatch(l -> l.contains(x))).findFirst().orElse(null);
        String good = shared == null ? "最在意的队友底线一致"
                : "最在意的队友底线一致：都很在意「" + labels.option(item.q(), shared) + "」";
        return new Scored(d, item, s, good, "最在意的队友底线不太一样，先说清楚彼此不能接受什么");
    }

    private Scored setIntersect(Dimension d, Item item, Person c, TeamContext t) {
        List<String> mine = list(c.answer(item.q()));
        if (mine.isEmpty()) {
            return null;
        }
        List<Double> scores = peers(item, t).stream().map(p -> list(p.answer(item.q()))).filter(l -> !l.isEmpty())
                .map(l -> Scorers.setIntersect(mine, l, item.hit(), item.miss())).toList();
        if (scores.isEmpty()) {
            return null;
        }
        String good = "P6".equals(item.q()) ? "能接受的署名规则有交集" : "「" + labels.title(item.q()) + "」能谈拢";
        String concern = item.missReason() != null ? item.missReason() : "「" + labels.title(item.q()) + "」可能谈不拢";
        return new Scored(d, item, mean(scores), good, concern);
    }

    private Scored slots(Dimension d, Item item, Person c, TeamContext t) {
        Double mine = num(c.answer(item.q()));
        List<Double> theirs = t.members().stream().map(p -> num(p.answer(item.q()))).filter(Objects::nonNull).toList();
        if (mine == null || theirs.isEmpty()) {
            return null;
        }
        int common = theirs.stream().mapToInt(Double::intValue).reduce(~0, (a, b) -> a & b);
        double meetingWeight = item.meetingSlotWeight() == null ? 1 : item.meetingSlotWeight();
        Double s = Scorers.slotOverlap(mine.intValue(), common, t.meetingSlots(), meetingWeight);
        if (s == null) {
            return null;
        }
        int n = Integer.bitCount(mine.intValue() & common);
        return new Scored(d, item, s, "每周有 " + n + " 个大家都有空的时段",
                n == 0 ? "每周找不到大家都有空的时段" : "大家都有空的时段很少，每周只有 " + n + " 段");
    }

    private Scored skills(Dimension d, Item item, Person c, TeamContext t) {
        List<Need> needs = t.openNeeds().stream().filter(n -> !n.skills().isEmpty()).toList();
        if (needs.isEmpty() || c.skills().isEmpty()) {
            return null;
        }
        Need best = needs.stream().max(Comparator.comparingDouble(n -> Scorers.coverage(c.skills(), n))).orElseThrow();
        double s = Scorers.coverage(c.skills(), best);
        List<String> covered = best.skills().entrySet().stream()
                .filter(e -> c.skills().getOrDefault(e.getKey(), 0) >= e.getValue())
                .map(e -> labels.skill(e.getKey())).toList();
        String good = covered.isEmpty() ? "技能和「" + best.roleName() + "」的要求比较接近"
                : "能补上「" + best.roleName() + "」需要的 " + String.join("、", covered);
        return new Scored(d, item, s, good, "技能和在招的方向对不上，先聊聊能负责哪一块");
    }

    private Scored roles(Dimension d, Item item, Person c, TeamContext t, Words w) {
        List<String> wishes = list(c.answer(item.q()));
        if (wishes.isEmpty() || t.openNeeds().isEmpty()) {
            return null;
        }
        Set<String> open = t.openNeeds().stream().map(Need::roleName).collect(Collectors.toSet());
        double s = Scorers.roleComplement(wishes, open, item.rankScores());
        String hit = wishes.stream().filter(open::contains).findFirst().orElse(null);
        String good = hit == null ? null : w.who + "想做的「" + hit + "」正是" + w.team + "在招的";
        return new Scored(d, item, s, good, w.who + "想做的工作" + w.team + "暂时不缺，先聊聊分工");
    }

    private Scored diversity(Dimension d, Item item, Person c, TeamContext t, Words w) {
        List<String> mine = list(c.answer(item.q()));
        if (mine.isEmpty()) {
            return null;
        }
        Set<String> teamRoles = new HashSet<>();
        t.members().forEach(m -> teamRoles.addAll(list(m.answer(item.q()))));
        double s = Scorers.roleDiversity(mine, teamRoles, item);
        List<String> fresh = mine.stream().filter(r -> !teamRoles.contains(r)).map(r -> labels.option(item.q(), r)).toList();
        List<String> clash = mine.stream().filter(r -> teamRoles.contains(r) && item.scarce().contains(r))
                .map(r -> labels.option(item.q(), r)).toList();
        String good = fresh.isEmpty() ? null : w.who + "常扮演的「" + fresh.getFirst() + "」" + w.team + "里还没有";
        String concern = !clash.isEmpty() ? w.team + "里已经有人「" + clash.getFirst() + "」，先说好谁来拿主意"
                : "团队角色和" + w.team + "现有成员重合";
        return new Scored(d, item, s, good, concern);
    }

    private Scored mentor(Dimension d, Item item, Person c, TeamContext t) {
        // 候选人自己没填、也没有参赛经历时，这题说明不了候选人什么，不计分
        if (str(c.answer(item.q())) == null && c.experiences() == 0) {
            return null;
        }
        List<Person> group = new ArrayList<>(t.members());
        group.add(c);
        List<Person> wanting = group.stream().filter(p -> item.wantsMentor().contains(str(p.answer(item.q())))).toList();
        List<Person> able = group.stream().filter(p -> item.canMentor().answers().contains(str(p.answer(item.q())))
                || p.experiences() >= item.canMentor().minExperiences()).toList();
        boolean matched = wanting.stream().anyMatch(x -> able.stream().anyMatch(y -> y.userId() != x.userId()));
        Map<String, Double> scores = item.scores();
        if (wanting.isEmpty()) {
            return new Scored(d, item, scores.get("noneNeeded"), null, null);
        }
        return matched
                ? new Scored(d, item, scores.get("matched"), "有人能带新人，想被带的人有着落", null)
                : new Scored(d, item, scores.get("unmet"), null, "大家都希望有人带，但还没有有经验的人");
    }

    // ---------- 承诺系数和队伍规则 ----------

    private double commitment(Person c, TeamContext t, Words w, List<String> warnings) {
        var cfg = config.commitment();
        if (cfg == null) {
            return 1;
        }
        double kh = c.credit() == null ? cfg.history().newUser()
                : cfg.history().min() + (cfg.history().max() - cfg.history().min()) * c.credit() / 100;
        if (kh < 1 && c.credit() != null) {
            warnings.add(w.who + "的历史互评信用分是 " + fmt(c.credit()));
        }
        var con = cfg.constraint();
        List<String> hits = list(c.answer(con.q())).stream().filter(con.timeTags()::contains).toList();
        double per = con.highGoals().contains(teamGoal(t)) ? con.perHitHighGoal() : con.perHitOther();
        double kc = Math.max(con.floor(), 1 - hits.size() * per);
        if (!hits.isEmpty()) {
            warnings.add(w.who + "备赛期间有" + hits.stream().map(h -> "「" + labels.option(con.q(), h) + "」")
                    .collect(Collectors.joining()) + "，投入可能受影响");
        }
        return kh * kc;
    }

    private double teamRules(Person c, TeamContext t, Words w, List<String> warnings) {
        double factor = 1;
        if (config.teamRules() == null) {
            return factor;
        }
        for (TeamRule rule : config.teamRules()) {
            List<String> mine = list(c.answer(rule.q()));
            switch (rule.type()) {
                case "onlineOnlyVsOffline" -> {
                    if (mine.contains(rule.tag()) && "OFFLINE".equals(t.meetingMode())) {
                        factor *= rule.factor() == null ? 0.7 : rule.factor();
                        warnings.add(w.who + "只能线上参与，" + w.team + "以线下为主");
                    }
                }
                case "englishRequired" -> {
                    boolean everyoneWeak = t.members().stream().allMatch(m -> list(m.answer(rule.q())).contains(rule.tag()));
                    if (t.englishRequired() && mine.contains(rule.tag()) && everyoneWeak) {
                        factor *= rule.factor() == null ? 0.85 : rule.factor();
                        warnings.add("英文赛题，加入后队里还没有英文读写没问题的人");
                    }
                }
                default -> {
                    // 不认识的规则忽略
                }
            }
        }
        return factor;
    }

    // ---------- 推荐理由 ----------

    /** 得分高、权重大的题排前面，最多 3 条 */
    private List<String> reasons(List<Scored> all) {
        return all.stream()
                .filter(s -> s.score >= GOOD && s.good != null)
                .sorted(Comparator.comparingDouble((Scored s) -> s.dim.weight() * s.item.weight() * s.score).reversed())
                .map(s -> s.good)
                .distinct()
                .limit(3)
                .toList();
    }

    /** 最弱的维度里最弱的一题；都挺合拍时为空 */
    private String talkAbout(List<Scored> all, Map<String, Double> dimScores) {
        Function<Scored, Double> dimOf = s -> dimScores.get(s.dim.key());
        return all.stream()
                .filter(s -> s.concern != null && s.score < GOOD && dimOf.apply(s) < CONCERN)
                .min(Comparator.comparingDouble((Scored s) -> dimOf.apply(s)).thenComparingDouble(s -> s.score))
                .map(s -> "组队前先聊聊：" + s.concern)
                .orElse(null);
    }

    // ---------- 小工具 ----------

    private String teamGoal(TeamContext t) {
        Person captain = t.captain();
        return captain == null ? null : str(captain.answer("P1"));
    }

    private record Scored(Dimension dim, Item item, double score, String good, String concern) {
    }

    private record Words(String who, String team) {
    }

    private static String str(Object o) {
        return o instanceof String s && !s.isBlank() ? s : null;
    }

    private static Double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    private static List<String> list(Object o) {
        if (!(o instanceof List<?> l)) {
            return List.of();
        }
        return l.stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }

    private static double mean(List<Double> xs) {
        return xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    private static String mostCommon(List<String> xs) {
        return xs.stream().collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }

    private static double round(double v) {
        return round(v, 1);
    }

    private static double round(double v, int digits) {
        double f = Math.pow(10, digits);
        return Math.round(v * f) / f;
    }
}
