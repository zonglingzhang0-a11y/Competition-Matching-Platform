package com.teammatch.ai;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.ai.AiDtos.AiProfileAnalysis;
import com.teammatch.ai.AiDtos.AiTeamAdvice;
import com.teammatch.ai.AiDtos.ApplySkillsForm;
import com.teammatch.common.Result;
import com.teammatch.profile.ProfileDtos.SkillLevel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 分析接口。
 * <ul>
 *   <li>个人：GET 读最近一次结果，POST 重新分析，POST apply 把建议的技能确认进档案（要用户点）。</li>
 *   <li>队伍：GET / POST 招募建议，只有队长能调用。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AiController {

    private final AiAnalysisService service;

    private static long me() {
        return StpUtil.getLoginIdAsLong();
    }

    @GetMapping("/profile/ai-analysis")
    public Result<AiProfileAnalysis> profile() {
        return Result.ok(service.profile(me()));
    }

    @PostMapping("/profile/ai-analysis")
    public Result<AiProfileAnalysis> analyze() {
        return Result.ok(service.analyzeProfile(me()));
    }

    @PostMapping("/profile/ai-analysis/apply")
    public Result<List<SkillLevel>> apply(@Valid @RequestBody ApplySkillsForm form) {
        return Result.ok(service.applySkills(me(), form.skills()));
    }

    @GetMapping("/teams/{id}/recruit-advice")
    public Result<AiTeamAdvice> advice(@PathVariable long id) {
        return Result.ok(service.teamAdvice(me(), id));
    }

    @PostMapping("/teams/{id}/recruit-advice")
    public Result<AiTeamAdvice> regenerate(@PathVariable long id) {
        return Result.ok(service.analyzeTeam(me(), id));
    }
}
