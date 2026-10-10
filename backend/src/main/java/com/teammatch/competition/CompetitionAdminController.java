package com.teammatch.competition;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.competition.CompetitionAdminDtos.AdminCompetitionRow;
import com.teammatch.competition.CompetitionAdminDtos.CompetitionForm;
import com.teammatch.competition.CompetitionAdminDtos.EditionForm;
import com.teammatch.competition.CompetitionAdminDtos.NodesForm;
import com.teammatch.competition.CompetitionAdminDtos.RoleTemplateView;
import com.teammatch.competition.CompetitionDtos.NodeView;
import com.teammatch.competition.CompetitionDtos.CompetitionDetail;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员维护比赛库：比赛、届次、官方时间节点。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class CompetitionAdminController {

    private final CompetitionAdminService adminService;
    private final CompetitionService competitionService;

    @GetMapping("/competitions")
    public Result<PageResult<AdminCompetitionRow>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.list(keyword, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/role-templates")
    public Result<List<RoleTemplateView>> roleTemplates() {
        return Result.ok(adminService.roleTemplates());
    }

    @GetMapping("/editions/{id}/nodes")
    public Result<List<NodeView>> nodes(@PathVariable long id) {
        return Result.ok(adminService.nodes(id));
    }

    /** 和学生端的详情一样，但包括还没发布的届次 */
    @GetMapping("/competitions/{id}")
    public Result<CompetitionDetail> detail(@PathVariable long id) {
        return Result.ok(competitionService.detail(StpUtil.getLoginIdAsLong(), id, true));
    }

    @PostMapping("/competitions")
    public Result<Long> createCompetition(@Valid @RequestBody CompetitionForm form) {
        return Result.ok(adminService.createCompetition(form));
    }

    @PutMapping("/competitions/{id}")
    public Result<Void> updateCompetition(@PathVariable long id, @Valid @RequestBody CompetitionForm form) {
        adminService.updateCompetition(id, form);
        return Result.ok();
    }

    @DeleteMapping("/competitions/{id}")
    public Result<Void> deleteCompetition(@PathVariable long id) {
        adminService.deleteCompetition(id);
        return Result.ok();
    }

    @PostMapping("/competitions/{id}/editions")
    public Result<Long> createEdition(@PathVariable long id, @Valid @RequestBody EditionForm form) {
        return Result.ok(adminService.createEdition(id, form));
    }

    @PutMapping("/editions/{id}")
    public Result<Void> updateEdition(@PathVariable long id, @Valid @RequestBody EditionForm form) {
        adminService.updateEdition(id, form);
        return Result.ok();
    }

    @DeleteMapping("/editions/{id}")
    public Result<Void> deleteEdition(@PathVariable long id) {
        adminService.deleteEdition(id);
        return Result.ok();
    }

    /** 整体替换这一届的官方时间节点 */
    @PutMapping("/editions/{id}/nodes")
    public Result<Void> replaceNodes(@PathVariable long id, @Valid @RequestBody NodesForm form) {
        adminService.replaceNodes(id, form.nodes());
        return Result.ok();
    }
}
