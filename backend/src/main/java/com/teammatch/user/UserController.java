package com.teammatch.user;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
