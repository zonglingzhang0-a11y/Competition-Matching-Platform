package com.teammatch.user;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时把 app.admin-emails 里已经注册过的账号升级为管理员。
 * 还没注册的，注册时由 AuthService 直接设成管理员。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AppProperties props;
    private final SysUserMapper userMapper;

    @Override
    public void run(ApplicationArguments args) {
        if (props.adminEmails().isEmpty()) {
            return;
        }
        int promoted = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .in(SysUser::getEmail, props.adminEmails())
                .ne(SysUser::getRole, SysUser.UserRole.ADMIN)
                .set(SysUser::getRole, SysUser.UserRole.ADMIN));
        if (promoted > 0) {
            log.info("已把 {} 个账号升级为管理员", promoted);
        }
    }
}
