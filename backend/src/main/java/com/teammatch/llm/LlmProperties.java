package com.teammatch.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 大模型连接（application.yml 里的 app.llm），默认关闭。爬虫补抽字段、比赛页 AI 整理、AI 能力分析共用这一份。
 * 接的是 OpenAI 兼容的接口：本机的小模型、DeepSeek 之类的云端模型都行。没配或连不上时各功能退回规则，不影响启动。
 * 以前叫 app.crawler.llm，还写着旧名字时启动日志会提醒（见 {@link LlmClient}）。
 *
 * @param enabled   打开后才会请求模型
 * @param baseUrl   接口地址，到 /v1 为止，比如 http://127.0.0.1:8096/v1
 * @param model     模型名
 * @param apiKey    云端模型的密钥，本机模型随便填或不填；写在 application-local.yml 里，不要提交
 * @param timeoutMs 一次请求最多等多久（本机模型一次只处理一个请求，慢一点）
 * @param maxChars  爬虫和 AI 整理发给模型的正文最多多少字，太长的截掉
 * @param waitMs    模型正在回答别的请求时，最多排队等多久；等不到就当这次没连上
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(Boolean enabled, String baseUrl, String model, String apiKey, Integer timeoutMs,
                            Integer maxChars, Integer waitMs) {

    public LlmProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        model = model == null || model.isBlank() ? "qwen3.8-27b" : model.strip();
        timeoutMs = timeoutMs == null || timeoutMs < 1000 ? 120_000 : timeoutMs;
        maxChars = maxChars == null || maxChars < 500 ? 6000 : maxChars;
        waitMs = waitMs == null || waitMs < 0 ? 20_000 : waitMs;
    }

    /** 打开了并且填了地址 */
    public boolean ready() {
        return enabled && !baseUrl.isEmpty();
    }
}
