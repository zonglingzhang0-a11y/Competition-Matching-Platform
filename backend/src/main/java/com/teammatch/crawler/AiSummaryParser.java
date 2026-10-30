package com.teammatch.crawler;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 比赛页 AI 总结的提示词和回答核对。纯计算，方便单元测试。
 * <p>
 * 大模型会编，所以每条要点都要带上原文里的一句话（quote）和出自第几篇通知（source）：
 * <ul>
 *   <li>这句话在那篇通知原文里找不到（不计空白），这条不要；</li>
 *   <li>要点里写的数字（日期、人数、金额）在这句话里找不到，这条不要；年份可以出现在通知别的地方。</li>
 * </ul>
 */
final class AiSummaryParser {

    /** 要点的分类，按这个顺序显示 */
    static final List<String> LABELS = List.of("报名时间", "比赛时间", "参赛对象", "组队要求", "报名方式", "提交作品",
            "赛程安排", "奖项设置", "费用", "其他注意事项");
    static final int MAX_POINTS = 8;
    static final int PER_LABEL = 2;
    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");
    private static final Pattern NUMBER = Pattern.compile("\\d+");

    static final String SYSTEM = """
            你帮大学生整理比赛的官方通知。只根据给出的通知原文，不要猜，原文没写的不要写。
            从这些方面里挑原文写到了的整理成要点：报名时间、比赛时间、参赛对象、组队要求、报名方式、提交作品、赛程安排、奖项设置、费用、其他注意事项。
            每条要点：label 用上面的名字之一；text 用一两句话说清楚（不超过 60 字）；
            quote 是原文里连续出现的一句话，照抄，不要改写（不超过 80 字），要能看出 text 的依据，text 里的日期和数字都要在 quote 里；
            source 是这句话出自第几篇通知（数字）。
            最多 8 条，同一个 label 最多 2 条。只输出一个 JSON 对象，不要输出别的文字：
            {"points": [{"label": "报名时间", "text": "……", "quote": "……", "source": 1}]}""";

    private AiSummaryParser() {
    }

    /** 交给大模型的一篇通知 */
    record Source(long recordId, String title, String text) {
    }

    /** 核对过的一条要点 */
    record Point(String label, String text, String quote, long recordId) {
    }

    /** 几篇通知平分字数，每篇太长就截掉 */
    static String userPrompt(String competition, List<Source> sources, int maxChars) {
        int each = Math.max(500, maxChars / Math.max(1, sources.size()));
        StringBuilder sb = new StringBuilder("比赛：").append(competition).append('\n');
        for (int i = 0; i < sources.size(); i++) {
            Source s = sources.get(i);
            String text = s.text().length() > each ? s.text().substring(0, each) + "\n（后面的内容省略）" : s.text();
            sb.append("\n第 ").append(i + 1).append(" 篇通知：").append(s.title()).append('\n').append(text).append('\n');
        }
        return sb.toString();
    }

    /**
     * 解析大模型的回答，逐条核对，核对不过的丢掉。回答里不是 JSON 时抛 IllegalArgumentException。
     */
    static List<Point> parse(String content, List<Source> sources, JsonMapper json) {
        String s = THINK.matcher(content == null ? "" : content).replaceAll("");
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("回答里没有 JSON");
        }
        JsonNode points;
        try {
            points = json.readTree(s.substring(from, to + 1)).path("points");
        } catch (JacksonException e) {
            throw new IllegalArgumentException("回答里的 JSON 解析不了");
        }
        if (!points.isArray()) {
            throw new IllegalArgumentException("回答里没有 points");
        }
        // 标题也算原文：年份、比赛名常常只写在标题里
        List<String> plain = sources.stream().map(x -> LlmFieldExtractor.squeeze(x.title() + " " + x.text())).toList();
        List<Point> out = new ArrayList<>();
        Map<String, Integer> perLabel = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode p : points) {
            String label = p.path("label").asString("").strip();
            if (!LABELS.contains(label)) {
                label = "其他注意事项";
            }
            String text = p.path("text").asString("").strip();
            String quote = p.path("quote").asString("").strip();
            int src = p.path("source").asInt(0);
            if (text.isEmpty() || quote.length() < 4 || src < 1 || src > sources.size()) {
                continue;
            }
            String sourceText = plain.get(src - 1);
            if (!sourceText.contains(LlmFieldExtractor.squeeze(quote)) || !numbersBacked(text, quote, sourceText)) {
                continue;
            }
            if (!seen.add(label + "|" + text) || perLabel.merge(label, 1, Integer::sum) > PER_LABEL) {
                continue;
            }
            out.add(new Point(label, cut(text, 120), cut(quote, 160), sources.get(src - 1).recordId()));
            if (out.size() == MAX_POINTS) {
                break;
            }
        }
        out.sort(Comparator.comparingInt(x -> LABELS.indexOf(x.label())));
        return out;
    }

    /** 要点里的每个数字都要在依据里出现；四位数的年份在通知别的地方出现也行 */
    static boolean numbersBacked(String text, String quote, String sourceText) {
        String q = LlmFieldExtractor.cnToDigits(LlmFieldExtractor.squeeze(quote).replace('两', '二'));
        String all = LlmFieldExtractor.cnToDigits(sourceText.replace('两', '二'));
        Matcher m = NUMBER.matcher(LlmFieldExtractor.cnToDigits(text.replace('两', '二')));
        while (m.find()) {
            String n = m.group();
            boolean inQuote = Pattern.compile("(?<!\\d)" + n + "(?!\\d)").matcher(q).find();
            boolean yearElsewhere = n.length() == 4 && Pattern.compile("(?<!\\d)" + n + "(?!\\d)").matcher(all).find();
            if (!inQuote && !yearElsewhere) {
                return false;
            }
        }
        return true;
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
