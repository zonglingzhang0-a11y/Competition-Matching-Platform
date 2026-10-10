package com.teammatch.match;

import com.teammatch.match.MatchConfig.Item;
import com.teammatch.match.MatchInputs.Need;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 各种打分器，都是纯函数，返回 0–1。公式见 docs/问卷草案-v2.md。
 * 返回 null 表示"没有足够的数据，这道题不计分"。
 */
final class Scorers {

    private Scorers() {
    }

    /** 相容矩阵：查表 */
    static Double single(Item item, String a, String b) {
        int i = item.options().indexOf(a);
        int j = item.options().indexOf(b);
        return i < 0 || j < 0 ? null : item.matrix().get(i).get(j);
    }

    /** 数值越接近越好，差 tolerance 以上记 0 */
    static double slider(double a, double b, double tolerance) {
        return Math.max(0, 1 - Math.abs(a - b) / tolerance);
    }

    /** 标签重合度（Jaccard）：交集 / 并集 */
    static Double jaccard(Collection<String> a, Collection<String> b) {
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        if (union.isEmpty()) {
            return null;
        }
        Set<String> inter = new HashSet<>(a);
        inter.retainAll(b);
        return (double) inter.size() / union.size();
    }

    /** 排序题：两人都选了的项，名次越接近得分越高。s = Σ (1 − |名次差| / k) ÷ k */
    static double ranking(List<String> a, List<String> b, int k) {
        double sum = 0;
        for (int i = 0; i < a.size(); i++) {
            int j = b.indexOf(a.get(i));
            if (j >= 0) {
                sum += 1 - (double) Math.abs(i - j) / k;
            }
        }
        return Math.min(1, sum / k);
    }

    /** 能接受的规则有交集就算谈得拢 */
    static double setIntersect(Collection<String> a, Collection<String> b, double hit, double miss) {
        for (String x : a) {
            if (b.contains(x)) {
                return hit;
            }
        }
        return miss;
    }

    /**
     * 时段重合：s = Σ 共同时段的权重 ÷ min(双方各自的权重和)。例会时段权重更高。
     */
    static Double slotOverlap(int a, int b, int meetingSlots, double meetingWeight) {
        double wa = slotWeight(a, meetingSlots, meetingWeight);
        double wb = slotWeight(b, meetingSlots, meetingWeight);
        double min = Math.min(wa, wb);
        return min == 0 ? null : slotWeight(a & b, meetingSlots, meetingWeight) / min;
    }

    private static double slotWeight(int bits, int meetingSlots, double meetingWeight) {
        return Integer.bitCount(bits & ~meetingSlots) + meetingWeight * Integer.bitCount(bits & meetingSlots);
    }

    /** 技能能补上一个需求的多少：每项要求按 min(实际, 要求) / 要求 算，再取平均 */
    static double coverage(Map<Long, Integer> skills, Need need) {
        double sum = 0;
        for (Map.Entry<Long, Integer> req : need.skills().entrySet()) {
            sum += Math.min(skills.getOrDefault(req.getKey(), 0), req.getValue()) / (double) req.getValue();
        }
        return sum / need.skills().size();
    }

    /** 想做的工作是不是队伍在招的：第 1 意愿命中得 rankScores[0]，第 2 意愿得 rankScores[1] */
    static double roleComplement(List<String> wishes, Set<String> openRoles, List<Double> rankScores) {
        for (int i = 0; i < wishes.size() && i < rankScores.size(); i++) {
            if (openRoles.contains(wishes.get(i))) {
                return rankScores.get(i);
            }
        }
        return 0;
    }

    /**
     * 团队角色是否分散：s = base + perNewRole × 新增角色数 + perDuplicateScarce × 重复的稀缺角色数，限制在 [0, 1]。
     * perDuplicateScarce 在配置里是负数。
     */
    static double roleDiversity(List<String> mine, Set<String> teamRoles, Item item) {
        int fresh = 0;
        int dupScarce = 0;
        for (String role : mine) {
            if (!teamRoles.contains(role)) {
                fresh++;
            } else if (item.scarce().contains(role)) {
                dupScarce++;
            }
        }
        double s = item.base() + item.perNewRole() * fresh + item.perDuplicateScarce() * dupScarce;
        return Math.max(0, Math.min(1, s));
    }
}
