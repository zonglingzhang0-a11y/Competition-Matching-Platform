package com.teammatch.verify;

import com.teammatch.user.SysUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public final class VerifyDtos {

    private VerifyDtos() {
    }

    public record EduCodeRequest(
            @NotBlank(message = "请填写教育邮箱") @Email(message = "邮箱格式不正确") String eduEmail) {
    }

    public record EduConfirmRequest(
            @NotBlank(message = "请填写教育邮箱") @Email(message = "邮箱格式不正确") String eduEmail,
            @NotBlank(message = "请填写验证码") String code) {
    }

    /**
     * 认证结果。邮箱后缀能对上学校就直接认证通过；对不上的话邮箱本身已经验证过，等管理员把这所学校加进来。
     */
    public record VerifyResult(
            SysUser.VerifyStatus verifyStatus,
            String schoolName,
            LocalDate verifiedUntil,
            String message) {
    }
}
