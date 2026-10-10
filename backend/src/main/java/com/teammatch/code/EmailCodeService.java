package com.teammatch.code;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.config.AppProperties;
import com.teammatch.mail.CodeMailer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 邮箱验证码：生成、发送、校验。
 * <ul>
 *   <li>数据库只存摘要，不存明文</li>
 *   <li>同一邮箱、同一用途，重发要隔一段时间</li>
 *   <li>只认最新的一条；输错超过次数就作废</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class EmailCodeService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmailCodeMapper codeMapper;
    private final CodeMailer mailer;
    private final AppProperties props;
    private final Clock clock;

    public void send(String email, CodePurpose purpose) {
        String to = normalize(email);
        LocalDateTime now = LocalDateTime.now(clock);
        AppProperties.Code cfg = props.code();

        EmailCode latest = latest(to, purpose);
        if (latest != null) {
            long waited = Duration.between(latest.getCreatedAt(), now).toSeconds();
            if (waited < cfg.resendSeconds()) {
                throw new BizException(429, "发送太频繁，请 " + (cfg.resendSeconds() - waited) + " 秒后再试");
            }
        }

        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        EmailCode record = new EmailCode();
        record.setEmail(to);
        record.setPurpose(purpose);
        record.setCodeHash(hash(to, purpose, code));
        record.setAttempts(0);
        record.setExpiresAt(now.plusMinutes(cfg.ttlMinutes()));
        record.setCreatedAt(now);
        codeMapper.insert(record);

        mailer.sendCode(to, purpose, code, cfg.ttlMinutes());
    }

    /**
     * 校验通过就把这条验证码标记为已使用；不通过直接抛出异常。
     * <p>
     * 放在独立事务里，并且业务异常不回滚：调用方的事务失败了，"输错次数 +1"也要保留，
     * 否则次数限制形同虚设。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = BizException.class)
    public void verify(String email, CodePurpose purpose, String code) {
        String to = normalize(email);
        LocalDateTime now = LocalDateTime.now(clock);

        EmailCode latest = latest(to, purpose);
        if (latest == null || latest.getUsedAt() != null || !now.isBefore(latest.getExpiresAt())) {
            throw new BizException("验证码已失效，请重新获取");
        }
        if (latest.getAttempts() >= props.code().maxAttempts()) {
            throw new BizException("输错次数太多，请重新获取验证码");
        }
        if (code == null || !MessageDigest.isEqual(
                hash(to, purpose, code.trim()).getBytes(StandardCharsets.UTF_8),
                latest.getCodeHash().getBytes(StandardCharsets.UTF_8))) {
            latest.setAttempts(latest.getAttempts() + 1);
            codeMapper.updateById(latest);
            throw new BizException("验证码不正确");
        }
        latest.setUsedAt(now);
        codeMapper.updateById(latest);
    }

    private EmailCode latest(String email, CodePurpose purpose) {
        return codeMapper.selectOne(new LambdaQueryWrapper<EmailCode>()
                .eq(EmailCode::getEmail, email)
                .eq(EmailCode::getPurpose, purpose)
                .orderByDesc(EmailCode::getId)
                .last("LIMIT 1"));
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    /** 把邮箱和用途一起算进摘要，同一个验证码换个邮箱或用途就对不上 */
    static String hash(String email, CodePurpose purpose, String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((email + "|" + purpose + "|" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
