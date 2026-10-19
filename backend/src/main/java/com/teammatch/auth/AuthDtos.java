package com.teammatch.auth;

import com.teammatch.user.MeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 登录注册接口的请求和响应。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record SendCodeRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") String email) {
    }

    public record RegisterRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请填写验证码") String code,
            @NotBlank(message = "请填写密码")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码要 8 到 64 位，同时包含字母和数字")
            String password,
            @NotBlank(message = "请填写昵称") @Size(max = 16, message = "昵称最多 16 个字") String nickname) {
    }

    /** 忘记密码：用邮箱验证码设新密码，密码规则和注册一样 */
    public record ResetPasswordRequest(
            @NotBlank(message = "请填写邮箱") @Email(message = "邮箱格式不正确") String email,
            @NotBlank(message = "请填写验证码") String code,
            @NotBlank(message = "请填写新密码")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$", message = "密码要 8 到 64 位，同时包含字母和数字")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "请填写邮箱") String email,
            @NotBlank(message = "请填写密码") String password) {
    }

    public record LoginResponse(String token, MeResponse me) {
    }
}
