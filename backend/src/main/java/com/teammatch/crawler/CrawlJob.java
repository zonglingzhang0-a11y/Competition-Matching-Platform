package com.teammatch.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每天定时抓一次比赛通知（app.crawler.cron，默认早上 7:30）。
 * 抓取在后台线程里跑，不占定时任务的线程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlJob {

    private final CrawlService crawlService;
    private final CrawlerProperties props;

    @Scheduled(cron = "${app.crawler.cron:0 30 7 * * *}")
    public void run() {
        if (!props.enabled()) {
            return;
        }
        if (!crawlService.startAll()) {
            log.info("上一次通知抓取还没结束，这次跳过");
        }
    }
}
