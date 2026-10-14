package com.teammatch.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsRulesTest {

    private static final String AGENT = "TeamMatchCrawler";

    @Test
    void starGroupAppliesWhenNotNamed() {
        RobotsRules r = RobotsRules.parse("""
                User-agent: *
                Disallow: /project/
                Disallow: /admin

                User-agent: GPTBot
                User-agent: ClaudeBot
                Disallow: /
                """, AGENT);
        assertThat(r.allowed("/")).isTrue();
        assertThat(r.allowed("/article/123.html")).isTrue();
        assertThat(r.allowed("/project/1")).isFalse();
        assertThat(r.allowed("/admin/login")).isFalse();
        assertThat(r.allowed("/administrator")).isFalse();
        // 网站拒绝了 AI 爬虫：能抓，但内容不交给大模型
        assertThat(r.aiOptOut()).isTrue();
    }

    @Test
    void namedGroupWinsOverStar() {
        RobotsRules r = RobotsRules.parse("""
                User-agent: *
                Disallow: /

                User-agent: teammatchcrawler/1.0
                Disallow: /private
                Crawl-delay: 5
                """, AGENT);
        assertThat(r.allowed("/news/details/330")).isTrue();
        assertThat(r.allowed("/private/a")).isFalse();
        assertThat(r.crawlDelaySeconds()).isEqualTo(5);
        assertThat(r.aiOptOut()).isFalse();
    }

    @Test
    void longestMatchWinsAndAllowWinsTies() {
        RobotsRules r = RobotsRules.parse("""
                User-agent: *
                Disallow: /news/
                Allow: /news/details/
                Disallow: /*.pdf$
                Allow: /page
                Disallow: /page
                """, AGENT);
        assertThat(r.allowed("/news/list")).isFalse();
        assertThat(r.allowed("/news/details/345")).isTrue();
        assertThat(r.allowed("/files/notice.pdf")).isFalse();
        assertThat(r.allowed("/files/notice.pdf?v=2")).isTrue();
        assertThat(r.allowed("/page")).isTrue();
    }

    @Test
    void emptyDisallowAndCommentsAndBlankFile() {
        RobotsRules r = RobotsRules.parse("""
                # 全部开放
                User-agent: * # 所有爬虫
                Disallow:
                """, AGENT);
        assertThat(r.allowed("/anything")).isTrue();
        assertThat(RobotsRules.parse("", AGENT).allowed("/x")).isTrue();
        assertThat(RobotsRules.DISALLOW_ALL.allowed("/x")).isFalse();
        assertThat(RobotsRules.ALLOW_ALL.allowed("/x")).isTrue();
    }
}
