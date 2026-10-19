package com.teammatch.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 对方断开时不回响应也不算服务器出错() {
        var e = new AsyncRequestNotUsableException("ServletOutputStream failed to write",
                new IOException("Connection reset by peer"));
        assertNull(handler.handleOther(e));
    }

    @Test
    void 别的异常还是500() {
        var r = handler.handleOther(new IllegalStateException("出错了"));
        assertEquals(500, r.getStatusCode().value());
        assertEquals("服务器出错了，请稍后再试", r.getBody().message());
    }
}
