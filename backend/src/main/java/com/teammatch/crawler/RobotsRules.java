package com.teammatch.crawler;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 一个网站的 robots.txt，按 RFC 9309 判断某个路径能不能抓：
 * <ul>
 *   <li>先找写了我们名字的那组规则，没有就用 User-agent: * 那组</li>
 *   <li>匹配到的规则里最长的那条说了算，一样长时 Allow 优先；支持 * 和结尾的 $</li>
 * </ul>
 * 另外看一眼网站有没有明确拒绝 AI 爬虫（GPTBot、ClaudeBot 等）——拒绝了的话，
 * 抓到的内容只用规则抽取，不交给大模型。
 */
public final class RobotsRules {

    /** 常见的 AI 爬虫名字，网站对它们 Disallow: / 就当作不希望内容被 AI 使用 */
    private static final Set<String> AI_AGENTS = Set.of("gptbot", "chatgpt-user", "oai-searchbot", "claudebot",
            "claude-web", "claude-user", "anthropic-ai", "google-extended", "ccbot", "bytespider", "perplexitybot",
            "cohere-ai", "meta-externalagent", "applebot-extended", "amazonbot");

    /** 什么都能抓（robots.txt 不存在时） */
    public static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), 0, false);
    /** 什么都不能抓（robots.txt 暂时拿不到时，按 RFC 先当作全部禁止） */
    public static final RobotsRules DISALLOW_ALL = new RobotsRules(List.of(new Rule("/", false)), 0, false);

    private final List<Rule> rules;
    private final int crawlDelaySeconds;
    private final boolean aiOptOut;

    private RobotsRules(List<Rule> rules, int crawlDelaySeconds, boolean aiOptOut) {
        this.rules = rules;
        this.crawlDelaySeconds = crawlDelaySeconds;
        this.aiOptOut = aiOptOut;
    }

    /**
     * @param agent 我们的爬虫名字（User-Agent 里的产品名，如 TeamMatchCrawler）
     */
    public static RobotsRules parse(String content, String agent) {
        String me = agent.toLowerCase(Locale.ROOT);
        List<Group> groups = new ArrayList<>();
        Group current = null;
        boolean lastWasAgent = false;
        for (String line : content.split("\\r?\\n|\\r")) {
            int hash = line.indexOf('#');
            String l = (hash >= 0 ? line.substring(0, hash) : line).trim();
            int colon = l.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = l.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = l.substring(colon + 1).trim();
            if (key.equals("user-agent")) {
                // 连着写的几个 User-agent 共用下面的规则
                if (current == null || !lastWasAgent) {
                    current = new Group();
                    groups.add(current);
                }
                current.agents.add(value.toLowerCase(Locale.ROOT));
                lastWasAgent = true;
                continue;
            }
            lastWasAgent = false;
            if (current == null) {
                continue;
            }
            switch (key) {
                case "allow" -> {
                    if (!value.isEmpty()) {
                        current.rules.add(new Rule(value, true));
                    }
                }
                // 空的 Disallow 表示不限制
                case "disallow" -> {
                    if (!value.isEmpty()) {
                        current.rules.add(new Rule(value, false));
                    }
                }
                case "crawl-delay" -> {
                    try {
                        current.crawlDelay = (int) Math.ceil(Double.parseDouble(value));
                    } catch (NumberFormatException ignored) {
                        // 写错了就不管
                    }
                }
                default -> {
                    // Sitemap 等其他字段用不到
                }
            }
        }

        List<Rule> mine = new ArrayList<>();
        List<Rule> star = new ArrayList<>();
        int myDelay = -1;
        int starDelay = 0;
        boolean aiOptOut = false;
        for (Group g : groups) {
            // 按产品名完全匹配（不区分大小写），有人会带上版本号，如 TeamMatchCrawler/1.0
            boolean forMe = g.agents.stream().anyMatch(a -> a.split("/")[0].trim().equals(me));
            if (forMe) {
                mine.addAll(g.rules);
                myDelay = Math.max(myDelay, g.crawlDelay);
            } else if (g.agents.contains("*")) {
                star.addAll(g.rules);
                starDelay = Math.max(starDelay, g.crawlDelay);
            }
            if (g.agents.stream().anyMatch(AI_AGENTS::contains)
                    && g.rules.stream().anyMatch(r -> !r.allow && r.path.equals("/"))) {
                aiOptOut = true;
            }
        }
        boolean useMine = myDelay >= 0;
        return new RobotsRules(List.copyOf(useMine ? mine : star), Math.max(useMine ? myDelay : starDelay, 0), aiOptOut);
    }

    /** path 带不带查询串都行，如 /news/details/330?x=1 */
    public boolean allowed(String path) {
        String p = path == null || path.isEmpty() ? "/" : path;
        Rule best = null;
        for (Rule r : rules) {
            if (r.matches(p) && (best == null || r.path.length() > best.path.length()
                    || r.path.length() == best.path.length() && r.allow)) {
                best = r;
            }
        }
        return best == null || best.allow || p.equals("/robots.txt");
    }

    /** 网站要求的两次请求之间的间隔，没写就是 0 */
    public int crawlDelaySeconds() {
        return crawlDelaySeconds;
    }

    /** 网站明确拒绝了 AI 爬虫 */
    public boolean aiOptOut() {
        return aiOptOut;
    }

    private static final class Group {
        private final List<String> agents = new ArrayList<>();
        private final List<Rule> rules = new ArrayList<>();
        private int crawlDelay;
    }

    private record Rule(String path, boolean allow) {

        boolean matches(String target) {
            String pattern = path;
            boolean anchored = pattern.endsWith("$");
            if (anchored) {
                pattern = pattern.substring(0, pattern.length() - 1);
            }
            return match(pattern, 0, target, 0, anchored);
        }

        /** * 匹配任意字符；不带 $ 时只要前缀能对上 */
        private static boolean match(String pattern, int pi, String target, int ti, boolean anchored) {
            while (pi < pattern.length()) {
                char c = pattern.charAt(pi);
                if (c == '*') {
                    for (int k = ti; k <= target.length(); k++) {
                        if (match(pattern, pi + 1, target, k, anchored)) {
                            return true;
                        }
                    }
                    return false;
                }
                if (ti >= target.length() || target.charAt(ti) != c) {
                    return false;
                }
                pi++;
                ti++;
            }
            return !anchored || ti == target.length();
        }
    }
}
