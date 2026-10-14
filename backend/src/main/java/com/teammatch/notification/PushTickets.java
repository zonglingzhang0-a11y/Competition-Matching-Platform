package com.teammatch.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 建立推送连接用的一次性票。
 * <p>
 * 浏览器建 WebSocket 时不能带 Authorization 头，只能把凭证放在网址里。为了不让 30 天有效的登录令牌出现在网址
 * （会被记进各种访问日志），前端先用令牌换一张票，30 秒内拿它建连接，用一次就作废。
 * 票存在内存里，只适合单台服务器；以后多台部署要挪到 Redis。
 */
@Component
@RequiredArgsConstructor
public class PushTickets {

    static final Duration TTL = Duration.ofSeconds(30);

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();

    /** token：换票时用的登录令牌，连接期间每次心跳都用它检查登录是否还有效 */
    public record Ticket(long userId, String token, long expiresAt) {
    }

    public String issue(long userId, String token) {
        long now = clock.millis();
        tickets.values().removeIf(t -> t.expiresAt() < now);
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        String ticket = HexFormat.of().formatHex(bytes);
        tickets.put(ticket, new Ticket(userId, token, now + TTL.toMillis()));
        return ticket;
    }

    /** 一张票只能用一次，过期了也不能用 */
    public Optional<Ticket> consume(String ticket) {
        if (ticket == null || ticket.isEmpty()) {
            return Optional.empty();
        }
        Ticket t = tickets.remove(ticket);
        return t == null || t.expiresAt() < clock.millis() ? Optional.empty() : Optional.of(t);
    }
}
