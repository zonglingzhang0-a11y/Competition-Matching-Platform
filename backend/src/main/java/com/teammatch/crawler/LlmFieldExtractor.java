package com.teammatch.crawler;

import com.teammatch.llm.LlmClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 规则没抽全的通知交给大模型补（OpenAI 兼容接口，见 {@link com.teammatch.llm.LlmProperties}）。
 * <p>
 * 大模型会编，所以每个字段都要带上原文里的一句话当依据：这句话在原文里找不到、或者句子里没有它给的日期 / 人数，
 * 这个字段就不要。补出来的字段在依据里标成"llm:xxx"，管理员核对时看得出是大模型抽的。
 * 连不上、超时、返回的不是 JSON，都当作没抽到，照常只用规则的结果。
 */
@Slf4j
@Component
public class LlmFieldExtractor implements FieldExtractor {

    /** 依据里大模型补的字段加这个前缀 */
    static final String LLM_PREFIX = "llm:";
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final String CN_DIGITS = "零一二三四五六七八九十";

    private static final String SYSTEM = """
            你从中国大学生学科竞赛的通知里抽取报名和组队信息。只根据给出的原文，不要猜，原文没写的填 null。
            每一项都要给出依据 quote：原文里连续出现的一句话（照抄，不要改写，不超过 80 字），这句话里要能看到你填的日期或人数。
            日期写成 "2026-09-10 17:00"，原文没写几点就写 "2026-09-10"；没写年份的按通知的年份补。
            只输出一个 JSON 对象，不要输出别的文字。格式：
            {"year": 2026,
             "registration": {"start": "日期或 null", "end": "日期或 null", "quote": "原句"} 或 null,
             "contest": {"start": "日期或 null", "end": "日期或 null", "quote": "原句"} 或 null,
             "teamSize": {"min": 数字或 null, "max": 数字, "quote": "原句"} 或 null,
             "sameSchool": {"value": true 或 false, "quote": "原句"} 或 null,
             "advisor": {"value": true 或 false, "quote": "原句"} 或 null}
            registration 是报名时间，contest 是比赛（竞赛）本身进行的时间，teamSize 是每队人数，
            sameSchool 是否要求队员同一所学校，advisor 是否要求（或允许）有指导老师。""";

    private final LlmClient llm;
    private final JsonMapper json;

    public LlmFieldExtractor(LlmClient llm, JsonMapper json) {
        this.llm = llm;
        this.json = json;
    }

    public boolean ready() {
        return llm.ready();
    }

    /** 规则缺了报名截止、比赛时间、人数里的任何一项，才值得问大模型 */
    static boolean worthAsking(Extracted rule) {
        return rule.registrationEnd() == null || rule.contestStart() == null || rule.teamMax() == null;
    }

    /** 大模型抽到的字段（都核对过依据）；连不上或返回的东西用不了时为 null */
    @Override
    public Extracted extract(String title, String text) {
        String content;
        try {
            content = llm.chat(SYSTEM, userPrompt(title, text, llm.maxChars()), 800);
        } catch (IOException e) {
            log.info("大模型请求失败，这篇只用规则抽取：{}", e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        try {
            return parse(content, text, json);
        } catch (IllegalArgumentException e) {
            log.info("大模型的回答用不了，这篇只用规则抽取：{}", e.getMessage());
            return null;
        }
    }

    static String userPrompt(String title, String text, int maxChars) {
        String body = text.length() > maxChars ? text.substring(0, maxChars) + "\n（后面的内容省略）" : text;
        return "通知标题：" + title + "\n通知正文：\n" + body;
    }

    /**
     * 解析大模型的回答，逐项核对依据；核对不过的项丢掉。回答里不是 JSON 时抛 IllegalArgumentException。
     *
     * @param content 大模型回答的原文（可能带 &lt;think&gt; 和多余的文字）
     * @param text    通知正文，核对依据用
     */
    static Extracted parse(String content, String text, JsonMapper json) {
        String s = THINK.matcher(content == null ? "" : content).replaceAll("");
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("回答里没有 JSON");
        }
        JsonNode root;
        try {
            root = json.readTree(s.substring(from, to + 1));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("回答里的 JSON 解析不了");
        }
        String plain = squeeze(text);
        Map<String, String> evidence = new LinkedHashMap<>();

        Integer year = root.path("year").isNumber() ? root.path("year").asInt() : null;
        if (year != null && (year < 2000 || year > 2100)) {
            year = null;
        }

        LocalDateTime regStart = null;
        LocalDateTime regEnd = null;
        JsonNode reg = root.path("registration");
        String regQuote = quote(reg, plain);
        if (regQuote != null) {
            regStart = dateIn(reg.path("start"), regQuote, false);
            regEnd = dateIn(reg.path("end"), regQuote, true);
            if (regStart != null && regEnd != null && regEnd.isBefore(regStart)) {
                regStart = null;
                regEnd = null;
            }
            if (regStart != null || regEnd != null) {
                evidence.put(LLM_PREFIX + "registration", regQuote);
            }
        }

        LocalDateTime contestStart = null;
        LocalDateTime contestEnd = null;
        boolean allDay = false;
        JsonNode contest = root.path("contest");
        String contestQuote = quote(contest, plain);
        if (contestQuote != null) {
            contestStart = dateIn(contest.path("start"), contestQuote, false);
            contestEnd = dateIn(contest.path("end"), contestQuote, false);
            if (contestStart == null || (contestEnd != null && contestEnd.isBefore(contestStart))) {
                contestStart = null;
                contestEnd = null;
            } else {
                allDay = !hasTime(contest.path("start"));
                evidence.put(LLM_PREFIX + "contest", contestQuote);
            }
        }

        Integer teamMin = null;
        Integer teamMax = null;
        JsonNode team = root.path("teamSize");
        String teamQuote = quote(team, plain);
        if (teamQuote != null && team.path("max").isNumber()) {
            int max = team.path("max").asInt();
            int min = team.path("min").isNumber() ? team.path("min").asInt() : 1;
            if (min >= 1 && max >= min && max <= 50 && mentionsNumber(teamQuote, max)) {
                teamMin = min;
                teamMax = max;
                evidence.put(LLM_PREFIX + "teamSize", teamQuote);
            }
        }

        Boolean sameSchool = flag(root.path("sameSchool"), plain, "sameSchool", evidence);
        Boolean advisor = flag(root.path("advisor"), plain, "advisor", evidence);
        return new Extracted(year, year == null ? null : year + " 年", regStart, regEnd, contestStart, contestEnd, allDay,
                teamMin, teamMax, sameSchool, advisor, evidence, "llm");
    }

    /**
     * 规则的结果为主，大模型只补规则没抽到的那几组（报名、比赛、人数、同校、指导老师、年份）。
     * 补了任何一组，extractor 记成 rule+llm。
     */
    static Extracted merge(Extracted rule, Extracted llm) {
        Map<String, String> evidence = new LinkedHashMap<>(rule.evidence() == null ? Map.of() : rule.evidence());
        boolean used = false;

        LocalDateTime regStart = rule.registrationStart();
        LocalDateTime regEnd = rule.registrationEnd();
        if (regEnd == null && llm.registrationEnd() != null) {
            regEnd = llm.registrationEnd();
            regStart = regStart != null ? regStart : llm.registrationStart();
            used |= copy(llm, "registration", evidence);
        }
        LocalDateTime contestStart = rule.contestStart();
        LocalDateTime contestEnd = rule.contestEnd();
        boolean allDay = rule.contestAllDay();
        if (contestStart == null && llm.contestStart() != null) {
            contestStart = llm.contestStart();
            contestEnd = llm.contestEnd();
            allDay = llm.contestAllDay();
            used |= copy(llm, "contest", evidence);
        }
        Integer teamMin = rule.teamMin();
        Integer teamMax = rule.teamMax();
        if (teamMax == null && llm.teamMax() != null) {
            teamMin = llm.teamMin();
            teamMax = llm.teamMax();
            used |= copy(llm, "teamSize", evidence);
        }
        Boolean sameSchool = rule.sameSchoolRequired();
        if (sameSchool == null && llm.sameSchoolRequired() != null) {
            sameSchool = llm.sameSchoolRequired();
            used |= copy(llm, "sameSchool", evidence);
        }
        Boolean advisor = rule.advisorRequired();
        if (advisor == null && llm.advisorRequired() != null) {
            advisor = llm.advisorRequired();
            used |= copy(llm, "advisor", evidence);
        }
        Integer year = rule.year() != null ? rule.year() : llm.year();
        String editionTitle = rule.editionTitle() != null ? rule.editionTitle() : llm.editionTitle();
        return new Extracted(year, editionTitle, regStart, regEnd, contestStart, contestEnd, allDay, teamMin, teamMax,
                sameSchool, advisor, evidence, used ? "rule+llm" : rule.extractor());
    }

    private static boolean copy(Extracted llm, String key, Map<String, String> evidence) {
        String quote = llm.evidence().get(LLM_PREFIX + key);
        if (quote != null) {
            evidence.put(LLM_PREFIX + key, quote);
        }
        return true;
    }

    /** 依据：原文里真的有这句话（不计空白）才算，否则这一项不要 */
    private static String quote(JsonNode node, String plainText) {
        if (!node.isObject()) {
            return null;
        }
        String q = node.path("quote").asString("").strip();
        if (q.isEmpty() || !plainText.contains(squeeze(q))) {
            return null;
        }
        return q.length() > 160 ? q.substring(0, 160) + "…" : q;
    }

    private static Boolean flag(JsonNode node, String plainText, String key, Map<String, String> evidence) {
        String q = quote(node, plainText);
        if (q == null || !node.path("value").isBoolean()) {
            return null;
        }
        evidence.put(LLM_PREFIX + key, q);
        return node.path("value").booleanValue();
    }

    /** 大模型给的日期，并且依据里能看到这个日期的月和日；只有日期的，结束取当天 23:59:59 */
    private static LocalDateTime dateIn(JsonNode node, String quote, boolean endOfDay) {
        if (!node.isString()) {
            return null;
        }
        String v = node.asString().strip();
        LocalDateTime t;
        try {
            t = v.length() > 10 ? LocalDateTime.parse(v.replace('T', ' ').substring(0, 16), MINUTE)
                    : LocalDate.parse(v).atTime(endOfDay ? LocalTime.of(23, 59, 59) : LocalTime.MIDNIGHT);
        } catch (DateTimeParseException | IndexOutOfBoundsException e) {
            return null;
        }
        String q = cnToDigits(squeeze(quote));
        boolean month = Pattern.compile("(?<!\\d)0?" + t.getMonthValue() + "(?!\\d)").matcher(q).find();
        boolean day = Pattern.compile("(?<!\\d)0?" + t.getDayOfMonth() + "(?!\\d)").matcher(q).find();
        return month && day ? t : null;
    }

    private static boolean hasTime(JsonNode node) {
        return node.isString() && node.asString().strip().length() > 10;
    }

    /** 依据里有这个人数：阿拉伯数字或者"三人""五名""两人"这样的中文数字 */
    static boolean mentionsNumber(String quote, int n) {
        return Pattern.compile("(?<!\\d)" + n + "(?!\\d)").matcher(cnToDigits(quote.replace('两', '二'))).find();
    }

    /** 把"十月二十日""三名"里的中文数字（一到九十九）换成阿拉伯数字，核对日期和人数用 */
    static String cnToDigits(String s) {
        StringBuilder out = new StringBuilder();
        Matcher m = Pattern.compile("[一二三四五六七八九十]{1,3}").matcher(s);
        while (m.find()) {
            m.appendReplacement(out, String.valueOf(cnNumber(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static int cnNumber(String w) {
        int ten = w.indexOf('十');
        if (ten < 0) {
            int v = 0;
            for (char c : w.toCharArray()) {
                v = v * 10 + CN_DIGITS.indexOf(c);
            }
            return v;
        }
        int tens = ten == 0 ? 1 : CN_DIGITS.indexOf(w.charAt(ten - 1));
        int ones = ten == w.length() - 1 ? 0 : CN_DIGITS.indexOf(w.charAt(ten + 1));
        return tens * 10 + ones;
    }

    /** 去掉所有空白，核对依据时用 */
    static String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

}
