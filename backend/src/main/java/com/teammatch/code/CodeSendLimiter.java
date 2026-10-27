package com.teammatch.code;

import com.teammatch.common.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * 防止有人换着邮箱一直点"发送验证码"，把发信邮箱每天的额度刷光（QQ 邮箱发太多会被暂时封禁，正常同学就收不到验证码了）：
 * <ul>
 *   <li>同一个 IP 一小时最多发 30 封、24 小时最多 100 封。宿舍和机房常常共用一个出口 IP，所以不能卡得太紧</li>
 *   <li>全站一天最多发 300 封，超了当天不再发</li>
 * </ul>
 * 同一个邮箱 60 秒内不能重发，由 {@link EmailCodeService} 检查。
 * <p>
 * 只放在内存里：只有一台服务器，重启后清零也没关系。
 */
@Slf4j
@Component
public class CodeSendLimiter {

    static final int IP_PER_HOUR = 30;
    static final int IP_PER_DAY = 100;
    static final int TOTAL_PER_DAY = 300;
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofDays(1);
    /** 记的 IP 太多时先清掉 24 小时内没发过的，防止被大量 IP 撑满内存 */
    private static final int CLEANUP_SIZE = 10_000;

    private final Clock clock;
    /** IP → 最近 24 小时里每次发送的时间，旧的在前 */
    private final Map<String, Deque<Instant>> byIp = new HashMap<>();
    private LocalDate today;
    private int sentToday;

    public CodeSendLimiter(Clock clock) {
        this.clock = clock;
    }

    /**
     * 要发一封验证码：超限就抛 429，没超就记一次。
     *
     * @param ip 访问者的 IP，拿不到时为空，只按全站总数限制
     */
    public synchronized void acquire(String ip) {
        Instant now = clock.instant();
        LocalDate date = LocalDate.now(clock);
        if (!date.equals(today)) {
            today = date;
            sentToday = 0;
        }
        if (sentToday >= TOTAL_PER_DAY) {
            throw new BizException(429, "今天发出的验证码太多了，请明天再试，或者联系管理员");
        }
        if (ip != null) {
            if (byIp.size() > CLEANUP_SIZE) {
                byIp.values().removeIf(d -> d.isEmpty() || !d.peekLast().isAfter(now.minus(DAY)));
            }
            Deque<Instant> sent = byIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
            while (!sent.isEmpty() && !sent.peekFirst().isAfter(now.minus(DAY))) {
                sent.pollFirst();
            }
            if (sent.size() >= IP_PER_DAY) {
                throw new BizException(429, "你所在的网络今天发送验证码太频繁了，请明天再试");
            }
            long lastHour = sent.stream().filter(t -> t.isAfter(now.minus(HOUR))).count();
            if (lastHour >= IP_PER_HOUR) {
                throw new BizException(429, "你所在的网络发送验证码太频繁了，请一小时后再试");
            }
            sent.addLast(now);
        }
        sentToday++;
        if (sentToday == TOTAL_PER_DAY) {
            log.warn("今天的验证码已经发了 {} 封，到达全站上限，今天剩下的时间不再发送", TOTAL_PER_DAY);
        }
    }
}
