package com.teammatch.competition;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EditionPhaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);

    @Test
    void phaseFollowsRegistrationWindow() {
        LocalDateTime start = NOW.minusDays(10);
        assertThat(EditionPhase.of(start, null, NOW)).isEqualTo(EditionPhase.UNKNOWN);
        assertThat(EditionPhase.of(NOW.plusDays(1), NOW.plusDays(20), NOW)).isEqualTo(EditionPhase.UPCOMING);
        assertThat(EditionPhase.of(start, NOW.plusDays(5), NOW)).isEqualTo(EditionPhase.OPEN);
        assertThat(EditionPhase.of(null, NOW.plusDays(5), NOW)).isEqualTo(EditionPhase.OPEN);
        assertThat(EditionPhase.of(start, NOW.plusDays(3), NOW)).isEqualTo(EditionPhase.CLOSING_SOON);
        assertThat(EditionPhase.of(start, NOW.plusHours(1), NOW)).isEqualTo(EditionPhase.CLOSING_SOON);
        assertThat(EditionPhase.of(start, NOW, NOW)).isEqualTo(EditionPhase.CLOSING_SOON);
        assertThat(EditionPhase.of(start, NOW.minusSeconds(1), NOW)).isEqualTo(EditionPhase.CLOSED);
    }

    @Test
    void onlyOpenPhasesAcceptRegistration() {
        assertThat(EditionPhase.OPEN.acceptsRegistration()).isTrue();
        assertThat(EditionPhase.CLOSING_SOON.acceptsRegistration()).isTrue();
        assertThat(EditionPhase.UPCOMING.acceptsRegistration()).isFalse();
        assertThat(EditionPhase.CLOSED.acceptsRegistration()).isFalse();
        assertThat(EditionPhase.UNKNOWN.acceptsRegistration()).isFalse();
    }
}
