package com.teammatch.user;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.user.UserAdminService.UserRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：账号列表、停用和恢复。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class UserAdminController {

    private final UserAdminService adminService;

    public record StatusForm(@NotNull(message = "请选择状态") SysUser.UserStatus status) {
    }

    @GetMapping
    public Result<PageResult<UserRow>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) SysUser.UserRole role,
            @RequestParam(required = false) SysUser.UserStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.list(keyword, role, status, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @PutMapping("/{id}/status")
    public Result<Void> setStatus(@PathVariable long id, @Valid @RequestBody StatusForm form) {
        adminService.setStatus(StpUtil.getLoginIdAsLong(), id, form.status());
        return Result.ok();
    }
}
