package com.teammatch.match;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.competition.Competition;
import com.teammatch.match.MatchConfigAdminService.ConfigForm;
import com.teammatch.match.MatchConfigAdminService.ConfigPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：匹配配置。改完立刻生效，下一次推荐就用新配置。
 */
@RestController
@RequestMapping("/api/admin/match-configs")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class MatchConfigAdminController {

    private final MatchConfigAdminService adminService;

    @GetMapping
    public Result<ConfigPage> page() {
        return Result.ok(adminService.page());
    }

    @PutMapping("/{preset}")
    public Result<Void> save(@PathVariable Competition.MatchPreset preset, @Valid @RequestBody ConfigForm form) {
        adminService.save(StpUtil.getLoginIdAsLong(), preset, form.config());
        return Result.ok();
    }
}
