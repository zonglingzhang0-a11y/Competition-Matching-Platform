package com.teammatch.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 爬虫用大模型补抽字段的配置（application.yml 里的 app.crawler.llm），默认关闭。
 * 接的是 OpenAI 兼容的接口：本机的小模型、DeepSeek 之类的云端模型都行。没配或连不上时只用规则抽取，不影响启动。
 *
 * @param enabled   打开后，规则没抽全的通知才交给大模型
 * @param baseUrl   接口地址，到 /v1 为止，比如 http://127.0.0.1:8096/v1
 * @param model     模型名
 * @param apiKey    云端模型的密钥，本机模型随便填或不填；写在 application-local.yml 里，不要提交
 * @param timeoutMs 一次请求最多等多久（本机模型一次只处理一个请求，慢一点）
 * @param maxChars  正文最多发多少字，太长的截掉
 */
@ConfigurationProperties(prefix = "app.crawler.llm")
public record LlmProperties(Boolean enabled, String baseUrl, String model, String apiKey, Integer timeoutMs,
                            Integer maxChars) {

    public LlmProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        model = model == null || model.isBlank() ? "qwen3.8-27b" : model.strip();
        timeoutMs = timeoutMs == null || timeoutMs < 1000 ? 120_000 : timeoutMs;
        maxChars = maxChars == null || maxChars < 500 ? 6000 : maxChars;
    }

    /** 打开了并且填了地址 */
    public boolean ready() {
        return enabled && !baseUrl.isEmpty();
    }
}
