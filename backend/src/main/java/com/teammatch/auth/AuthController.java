package com.teammatch.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.auth.AuthDtos.LoginRequest;
import com.teammatch.auth.AuthDtos.LoginResponse;
import com.teammatch.auth.AuthDtos.RegisterRequest;
import com.teammatch.auth.AuthDtos.SendCodeRequest;
import com.teammatch.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 发注册验证码 */
    @PostMapping("/register-code")
    public Result<Void> sendRegisterCode(@Valid @RequestBody SendCodeRequest req) {
        authService.sendRegisterCode(req.email());
        return Result.ok();
    }

    /** 注册成功后直接登录 */
    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        StpUtil.logout();
        return Result.ok();
    }
}
