package com.teammatch.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 推送连接的地址：/api/ws/notifications?ticket=…
 * <p>
 * 放在 /api 下面，开发时 Vite 的 /api 转发顺带转发 WebSocket，部署时也和接口走同一个域名。
 * 来源（Origin）检查用 Spring 默认的"只允许同域"：开发时经过 Vite 转发、部署时经过反向代理，浏览器看到的都是同一个域名。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class PushConfig implements WebSocketConfigurer {

    public static final String PATH = "/api/ws/notifications";

    private final PushHandler handler;
    private final PushHandler.Handshake handshake;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, PATH).addInterceptors(handshake);
    }

    /** 90 秒没收到任何东西（前端 30 秒一次心跳）就当连接已经断了，释放掉 */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(90_000L);
        container.setMaxTextMessageBufferSize(8 * 1024);
        return container;
    }
}
