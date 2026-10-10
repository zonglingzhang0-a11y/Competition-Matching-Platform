package com.teammatch.verify;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class EduVerifyServiceTest {

    @Test
    void verificationLastsUntilEndOfAcademicYear() {
        assertThat(EduVerifyService.academicYearEnd(LocalDate.of(2026, 10, 10))).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(EduVerifyService.academicYearEnd(LocalDate.of(2026, 9, 1))).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(EduVerifyService.academicYearEnd(LocalDate.of(2027, 3, 1))).isEqualTo(LocalDate.of(2027, 8, 31));
        assertThat(EduVerifyService.academicYearEnd(LocalDate.of(2027, 8, 31))).isEqualTo(LocalDate.of(2027, 8, 31));
    }
}
