package com.teammatch.crawler;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用规则从比赛通知里抽字段：届次年份、报名起止、比赛起止、每队人数、是否限同校、是否必须有指导老师。
 * <p>
 * 做法是先把正文按句子切开，每个字段挑"关键词最贴切、又带日期 / 数字"的那句来解析，并记下原句给管理员核对。
 * 规则是对着数模、电赛这些官网的真实通知写的，写法差得太远的抽不出来就留空，由管理员补。
 */
@Component
public class RuleFieldExtractor implements FieldExtractor {

    private static final String NUM = "(\\d{1,2}|[一二三四五六七八九十])";

    /** 2025年7月30日（周三）8:00、7月30日、8月2日20时；年份、星期、时间都可省 */
    private static final Pattern DATE = Pattern.compile(
            "(?:(20\\d{2})\\s*年\\s*)?(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号](?:\\s*[(（][^)）]{0,6}[)）])?"
                    + "\\s*(?:(\\d{1,2})\\s*(?::|时|点)\\s*(\\d{1,2})?\\s*分?)?");
    /** 2025-07-30、2025/7/30 08:00 */
    private static final Pattern ISO_DATE = Pattern.compile(
            "(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?");
    /** 区间的简写："8月23日—27日" 后面的 "—27日" */
    private static final Pattern RANGE_DAY = Pattern.compile(
            "\\s*(?:[—–\\-~～]|至|到)\\s*(\\d{1,2})\\s*[日号](?:\\s*[(（][^)）]{0,6}[)）])?"
                    + "\\s*(?:(\\d{1,2})\\s*(?::|时|点)\\s*(\\d{1,2})?)?");
    private static final Pattern RANGE_SEP = Pattern.compile("[—–\\-~～]|至|到");
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*年");
    /** 标题里不带"年"的年份，如 4C2025、2026 中国大学生… */
    private static final Pattern BARE_YEAR = Pattern.compile("(?<!\\d)(20\\d{2})(?!\\d)");

    private static final Pattern CONTEST_KEY = Pattern.compile("竞赛时间|比赛时间|竞赛日期|比赛日期|决赛|竞赛于|比赛于|举行|开赛|举办时间");

    private static final Pattern TEAM_RANGE = Pattern.compile(NUM + "\\s*(?:[—–\\-~～]|至|到)\\s*" + NUM + "\\s*(?:名|人)");
    private static final Pattern TEAM_EXACT = Pattern.compile(NUM + "\\s*人\\s*(?:一|1)\\s*(?:队|组)|由\\s*" + NUM + "\\s*名(?:同学|学生|队员)组成");
    private static final Pattern TEAM_EACH = Pattern.compile(
            "每(?:个|支)?(?:参赛)?(?:队|团队|小组)(?:伍)?(?:成员|人数)?(?:由|为)?\\s*(不超过|不多于|最多|至多|不少于|至少)?\\s*"
                    + NUM + "\\s*(?:名|人|位)");
    private static final Pattern TEAM_LIMIT = Pattern.compile(
            "(?:团队|队伍|参赛队)(?:人数|成员)?\\s*(不超过|不多于|最多|至多)\\s*" + NUM + "\\s*(?:名|人)");

    /** 注意"同一所学校获得全国一、二等奖的总队数"这种评奖规则不能算 */
    private static final Pattern SAME_SCHOOL_YES = Pattern.compile(
            "校内(?:自愿)?组(?:合|队)|同校(?:学生)?(?:自愿)?组(?:队|合)|(?:须|必须|应|均)(?:为|是)?同一(?:所)?(?:高校|学校)"
                    + "|不得跨校|不允许跨校|同一(?:所)?(?:高校|学校)的?(?:在校|在籍)?(?:学生|同学)(?:组成|组队)");
    private static final Pattern SAME_SCHOOL_NO = Pattern.compile("(?:允许|可以|可)跨校");

    private static final Pattern ADVISOR_YES = Pattern.compile(
            "(?:须|必须|应|需|需要)(?:配备|配有|有|设)?\\s*(?:\\d|[一二])?\\s*(?:名|位)?\\s*(?:赛前)?指导(?:教师|老师)");
    private static final Pattern ADVISOR_NO = Pattern.compile(
            "(?:可|可以)(?:配备|配|设|有|聘请)[^。]{0,10}?指导(?:教师|老师)|不超过\\s*\\d\\s*名[^。]{0,6}指导(?:教师|老师)"
                    + "|指导(?:教师|老师)(?:不是必须|非必须|可选)");

    record DateHit(int start, int end, LocalDate date, LocalTime time, boolean explicitYear) {
    }

    @Override
    public Extracted extract(String title, String text) {
        String t = normalize(title == null ? "" : title);
        String body = normalize(text == null ? "" : text);
        List<String> sentences = sentences(body);
        Map<String, String> evidence = new LinkedHashMap<>();

        // 标题里的年份最可靠；标题没有就取正文里最晚的年份（正文常回顾往届，如"大赛始于2008年"）
        Integer year = firstYear(t);
        if (year == null) {
            year = latestYear(body);
        }

        // ---------- 报名 ----------
        LocalDateTime regStart = null;
        LocalDateTime regEnd = null;
        String regSentence = best(sentences, year, s -> {
            if (!s.contains("报名") || s.contains("报名费") || s.contains("征题")) {
                return 0;
            }
            return 1 + (s.matches(".*(报名时间|报名截止|截止报名|报名日期|报名起止).*") ? 3 : 0);
        });
        if (regSentence != null) {
            List<DateHit> hits = dates(regSentence, year);
            if (hits.size() >= 2 && isRange(regSentence, hits.get(0), hits.get(1))) {
                regStart = at(hits.get(0), LocalTime.MIDNIGHT);
                regEnd = at(hits.get(1), LocalTime.of(23, 59, 59));
            } else {
                regEnd = at(hits.getLast(), LocalTime.of(23, 59, 59));
            }
            evidence.put("registration", clip(regSentence));
        }

        // ---------- 比赛 ----------
        LocalDateTime contestStart = null;
        LocalDateTime contestEnd = null;
        boolean allDay = false;
        String contestSentence = best(sentences, year, s -> {
            boolean timeKey = s.matches(".*(竞赛时间|比赛时间|竞赛日期|比赛日期).*");
            if (!CONTEST_KEY.matcher(s).find() || (s.contains("报名") && !timeKey)) {
                return 0;
            }
            int score = 1;
            if (timeKey) {
                score += 3;
            }
            if (s.contains("举行") || s.contains("开赛")) {
                score += 2;
            }
            return score;
        });
        if (contestSentence != null) {
            List<DateHit> hits = dates(contestSentence, year);
            DateHit first = hits.getFirst();
            DateHit second = hits.size() >= 2 && isRange(contestSentence, first, hits.get(1)) ? hits.get(1) : null;
            allDay = first.time() == null && (second == null || second.time() == null);
            if (allDay) {
                // 全天的节点和种子数据一样：开始、结束都记成当天 0 点
                contestStart = first.date().atStartOfDay();
                contestEnd = (second == null ? first.date() : second.date()).atStartOfDay();
            } else {
                contestStart = at(first, LocalTime.MIDNIGHT);
                contestEnd = second == null ? null : at(second, LocalTime.of(23, 59, 59));
            }
            evidence.put("contest", clip(contestSentence));
        }

        // ---------- 每队人数 ----------
        Integer teamMin = null;
        Integer teamMax = null;
        for (String s : sentences) {
            Matcher m;
            if ((m = TEAM_RANGE.matcher(s)).find() && s.contains("队")) {
                teamMin = num(m.group(1));
                teamMax = num(m.group(2));
            } else if ((m = TEAM_EXACT.matcher(s)).find()) {
                teamMin = teamMax = num(m.group(1) != null ? m.group(1) : m.group(2));
            } else if ((m = TEAM_EACH.matcher(s)).find()) {
                int n = num(m.group(2));
                String q = m.group(1);
                if (q == null) {
                    teamMin = teamMax = n;
                } else if (q.equals("不少于") || q.equals("至少")) {
                    teamMin = n;
                } else {
                    teamMin = 1;
                    teamMax = n;
                }
            } else if ((m = TEAM_LIMIT.matcher(s)).find()) {
                teamMin = 1;
                teamMax = num(m.group(2));
            } else {
                continue;
            }
            if (!valid(teamMin) || !valid(teamMax) || (teamMin != null && teamMax != null && teamMin > teamMax)) {
                teamMin = teamMax = null;
                continue;
            }
            evidence.put("teamSize", clip(s));
            break;
        }

        // ---------- 同校、指导老师 ----------
        Boolean sameSchool = flag(sentences, SAME_SCHOOL_YES, SAME_SCHOOL_NO, "sameSchool", evidence);
        Boolean advisor = flag(sentences, ADVISOR_YES, ADVISOR_NO, "advisor", evidence);

        return new Extracted(year, year == null ? null : year + " 年", regStart, regEnd, contestStart, contestEnd, allDay,
                teamMin, teamMax, sameSchool, advisor, evidence, "rule");
    }

    // ---------- 日期 ----------

    /** 一句话里的所有日期，按出现顺序；没写年份的沿用前一个日期或通知的年份 */
    static List<DateHit> dates(String s, Integer defaultYear) {
        List<DateHit> hits = new ArrayList<>();
        Integer lastYear = null;
        LocalDate previous = null;
        // 两种写法的日期按出现位置合在一起
        List<int[]> spans = new ArrayList<>();
        List<Object[]> raw = new ArrayList<>();
        Matcher m = DATE.matcher(s);
        while (m.find()) {
            raw.add(new Object[]{m.start(), m.end(), m.group(1), m.group(2), m.group(3), m.group(4), m.group(5)});
        }
        Matcher iso = ISO_DATE.matcher(s);
        while (iso.find()) {
            raw.add(new Object[]{iso.start(), iso.end(), iso.group(1), iso.group(2), iso.group(3), iso.group(4), iso.group(5)});
        }
        raw.sort((a, b) -> Integer.compare((int) a[0], (int) b[0]));
        for (Object[] r : raw) {
            int start = (int) r[0];
            int end = (int) r[1];
            if (!spans.isEmpty() && start < spans.getLast()[1]) {
                continue;
            }
            Integer year = r[2] != null ? Integer.valueOf((String) r[2]) : lastYear != null ? lastYear : defaultYear;
            if (year == null) {
                continue;
            }
            LocalDate date = date(year, (String) r[3], (String) r[4]);
            if (date == null) {
                continue;
            }
            boolean explicit = r[2] != null;
            if (!explicit && previous != null && date.isBefore(previous)) {
                // 12月28日至1月3日：跨年
                date = date.plusYears(1);
            }
            hits.add(new DateHit(start, end, date, time((String) r[5], (String) r[6]), explicit));
            spans.add(new int[]{start, end});
            lastYear = date.getYear();
            previous = date;

            // 8月23日—27日：后面的"27日"沿用同一个月
            Matcher range = RANGE_DAY.matcher(s);
            range.region(end, s.length());
            if (range.lookingAt()) {
                LocalDate second = date(date.getYear(), String.valueOf(date.getMonthValue()), range.group(1));
                if (second != null && !second.isBefore(date)) {
                    hits.add(new DateHit(range.start(1), range.end(), second, time(range.group(2), range.group(3)), false));
                    spans.add(new int[]{end, range.end()});
                    previous = second;
                }
            }
        }
        return hits;
    }

    private static boolean isRange(String s, DateHit a, DateHit b) {
        if (b.start() < a.end()) {
            return false;
        }
        String between = s.substring(a.end(), Math.max(a.end(), b.start()));
        return between.length() <= 12 && RANGE_SEP.matcher(between).find();
    }

    private static LocalDate date(int year, String month, String day) {
        try {
            return LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 24 时记成当天 23:59:59；没写时间返回 null */
    private static LocalTime time(String hour, String minute) {
        if (hour == null) {
            return null;
        }
        int h = Integer.parseInt(hour);
        int min = minute == null ? 0 : Integer.parseInt(minute);
        if (h == 24 && min == 0) {
            return LocalTime.of(23, 59, 59);
        }
        if (h > 23 || min > 59) {
            return null;
        }
        return LocalTime.of(h, min);
    }

    private static LocalDateTime at(DateHit hit, LocalTime fallback) {
        return hit.date().atTime(hit.time() == null ? fallback : hit.time());
    }

    // ---------- 句子 ----------

    private interface Scorer {
        int score(String sentence);
    }

    /** 分数最高、而且带日期的那句；分数一样取靠前的 */
    private static String best(List<String> sentences, Integer year, Scorer scorer) {
        String best = null;
        int bestScore = 0;
        for (String s : sentences) {
            int score = scorer.score(s);
            if (score > bestScore && !dates(s, year).isEmpty()) {
                best = s;
                bestScore = score;
            }
        }
        return best;
    }

    private static Boolean flag(List<String> sentences, Pattern yes, Pattern no, String key, Map<String, String> evidence) {
        for (String s : sentences) {
            if (yes.matcher(s).find()) {
                evidence.put(key, clip(s));
                return true;
            }
            if (no.matcher(s).find()) {
                evidence.put(key, clip(s));
                return false;
            }
        }
        return null;
    }

    static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        for (String s : text.split("[。；;！!\\n]+")) {
            String trimmed = s.strip();
            if (!trimmed.isEmpty()) {
                out.add(trimmed.length() > 400 ? trimmed.substring(0, 400) : trimmed);
            }
        }
        return out;
    }

    /** 全角数字、冒号、空格换成半角，日期正则好写 */
    static String normalize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) (c - '０' + '0'));
            } else if (c == '：') {
                sb.append(':');
            } else if (c == '　' || c == ' ') {
                sb.append(' ');
            } else if (c == '－') {
                sb.append('-');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static Integer firstYear(String s) {
        Matcher m = YEAR.matcher(s);
        if (m.find()) {
            return Integer.valueOf(m.group(1));
        }
        Matcher bare = BARE_YEAR.matcher(s);
        return bare.find() ? Integer.valueOf(bare.group(1)) : null;
    }

    private static Integer latestYear(String s) {
        Matcher m = YEAR.matcher(s);
        Integer latest = null;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            latest = latest == null ? y : Math.max(latest, y);
        }
        return latest;
    }

    private static int num(String s) {
        int i = "一二三四五六七八九十".indexOf(s);
        return i >= 0 ? i + 1 : Integer.parseInt(s);
    }

    private static boolean valid(Integer n) {
        return n == null || (n >= 1 && n <= 30);
    }

    private static String clip(String s) {
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }
}
