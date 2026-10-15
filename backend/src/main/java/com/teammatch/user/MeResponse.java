package com.teammatch.user;

import java.time.LocalDate;

/**
 * 当前登录用户的信息。school 在认证通过前为空。emailNotify：不在网站上时把新消息汇总发到邮箱。
 */
public record MeResponse(
        Long id,
        String email,
        String nickname,
        SysUser.UserRole role,
        SysUser.VerifyStatus verifyStatus,
        LocalDate verifiedUntil,
        SchoolBrief school,
        boolean emailNotify) {

    public record SchoolBrief(Long id, String name) {
    }
}
