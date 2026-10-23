package com.teammatch.ai;

import java.util.List;

/**
 * 送去分析的事实。个人分析和队伍建议都用这一套结构：只放和组队有关的信息，
 * 不放邮箱、联系方式、真实姓名，也不把问卷原始答案整段发出去（见 {@link SurveyText}）。
 */
public final class AiFacts {

    private AiFacts() {
    }

    /** 一项技能（个人档案或招募需求里的），level 是熟练度 1–3 */
    public record SkillFact(String name, String category, int level) {
    }

    /** 能力轴分数，和雷达图一致 */
    public record AxisScore(String key, String label, int score) {
    }

    /**
     * @param selfIntro        问卷开放题"我能给队伍带来的三点"
     * @param surveyLines      问卷里和组队有关的几道题，已经转成"题目：答案"的文字
     * @param personalityLines 只取协作、角色这几道题的答案，页面上的"合作风格"用它
     * @param experiences      履历，一行一条（比赛 + 年份 + 奖项）
     */
    public record ProfileFacts(String nickname, String major, String degree, String bio, String selfIntro,
                               List<SkillFact> skills, List<String> experiences, List<String> surveyLines,
                               List<String> personalityLines) {

        /** 核对"依据"用：所有送出去的原文拼在一起 */
        public String sourceText() {
            StringBuilder sb = new StringBuilder();
            append(sb, major);
            append(sb, degree);
            append(sb, bio);
            append(sb, selfIntro);
            skills.forEach(s -> append(sb, s.name()));
            experiences.forEach(e -> append(sb, e));
            surveyLines.forEach(l -> append(sb, l));
            personalityLines.forEach(l -> append(sb, l));
            return sb.toString();
        }

        private static void append(StringBuilder sb, String s) {
            if (s != null && !s.isBlank()) {
                sb.append(s).append('\n');
            }
        }
    }

    /** 一条还开着的招募需求 */
    public record NeedFact(String roleName, int headcount, int filled, String note, List<SkillFact> skills) {
    }

    /**
     * @param radar 这个人的六维雷达（只有分数，不带技能细节）
     * @param traits 问卷里和合作有关的特点，一行一条
     */
    public record MemberFact(String nickname, List<SkillFact> skills, List<String> traits, List<AxisScore> radar) {
    }

    /**
     * @param needAxes  招募需求雷达（六个轴，0–100）
     * @param bestAxes  队里每个人同一轴上最高的分数
     * @param skillGaps 每项需求技能和队里最好水平的差距
     */
    public record TeamFacts(String teamName, String competitionName, String editionTitle,
                            List<NeedFact> needs, List<MemberFact> members,
                            List<AxisScore> needAxes, List<AxisScore> bestAxes, List<AiDtos.SkillGap> skillGaps) {
    }
}
