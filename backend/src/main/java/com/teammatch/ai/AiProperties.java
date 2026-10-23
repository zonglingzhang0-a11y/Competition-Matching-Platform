package com.teammatch.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 分析用的配置（application.yml 里的 app.ai），默认关闭。接的是 OpenAI 兼容的接口：
 * DeepSeek、通义、本机 Ollama / llama.cpp 都行。
 *
 * <p>没配、连不上、返回的不是能用的 JSON 时，自动退回本地规则分析（不花钱、不联网），
 * 页面上会标明这一份来自"本地规则"还是"AI 模型"，不会假装是 AI 给的。
 *
 * @param enabled         打开后才调用模型；关着的时候只跑本地规则
 * @param baseUrl         接口地址，到 /v1 为止，比如 https://api.deepseek.com/v1
 * @param apiKey          云端模型的密钥，写在 application-local.yml 里，不要提交
 * @param model           模型名，比如 deepseek-chat
 * @param timeoutMs       一次请求最多等多久
 * @param maxTokens       一次回答最多多少 token
 * @param cooldownSeconds 同一个人 / 同一支队两次 AI 分析至少隔多少秒
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(Boolean enabled, String baseUrl, String apiKey, String model, Integer timeoutMs,
                           Integer maxTokens, Integer cooldownSeconds) {

    public AiProperties {
        enabled = enabled != null && enabled;
        baseUrl = baseUrl == null ? "" : baseUrl.strip().replaceAll("/+$", "");
        model = model == null || model.isBlank() ? "deepseek-chat" : model.strip();
        timeoutMs = timeoutMs == null || timeoutMs < 1000 ? 30_000 : timeoutMs;
        maxTokens = maxTokens == null || maxTokens < 200 ? 1200 : maxTokens;
        cooldownSeconds = cooldownSeconds == null || cooldownSeconds < 0 ? 60 : cooldownSeconds;
    }

    /** 打开了并且填了地址才真的去请求模型 */
    public boolean ready() {
        return enabled && !baseUrl.isEmpty();
    }
}
