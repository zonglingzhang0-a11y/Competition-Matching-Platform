package com.teammatch.match;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 匹配打分配置，对应 match_config 表里的 JSON。说明见 docs/问卷草案-v2.md。
 * <p>
 * 预设（短期集中型、长周期型）只写和默认配置不同的地方：维度权重、相容矩阵里的个别格子，
 * 用 {@link #applyPreset} 合并成完整配置。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MatchConfig(
        Aggregate aggregate,
        List<Dimension> dimensions,
        Commitment commitment,
        List<HardFilter> hardFilters,
        List<TeamRule> teamRules,
        /* 预设专用：基于哪套配置 */
        @JsonProperty("extends") String base,
        Map<String, Double> dimensionWeights,
        Map<String, List<MatrixOverride>> matrixOverrides) {

    /** captainOnly：只和队长比的题 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Aggregate(String pairToTeam, List<String> captainOnly) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Dimension(String key, String name, double weight, List<Item> items) {
    }

    /**
     * 一道题怎么打分。不同打分器用到的字段不同，用不到的为空：
     * single 用 options、matrix、compareWith；slider 用 tolerance、slowPenalty；ranking 用 k；
     * setIntersect 用 hit、miss；roleComplement 用 rankScores；roleDiversity 用 base、perNewRole、perDuplicateScarce、scarce；
     * mentorResource 用 canMentor、wantsMentor、scores；slotOverlap 用 meetingSlotWeight。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Item(String q, String scorer, double weight,
                       List<String> options, List<List<Double>> matrix, String compareWith,
                       Double tolerance, SlowPenalty slowPenalty,
                       Integer k, Double hit, Double miss, String missReason,
                       String source, List<Double> rankScores,
                       Double base, Double perNewRole, Double perDuplicateScarce, List<String> scarce,
                       CanMentor canMentor, List<String> wantsMentor, Map<String, Double> scores,
                       Double meetingSlotWeight) {

        Item withMatrix(List<List<Double>> newMatrix) {
            return new Item(q, scorer, weight, options, newMatrix, compareWith, tolerance, slowPenalty, k, hit, miss,
                    missReason, source, rankScores, base, perNewRole, perDuplicateScarce, scarce, canMentor,
                    wantsMentor, scores, meetingSlotWeight);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SlowPenalty(List<String> whenTeamGoal, double overHours, double factor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CanMentor(List<String> answers, int minExperiences) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Commitment(History history, Constraint constraint) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record History(double newUser, double min, double max) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Constraint(String q, List<String> timeTags, List<String> highGoals, double perHitHighGoal,
                             double perHitOther, double floor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record HardFilter(String type, String q, List<List<String>> pairs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TeamRule(String type, String q, String tag, Double factor) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MatrixOverride(String row, String col, double value) {
    }

    /** 把预设合并到这套（默认）配置上，返回新的完整配置 */
    public MatchConfig applyPreset(MatchConfig preset) {
        if (preset == null) {
            return this;
        }
        Map<String, Double> weights = preset.dimensionWeights() == null ? Map.of() : preset.dimensionWeights();
        Map<String, List<MatrixOverride>> overrides = preset.matrixOverrides() == null ? Map.of() : preset.matrixOverrides();
        List<Dimension> merged = dimensions.stream().map(d -> new Dimension(d.key(), d.name(),
                weights.getOrDefault(d.key(), d.weight()),
                d.items().stream().map(item -> overrides.containsKey(item.q())
                        ? item.withMatrix(override(item, overrides.get(item.q()))) : item).toList())).toList();
        return new MatchConfig(aggregate, merged, commitment, hardFilters, teamRules, null, null, null);
    }

    private static List<List<Double>> override(Item item, List<MatrixOverride> changes) {
        List<List<Double>> copy = new ArrayList<>();
        for (List<Double> row : item.matrix()) {
            copy.add(new ArrayList<>(row));
        }
        for (MatrixOverride o : changes) {
            int r = item.options().indexOf(o.row());
            int c = item.options().indexOf(o.col());
            if (r >= 0 && c >= 0) {
                copy.get(r).set(c, o.value());
                copy.get(c).set(r, o.value());
            }
        }
        return copy;
    }
}
