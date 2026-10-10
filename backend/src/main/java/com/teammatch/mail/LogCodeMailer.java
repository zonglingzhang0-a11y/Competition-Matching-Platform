package com.teammatch.mail;

import com.teammatch.code.CodePurpose;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发模式：不真的发邮件，把验证码打印在后端控制台。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogCodeMailer implements CodeMailer {

    @Override
    public void sendCode(String to, CodePurpose purpose, String code, int ttlMinutes) {
        log.info("[开发模式] 发往 {} 的{}验证码：{}（{} 分钟内有效）", to, purpose.getLabel(), code, ttlMinutes);
    }
}
