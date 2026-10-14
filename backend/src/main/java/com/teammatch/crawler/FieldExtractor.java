package com.teammatch.crawler;

/**
 * 从通知原文里抽字段。现在只有规则实现 {@link RuleFieldExtractor}。
 * <p>
 * 以后接大模型：新写一个实现，在 CrawlService 里按来源的 allow_llm 和是否配置了 key 选用。
 * 网站不希望内容被 AI 使用的来源（allow_llm = 0）始终只用规则。
 */
public interface FieldExtractor {

    Extracted extract(String title, String text);
}
