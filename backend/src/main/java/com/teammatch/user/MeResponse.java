package com.teammatch.user;

import java.time.LocalDate;

/**
 * 当前登录用户的信息。school 在认证通过前为空。
 */
public record MeResponse(
        Long id,
        String email,
        String nickname,
        SysUser.UserRole role,
        SysUser.VerifyStatus verifyStatus,
        LocalDate verifiedUntil,
        SchoolBrief school) {

    public record SchoolBrief(Long id, String name) {
    }
}
