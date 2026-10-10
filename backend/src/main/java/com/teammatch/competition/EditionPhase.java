package com.teammatch.competition;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 届次当前处在报名的哪个阶段，由报名起止时间和当前时间算出来，不入库。
 */
public enum EditionPhase {

    /** 报名时间还没公布 */
    UNKNOWN,
    /** 还没开始报名 */
    UPCOMING,
    /** 报名中 */
    OPEN,
    /** 报名中，3 天内截止 */
    CLOSING_SOON,
    /** 报名已截止 */
    CLOSED;

    static final Duration CLOSING_SOON_WINDOW = Duration.ofDays(3);

    public static EditionPhase of(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (end == null) {
            return UNKNOWN;
        }
        if (now.isAfter(end)) {
            return CLOSED;
        }
        if (start != null && now.isBefore(start)) {
            return UPCOMING;
        }
        return Duration.between(now, end).compareTo(CLOSING_SOON_WINDOW) <= 0 ? CLOSING_SOON : OPEN;
    }

    public boolean acceptsRegistration() {
        return this == OPEN || this == CLOSING_SOON;
    }
}
