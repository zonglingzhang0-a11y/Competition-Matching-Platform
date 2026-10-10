package com.teammatch.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CodePurpose {

    REGISTER("注册"),
    EDU_VERIFY("教育邮箱认证"),
    RESET_PASSWORD("重置密码");

    /** 写在邮件里的用途说明 */
    private final String label;
}
