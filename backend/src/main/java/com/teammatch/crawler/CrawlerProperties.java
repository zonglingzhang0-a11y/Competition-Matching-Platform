package com.teammatch.crawler;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知爬虫的配置（application.yml 里的 app.crawler）。
 *
 * @param enabled           每天定时抓；关掉后管理员仍可以在后台手动抓
 * @param cron              定时抓的时间，默认每天早上 7:30
 * @param delayMs           同一个网站两次请求之间至少隔多久（robots.txt 要求更久就按它的）
 * @param timeoutMs         单个页面的超时
 * @param maxPagesPerSource 每个来源一次最多打开几篇新通知
 * @param contact           写进 User-Agent 的联系地址，网站管理员能找到我们
 */
@ConfigurationProperties(prefix = "app.crawler")
public record CrawlerProperties(Boolean enabled, String cron, Integer delayMs, Integer timeoutMs,
                                Integer maxPagesPerSource, String contact) {

    public CrawlerProperties {
        enabled = enabled == null || enabled;
        cron = cron == null || cron.isBlank() ? "0 30 7 * * *" : cron;
        delayMs = delayMs == null || delayMs < 0 ? 2000 : delayMs;
        timeoutMs = timeoutMs == null || timeoutMs < 1000 ? 15000 : timeoutMs;
        maxPagesPerSource = maxPagesPerSource == null || maxPagesPerSource < 1 ? 10 : maxPagesPerSource;
        contact = contact == null || contact.isBlank()
                ? "https://github.com/zonglingzhang0-a11y/Competition-Matching-Platform" : contact;
    }

    public String userAgent() {
        return CrawlHttp.AGENT + "/1.0 (+" + contact + ")";
    }
}
