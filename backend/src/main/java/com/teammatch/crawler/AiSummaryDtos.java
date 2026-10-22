package com.teammatch.crawler;

import java.time.LocalDateTime;
import java.util.List;

/** 比赛页的 AI 总结 */
public final class AiSummaryDtos {

    private AiSummaryDtos() {
    }

    /**
     * @param llmReady    后端打开了大模型（管理员才能生成）
     * @param canGenerate 当前用户能不能点"生成"（管理员）
     * @param summary     还没生成过时为空
     * @param missing     报名时间、比赛时间、组队要求里没总结出来的，提醒去别的渠道查
     * @param sources     平台抓到的这个比赛的通知（最近的几篇）
     * @param channels    更多信息渠道
     */
    public record View(boolean llmReady, boolean canGenerate, Summary summary, List<String> missing,
                       List<SourceView> sources, List<Channel> channels) {
    }

    public record Summary(List<PointView> points, String model, LocalDateTime generatedAt) {
    }

    /** 一条要点；quote 是原文里的那句话，sourceTitle / sourceUrl 是出自哪篇通知 */
    public record PointView(String label, String text, String quote, Long recordId, String sourceTitle, String sourceUrl) {
    }

    /**
     * @param aiAllowed 来源网站允许内容交给大模型；不允许的只列链接
     * @param used      这篇交给了大模型（在当前的总结里）
     */
    public record SourceView(Long id, String title, String url, LocalDateTime fetchedAt, boolean aiAllowed, boolean used) {
    }

    /** 信息渠道；url 为空的是"去哪里问"这类建议 */
    public record Channel(String name, String url, String hint) {
    }
}
