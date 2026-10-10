package com.teammatch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * application.yml 里 app 开头的自定义配置。
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Mail mail, Code code) {

    /** mode：log 打印到控制台 / smtp 真的发邮件 */
    public record Mail(String mode, String from) {
    }

    /** 验证码：有效分钟数、重发间隔秒数、最多尝试次数 */
    public record Code(int ttlMinutes, int resendSeconds, int maxAttempts) {
    }
}
