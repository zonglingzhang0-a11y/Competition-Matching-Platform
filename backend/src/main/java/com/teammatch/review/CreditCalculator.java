package com.teammatch.review;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 互评打分。纯计算，不查数据库，规则见 docs/问卷草案-v2.md 的"赛后互评"。
 * <ul>
 *   <li>每项行为按频率打 0–3（从未 / 偶尔 / 有时 / 经常），前三项（失联、不按时交付、敷衍）算两倍分量。</li>
 *   <li>一个评分人给出的分 = 100 × (1 − 加权频率 / 加权满分)，全是"从未"就是 100。</li>
 *   <li>本轮得分 = 各评分人的分按权重平均；自评不算进去。</li>
 *   <li>异常检测：被评人有 3 个及以上评分人时，某个评分人在至少 4 项上和其他人的平均值差 2 档以上，
 *       这个人的权重降到 0.5 并标记给管理员。只有两个评分人时分不清谁偏了，不做检测。</li>
 * </ul>
 */
public final class CreditCalculator {

    /** 行为清单，顺序和 answers 数组一致 */
    public static final List<String> ITEMS = List.of(
            "联系不上、长时间不回消息",
            "没按约定时间交付",
            "交的东西敷衍凑数",
            "讨论时不发言",
            "开会时在做别的事",
            "不打招呼就改别人负责的部分");

    public static final List<String> FREQUENCIES = List.of("从未", "偶尔", "有时", "经常");

    /** 前三项是"搭便车"的硬伤，分量加倍 */
    static final int[] WEIGHTS = {2, 2, 2, 1, 1, 1};

    static final double FLAGGED_WEIGHT = 0.5;
    private static final int MIN_RATERS_FOR_ANOMALY = 3;
    private static final int ANOMALY_GAP = 2;
    private static final int ANOMALY_ITEMS = 4;

    private CreditCalculator() {
    }

    /** 一条评分：rater 给 ratee 打的 6 项频率 */
    public record Rating(long raterId, long rateeId, List<Integer> answers) {
    }

    /**
     * 一轮的结果。
     *
     * @param scores  被评人 → 本轮得分（0–100）；没人评的不在里面
     * @param flagged 被判为异常、权重降低的评分（rater, ratee）
     */
    public record Result(Map<Long, Double> scores, Set<Pair> flagged) {
    }

    public record Pair(long raterId, long rateeId) {
    }

    /** 检查一份答案：6 项、每项 0–3 */
    public static boolean valid(List<Integer> answers) {
        return answers != null && answers.size() == ITEMS.size()
                && answers.stream().allMatch(v -> v != null && v >= 0 && v <= 3);
    }

    /** 一个评分人给出的分 */
    public static double raterScore(List<Integer> answers) {
        double penalty = 0;
        double max = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            penalty += WEIGHTS[i] * answers.get(i);
            max += WEIGHTS[i] * 3;
        }
        return 100 * (1 - penalty / max);
    }

    public static Result evaluate(List<Rating> ratings) {
        Map<Long, List<Rating>> byRatee = ratings.stream()
                .filter(r -> r.raterId() != r.rateeId())
                .collect(Collectors.groupingBy(Rating::rateeId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, Double> scores = new LinkedHashMap<>();
        Set<Pair> flagged = new HashSet<>();
        for (Map.Entry<Long, List<Rating>> e : byRatee.entrySet()) {
            List<Rating> raters = e.getValue();
            Map<Long, Double> weights = new HashMap<>();
            for (Rating r : raters) {
                boolean odd = raters.size() >= MIN_RATERS_FOR_ANOMALY && isOutlier(r, raters);
                if (odd) {
                    flagged.add(new Pair(r.raterId(), r.rateeId()));
                }
                weights.put(r.raterId(), odd ? FLAGGED_WEIGHT : 1.0);
            }
            double sum = 0;
            double weightSum = 0;
            for (Rating r : raters) {
                double w = weights.get(r.raterId());
                sum += w * raterScore(r.answers());
                weightSum += w;
            }
            scores.put(e.getKey(), round(sum / weightSum));
        }
        return new Result(scores, flagged);
    }

    /**
     * 每项的平均频率（0–3），自评不算；有权重时按权重平均。没人评返回 null。
     */
    public static List<Double> itemAverages(List<Rating> ratingsOfOne, Map<Pair, Double> weights) {
        List<Rating> others = ratingsOfOne.stream().filter(r -> r.raterId() != r.rateeId()).toList();
        if (others.isEmpty()) {
            return null;
        }
        List<Double> result = new ArrayList<>();
        for (int i = 0; i < ITEMS.size(); i++) {
            double sum = 0;
            double weightSum = 0;
            for (Rating r : others) {
                double w = weights.getOrDefault(new Pair(r.raterId(), r.rateeId()), 1.0);
                sum += w * r.answers().get(i);
                weightSum += w;
            }
            result.add(round(sum / weightSum));
        }
        return result;
    }

    /** 失联或不按时交付被至少一半的评分人评为"经常" */
    public static boolean severe(List<Rating> ratingsOfOne) {
        List<Rating> others = ratingsOfOne.stream().filter(r -> r.raterId() != r.rateeId()).toList();
        if (others.isEmpty()) {
            return false;
        }
        long hits = others.stream().filter(r -> r.answers().get(0) == 3 || r.answers().get(1) == 3).count();
        return hits * 2 >= others.size();
    }

    private static boolean isOutlier(Rating r, List<Rating> raters) {
        int gaps = 0;
        for (int i = 0; i < ITEMS.size(); i++) {
            double sum = 0;
            int n = 0;
            for (Rating o : raters) {
                if (o.raterId() != r.raterId()) {
                    sum += o.answers().get(i);
                    n++;
                }
            }
            if (Math.abs(r.answers().get(i) - sum / n) >= ANOMALY_GAP) {
                gaps++;
            }
        }
        return gaps >= ANOMALY_ITEMS;
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
