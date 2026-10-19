package com.teammatch.auth;

import com.teammatch.common.BizException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleTest {

    private final MutableClock clock = new MutableClock();
    private final LoginThrottle throttle = new LoginThrottle(clock);

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            throttle.check(email);
            throttle.failed(email);
        }
    }

    @Test
    void locksAfterFiveFailuresInFifteenMinutes() {
        fail("a@x.com", 4);
        assertThatCode(() -> throttle.check("a@x.com")).as("错 4 次还能试").doesNotThrowAnyException();
        throttle.failed("a@x.com");
        assertThatThrownBy(() -> throttle.check("a@x.com"))
                .isInstanceOfSatisfying(BizException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(429);
                    assertThat(e.getMessage()).contains("15 分钟", "忘记密码");
                });
        assertThatCode(() -> throttle.check("b@x.com")).as("别的邮箱不受影响").doesNotThrowAnyException();

        clock.advance(Duration.ofMinutes(14));
        assertThatThrownBy(() -> throttle.check("a@x.com")).hasMessageContaining("1 分钟");
        clock.advance(Duration.ofMinutes(1));
        assertThatCode(() -> throttle.check("a@x.com")).as("锁过了").doesNotThrowAnyException();
        throttle.failed("a@x.com");
        assertThatCode(() -> throttle.check("a@x.com")).as("重新从 1 开始数").doesNotThrowAnyException();
    }

    @Test
    void failuresSpreadOutDoNotLock() {
        fail("a@x.com", 4);
        clock.advance(Duration.ofMinutes(16));
        fail("a@x.com", 4);
        assertThatCode(() -> throttle.check("a@x.com")).as("超过 15 分钟的不算在一起").doesNotThrowAnyException();
    }

    @Test
    void successOrResetClearsTheCount() {
        fail("a@x.com", 4);
        throttle.reset("a@x.com");
        fail("a@x.com", 4);
        assertThatCode(() -> throttle.check("a@x.com")).doesNotThrowAnyException();

        fail("a@x.com", 1);
        assertThatThrownBy(() -> throttle.check("a@x.com")).isInstanceOf(BizException.class);
        throttle.reset("a@x.com");
        assertThatCode(() -> throttle.check("a@x.com")).as("重设密码后马上能登录").doesNotThrowAnyException();
    }

    private static final class MutableClock extends Clock {

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
