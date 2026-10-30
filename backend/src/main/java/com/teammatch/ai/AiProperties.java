package com.teammatch.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 能力分析 / 招募建议的开关（application.yml 里的 app.ai），默认关闭。
 * 模型的地址、密钥这些和爬虫、AI 整理共用一份（app.llm，见 {@link com.teammatch.llm.LlmProperties}）；
 * 这里单独一个开关，是因为这个功能会把档案内容发给模型，要另外同意。
 *
 * <p>没打开、模型没配或连不上、返回的不是能用的 JSON 时，自动退回本地规则分析（不花钱、不联网），
 * 页面上会标明这一份来自"本地规则"还是"AI 模型"，不会假装是 AI 给的。
 *
 * @param enabled         打开、并且 app.llm 也配好了，才调用模型；否则只跑本地规则
 * @param maxTokens       一次回答最多多少 token
 * @param cooldownSeconds 同一个人 / 同一支队两次 AI 分析至少隔多少秒
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(Boolean enabled, Integer maxTokens, Integer cooldownSeconds) {

    public AiProperties {
        enabled = enabled != null && enabled;
        maxTokens = maxTokens == null || maxTokens < 200 ? 1200 : maxTokens;
        cooldownSeconds = cooldownSeconds == null || cooldownSeconds < 0 ? 60 : cooldownSeconds;
    }
}
