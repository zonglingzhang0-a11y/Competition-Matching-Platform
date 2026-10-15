package com.teammatch.mail;

/**
 * 发一封纯文本邮件（新消息汇总这类通知）。开发时用 {@link LogMailer} 打印到控制台，配好 SMTP 后换成 {@link SmtpMailer}。
 * 发不出去时抛异常，由调用方决定怎么处理。
 */
public interface Mailer {

    void send(String to, String subject, String text);
}
