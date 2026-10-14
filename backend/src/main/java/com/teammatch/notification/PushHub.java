package com.teammatch.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 在线的推送连接：用户 → 他开着的所有连接（多个标签页、手机电脑同时登录都算）。
 * 业务线程和心跳可能同时往一个连接里写，所以每个连接都包一层 {@link ConcurrentWebSocketSessionDecorator}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushHub {

    static final String USER_ID = "userId";
    static final String TOKEN = "token";
    /** 单条消息发送超时；对方一直收不完就断开它，免得拖住业务线程 */
    private static final int SEND_TIMEOUT_MS = 5000;
    private static final int BUFFER_LIMIT = 64 * 1024;

    private final JsonMapper json;
    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    void add(WebSocketSession session) {
        sessions.computeIfAbsent(userId(session), k -> new ConcurrentHashMap<>())
                .put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIMEOUT_MS, BUFFER_LIMIT));
    }

    void remove(WebSocketSession session) {
        sessions.computeIfPresent(userId(session), (k, map) -> {
            map.remove(session.getId());
            return map.isEmpty() ? null : map;
        });
    }

    public boolean online(long userId) {
        return sessions.containsKey(userId);
    }

    /** 这个人现在开着几个连接 */
    public int connections(long userId) {
        Map<String, WebSocketSession> map = sessions.get(userId);
        return map == null ? 0 : map.size();
    }

    /** 推给这个人的所有连接；没在线就什么都不做 */
    public void send(long userId, Object payload) {
        Map<String, WebSocketSession> map = sessions.get(userId);
        if (map == null) {
            return;
        }
        TextMessage message = new TextMessage(json.writeValueAsString(payload));
        map.values().forEach(s -> write(s, message));
    }

    /** 回给某一个连接（比如心跳的回应） */
    void reply(WebSocketSession raw, Object payload) {
        Map<String, WebSocketSession> map = sessions.get(userId(raw));
        WebSocketSession s = map == null ? null : map.get(raw.getId());
        if (s != null) {
            write(s, new TextMessage(json.writeValueAsString(payload)));
        }
    }

    private void write(WebSocketSession s, TextMessage message) {
        try {
            if (s.isOpen()) {
                s.sendMessage(message);
            }
        } catch (IOException | RuntimeException e) {
            // 网断了或对方太慢：关掉，前端会自己重连
            log.debug("推送失败，断开连接 {}：{}", s.getId(), e.getMessage());
            try {
                s.close(CloseStatus.SESSION_NOT_RELIABLE);
            } catch (IOException ignored) {
                // 已经断了
            }
        }
    }

    static long userId(WebSocketSession session) {
        return (Long) session.getAttributes().get(USER_ID);
    }
}
