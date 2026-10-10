package com.teammatch.common;

import lombok.Getter;

/**
 * 业务异常：消息会原样返回给前端，所以要写成用户能看懂的话。
 */
@Getter
public class BizException extends RuntimeException {

    /** 对应 HTTP 状态码，默认 400 */
    private final int status;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(int status, String message) {
        super(message);
        this.status = status;
    }
}
