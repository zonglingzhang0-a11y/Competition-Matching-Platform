package com.teammatch.crawler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 从一篇比赛通知里抽出来的字段，没找到的为空。存进 crawl_record.extracted，等管理员确认。
 *
 * @param contestAllDay 比赛时间只写了日期、没写几点
 * @param evidence      每个字段是从哪句话里抽的（字段名 → 原句），管理员核对用
 * @param extractor     rule：规则抽取 / llm：大模型抽取
 */
public record Extracted(
        Integer year,
        String editionTitle,
        LocalDateTime registrationStart,
        LocalDateTime registrationEnd,
        LocalDateTime contestStart,
        LocalDateTime contestEnd,
        boolean contestAllDay,
        Integer teamMin,
        Integer teamMax,
        Boolean sameSchoolRequired,
        Boolean advisorRequired,
        Map<String, String> evidence,
        String extractor) {

    /** 一个字段都没抽出来（比如赛题讲评、获奖名单这类通知） */
    public boolean empty() {
        return registrationEnd == null && contestStart == null && teamMax == null
                && sameSchoolRequired == null && advisorRequired == null;
    }
}
