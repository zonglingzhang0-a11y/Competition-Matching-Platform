package com.teammatch.verify;

import com.teammatch.user.SysUser;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    /** 最近一次认证申请。method 是 EDU_EMAIL 时有 eduEmail，SCREENSHOT 时为空 */
    public record LatestRequest(
            VerificationRequest.Status status,
            VerificationRequest.Method method,
            String eduEmail,
            String reviewNote,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {
    }

    /**
     * 截图认证提交后的结果。
     *
     * @param confidence HIGH：填的三项都在图里认出来了 / LOW：有对不上的 / 空：没能识别（只能人工看）
     * @param matched    每一项有没有认出来；没能识别时为空
     */
    public record ScreenshotResult(
            SysUser.VerifyStatus verifyStatus,
            String confidence,
            ScreenshotMatcher.Matched matched,
            String message) {
    }
}
