package com.teammatch.notification;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * 站内信推送的 WebSocket 连接。
 * <ul>
 *   <li>握手时用 {@link PushTickets} 的一次性票确认是谁</li>
 *   <li>前端每 30 秒发一次心跳，这里回 pong；每次心跳顺便检查登录还有没有效，退出登录或被停用后关掉连接（4401）</li>
 *   <li>推送的内容见 {@link NotificationService}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PushHandler extends TextWebSocketHandler {

    /** 登录失效：前端收到后不再重连 */
    static final CloseStatus LOGGED_OUT = new CloseStatus(4401, "登录已失效");
    private static final Map<String, String> PONG = Map.of("type", "pong");

    private final PushHub hub;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        hub.add(session);
    }

    /** 前端只会发心跳，内容不看 */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        if (!stillLoggedIn(session)) {
            session.close(LOGGED_OUT);
            return;
        }
        hub.reply(session, PONG);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        hub.remove(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        hub.remove(session);
    }

    private static boolean stillLoggedIn(WebSocketSession session) {
        Object loginId = StpUtil.getLoginIdByToken((String) session.getAttributes().get(PushHub.TOKEN));
        return loginId != null && Objects.equals(String.valueOf(loginId), String.valueOf(PushHub.userId(session)));
    }

    /** 握手：网址里的 ticket 换成用户，票不对就 401 */
    @Component
    @RequiredArgsConstructor
    public static class Handshake implements HandshakeInterceptor {

        private final PushTickets tickets;

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            return tickets.consume(ticket).map(t -> {
                attributes.put(PushHub.USER_ID, t.userId());
                attributes.put(PushHub.TOKEN, t.token());
                return true;
            }).orElseGet(() -> {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            });
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
            // 不用做什么
        }
    }
}
