package com.teammatch.ai;

import com.teammatch.ai.AiDtos.AxisGap;
import com.teammatch.ai.AiDtos.AxisHint;
import com.teammatch.ai.AiDtos.ProfileBody;
import com.teammatch.ai.AiDtos.SkillGap;
import com.teammatch.ai.AiDtos.Strength;
import com.teammatch.ai.AiDtos.TeamBody;
import com.teammatch.ai.AiDtos.Trait;
import com.teammatch.ai.AiFacts.AxisScore;
import com.teammatch.ai.AiFacts.MemberFact;
import com.teammatch.ai.AiFacts.NeedFact;
import com.teammatch.ai.AiFacts.ProfileFacts;
import com.teammatch.ai.AiFacts.SkillFact;
import com.teammatch.ai.AiFacts.TeamFacts;
import com.teammatch.radar.RadarDtos.Axis;
import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.radar.RadarDtos.SkillRef;
import com.teammatch.radar.RadarMath;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 没配模型、模型连不上、回答用不了时的本地规则分析。
 * 不联网、不花钱，结果也能看：强项从技能算，性格从问卷答案摘，缺口从需求雷达算。
 * 页面上会标明这一份是"本地规则"，不是 AI 生成的。
 */
public final class RuleAnalysis {

    static final String[] LEVELS = {"没填", "入门", "熟练", "精通"};

    private RuleAnalysis() {
    }

    public static ProfileBody profile(ProfileFacts facts, Radar radar) {
        String nickname = facts.nickname() == null || facts.nickname().isBlank() ? "你" : facts.nickname();
        List<Axis> filled = radar.axes().stream().filter(a -> RadarMath.BASIS_SKILL.equals(a.basis()))
                .sorted(Comparator.comparingInt(Axis::score).reversed()).toList();
        List<Axis> guessed = radar.axes().stream().filter(a -> !RadarMath.BASIS_SKILL.equals(a.basis())).toList();

        List<Strength> strengths = new ArrayList<>();
        for (Axis axis : filled.stream().limit(2).toList()) {
            String detail = axis.skills().isEmpty() ? "这一类有基础" : "会 " + skillRefText(axis.skills());
            strengths.add(new Strength(axis.label(), detail,
                    "档案里的技能：" + (axis.skills().isEmpty() ? axis.label() : skillRefText(axis.skills()))));
        }

        List<Trait> personality = new ArrayList<>();
        for (String line : facts.personalityLines().stream().limit(3).toList()) {
            personality.add(new Trait(traitText(line), "问卷：" + cut(line, 80)));
        }

        List<AxisHint> hints = new ArrayList<>();
        for (Axis axis : guessed.stream().limit(3).toList()) {
            hints.add(new AxisHint(axis.key(), axis.label(),
                    "还没填具体技能，图上按 " + axis.score() + " 分估的（" + axis.band() + "）。"
                            + "竞赛里常见，补一项就会按实际技能算。"));
        }

        String summary;
        if (filled.isEmpty()) {
            summary = nickname + "还没有填具体技能。图上的形状不是 0，是填过资料的基础分，补上会的技能和熟练度就会按实际算。";
        } else {
            summary = nickname + "的能力集中在「" + filled.get(0).label() + "」，共 " + facts.skills().size()
                    + " 项技能、 " + facts.experiences().size() + " 条履历。";
            if (!personality.isEmpty()) {
                summary += "问卷里填的是「" + personality.get(0).trait() + "」。";
            }
            if (!guessed.isEmpty()) {
                summary += " " + guessed.stream().map(Axis::label).collect(Collectors.joining("、"))
                        + "没有填具体技能，暂时按基础分估，补上会更准。";
            }
        }
        return new ProfileBody(summary, strengths, List.of(), personality, hints);
    }

    public static TeamBody team(TeamFacts facts) {
        List<AxisGap> gaps = new ArrayList<>();
        for (AxisScore need : facts.needAxes()) {
            if (need.score() <= 0) {
                continue;
            }
            AxisScore best = facts.bestAxes().stream().filter(b -> b.key().equals(need.key())).findFirst()
                    .orElse(new AxisScore(need.key(), need.label(), 0));
            String status = best.score() >= need.score() ? "COVERED" : best.score() > 0 ? "PARTIAL" : "MISSING";
            gaps.add(new AxisGap(need.key(), need.label(), need.score(), best.score(), status));
        }
        gaps.sort(Comparator.comparingInt((AxisGap g) -> switch (g.status()) {
            case "MISSING" -> 0;
            case "PARTIAL" -> 1;
            default -> 2;
        }).thenComparing(Comparator.comparingInt(AxisGap::need).reversed()));
        List<AxisGap> weak = gaps.stream().filter(g -> !"COVERED".equals(g.status())).limit(4).toList();

        List<String> suggestions = new ArrayList<>();
        for (SkillGap gap : facts.skillGaps().stream().filter(g -> !"COVERED".equals(g.status())).limit(3).toList()) {
            suggestions.add("找一位「" + gap.name() + " ≥ " + LEVELS[gap.minLevel()] + "」的人：队里现在"
                    + (gap.bestLevel() == 0 ? "还没人会" : "最高是" + LEVELS[gap.bestLevel()]));
        }
        if (!weak.isEmpty()) {
            AxisGap first = weak.get(0);
            suggestions.add("先补「" + first.label() + "」这一轴：需求 " + first.need() + " 分，队里最高 "
                    + first.best() + " 分" + ("MISSING".equals(first.status()) ? "，这一轴还没有人能顶" : "") + "。");
        } else if (!gaps.isEmpty()) {
            suggestions.add("需求里的技能队里都有人能顶，按缺的人数补齐、把分工写清楚就行。");
        } else {
            suggestions.add("还没写技能要求。先写清楚缺谁、要什么技能，推荐和雷达才有依据。");
        }
        if (!facts.needs().isEmpty()) {
            NeedFact need = facts.needs().get(0);
            suggestions.add("「" + need.roleName() + "」还差 " + Math.max(0, need.headcount() - need.filled())
                    + " 人，可以在申请列表里优先看这个角色的人。");
        }

        List<String> candidate = new ArrayList<>();
        if (!weak.isEmpty()) {
            AxisGap first = weak.get(0);
            List<SkillFact> wanted = new ArrayList<>();
            for (SkillGap gap : facts.skillGaps()) {
                if ("COVERED".equals(gap.status())) {
                    continue;
                }
                facts.needs().stream().flatMap(n -> n.skills().stream())
                        .filter(s -> s.name().equals(gap.name())).findFirst().ifPresent(wanted::add);
            }
            candidate.add("优先找「" + first.label() + "」强的人" + (wanted.isEmpty() ? ""
                    : "，会 " + wanted.stream().map(SkillFact::name).distinct().limit(4)
                    .collect(Collectors.joining("、")) + " 更好"));
            candidate.add("现有队员最好的一轴是 " + topAxis(facts) + "，新人和这块别重复太多");
        } else {
            candidate.add("缺的是人手，不是某一项技能：优先找愿意做「" + roleNames(facts) + "」的人");
        }
        candidate.add("性格上，" + traitHint(facts));

        List<String> questions = List.of(
                "这个方向你做过什么？有没有作品、代码仓库或论文能看一眼？",
                "比赛那几周每周大概能投入多少小时，会不会撞上考试或实习？",
                "如果方案卡住了或者和队友意见不一致，你一般怎么处理？");

        String summary = facts.teamName() + "现在 " + facts.members().size() + " 人、"
                + facts.needs().size() + " 条招募需求。";
        if (!gaps.isEmpty()) {
            summary += "需求最重的是「" + gaps.get(0).label() + "」（" + gaps.get(0).need() + " 分），队里最高 "
                    + gaps.get(0).best() + " 分。";
        }
        if (!facts.members().isEmpty()) {
            summary += "队员：" + facts.members().stream().map(MemberFact::nickname)
                    .collect(Collectors.joining("、")) + "。能力最集中的是 " + topAxis(facts) + "。";
        }
        return new TeamBody(summary, weak, suggestions, candidate, questions, facts.skillGaps());
    }

    private static String topAxis(TeamFacts facts) {
        return facts.bestAxes().stream().max(Comparator.comparingInt(AxisScore::score))
                .map(AxisScore::label).orElse("还没填");
    }

    private static String traitHint(TeamFacts facts) {
        List<String> traits = facts.members().stream().flatMap(m -> m.traits().stream()).toList();
        if (traits.isEmpty()) {
            return "队员还没填问卷，先让他们补一下组队问卷，建议会更准。";
        }
        return "问卷里常见的是「" + traitText(traits.get(0)) + "」，新人和这一点别太冲突。";
    }

    private static String roleNames(TeamFacts facts) {
        return facts.needs().stream().map(NeedFact::roleName).distinct().collect(Collectors.joining("、"));
    }

    static String skillText(List<SkillFact> skills) {
        return skills.stream().limit(3).map(s -> s.name() + "（" + LEVELS[s.level()] + "）")
                .collect(Collectors.joining("、"));
    }

    /** 雷达轴下面的技能和上面的 SkillFact 是两种类型，单独一个方法 */
    static String skillRefText(List<SkillRef> skills) {
        return skills.stream().limit(3).map(s -> s.name() + "（" + LEVELS[s.level()] + "）")
                .collect(Collectors.joining("、"));
    }

    /** "题目：答案"只保留答案，当合作风格的标签 */
    private static String traitText(String line) {
        int i = line.lastIndexOf('：');
        String answer = i >= 0 ? line.substring(i + 1) : line;
        return cut(answer.strip(), 24);
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
