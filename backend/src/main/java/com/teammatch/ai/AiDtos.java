package com.teammatch.ai;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * AI 分析的返回结构。页面上分两块用：个人档案的"AI 能力分析"和队伍页的"AI 招募建议"。
 * 所有内容都只是建议：技能要用户自己勾了确认才会写进档案，招募建议也不会自动替队长做决定。
 */
public final class AiDtos {

    private AiDtos() {
    }

    /** 一条优势，带资料里的原话当依据 */
    public record Strength(String title, String detail, String evidence) {
    }

    /** 从文字里看出来的技能：名字必须在技能字典里，等级 1–3，带原话依据 */
    public record SuggestedSkill(String name, String category, int level, String evidence, boolean owned) {
    }

    /** 问卷和文字里体现的合作风格，只说和组队有关的 */
    public record Trait(String trait, String evidence) {
    }

    /** 建议加强的一个能力轴 */
    public record AxisHint(String key, String label, String reason) {
    }

    public record ProfileBody(String summary, List<Strength> strengths, List<SuggestedSkill> skills,
                              List<Trait> personality, List<AxisHint> axisHints) {
    }

    /**
     * @param source RULE 本地规则 / LLM 大模型
     * @param model  模型名，本地规则时为空
     */
    public record AiProfileAnalysis(ProfileBody body, String source, String model, LocalDateTime createdAt) {
    }

    /** 需求轴上队伍现有的最好水平 */
    public record AxisGap(String key, String label, int need, int best, String status) {
    }

    /** 某项需求技能队里现有的最好熟练度，0 表示没人填过 */
    public record SkillGap(String name, int minLevel, int bestLevel, String status) {
    }

    public record TeamBody(String summary, List<AxisGap> weakAxes, List<String> suggestions,
                           List<String> candidateProfile, List<String> questions, List<SkillGap> skillGaps) {
    }

    public record AiTeamAdvice(TeamBody body, String source, String model, LocalDateTime createdAt) {
    }

    /** 确认把 AI 建议的技能写进自己的档案 */
    public record ApplySkillsForm(
            @NotNull(message = "没有要加入的技能")
            @Valid
            @Size(max = 10, message = "一次最多确认 10 项技能")
            List<SkillPick> skills) {
    }

    public record SkillPick(
            @NotBlank(message = "技能名不能为空")
            @Size(max = 32, message = "技能名太长了")
            String name,
            @Min(value = 1, message = "熟练度是 1 到 3")
            @Max(value = 3, message = "熟练度是 1 到 3")
            int level) {
    }
}
