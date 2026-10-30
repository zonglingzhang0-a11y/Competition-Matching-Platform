package com.teammatch.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpTest {

    private static MockHttpServletRequest request(String remote, String realIp) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr(remote);
        if (realIp != null) {
            r.addHeader("X-Real-IP", realIp);
        }
        return r;
    }

    @Test
    void trustsNginxHeaderOnlyFromLocalhost() {
        assertThat(ClientIp.of(request("127.0.0.1", "203.0.113.7"))).as("经过本机的 Nginx").isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request("0:0:0:0:0:0:0:1", "203.0.113.7"))).isEqualTo("203.0.113.7");
        assertThat(ClientIp.of(request("198.51.100.2", "203.0.113.7"))).as("直接连过来的，自己填的头不算")
                .isEqualTo("198.51.100.2");
        assertThat(ClientIp.of(request("127.0.0.1", null))).as("本地开发没有这个头").isEqualTo("127.0.0.1");
    }
}
