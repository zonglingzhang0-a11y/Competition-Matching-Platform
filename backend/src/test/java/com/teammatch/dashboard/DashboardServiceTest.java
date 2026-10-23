package com.teammatch.dashboard;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 看板"运营"里的处理时长：中位数，分钟换成小时 */
class DashboardServiceTest {

    @Test
    void 奇数个取中间那个_偶数个取中间两个的平均() {
        assertThat(DashboardService.medianHours(List.of(600L, 60L, 120L))).isEqualTo(2.0);
        assertThat(DashboardService.medianHours(List.of(60L, 90L, 120L, 6000L))).isEqualTo(1.8);
    }

    @Test
    void 没有记录时为空_负数和空值不算() {
        assertThat(DashboardService.medianHours(List.of())).isNull();
        assertThat(DashboardService.medianHours(Arrays.asList(null, -5L, 30L))).isEqualTo(0.5);
    }
}
