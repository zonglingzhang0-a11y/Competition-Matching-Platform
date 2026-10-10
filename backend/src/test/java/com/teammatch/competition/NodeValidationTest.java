package com.teammatch.competition;

import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionAdminDtos.NodeForm;
import com.teammatch.competition.CompetitionNode.EndMode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeValidationTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 23, 18, 0);

    private static NodeForm node(LocalDateTime end, LocalDateTime startLatest, EndMode mode, LocalDateTime endLatest) {
        return new NodeForm("比赛", CompetitionNode.NodeType.CONTEST, T, end, false, startLatest, mode, endLatest, true, null);
    }

    @Test
    void acceptsConsistentTimes() {
        assertThatCode(() -> CompetitionAdminService.validate(node(T.plusHours(72), null, EndMode.FIXED, null), 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> CompetitionAdminService.validate(node(null, null, null, null), 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> CompetitionAdminService.validate(node(T.plusDays(20), null, EndMode.RANGE, T.plusDays(35)), 1))
                .doesNotThrowAnyException();
        assertThatCode(() -> CompetitionAdminService.validate(node(null, null, EndMode.OPEN, null), 1))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> CompetitionAdminService.validate(node(T.minusHours(1), null, EndMode.FIXED, null), 2))
                .isInstanceOf(BizException.class)
                .hasMessageStartingWith("第 2 个节点");
    }

    @Test
    void rejectsLatestStartBeforeStart() {
        assertThatThrownBy(() -> CompetitionAdminService.validate(node(null, T.minusDays(1), EndMode.FIXED, null), 1))
                .hasMessageContaining("最晚开始时间");
    }

    @Test
    void rangeNeedsBothBoundsInOrder() {
        assertThatThrownBy(() -> CompetitionAdminService.validate(node(T.plusDays(20), null, EndMode.RANGE, null), 1))
                .hasMessageContaining("最早和最晚");
        assertThatThrownBy(() -> CompetitionAdminService.validate(node(T.plusDays(20), null, EndMode.RANGE, T.plusDays(10)), 1))
                .hasMessageContaining("最晚不能早于最早");
    }
}
