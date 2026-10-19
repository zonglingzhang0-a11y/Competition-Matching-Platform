package com.teammatch.crawler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爬虫的网络请求。守规矩：
 * <ul>
 *   <li>User-Agent 写明是谁、怎么联系我们</li>
 *   <li>先看 robots.txt，不让抓的不抓；robots.txt 缓存 12 小时</li>
 *   <li>同一个网站上一个请求结束后至少隔 {@code app.crawler.delay-ms} 再发下一个，robots.txt 写了 Crawl-delay 就按更久的</li>
 *   <li>只抓网页，不下载附件；跳转也要过 robots.txt</li>
 * </ul>
 * 爬虫同一时间只有一个线程在跑，所以这里的等待直接 sleep。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrawlHttp {

    /** robots.txt 里认的名字 */
    public static final String AGENT = "TeamMatchCrawler";

    private static final Duration ROBOTS_TTL = Duration.ofHours(12);
    /** robots.txt 暂时拿不到时，先当作全部禁止，过一会儿再试 */
    private static final Duration ROBOTS_RETRY = Duration.ofMinutes(10);
    private static final int MAX_BODY = 3 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    /** Crawl-delay 写得太离谱时的上限 */
    private static final long MAX_DELAY_MS = 30_000;

    private final CrawlerProperties props;
    private final Map<String, CachedRobots> robotsCache = new ConcurrentHashMap<>();
    private final Map<String, Long> lastRequestAt = new ConcurrentHashMap<>();

    /** robots.txt 不让抓 */
    public static class BlockedException extends IOException {
        public BlockedException(String url) {
            super("robots.txt 不允许抓取 " + url);
        }
    }

    /** 打开一个网页。不是网页（PDF、Word 附件等）会抛 IOException */
    public Document get(String url) throws IOException {
        String current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            URI uri = parse(current);
            if (!robots(uri).allowed(pathOf(uri))) {
                throw new BlockedException(current);
            }
            // 内容类型自己判断：Jsoup 会连跳转响应（通常没有 Content-Type）一起拒掉
            Connection.Response res = execute(uri, request(uri, current).followRedirects(false).ignoreContentType(true));
            int status = res.statusCode();
            if (status >= 300 && status < 400 && res.hasHeader("Location")) {
                try {
                    current = uri.resolve(res.header("Location")).toString();
                } catch (IllegalArgumentException e) {
                    throw new IOException("跳转地址格式不对：" + res.header("Location"));
                }
                continue;
            }
            if (status >= 400) {
                throw new IOException("网站返回 " + status + "：" + current);
            }
            String type = res.contentType();
            if (type != null && !type.startsWith("text/html") && !type.startsWith("application/xhtml")) {
                throw new IOException("不是网页（" + type + "）：" + current);
            }
            return res.parse();
        }
        throw new IOException("跳转次数太多：" + url);
    }

    /** 这个网站的 robots.txt，带缓存 */
    public RobotsRules robots(URI uri) throws IOException {
        String origin = origin(uri);
        CachedRobots cached = robotsCache.get(origin);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            return cached.rules;
        }
        RobotsRules rules;
        Duration ttl = ROBOTS_TTL;
        try {
            Connection.Response res = execute(uri, request(uri, origin + "/robots.txt").ignoreContentType(true));
            int status = res.statusCode();
            if (status >= 200 && status < 300) {
                rules = RobotsRules.parse(res.body(), AGENT);
            } else if (status >= 400 && status < 500) {
                // RFC 9309：robots.txt 不存在（4xx）就是没有限制
                rules = RobotsRules.ALLOW_ALL;
            } else {
                rules = RobotsRules.DISALLOW_ALL;
                ttl = ROBOTS_RETRY;
            }
        } catch (IOException e) {
            log.info("拿不到 {} 的 robots.txt，先不抓：{}", origin, e.getMessage());
            rules = RobotsRules.DISALLOW_ALL;
            ttl = ROBOTS_RETRY;
        }
        robotsCache.put(origin, new CachedRobots(rules, now + ttl.toMillis()));
        return rules;
    }

    /** 发请求：离上次请求这个网站不够间隔就先等；间隔从上一个请求结束算起，网站那边看到的间隔不会比设定的短 */
    private Connection.Response execute(URI uri, Connection connection) throws IOException {
        waitTurn(uri);
        try {
            return connection.execute();
        } finally {
            lastRequestAt.put(origin(uri), System.currentTimeMillis());
        }
    }

    private Connection request(URI uri, String url) {
        return Jsoup.connect(url)
                .userAgent(props.userAgent())
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .timeout(props.timeoutMs())
                .maxBodySize(MAX_BODY)
                .ignoreHttpErrors(true);
    }

    /** 离这个网站上次请求结束不够间隔就等一等 */
    private void waitTurn(URI uri) throws IOException {
        String origin = origin(uri);
        CachedRobots cached = robotsCache.get(origin);
        long delay = props.delayMs();
        if (cached != null) {
            delay = Math.max(delay, Math.min(cached.rules.crawlDelaySeconds() * 1000L, MAX_DELAY_MS));
        }
        Long last = lastRequestAt.get(origin);
        if (last != null) {
            long wait = last + delay - System.currentTimeMillis();
            if (wait > 0) {
                try {
                    Thread.sleep(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("抓取被中断");
                }
            }
        }
    }

    /** 网页的可读文字：块级元素和 br 换行，其余连在一起；多余的空白去掉 */
    public static String plainText(Element root) {
        StringBuilder sb = new StringBuilder();
        NodeTraversor.traverse(new NodeVisitor() {
            @Override
            public void head(Node node, int depth) {
                if (node instanceof TextNode t) {
                    sb.append(t.text());
                } else if (node instanceof Element e && (e.isBlock() || e.nameIs("br"))) {
                    sb.append('\n');
                }
            }

            @Override
            public void tail(Node node, int depth) {
                if (node instanceof Element e && e.isBlock()) {
                    sb.append('\n');
                }
            }
        }, root);
        StringBuilder out = new StringBuilder();
        for (String line : sb.toString().split("\n")) {
            String l = line.replace(' ', ' ').replace('　', ' ').strip();
            if (!l.isEmpty()) {
                out.append(l).append('\n');
            }
        }
        return out.toString().strip();
    }

    /** http/https 的绝对地址，否则抛 IOException */
    static URI parse(String url) throws IOException {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new IOException("只能抓 http 或 https 的网址：" + url);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IOException("网址格式不对：" + url);
        }
    }

    static String origin(URI uri) {
        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (port == -1 ? "" : ":" + port);
    }

    /** robots.txt 匹配用的路径，带查询串 */
    static String pathOf(URI uri) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        return uri.getRawQuery() == null ? path : path + "?" + uri.getRawQuery();
    }

    private record CachedRobots(RobotsRules rules, long expiresAt) {
    }
}
