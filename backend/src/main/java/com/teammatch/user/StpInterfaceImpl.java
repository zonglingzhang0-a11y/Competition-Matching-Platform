package com.teammatch.user;

import cn.dev33.satoken.stp.StpInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 告诉 Sa-Token 每个用户有哪些角色，@SaCheckRole("ADMIN") 之类的注解靠它判断。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final SysUserMapper userMapper;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = userMapper.selectById(Long.valueOf(loginId.toString()));
        return user == null ? List.of() : List.of(user.getRole().name());
    }
}
