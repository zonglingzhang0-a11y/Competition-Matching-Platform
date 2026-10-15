package com.teammatch.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 开发模式：不真的发邮件，把邮件打印在后端控制台。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "log", matchIfMissing = true)
public class LogMailer implements Mailer {

    @Override
    public void send(String to, String subject, String text) {
        log.info("[开发模式] 发往 {} 的邮件：{}\n{}", to, subject, text);
    }
}
