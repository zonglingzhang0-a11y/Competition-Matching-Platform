package com.teammatch.code;

import com.teammatch.common.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeSendLimiterTest {

    private final MutableClock clock = new MutableClock();
    private final CodeSendLimiter limiter = new CodeSendLimiter(clock);

    private void send(String ip, int times) {
        for (int i = 0; i < times; i++) {
            limiter.acquire(ip);
        }
    }

    @Test
    void sameIpLimitedPerHour() {
        send("1.1.1.1", CodeSendLimiter.IP_PER_HOUR);
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(429);
                    assertThat(e.getMessage()).contains("一小时后");
                });
        assertThatCode(() -> limiter.acquire("2.2.2.2")).as("别的 IP 不受影响").doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> limiter.acquire("1.1.1.1")).as("过了一小时").doesNotThrowAnyException();
    }

    @Test
    void sameIpLimitedPer24Hours() {
        // 每小时发满，第 4 个小时就到了 24 小时 100 封的上限
        for (int hour = 0; hour < 3; hour++) {
            send("1.1.1.1", CodeSendLimiter.IP_PER_HOUR);
            clock.advance(Duration.ofMinutes(61));
        }
        send("1.1.1.1", CodeSendLimiter.IP_PER_DAY - 3 * CodeSendLimiter.IP_PER_HOUR);
        assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).hasMessageContaining("明天");

        clock.advance(Duration.ofHours(21));
        assertThatCode(() -> limiter.acquire("1.1.1.1")).as("最早那批已经超过 24 小时").doesNotThrowAnyException();
    }

    @Test
    void totalPerDayAcrossIps() {
        for (int i = 0; i < CodeSendLimiter.TOTAL_PER_DAY; i++) {
            limiter.acquire("10.0.0." + i);
        }
        assertThatThrownBy(() -> limiter.acquire("9.9.9.9")).hasMessageContaining("今天发出的验证码太多");
        assertThatThrownBy(() -> limiter.acquire(null)).as("拿不到 IP 也算在全站总数里")
                .isInstanceOf(BizException.class);

        // 北京时间第二天 0 点清零（时钟从 16:00 开始）
        clock.advance(Duration.ofHours(8));
        assertThatCode(() -> limiter.acquire("9.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    void rejectedAttemptsAreNotCounted() {
        send("1.1.1.1", CodeSendLimiter.IP_PER_HOUR);
        for (int i = 0; i < 50; i++) {
            assertThatThrownBy(() -> limiter.acquire("1.1.1.1")).isInstanceOf(BizException.class);
        }
        clock.advance(Duration.ofMinutes(61));
        assertThatCode(() -> send("1.1.1.1", CodeSendLimiter.IP_PER_HOUR))
                .as("被拒的那些不算数，下一个小时还能发满").doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

        /** 北京时间 16:00 */
        private Instant now = Instant.parse("2026-10-20T08:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Shanghai");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
