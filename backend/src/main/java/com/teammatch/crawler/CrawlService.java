package com.teammatch.crawler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.crawler.CrawlDtos.PreviewLink;
import com.teammatch.crawler.CrawlDtos.PreviewResult;
import com.teammatch.crawler.CrawlDtos.RunStatus;
import com.teammatch.crawler.CrawlDtos.SourceForm;
import com.teammatch.crawler.CrawlEntities.CrawlRecord;
import com.teammatch.crawler.CrawlEntities.CrawlRecordMapper;
import com.teammatch.crawler.CrawlEntities.CrawlSource;
import com.teammatch.crawler.CrawlEntities.CrawlSourceMapper;
import com.teammatch.crawler.CrawlEntities.RecordStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Selector;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 抓比赛官网的通知：打开列表页 → 按选择器和标题关键词挑出通知链接 → 打开没抓过的 → 取正文、抽字段 → 存起来等管理员确认。
 * <p>
 * 同一时间只跑一次抓取（定时任务和管理员手动触发共用），在后台线程里跑，进度看 {@link #status()}。
 * 抓到的内容只进待确认列表，不会直接改比赛库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrawlService {

    /** 正文最多存这么多字 */
    static final int MAX_TEXT = 100_000;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** 附件不抓（只抓网页），免得每次都去请求一遍 */
    private static final Pattern ATTACHMENT = Pattern.compile(
            "\\.(pdf|docx?|xlsx?|pptx?|zip|rar|7z|jpe?g|png|gif)$", Pattern.CASE_INSENSITIVE);
    /** 列表页把发布日期和标题放在同一个链接里：09-18 .2026 标题、14 2026.09 标题、[2026-09-18] 标题 */
    private static final Pattern DATE_PREFIX = Pattern.compile(
            "^(?:[\\[【(（]?(?:\\d{1,2}|\\d*[-./]\\d[\\d./-]*)[\\]】)）]?\\s+)+");
    /** 标题后面跟着日期：标题 2026-09-18、标题 [2026.09.18] */
    private static final Pattern DATE_SUFFIX = Pattern.compile(
            "\\s+[\\[【(（]?\\d{4}[-./]\\d{1,2}(?:[-./]\\d{1,2})?[\\]】)）]?$");

    private final CrawlHttp http;
    private final RuleFieldExtractor ruleExtractor;
    private final CrawlSourceMapper sourceMapper;
    private final CrawlRecordMapper recordMapper;
    private final CrawlerProperties props;
    private final JsonMapper json;
    private final Clock clock;

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile RunStatus status = new RunStatus(false, null, null, null);

    public RunStatus status() {
        return status;
    }

    /** 在后台抓全部启用的来源。已经在抓了就返回 false */
    public boolean startAll() {
        return start(null);
    }

    /** 在后台抓一个来源（停用的也能手动抓）。已经在抓了就返回 false */
    public boolean startOne(long sourceId) {
        if (sourceMapper.selectById(sourceId) == null) {
            throw new BizException(404, "这个抓取来源不存在");
        }
        return start(sourceId);
    }

    private boolean start(Long sourceId) {
        if (!running.compareAndSet(false, true)) {
            return false;
        }
        LocalDateTime started = now();
        status = new RunStatus(true, started, null, null);
        Thread.ofVirtual().name("crawler").start(() -> {
            String summary = "出错了";
            try {
                summary = sourceId == null ? runAll() : runSource(sourceMapper.selectById(sourceId)).summary();
            } catch (RuntimeException e) {
                log.warn("抓取出错", e);
                summary = "出错了：" + e.getMessage();
            } finally {
                status = new RunStatus(false, started, now(), summary);
                running.set(false);
            }
        });
        return true;
    }

    /** 依次抓全部启用的来源，返回总结 */
    String runAll() {
        List<CrawlSource> sources = sourceMapper.selectList(new LambdaQueryWrapper<CrawlSource>()
                .eq(CrawlSource::getEnabled, true)
                .orderByAsc(CrawlSource::getId));
        int created = 0;
        int ignored = 0;
        int failed = 0;
        for (CrawlSource s : sources) {
            Outcome o = runSource(s);
            created += o.created;
            ignored += o.ignored;
            if (o.error != null) {
                failed++;
            }
        }
        StringBuilder sb = new StringBuilder("抓了 ").append(sources.size()).append(" 个来源，");
        sb.append(created == 0 ? "没有新通知" : "新通知 " + created + " 条");
        if (ignored > 0) {
            sb.append("，旧通知 ").append(ignored).append(" 条已自动忽略");
        }
        if (failed > 0) {
            sb.append("，").append(failed).append(" 个来源出错（看来源列表）");
        }
        log.info("通知抓取：{}", sb);
        return sb.toString();
    }

    /** 抓一个来源，结果记到来源的 last_result */
    Outcome runSource(CrawlSource s) {
        LocalDateTime now = now();
        Outcome o;
        try {
            o = crawl(s, now);
        } catch (IOException | RuntimeException e) {
            log.info("抓取来源 {} 出错：{}", s.getName(), e.getMessage());
            o = new Outcome(0, 0, 0, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        sourceMapper.finished(s.getId(), now, cut(o.summary(), 255));
        return o;
    }

    private Outcome crawl(CrawlSource s, LocalDateTime now) throws IOException {
        URI listUri = CrawlHttp.parse(s.getListUrl());
        RobotsRules robots = http.robots(listUri);
        if (robots.aiOptOut() && Boolean.TRUE.equals(s.getAllowLlm())) {
            sourceMapper.disallowLlm(s.getId(), "robots.txt 拒绝了 AI 爬虫：只用规则抽取，不交给大模型");
        }
        Document list = http.get(s.getListUrl());
        int created = 0;
        int ignored = 0;
        int skipped = 0;
        for (PreviewLink link : links(s.getLinkSelector(), s.getTitleKeywords(), s.getExcludeKeywords(), list, listUri, robots)) {
            if (!link.picked()) {
                continue;
            }
            CrawlRecord r;
            try {
                r = fetch(s, link, now);
            } catch (IOException e) {
                log.info("打不开 {}：{}", link.url(), e.getMessage());
                skipped++;
                continue;
            }
            if (r == null) {
                skipped++;
                continue;
            }
            try {
                recordMapper.insert(r);
            } catch (DuplicateKeyException e) {
                // 别的链接上有一模一样的正文
                skipped++;
                continue;
            }
            if (r.getStatus() == RecordStatus.IGNORED) {
                ignored++;
            } else {
                created++;
            }
        }
        return new Outcome(created, ignored, skipped, null);
    }

    /** 打开一篇通知，取正文、抽字段。正文为空或和已有的重复时返回 null */
    private CrawlRecord fetch(CrawlSource s, PreviewLink link, LocalDateTime now) throws IOException {
        Document doc = http.get(link.url());
        Element content = StringUtils.hasText(s.getContentSelector()) ? doc.selectFirst(s.getContentSelector()) : null;
        String text = CrawlHttp.plainText(content == null ? doc.body() : content);
        if (text.isBlank()) {
            return null;
        }
        text = cut(text, MAX_TEXT);
        String hash = sha256(text);
        if (recordMapper.hashSeen(hash)) {
            return null;
        }
        String title = cut(StringUtils.hasText(link.title()) ? link.title() : doc.title(), 255);
        Extracted e = ruleExtractor.extract(title, text);
        CrawlRecord r = new CrawlRecord();
        r.setSourceId(s.getId());
        r.setSourceUrl(link.url());
        r.setTitle(title);
        r.setRawText(text);
        r.setContentHash(hash);
        r.setExtracted(json.writeValueAsString(e));
        r.setFetchedAt(now);
        String stale = staleReason(title, e, now);
        if (stale != null) {
            r.setStatus(RecordStatus.IGNORED);
            r.setNote(stale);
        } else {
            r.setStatus(e.empty() ? RecordStatus.NEW : RecordStatus.EXTRACTED);
        }
        return r;
    }

    /** 试抓：只看列表页，告诉管理员哪些链接会被抓、哪些不会以及为什么。什么都不存 */
    public PreviewResult preview(SourceForm form) {
        checkSelector(form.linkSelector(), "通知链接");
        checkSelector(form.contentSelector(), "正文");
        try {
            URI uri = CrawlHttp.parse(form.listUrl().trim());
            Document list = http.get(uri.toString());
            List<PreviewLink> links = links(form.linkSelector(), form.titleKeywords(), form.excludeKeywords(), list, uri,
                    http.robots(uri));
            return new PreviewResult(links.size(), links);
        } catch (IOException e) {
            throw new BizException("打不开这个网址：" + e.getMessage());
        }
    }

    /** 列表页里的通知链接（同一网站、去重），逐个判断要不要抓 */
    private List<PreviewLink> links(String linkSelector, String include, String exclude, Document list, URI listUri,
                                    RobotsRules robots) {
        List<String> includes = keywords(include);
        List<String> excludes = keywords(exclude);
        String host = listUri.getHost();
        Map<String, String> found = new LinkedHashMap<>();
        for (Element a : list.select(linkSelector)) {
            String url = a.absUrl("href");
            int hash = url.indexOf('#');
            if (hash >= 0) {
                url = url.substring(0, hash);
            }
            if (url.isEmpty()) {
                continue;
            }
            String text = a.text().strip();
            String attr = a.attr("title").strip();
            String title = cleanTitle(attr.length() > text.length() ? attr : text);
            found.merge(url, title, (old, t) -> t.length() > old.length() ? t : old);
        }
        List<PreviewLink> result = new ArrayList<>();
        int picked = 0;
        for (Map.Entry<String, String> entry : found.entrySet()) {
            String url = entry.getKey();
            String title = entry.getValue();
            URI uri = uriOrNull(url);
            String excluded = excludes.stream().filter(title::contains).findFirst().orElse(null);
            String reason = null;
            if (uri == null) {
                reason = "不是网页链接";
            } else if (!host.equalsIgnoreCase(uri.getHost())) {
                reason = "不是这个网站的链接";
            } else if (url.length() > 512) {
                reason = "网址太长";
            } else if (!includes.isEmpty() && includes.stream().noneMatch(title::contains)) {
                reason = "标题里没有关键词";
            } else if (excluded != null) {
                reason = "标题里有排除词「" + excluded + "」";
            } else if (uri.getPath() != null && ATTACHMENT.matcher(uri.getPath()).find()) {
                reason = "附件，不抓（打开原文查看）";
            } else if (!robots.allowed(CrawlHttp.pathOf(uri))) {
                reason = "robots.txt 不允许抓";
            } else if (recordMapper.urlSeen(url)) {
                reason = "已经抓过";
            } else if (picked >= props.maxPagesPerSource()) {
                reason = "这次已经抓够 " + props.maxPagesPerSource() + " 篇，下次再抓";
            }
            if (reason == null) {
                picked++;
            }
            result.add(new PreviewLink(title, url, reason == null, reason == null ? "会抓取" : reason));
        }
        return result;
    }

    /**
     * 过时的通知自动忽略（仍然存下来，下次就不用再打开它）：
     * 抽出的时间都已经过去了；或者没抽出时间，但标题写的是往年。
     */
    static String staleReason(String title, Extracted e, LocalDateTime now) {
        LocalDateTime latest = Stream.of(e.registrationEnd(), e.contestStart(), e.contestEnd())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        if (latest != null) {
            return latest.toLocalDate().isBefore(now.toLocalDate())
                    ? "通知里的时间都已经过去了（最晚是 " + DATE.format(latest) + "），自动忽略" : null;
        }
        if (e.year() != null && e.year() < now.getYear() && title != null && title.contains(String.valueOf(e.year()))) {
            return e.year() + " 年的旧通知，自动忽略";
        }
        return null;
    }

    private static URI uriOrNull(String url) {
        try {
            return CrawlHttp.parse(url);
        } catch (IOException e) {
            return null;
        }
    }

    /** 去掉列表页混进标题里的发布日期；去完是空的就保留原样 */
    static String cleanTitle(String title) {
        String t = DATE_SUFFIX.matcher(DATE_PREFIX.matcher(title).replaceFirst("")).replaceFirst("").strip();
        return t.isEmpty() ? title : t;
    }

    /** 选择器能不能用；空的不检查（正文选择器可以不填） */
    static void checkSelector(String selector, String label) {
        if (!StringUtils.hasText(selector)) {
            return;
        }
        try {
            new Document("").select(selector);
        } catch (Selector.SelectorParseException | IllegalArgumentException e) {
            throw new BizException(label + "的选择器写得不对：" + e.getMessage());
        }
    }

    static List<String> keywords(String s) {
        if (!StringUtils.hasText(s)) {
            return List.of();
        }
        return Arrays.stream(s.split("[,，、]")).map(String::strip).filter(k -> !k.isEmpty()).toList();
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    /** 一个来源抓一次的结果。skipped：打不开、robots.txt 不让抓、正文为空或重复的 */
    record Outcome(int created, int ignored, int skipped, String error) {

        String summary() {
            if (error != null) {
                return "出错：" + error;
            }
            StringBuilder sb = new StringBuilder(created == 0 ? "没有新通知" : "新通知 " + created + " 条");
            if (ignored > 0) {
                sb.append("，旧通知 ").append(ignored).append(" 条已自动忽略");
            }
            if (skipped > 0) {
                sb.append("，").append(skipped).append(" 篇没抓到（打不开、robots.txt 不允许或内容重复）");
            }
            return sb.toString();
        }
    }
}
