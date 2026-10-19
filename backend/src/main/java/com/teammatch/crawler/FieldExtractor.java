package com.teammatch.crawler;

/**
 * 从通知原文里抽字段：规则 {@link RuleFieldExtractor}，大模型 {@link LlmFieldExtractor}（规则没抽全时补，可选）。
 * 网站不希望内容被 AI 使用的来源（allow_llm = 0）始终只用规则。
 */
public interface FieldExtractor {

    Extracted extract(String title, String text);
}
