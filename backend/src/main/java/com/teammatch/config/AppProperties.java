package com.teammatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * application.yml 里 app 开头的自定义配置。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Mail mail, Code code, List<String> adminEmails) {

    public AppProperties {
        adminEmails = adminEmails == null ? List.of()
                : adminEmails.stream().map(e -> e.trim().toLowerCase(Locale.ROOT)).toList();
    }

    public boolean isAdminEmail(String email) {
        return email != null && adminEmails.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /** mode：log 打印到控制台 / smtp 真的发邮件 */
    public record Mail(String mode, String from) {
    }

    /**
     * 验证码：有效分钟数、重发间隔秒数、最多尝试次数；
     * 发送量上限：同一个 IP 每小时、每 24 小时，全站每天，不填用 CodeSendLimiter 里的默认值
     */
    public record Code(int ttlMinutes, int resendSeconds, int maxAttempts,
                       Integer ipPerHour, Integer ipPerDay, Integer totalPerDay) {
    }
}
