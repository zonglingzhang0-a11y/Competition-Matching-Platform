package com.teammatch.user;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.school.School;
import com.teammatch.school.SchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员查看账号、停用和恢复账号。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final UserService userService;

    public record UserRow(Long id, String email, String nickname, SysUser.UserRole role, SysUser.UserStatus status,
                          SysUser.VerifyStatus verifyStatus, String schoolName, LocalDateTime createdAt,
                          LocalDateTime lastLoginAt) {
    }

    /** keyword 搜邮箱和昵称 */
    public PageResult<UserRow> list(String keyword, SysUser.UserRole role, SysUser.UserStatus status, int page, int size) {
        LambdaQueryWrapper<SysUser> where = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            where.and(w -> w.like(SysUser::getEmail, kw).or().like(SysUser::getNickname, kw));
        }
        where.eq(role != null, SysUser::getRole, role)
                .eq(status != null, SysUser::getStatus, status)
                .orderByDesc(SysUser::getId);
        Page<SysUser> p = userMapper.selectPage(Page.of(page, size), where);
        Set<Long> schoolIds = p.getRecords().stream().map(SysUser::getSchoolId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> schools = schoolIds.isEmpty() ? Map.of()
                : schoolMapper.selectByIds(schoolIds).stream().collect(Collectors.toMap(School::getId, School::getName));
        return PageResult.of(p, u -> new UserRow(u.getId(), u.getEmail(), u.getNickname(), u.getRole(), u.getStatus(),
                userService.effectiveVerifyStatus(u), u.getSchoolId() == null ? null : schools.get(u.getSchoolId()),
                u.getCreatedAt(), u.getLastLoginAt()));
    }

    /** 停用后立刻踢下线；不能停用自己和其他管理员 */
    public void setStatus(long adminId, long userId, SysUser.UserStatus status) {
        SysUser target = userMapper.selectById(userId);
        if (target == null) {
            throw new BizException(404, "这个账号不存在");
        }
        if (status == SysUser.UserStatus.BANNED) {
            if (userId == adminId) {
                throw new BizException("不能停用自己的账号");
            }
            if (target.getRole() == SysUser.UserRole.ADMIN) {
                throw new BizException("不能停用管理员账号");
            }
        }
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .set(SysUser::getStatus, status));
        if (status == SysUser.UserStatus.BANNED) {
            StpUtil.logout(userId);
        }
    }
}
