package com.teammatch.school;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchoolResolverTest {

    @Test
    void recognisesEduCnEmails() {
        assertThat(SchoolResolver.isEduEmail("a@stu.example.edu.cn")).isTrue();
        assertThat(SchoolResolver.isEduEmail("A@EXAMPLE.EDU.CN")).isTrue();
        assertThat(SchoolResolver.isEduEmail("a@qq.com")).isFalse();
        assertThat(SchoolResolver.isEduEmail("a@edu.cn")).isFalse();
        assertThat(SchoolResolver.isEduEmail("a@example.edu.cn.evil.com")).isFalse();
        assertThat(SchoolResolver.isEduEmail("no-at-sign")).isFalse();
        assertThat(SchoolResolver.isEduEmail(null)).isFalse();
    }

    @Test
    void candidateDomainsPeelOffSubdomains() {
        assertThat(SchoolResolver.candidateDomains("a@mail.stu.example.edu.cn"))
                .containsExactly("mail.stu.example.edu.cn", "stu.example.edu.cn", "example.edu.cn");
        assertThat(SchoolResolver.candidateDomains("a@example.edu.cn")).containsExactly("example.edu.cn");
        assertThat(SchoolResolver.candidateDomains("a@gmail.com")).isEmpty();
    }
}
