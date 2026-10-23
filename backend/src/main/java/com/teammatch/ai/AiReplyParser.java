package com.teammatch.ai;

import com.teammatch.ai.AiDtos.AxisHint;
import com.teammatch.ai.AiDtos.ProfileBody;
import com.teammatch.ai.AiDtos.Strength;
import com.teammatch.ai.AiDtos.SuggestedSkill;
import com.teammatch.ai.AiDtos.TeamBody;
import com.teammatch.ai.AiDtos.Trait;
import com.teammatch.ai.AiFacts.ProfileFacts;
import com.teammatch.ai.AiFacts.SkillFact;
import com.teammatch.ai.AiFacts.TeamFacts;
import com.teammatch.radar.RadarAxis;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 解析模型回答，并逐项核对：每条优势、性格、技能都要带资料里的原话当依据，
 * 依据在送出去的资料里找不到就丢掉；技能名不在技能字典里、等级不在 1–3 的也丢掉。
 * 和爬虫那边一样，宁可少几条，也不让编出来的东西进档案。
 */
public final class AiReplyParser {

    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");

    private AiReplyParser() {
    }

    /**
     * @param content     模型回答（可能带 &lt;think&gt;、```json 包裹）
     * @param facts       送出去的资料，核对依据用
     * @param categories  技能字典：名字 → 类别
     */
    public static ProfileBody parseProfile(String content, ProfileFacts facts, Map<String, String> categories,
                                           JsonMapper json) {
        JsonNode root = root(content, json);
        String plain = squeeze(facts.sourceText());
        Set<String> ownSkillNames = new LinkedHashSet<>();
        facts.skills().forEach(s -> ownSkillNames.add(normalize(s.name())));

        String summary = cut(root.path("summary").asString(""), 240);
        List<Strength> strengths = new ArrayList<>();
        for (JsonNode n : array(root.path("strengths"))) {
            String title = cut(n.path("title").asString(""), 30);
            String detail = cut(n.path("detail").asString(""), 100);
            String evidence = evidence(n, plain);
            if (!title.isBlank() && evidence != null) {
                strengths.add(new Strength(title, detail, evidence));
            }
            if (strengths.size() >= 3) {
                break;
            }
        }

        List<SuggestedSkill> skills = new ArrayList<>();
        Map<String, SuggestedSkill> byName = new LinkedHashMap<>();
        for (JsonNode n : array(root.path("skills"))) {
            String rawName = n.path("name").asString("").strip();
            String name = lookup(categories, rawName);
            if (name == null) {
                continue;
            }
            int level = level(n.path("level"));
            String evidence = evidence(n, plain);
            if (level == 0 || evidence == null) {
                continue;
            }
            SuggestedSkill old = byName.get(name);
            if (old != null && old.level() >= level) {
                continue;
            }
            byName.put(name, new SuggestedSkill(name, categories.get(name), level, evidence,
                    ownSkillNames.contains(normalize(name))));
        }
        skills.addAll(byName.values());
        if (skills.size() > 6) {
            skills = skills.subList(0, 6);
        }

        List<Trait> personality = new ArrayList<>();
        for (JsonNode n : array(root.path("personality"))) {
            String trait = cut(n.path("trait").asString(""), 30);
            String evidence = evidence(n, plain);
            if (!trait.isBlank() && evidence != null) {
                personality.add(new Trait(trait, evidence));
            }
            if (personality.size() >= 4) {
                break;
            }
        }

        List<AxisHint> hints = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode n : array(root.path("axisHints"))) {
            RadarAxis axis = axis(n.path("axis").asString(""));
            String reason = cut(n.path("reason").asString(""), 120);
            if (axis == null || reason.isBlank() || !seen.add(axis.key())) {
                continue;
            }
            hints.add(new AxisHint(axis.key(), axis.label(), reason));
            if (hints.size() >= 3) {
                break;
            }
        }

        if (summary.isBlank() && strengths.isEmpty() && personality.isEmpty() && skills.isEmpty()) {
            throw new IllegalArgumentException("回答里没有能用的内容");
        }
        return new ProfileBody(summary, strengths, skills, personality, hints);
    }

    public static TeamBody parseTeam(String content, TeamFacts facts, JsonMapper json) {
        JsonNode root = root(content, json);
        String summary = cut(root.path("summary").asString(""), 400);

        List<AiDtos.AxisGap> weak = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode n : array(root.path("weakAxes"))) {
            RadarAxis axis = axis(n.path("axis").asString(""));
            if (axis == null || !seen.add(axis.key())) {
                continue;
            }
            AiFacts.AxisScore needAxis = facts.needAxes().stream()
                    .filter(a -> a.key().equals(axis.key())).findFirst().orElse(null);
            if (needAxis == null || needAxis.score() <= 0) {
                continue;
            }
            AiFacts.AxisScore bestAxis = facts.bestAxes().stream()
                    .filter(a -> a.key().equals(axis.key())).findFirst().orElse(null);
            int need = needAxis.score();
            int best = bestAxis == null ? 0 : bestAxis.score();
            // 本地已经算过队里够不够，够的轴不算"缺"，避免模型把达标的说成缺口
            if (best >= need) {
                continue;
            }
            weak.add(new AiDtos.AxisGap(axis.key(), axis.label(), need, best, best > 0 ? "PARTIAL" : "MISSING"));
            if (weak.size() >= 4) {
                break;
            }
        }

        List<String> suggestions = strings(root.path("suggestions"), 4, 140);
        List<String> candidate = strings(root.path("candidateProfile"), 4, 100);
        List<String> questions = strings(root.path("questions"), 4, 100);
        if (summary.isBlank() && suggestions.isEmpty() && candidate.isEmpty()) {
            throw new IllegalArgumentException("回答里没有能用的招募建议");
        }
        return new TeamBody(summary, weak, suggestions, candidate, questions, facts.skillGaps());
    }

    private static JsonNode root(String content, JsonMapper json) {
        String s = THINK.matcher(content == null ? "" : content).replaceAll("");
        int from = s.indexOf('{');
        int to = s.lastIndexOf('}');
        if (from < 0 || to <= from) {
            throw new IllegalArgumentException("回答里没有 JSON");
        }
        try {
            return json.readTree(s.substring(from, to + 1));
        } catch (JacksonException e) {
            throw new IllegalArgumentException("回答里的 JSON 解析不了");
        }
    }

    private static List<JsonNode> array(JsonNode node) {
        List<JsonNode> out = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(out::add);
        }
        return out;
    }

    private static String evidence(JsonNode node, String plainSource) {
        String q = node.path("evidence").asString("").strip();
        if (q.isEmpty()) {
            return null;
        }
        return plainSource.contains(squeeze(q)) ? cut(q, 100) : null;
    }

    private static String lookup(Map<String, String> categories, String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return null;
        }
        String name = rawName.strip();
        if (categories.containsKey(name)) {
            return name;
        }
        String norm = normalize(name);
        return categories.keySet().stream().filter(k -> normalize(k).equals(norm)).findFirst().orElse(null);
    }

    private static int level(JsonNode node) {
        if (!node.isNumber()) {
            return 0;
        }
        int v = node.asInt();
        return v >= 1 && v <= 3 ? v : 0;
    }

    private static RadarAxis axis(String value) {
        if (value == null) {
            return null;
        }
        String key = value.strip().toUpperCase(Locale.ROOT);
        for (RadarAxis axis : RadarAxis.values()) {
            if (axis.key().equals(key) || axis.label().equals(value.strip())) {
                return axis;
            }
        }
        return null;
    }

    private static List<String> strings(JsonNode node, int max, int maxLen) {
        List<String> out = new ArrayList<>();
        for (JsonNode n : array(node)) {
            String s = cut(n.asString(""), maxLen);
            if (!s.isBlank()) {
                out.add(s);
            }
            if (out.size() >= max) {
                break;
            }
        }
        return out;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String squeeze(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private static String cut(String s, int max) {
        String t = s == null ? "" : s.strip();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
