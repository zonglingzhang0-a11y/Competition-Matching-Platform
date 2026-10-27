package com.teammatch.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 访问者的 IP。
 * <p>
 * 线上后端只监听本机，请求都经过 Nginx 转发，真实地址在 Nginx 设置的 X-Real-IP 里（会覆盖客户端自己带的同名头）。
 * 只有请求确实来自本机时才信这个头，否则任何人都能自己填一个 IP 绕过限制。
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String remote = request.getRemoteAddr();
        String forwarded = request.getHeader("X-Real-IP");
        if (forwarded != null && !forwarded.isBlank() && isLoopback(remote)) {
            return forwarded.trim();
        }
        return remote;
    }

    static boolean isLoopback(String address) {
        return address != null
                && (address.startsWith("127.") || address.equals("::1") || address.equals("0:0:0:0:0:0:0:1"));
    }
}
