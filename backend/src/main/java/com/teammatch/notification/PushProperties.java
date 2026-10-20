package com.teammatch.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 站内信推送的配置（application.yml 里的 app.push）。
 *
 * @param allowedOriginPatterns 除了同域，还允许哪些来源建立推送连接，比如 https://*.trycloudflare.com。
 *                              平时留空：开发时经过 Vite 转发、部署时经过反向代理，浏览器看到的都是同一个域名。
 *                              公网演示脚本走临时隧道，浏览器的来源是隧道域名、后端看到的是 localhost，要在这里放行
 */
@ConfigurationProperties(prefix = "app.push")
public record PushProperties(List<String> allowedOriginPatterns) {

    public PushProperties {
        allowedOriginPatterns = allowedOriginPatterns == null ? List.of() : List.copyOf(allowedOriginPatterns);
    }
}
