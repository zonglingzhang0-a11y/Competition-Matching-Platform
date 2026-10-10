package com.teammatch.mail;

import com.teammatch.code.CodePurpose;

/**
 * 发验证码邮件。开发时用 {@link LogCodeMailer} 打印到控制台，配好 SMTP 后换成 {@link SmtpCodeMailer}。
 */
public interface CodeMailer {

    void sendCode(String to, CodePurpose purpose, String code, int ttlMinutes);
}
