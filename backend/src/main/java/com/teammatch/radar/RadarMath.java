package com.teammatch.radar;

import com.teammatch.radar.RadarDtos.Axis;
import com.teammatch.radar.RadarDtos.Gap;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.radar.RadarDtos.SkillRef;
import com.teammatch.radar.RadarDtos.TeamRadar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 雷达图怎么算分。纯函数，不查库，方便单独测。
 *
 * <p><b>个人</b>：有具体技能的轴按技能算——取这一轴里最高的一项熟练度（入门 40 / 熟练 70 / 精通 100），
 * 同一轴每多一项技能加 5 分（最多加 15），封顶 100。
 *
 * <p>没填技能的轴不会直接给 0：不会一项技能不代表一点都不会。按三种间接依据估一个基础分，
 * 结果里用 {@code basis} 标出来，页面上写「推断」或「基础」，不会冒充实测：
 * <ul>
 *   <li><b>专业</b>：计算机相关给编程开发、数学统计给建模/数据、设计艺术给设计表达、电子自动化给硬件……按 30 估；</li>
 *   <li><b>相关技能外溢</b>：编程很强的人一般也能做点数据处理（各轴之间有固定的折算比例，最高 35 分）；</li>
 *   <li><b>基础分</b>：填过资料的人各轴至少 22，什么都没填的新账号 15。</li>
 * </ul>
 * 有具体技能的轴永远以技能为准（入门 40 也高于任何推断值）。
 *
 * <p><b>需求</b>：每个轴取最高的一项最低要求（入门 55 / 熟练 80 / 精通 100），
 * 同一轴每多一项要求加 5 分（最多加 10）。要求强度比个人能力略高一点，
 * 因为"要求熟练"意味着队伍指望你能独立干活。需求没有"基础分"——没勾要求就是 0。
 */
public final class RadarMath {

    public static final int MAX_SCORE = 100;

    /** 个人轴的来源：SKILL 有具体技能 / INFERRED 按专业或相关技能推断 / BASE 基础分；需求轴是 NEED / NONE */
    public static final String BASIS_SKILL = "SKILL";
    public static final String BASIS_INFERRED = "INFERRED";
    public static final String BASIS_BASE = "BASE";
    public static final String BASIS_NEED = "NEED";
    public static final String BASIS_NONE = "NONE";

    private static final int[] SKILL_POINTS = {0, 40, 70, 100};
    private static final int[] NEED_POINTS = {0, 55, 80, 100};
    private static final String[] BANDS = {"未填", "入门", "熟练", "精通"};
    private static final String[] NEED_BANDS = {"无要求", "要求入门", "要求熟练", "要求精通"};
    private static final int SKILL_BONUS = 5;
    private static final int SKILL_BONUS_CAP = 15;
    private static final int NEED_BONUS = 5;
    private static final int NEED_BONUS_CAP = 10;
    private static final int BASE_EMPTY = 15;
    private static final int BASE_HAS_DATA = 22;
    private static final int MAJOR_PRIOR = 30;
    /** 低于这个分数只算"基础/推断"，不算真的会这一项 */
    private static final int MEANINGFUL = 30;

    /** 各轴之间"会 A 通常也能做点 B"的折算比例：key 是目标轴，值是（来源轴，百分比） */
    private static final Map<RadarAxis, List<Spill>> SPILL = Map.of(
            RadarAxis.CODE, List.of(new Spill(RadarAxis.MODEL, 15), new Spill(RadarAxis.DATA, 12),
                    new Spill(RadarAxis.HARDWARE, 20), new Spill(RadarAxis.DESIGN, 5)),
            RadarAxis.MODEL, List.of(new Spill(RadarAxis.CODE, 22), new Spill(RadarAxis.DATA, 25),
                    new Spill(RadarAxis.WRITE, 10), new Spill(RadarAxis.HARDWARE, 10)),
            RadarAxis.DATA, List.of(new Spill(RadarAxis.CODE, 30), new Spill(RadarAxis.MODEL, 35),
                    new Spill(RadarAxis.WRITE, 10)),
            RadarAxis.DESIGN, List.of(new Spill(RadarAxis.CODE, 8), new Spill(RadarAxis.WRITE, 10)),
            RadarAxis.WRITE, List.of(new Spill(RadarAxis.MODEL, 10), new Spill(RadarAxis.DATA, 10),
                    new Spill(RadarAxis.DESIGN, 15)),
            RadarAxis.HARDWARE, List.of(new Spill(RadarAxis.CODE, 15), new Spill(RadarAxis.MODEL, 10)));

    /** 专业关键词 → 按 30 起估的轴 */
    private static final Map<String, List<RadarAxis>> MAJORS = Map.ofEntries(
            Map.entry("计算机", List.of(RadarAxis.CODE)), Map.entry("软件", List.of(RadarAxis.CODE)),
            Map.entry("人工智能", List.of(RadarAxis.CODE, RadarAxis.MODEL)), Map.entry("网络", List.of(RadarAxis.CODE)),
            Map.entry("信息", List.of(RadarAxis.CODE)), Map.entry("物联网", List.of(RadarAxis.CODE, RadarAxis.HARDWARE)),
            Map.entry("数据", List.of(RadarAxis.DATA)), Map.entry("统计", List.of(RadarAxis.DATA, RadarAxis.MODEL)),
            Map.entry("数学", List.of(RadarAxis.MODEL)), Map.entry("物理", List.of(RadarAxis.MODEL)),
            Map.entry("经济", List.of(RadarAxis.DATA)), Map.entry("金融", List.of(RadarAxis.DATA)),
            Map.entry("管理", List.of(RadarAxis.DATA)),
            Map.entry("设计", List.of(RadarAxis.DESIGN)), Map.entry("艺术", List.of(RadarAxis.DESIGN)),
            Map.entry("广告", List.of(RadarAxis.DESIGN)), Map.entry("数字媒体", List.of(RadarAxis.DESIGN, RadarAxis.CODE)),
            Map.entry("新闻", List.of(RadarAxis.WRITE)), Map.entry("中文", List.of(RadarAxis.WRITE)),
            Map.entry("汉语言", List.of(RadarAxis.WRITE)), Map.entry("法学", List.of(RadarAxis.WRITE)),
            Map.entry("教育", List.of(RadarAxis.WRITE)),
            Map.entry("电子", List.of(RadarAxis.HARDWARE)), Map.entry("电气", List.of(RadarAxis.HARDWARE)),
            Map.entry("自动化", List.of(RadarAxis.HARDWARE)), Map.entry("机械", List.of(RadarAxis.HARDWARE)),
            Map.entry("通信", List.of(RadarAxis.HARDWARE)), Map.entry("材料", List.of(RadarAxis.HARDWARE)),
            Map.entry("能源", List.of(RadarAxis.HARDWARE)));

    private RadarMath() {
    }

    /** 一个人填的一项技能 */
    public record Entry(String name, String category, int level) {
    }

    /** 一条招募需求里的一项技能要求 */
    public record Requirement(String roleName, String skillName, String category, int minLevel) {
    }

    private record Spill(RadarAxis from, int percent) {
    }

    /** 一个人的能力雷达，不知道专业时只看技能 */
    public static Radar person(List<Entry> skills) {
        return person(skills, null);
    }

    /**
     * @param major 专业，用来给没填技能的轴估基础分；没有就传 null
     */
    public static Radar person(List<Entry> skills, String major) {
        Map<RadarAxis, List<Entry>> byAxis = new EnumMap<>(RadarAxis.class);
        for (Entry e : skills) {
            if (e.level() <= 0) {
                continue;
            }
            byAxis.computeIfAbsent(RadarAxis.of(e.name(), e.category()), a -> new ArrayList<>()).add(e);
        }
        Map<RadarAxis, Integer> explicit = new EnumMap<>(RadarAxis.class);
        Map<RadarAxis, Integer> bestLevels = new EnumMap<>(RadarAxis.class);
        for (Map.Entry<RadarAxis, List<Entry>> e : byAxis.entrySet()) {
            List<Entry> list = e.getValue();
            int best = list.stream().mapToInt(s -> Math.min(3, s.level())).max().orElse(0);
            bestLevels.put(e.getKey(), best);
            explicit.put(e.getKey(), clamp(SKILL_POINTS[best]
                    + Math.min(SKILL_BONUS_CAP, SKILL_BONUS * (distinct(list) - 1))));
        }
        Map<RadarAxis, Integer> priors = majorPriors(major);
        int base = skills.isEmpty() && priors.isEmpty() ? BASE_EMPTY : BASE_HAS_DATA;

        List<Axis> axes = new ArrayList<>();
        for (RadarAxis axis : RadarAxis.values()) {
            int skillScore = explicit.getOrDefault(axis, 0);
            int prior = priors.getOrDefault(axis, 0);
            int spill = 0;
            RadarAxis spillFrom = null;
            for (Spill s : SPILL.getOrDefault(axis, List.of())) {
                int from = explicit.getOrDefault(s.from(), 0);
                int v = from * s.percent() / 100;
                if (v > spill) {
                    spill = v;
                    spillFrom = s.from();
                }
            }
            int guess = Math.max(base, Math.max(prior, spill));
            if (skillScore > 0) {
                axes.add(new Axis(axis.key(), axis.label(), axis.hint(), skillScore, BANDS[bestLevels.getOrDefault(axis, 0)],
                        skillRefs(byAxis.getOrDefault(axis, List.of())), List.of(), BASIS_SKILL, null));
                continue;
            }
            boolean inferred = prior > base || spill > base;
            String reason;
            if (prior >= spill && prior > base) {
                reason = "专业是「" + major.strip() + "」，这一类按 " + guess + " 估";
            } else if (spill > base) {
                RadarAxis from = spillFrom;
                reason = "你的「" + from.label() + "」是 " + explicit.get(from) + " 分，这一类按 " + guess + " 估";
            } else {
                reason = "还没有填这一类的技能，按基础 " + guess + " 估";
            }
            axes.add(new Axis(axis.key(), axis.label(), axis.hint(), guess, inferred ? "推断" : "基础",
                    List.of(), List.of(), inferred ? BASIS_INFERRED : BASIS_BASE, reason));
        }

        int count = byAxis.values().stream().mapToInt(List::size).sum();
        Axis strongest = strongestSkill(axes);
        return new Radar(List.copyOf(axes), count,
                strongest == null ? null : strongest.key(),
                strongest == null ? null : strongest.label(),
                personAdvice(axes, count, strongest, major));
    }

    /** 一支队伍还开着的招募需求雷达。needCount 是还开着的需求条数 */
    public static Radar need(List<Requirement> requirements, int needCount) {
        Map<RadarAxis, List<Requirement>> byAxis = new EnumMap<>(RadarAxis.class);
        for (Requirement r : requirements) {
            if (r.minLevel() <= 0) {
                continue;
            }
            byAxis.computeIfAbsent(RadarAxis.of(r.skillName(), r.category()), a -> new ArrayList<>()).add(r);
        }

        List<Axis> axes = new ArrayList<>();
        for (RadarAxis axis : RadarAxis.values()) {
            List<Requirement> list = byAxis.getOrDefault(axis, List.of());
            int best = list.stream().mapToInt(r -> Math.min(3, r.minLevel())).max().orElse(0);
            int score = best == 0 ? 0
                    : clamp(NEED_POINTS[best] + Math.min(NEED_BONUS_CAP, NEED_BONUS * (distinctSkills(list) - 1)));
            axes.add(new Axis(axis.key(), axis.label(), axis.hint(), score, NEED_BANDS[best],
                    needSkillRefs(list), roles(list),
                    score > 0 ? BASIS_NEED : BASIS_NONE, null));
        }

        Axis strongest = strongest(axes);
        return new Radar(List.copyOf(axes), needCount,
                strongest == null ? null : strongest.key(),
                strongest == null ? null : strongest.label(),
                needAdvice(strongest, requirements.isEmpty()));
    }

    /** 需求雷达和个人雷达放一起：算覆盖率、差在哪、给一句话总结 */
    public static TeamRadar team(Radar need, Radar mine) {
        List<Gap> gaps = new ArrayList<>();
        int needSum = 0;
        int coveredSum = 0;
        boolean hasDemand = false;
        boolean hasMine = mine.count() > 0;
        for (Axis n : need.axes()) {
            if (n.score() <= 0) {
                continue;
            }
            hasDemand = true;
            Axis m = mine.axes().stream().filter(a -> a.key().equals(n.key())).findFirst().orElse(null);
            int myScore = m == null ? 0 : m.score();
            needSum += n.score();
            coveredSum += Math.min(myScore, n.score());
            String status = myScore >= n.score() ? "COVERED" : myScore >= MEANINGFUL ? "PARTIAL" : "MISSING";
            gaps.add(new Gap(n.key(), n.label(), n.score(), myScore, status, gapText(n, m, status)));
        }
        gaps.sort(Comparator.comparingInt((Gap g) -> switch (g.status()) {
            case "MISSING" -> 0;
            case "PARTIAL" -> 1;
            default -> 2;
        }).thenComparing(Comparator.comparingInt(Gap::need).reversed()));

        Integer fit = !hasDemand || !hasMine || needSum == 0 ? null
                : (int) Math.round(coveredSum * 100.0 / needSum);
        return new TeamRadar(need, mine, fit, List.copyOf(gaps), teamVerdict(need, mine, fit, gaps));
    }

    private static String gapText(Axis need, Axis mine, String status) {
        String want = need.band();
        String have = mine == null || mine.score() == 0 ? "你还没有这一项"
                : BASIS_SKILL.equals(mine.basis()) ? "你是" + mine.band()
                : BASIS_INFERRED.equals(mine.basis()) ? "你只有按推断估的 " + mine.score() + " 分"
                : "你只有基础分 " + mine.score();
        if ("COVERED".equals(status)) {
            return need.label() + "：" + want + "，" + have;
        }
        if ("MISSING".equals(status)) {
            return need.label() + "：" + want + "，" + have;
        }
        return need.label() + "：" + want + "，" + have + "，还差一档";
    }

    private static String personAdvice(List<Axis> axes, int count, Axis strongest, String major) {
        List<Axis> inferred = axes.stream().filter(a -> !BASIS_SKILL.equals(a.basis())).toList();
        if (count == 0) {
            int base = axes.stream().mapToInt(Axis::score).min().orElse(0);
            boolean anyPrior = axes.stream().anyMatch(a -> BASIS_INFERRED.equals(a.basis()));
            String head = "还没有填具体技能（技能档案是空的）。图上的形状是估出来的，不是 0：各轴 " + base + " 分起";
            if (anyPrior) {
                head += "，专业相关的轴按 30、相关技能外溢的轴也估了分";
            }
            return head + "。填上会的技能和熟练度，就会换成按实际技能算。";
        }
        String head = "你的强项是「" + strongest.label() + "」，由 " + strongest.skills().size()
                + " 项技能撑起来；一共填了 " + count + " 项技能。";
        if (inferred.isEmpty()) {
            return head + "六类能力都有具体技能，队伍缺什么一眼看得出来。";
        }
        String labels = String.join("、", inferred.stream().map(Axis::label).toList());
        return head + labels + "没有填具体技能，按专业和相关技能估了基础分（图上标「推断」「基础」），补上会更准。";
    }

    private static String needAdvice(Axis strongest, boolean empty) {
        if (empty) {
            return "队长还没在招募需求里勾技能要求。写清楚缺谁、要什么技能，才能画出需求雷达图，推荐也会更准。";
        }
        return "这支队最缺的是「" + strongest.label() + "」（" + strongest.band() + "），"
                + "要 " + strongest.skills().size() + " 项技能。";
    }

    private static String teamVerdict(Radar need, Radar mine, Integer fit, List<Gap> gaps) {
        if (need.count() == 0) {
            return "队长还没写技能要求，暂时画不出需求雷达图。";
        }
        if (mine.count() == 0) {
            return "你还没填技能。图上这条线是按基础分估的，补上技能才能算出覆盖率。";
        }
        List<String> covered = gaps.stream().filter(g -> "COVERED".equals(g.status())).map(Gap::label).toList();
        List<String> short_of = gaps.stream().filter(g -> !"COVERED".equals(g.status())).map(Gap::label).toList();
        StringBuilder sb = new StringBuilder("你的能力覆盖了招募需求的 ").append(fit).append("%：");
        if (!covered.isEmpty()) {
            sb.append(String.join("、", covered)).append("达标");
        }
        if (!short_of.isEmpty()) {
            if (!covered.isEmpty()) {
                sb.append("；");
            }
            sb.append(String.join("、", short_of)).append("还差一点");
        }
        sb.append(short_of.isEmpty() ? "，可以试着申请了。" : "，申请时说说你打算怎么补。");
        return sb.toString();
    }

    /** 最强的一轴：优先有具体技能的轴，都没有技能时就是估得最高的那轴 */
    private static Axis strongestSkill(List<Axis> axes) {
        Axis skill = axes.stream().filter(a -> BASIS_SKILL.equals(a.basis()))
                .max(Comparator.comparingInt(Axis::score).thenComparing(a -> -indexOf(a))).orElse(null);
        return skill != null ? skill : strongest(axes);
    }

    private static Axis strongest(List<Axis> axes) {
        return axes.stream().filter(a -> a.score() > 0)
                .max(Comparator.comparingInt(Axis::score).thenComparing(a -> -indexOf(a))).orElse(null);
    }

    private static Map<RadarAxis, Integer> majorPriors(String major) {
        Map<RadarAxis, Integer> priors = new EnumMap<>(RadarAxis.class);
        if (major == null || major.isBlank()) {
            return priors;
        }
        String text = major.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<RadarAxis>> e : MAJORS.entrySet()) {
            if (text.contains(e.getKey())) {
                for (RadarAxis axis : e.getValue()) {
                    priors.put(axis, MAJOR_PRIOR);
                }
            }
        }
        return priors;
    }

    private static int indexOf(Axis axis) {
        RadarAxis[] all = RadarAxis.values();
        for (int i = 0; i < all.length; i++) {
            if (all[i].key().equals(axis.key())) {
                return i;
            }
        }
        return all.length;
    }

    private static int distinct(List<Entry> list) {
        return (int) list.stream().map(Entry::name).distinct().count();
    }

    private static int distinctSkills(List<Requirement> list) {
        return (int) list.stream().map(Requirement::skillName).distinct().count();
    }

    /** 个人雷达轴下的技能：全部列出来，熟练度高的在前 */
    private static List<SkillRef> skillRefs(List<Entry> list) {
        return list.stream()
                .sorted(Comparator.comparingInt(Entry::level).reversed().thenComparing(Entry::name))
                .map(e -> new SkillRef(e.name(), e.category(), Math.min(3, e.level())))
                .toList();
    }

    /** 需求雷达轴下的技能：同一项技能被多条需求提到时取最高的要求 */
    private static List<SkillRef> needSkillRefs(List<Requirement> list) {
        Map<String, SkillRef> best = new LinkedHashMap<>();
        for (Requirement r : list) {
            int level = Math.min(3, r.minLevel());
            SkillRef old = best.get(r.skillName());
            if (old == null || old.level() < level) {
                best.put(r.skillName(), new SkillRef(r.skillName(), r.category(), level));
            }
        }
        return best.values().stream()
                .sorted(Comparator.comparingInt(SkillRef::level).reversed().thenComparing(SkillRef::name))
                .toList();
    }

    private static List<String> roles(List<Requirement> list) {
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        for (Requirement r : list) {
            if (r.roleName() != null && !r.roleName().isBlank()) {
                roles.add(r.roleName().trim());
            }
        }
        return List.copyOf(roles);
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(MAX_SCORE, score));
    }
}
