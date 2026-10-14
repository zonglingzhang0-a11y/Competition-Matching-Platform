package com.teammatch.notification;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PushTest {

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void ticketWorksOnceAndExpires() {
        MutableClock clock = new MutableClock();
        PushTickets tickets = new PushTickets(clock);

        String t1 = tickets.issue(7, "token-a");
        assertThat(tickets.consume(t1)).hasValueSatisfying(t -> {
            assertThat(t.userId()).isEqualTo(7);
            assertThat(t.token()).isEqualTo("token-a");
        });
        assertThat(tickets.consume(t1)).as("用过一次就作废").isEmpty();

        String t2 = tickets.issue(7, "token-a");
        clock.advance(PushTickets.TTL.toMillis() + 1);
        assertThat(tickets.consume(t2)).as("过期了").isEmpty();

        assertThat(tickets.consume("nope")).isEmpty();
        assertThat(tickets.consume(null)).isEmpty();
        assertThat(tickets.issue(1, "x")).hasSize(48).isNotEqualTo(tickets.issue(1, "x"));
    }

    @Test
    void pushWaitsForCommitAndSkipsRollback() {
        List<String> pushed = new ArrayList<>();

        // 提交：提交前不推，提交后才推
        TransactionSynchronizationManager.initSynchronization();
        NotificationService.afterCommit(() -> pushed.add("a"));
        assertThat(pushed).isEmpty();
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(pushed).containsExactly("a");
        TransactionSynchronizationManager.clearSynchronization();

        // 回滚：只会走 afterCompletion，不会推
        TransactionSynchronizationManager.initSynchronization();
        NotificationService.afterCommit(() -> pushed.add("b"));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        assertThat(pushed).containsExactly("a");

        // 没有事务（比如定时任务里）：马上推
        NotificationService.afterCommit(() -> pushed.add("c"));
        assertThat(pushed).containsExactly("a", "c");
    }

    @Test
    void pushFailureDoesNotBreakBusiness() {
        NotificationService.afterCommit(() -> {
            throw new IllegalStateException("连接断了");
        });
        // 没有抛出来就对了
    }

    /** 可以往前拨的时钟 */
    private static final class MutableClock extends Clock {
        private long millis = Instant.parse("2026-10-15T08:00:00Z").toEpochMilli();

        void advance(long ms) {
            millis += ms;
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
            return Instant.ofEpochMilli(millis);
        }
    }
}
