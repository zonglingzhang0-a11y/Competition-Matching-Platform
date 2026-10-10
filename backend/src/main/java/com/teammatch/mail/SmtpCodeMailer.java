package com.teammatch.mail;

import com.teammatch.code.CodePurpose;
import com.teammatch.common.BizException;
import com.teammatch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
public class SmtpCodeMailer implements CodeMailer {

    private final JavaMailSender sender;
    private final AppProperties props;

    @Override
    public void sendCode(String to, CodePurpose purpose, String code, int ttlMinutes) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(props.mail().from());
        message.setTo(to);
        message.setSubject("【竞赛组队平台】" + purpose.getLabel() + "验证码");
        message.setText("你的" + purpose.getLabel() + "验证码是 " + code + "，" + ttlMinutes
                + " 分钟内有效。\n\n如果不是你本人操作，忽略这封邮件即可。");
        try {
            sender.send(message);
        } catch (MailException e) {
            log.warn("验证码邮件发送失败：{}", to, e);
            throw new BizException(502, "邮件发送失败，请检查邮箱地址或稍后再试");
        }
    }
}
