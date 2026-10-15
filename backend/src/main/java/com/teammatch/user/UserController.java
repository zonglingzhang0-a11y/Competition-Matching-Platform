package com.teammatch.user;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public Result<MeResponse> me() {
        return Result.ok(userService.me(StpUtil.getLoginIdAsLong()));
    }

    /** 不在网站上时，要不要把没看的新消息汇总发到邮箱 */
    @PutMapping("/me/email-notify")
    public Result<Void> setEmailNotify(@RequestBody EmailNotifyForm form) {
        userService.setEmailNotify(StpUtil.getLoginIdAsLong(), form.enabled());
        return Result.ok();
    }

    public record EmailNotifyForm(boolean enabled) {
    }
}
