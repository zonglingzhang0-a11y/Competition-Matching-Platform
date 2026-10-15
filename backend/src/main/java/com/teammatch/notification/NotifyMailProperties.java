package com.teammatch.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 新消息汇总发邮件的配置（application.yml 里的 app.notify-mail）。检查间隔另见 interval-ms。
 *
 * @param enabled      关掉后只有站内信
 * @param delaySeconds 消息多久没看才发邮件；这段时间里在网站上看过了就不发
 * @param siteUrl      邮件里"打开竞搭"的网址，部署到服务器后改成真实地址
 */
@ConfigurationProperties(prefix = "app.notify-mail")
public record NotifyMailProperties(Boolean enabled, Integer delaySeconds, String siteUrl) {

    public NotifyMailProperties {
        enabled = enabled == null || enabled;
        delaySeconds = delaySeconds == null || delaySeconds < 0 ? 600 : delaySeconds;
        siteUrl = siteUrl == null || siteUrl.isBlank() ? "http://localhost:5173" : siteUrl.replaceAll("/+$", "");
    }
}
