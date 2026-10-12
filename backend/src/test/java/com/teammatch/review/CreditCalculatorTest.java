package com.teammatch.review;

import com.teammatch.review.CreditCalculator.Pair;
import com.teammatch.review.CreditCalculator.Rating;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CreditCalculatorTest {

    private static final List<Integer> NEVER = List.of(0, 0, 0, 0, 0, 0);
    private static final List<Integer> ALWAYS = List.of(3, 3, 3, 3, 3, 3);

    @Test
    void raterScoreWeighsFreeRidingTwice() {
        assertThat(CreditCalculator.raterScore(NEVER)).isEqualTo(100);
        assertThat(CreditCalculator.raterScore(ALWAYS)).isEqualTo(0);
        // 满分 = 3 × (2+2+2+1+1+1) = 27；"失联"经常扣 6 分，"不发言"经常扣 3 分
        assertThat(CreditCalculator.raterScore(List.of(3, 0, 0, 0, 0, 0))).isCloseTo(100 * (1 - 6 / 27.0), within());
        assertThat(CreditCalculator.raterScore(List.of(0, 0, 0, 3, 0, 0))).isCloseTo(100 * (1 - 3 / 27.0), within());
    }

    @Test
    void selfReviewDoesNotCount() {
        var result = CreditCalculator.evaluate(List.of(
                new Rating(1, 1, NEVER),
                new Rating(2, 1, List.of(1, 1, 1, 1, 1, 1))));
        assertThat(result.scores().get(1L)).isCloseTo(100 * (1 - 9 / 27.0), within());
        assertThat(result.scores()).doesNotContainKey(2L);
    }

    @Test
    void outlierIsDownWeightedOnlyWithThreeOrMoreRaters() {
        // 三个人都说 5 号表现好，一个人全打"经常"
        List<Rating> four = List.of(
                new Rating(1, 5, NEVER), new Rating(2, 5, NEVER), new Rating(3, 5, NEVER), new Rating(4, 5, ALWAYS));
        var result = CreditCalculator.evaluate(four);
        assertThat(result.flagged()).containsExactly(new Pair(4, 5));
        // (100 + 100 + 100 + 0.5 × 0) / 3.5
        assertThat(result.scores().get(5L)).isCloseTo(300 / 3.5, within());

        // 只有两个评分人时分不清谁偏了，照常平均
        var two = CreditCalculator.evaluate(List.of(new Rating(1, 5, NEVER), new Rating(4, 5, ALWAYS)));
        assertThat(two.flagged()).isEmpty();
        assertThat(two.scores().get(5L)).isEqualTo(50);
    }

    @Test
    void smallDisagreementIsNotAnOutlier() {
        var result = CreditCalculator.evaluate(List.of(
                new Rating(1, 5, NEVER), new Rating(2, 5, NEVER), new Rating(3, 5, List.of(2, 2, 2, 1, 0, 0))));
        assertThat(result.flagged()).isEmpty();
    }

    @Test
    void severeWhenHalfTheRatersSayAlwaysMissingOrLate() {
        assertThat(CreditCalculator.severe(List.of(
                new Rating(1, 5, List.of(3, 0, 0, 0, 0, 0)), new Rating(2, 5, NEVER)))).isTrue();
        assertThat(CreditCalculator.severe(List.of(
                new Rating(1, 5, List.of(0, 3, 0, 0, 0, 0)), new Rating(2, 5, NEVER), new Rating(3, 5, NEVER)))).isFalse();
        // "不发言"经常不算严重
        assertThat(CreditCalculator.severe(List.of(new Rating(1, 5, List.of(0, 0, 0, 3, 3, 0))))).isFalse();
        // 自评不算
        assertThat(CreditCalculator.severe(List.of(new Rating(5, 5, ALWAYS), new Rating(1, 5, NEVER)))).isFalse();
    }

    @Test
    void itemAveragesUseWeightsAndSkipSelf() {
        var avg = CreditCalculator.itemAverages(List.of(
                new Rating(5, 5, ALWAYS),
                new Rating(1, 5, List.of(2, 0, 0, 0, 0, 0)),
                new Rating(2, 5, List.of(0, 0, 0, 0, 0, 0))), Map.of(new Pair(2, 5), 0.5));
        // 第一项：(1 × 2 + 0.5 × 0) / 1.5
        assertThat(avg.get(0)).isCloseTo(2 / 1.5, within());
        assertThat(avg.get(5)).isEqualTo(0);
        assertThat(CreditCalculator.itemAverages(List.of(new Rating(5, 5, NEVER)), Map.of())).isNull();
    }

    @Test
    void validatesAnswers() {
        assertThat(CreditCalculator.valid(NEVER)).isTrue();
        assertThat(CreditCalculator.valid(List.of(0, 0, 0))).isFalse();
        assertThat(CreditCalculator.valid(List.of(0, 0, 0, 0, 0, 4))).isFalse();
        assertThat(CreditCalculator.valid(null)).isFalse();
    }

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.01);
    }
}
