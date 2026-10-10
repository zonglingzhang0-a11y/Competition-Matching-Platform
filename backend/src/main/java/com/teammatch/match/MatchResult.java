package com.teammatch.match;

import java.util.List;

/**
 * 一次打分的结果。
 *
 * @param score        0–100，已经乘过承诺系数和队伍规则
 * @param k            承诺系数 0.6–1.0
 * @param completeness 数据完整度 0–1：问卷填得越全，推荐越可信
 * @param dimensions   各维度得分（0–100），没有数据的维度为空
 * @param reasons      推荐理由，最多 3 条
 * @param talkAbout    组队前最值得先聊的一点；都挺合拍时为空
 * @param warnings     需要提醒的情况：承诺系数打折的原因、协作方式冲突等
 * @param veto         一票否决的原因；不为空时 score 为 0，不进推荐列表
 */
public record MatchResult(double score, double k, double completeness, List<DimensionScore> dimensions,
                          List<String> reasons, String talkAbout, List<String> warnings, String veto) {

    public record DimensionScore(String key, String name, Double score) {
    }

    static MatchResult vetoed(String reason) {
        return new MatchResult(0, 1, 0, List.of(), List.of(), null, List.of(), reason);
    }
}
