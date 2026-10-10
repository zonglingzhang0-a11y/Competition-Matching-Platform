package com.teammatch.code;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.teammatch.common.BizException;
import com.teammatch.config.AppProperties;
import com.teammatch.mail.CodeMailer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailCodeServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 10, 12, 0);
    private static final String EMAIL = "a@stu.example.edu.cn";

    private EmailCodeMapper mapper;
    private CodeMailer mailer;
    private EmailCodeService service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper 要靠这份元数据把 EmailCode::getEmail 翻译成列名
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), EmailCode.class);
    }

    @BeforeEach
    void setUp() {
        mapper = mock(EmailCodeMapper.class);
        mailer = mock(CodeMailer.class);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        AppProperties props = new AppProperties(new AppProperties.Mail("log", ""), new AppProperties.Code(10, 60, 5));
        service = new EmailCodeService(mapper, mailer, props, clock);
    }

    @Test
    void sendStoresHashAndMailsSixDigitCode() {
        service.send(" A@Stu.Example.edu.cn ", CodePurpose.EDU_VERIFY);

        ArgumentCaptor<EmailCode> saved = ArgumentCaptor.forClass(EmailCode.class);
        verify(mapper).insert(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailer).sendCode(eq(EMAIL), eq(CodePurpose.EDU_VERIFY), code.capture(), eq(10));

        assertThat(code.getValue()).matches("\\d{6}");
        assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
        assertThat(saved.getValue().getCodeHash())
                .isEqualTo(EmailCodeService.hash(EMAIL, CodePurpose.EDU_VERIFY, code.getValue()))
                .doesNotContain(code.getValue());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void sendTooSoonIsRejected() {
        when(mapper.selectOne(any())).thenReturn(record("123456", NOW.minusSeconds(20), 0));

        assertThatThrownBy(() -> service.send(EMAIL, CodePurpose.EDU_VERIFY))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("40 秒后再试");
        verify(mailer, never()).sendCode(any(), any(), any(), anyInt());
    }

    @Test
    void correctCodeIsMarkedUsed() {
        EmailCode rec = record("123456", NOW.minusMinutes(1), 0);
        when(mapper.selectOne(any())).thenReturn(rec);

        service.verify(EMAIL, CodePurpose.EDU_VERIFY, " 123456 ");

        assertThat(rec.getUsedAt()).isEqualTo(NOW);
        verify(mapper).updateById(rec);
    }

    @Test
    void wrongCodeCountsAnAttempt() {
        EmailCode rec = record("123456", NOW.minusMinutes(1), 2);
        when(mapper.selectOne(any())).thenReturn(rec);

        assertThatThrownBy(() -> service.verify(EMAIL, CodePurpose.EDU_VERIFY, "000000"))
                .hasMessage("验证码不正确");
        assertThat(rec.getAttempts()).isEqualTo(3);
        assertThat(rec.getUsedAt()).isNull();
    }

    @Test
    void tooManyAttemptsBlocksEvenTheRightCode() {
        when(mapper.selectOne(any())).thenReturn(record("123456", NOW.minusMinutes(1), 5));

        assertThatThrownBy(() -> service.verify(EMAIL, CodePurpose.EDU_VERIFY, "123456"))
                .hasMessageContaining("输错次数太多");
    }

    @Test
    void expiredCodeIsRejected() {
        when(mapper.selectOne(any())).thenReturn(record("123456", NOW.minusMinutes(11), 0));

        assertThatThrownBy(() -> service.verify(EMAIL, CodePurpose.EDU_VERIFY, "123456"))
                .hasMessageContaining("已失效");
    }

    @Test
    void usedCodeCannotBeReused() {
        EmailCode rec = record("123456", NOW.minusMinutes(1), 0);
        rec.setUsedAt(NOW.minusSeconds(30));
        when(mapper.selectOne(any())).thenReturn(rec);

        assertThatThrownBy(() -> service.verify(EMAIL, CodePurpose.EDU_VERIFY, "123456"))
                .hasMessageContaining("已失效");
    }

    @Test
    void codeForAnotherPurposeDoesNotMatch() {
        EmailCode rec = record("123456", NOW.minusMinutes(1), 0);
        rec.setCodeHash(EmailCodeService.hash(EMAIL, CodePurpose.REGISTER, "123456"));
        when(mapper.selectOne(any())).thenReturn(rec);

        assertThatThrownBy(() -> service.verify(EMAIL, CodePurpose.EDU_VERIFY, "123456"))
                .hasMessage("验证码不正确");
    }

    private static EmailCode record(String code, LocalDateTime createdAt, int attempts) {
        EmailCode rec = new EmailCode();
        rec.setId(1L);
        rec.setEmail(EMAIL);
        rec.setPurpose(CodePurpose.EDU_VERIFY);
        rec.setCodeHash(EmailCodeService.hash(EMAIL, CodePurpose.EDU_VERIFY, code));
        rec.setAttempts(attempts);
        rec.setCreatedAt(createdAt);
        rec.setExpiresAt(createdAt.plusMinutes(10));
        return rec;
    }
}
