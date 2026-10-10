package com.teammatch.dashboard;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.teammatch.common.Result;
import com.teammatch.dashboard.DashboardService.EditionStat;
import com.teammatch.dashboard.DashboardService.Overview;
import com.teammatch.dashboard.DashboardService.SkillGap;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：数据看板。
 */
@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<Overview> overview() {
        return Result.ok(dashboardService.overview());
    }

    @GetMapping("/editions")
    public Result<List<EditionStat>> editions() {
        return Result.ok(dashboardService.editions());
    }

    /** editionId 不传就汇总所有报名还没截止的届次 */
    @GetMapping("/skill-gap")
    public Result<List<SkillGap>> skillGap(@RequestParam(required = false) Long editionId) {
        return Result.ok(dashboardService.skillGap(editionId));
    }
}
