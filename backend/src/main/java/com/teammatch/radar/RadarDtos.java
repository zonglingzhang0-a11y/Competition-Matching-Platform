package com.teammatch.radar;

import java.util.List;

/**
 * 雷达图的数据结构。个人档案页、个人资料页、队伍招募页用的是同一套：
 * 六个轴各 0–100 分，每个轴下面带着归到这个轴的具体技能——雷达图看形状，
 * 轴下面的技能列表看"这个形状是由哪些技能撑起来的"。
 */
public final class RadarDtos {

    private RadarDtos() {
    }

    /**
     * 轴下面的一项具体技能。
     *
     * @param level 个人雷达：自己的熟练度 1–3；需求雷达：要求的熟练度 1–3
     */
    public record SkillRef(String name, String category, int level) {
    }

    /**
     * @param score  0–100，个人是能力强度，需求是要求强度
     * @param band   个人有技能时：入门 / 熟练 / 精通；没技能时：推断 / 基础；需求：要求入门 / 要求熟练 / 要求精通 / 无要求
     * @param skills 归到这个轴的具体技能，按熟练度从高到低
     * @param roles  需求雷达：这一轴对应哪些缺人的角色（个人雷达为空）
     * @param basis  SKILL 有具体技能 / INFERRED 按专业或相关技能推断 / BASE 基础分 / NEED 有要求 / NONE 没要求
     * @param reason 推断或基础分的依据，前端直接显示；有具体技能时为空
     */
    public record Axis(String key, String label, String hint, int score, String band,
                       List<SkillRef> skills, List<String> roles, String basis, String reason) {
    }

    /**
     * 一张雷达图。
     *
     * @param count        参与计算的条目数：个人是技能项数，需求是还开着的招募需求条数
     * @param strongestKey 最强的一项的 key；一条技能都没填（或没有需求）时为空
     * @param advice       一句话说明，前端直接显示
     */
    public record Radar(List<Axis> axes, int count, String strongestKey, String strongestLabel, String advice) {
    }

    /** 需求和个人在这一轴上的差距。COVERED 达标 / PARTIAL 差一档 / MISSING 还没有 */
    public record Gap(String key, String label, int need, int mine, String status, String text) {
    }

    /**
     * 队伍招募页的雷达：招募需求 + 看的人自己的能力 + 覆盖率。
     *
     * @param fit     0–100，需求轴上"我的能力 / 要求"的加权覆盖；需求里没勾技能、或者我没填技能时为空
     * @param verdict 一句话总结，前端直接显示
     */
    public record TeamRadar(Radar need, Radar mine, Integer fit, List<Gap> gaps, String verdict) {
    }
}
