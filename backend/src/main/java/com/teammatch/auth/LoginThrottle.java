package com.teammatch.auth;

import com.teammatch.common.BizException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 防止有人对着一个邮箱一直猜密码：15 分钟内输错 5 次，这个邮箱锁 15 分钟，期间密码对了也登录不了。
 * 没注册的邮箱一样计数，免得通过"会不会被锁"看出哪些邮箱注册过。
 * <p>
 * 只放在内存里：只有一台服务器，重启后清零也没关系（这时候还有验证码重置密码可以用）。
 */
@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);
    static final Duration LOCK = Duration.ofMinutes(15);
    /** 记的邮箱太多时先清掉过期的，防止被随便填的邮箱撑满内存 */
    private static final int CLEANUP_SIZE = 10_000;

    private final Clock clock;
    private final Map<String, State> states = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    /** firstFailure：这一轮第一次输错的时间；lockedUntil：锁到什么时候，没锁为空 */
    private record State(int failures, Instant firstFailure, Instant lockedUntil) {
    }

    /** 还锁着就抛 429 */
    public void check(String email) {
        State s = states.get(email);
        Instant now = clock.instant();
        if (s != null && s.lockedUntil() != null && now.isBefore(s.lockedUntil())) {
            long minutes = Math.max(1, (Duration.between(now, s.lockedUntil()).toSeconds() + 59) / 60);
            throw new BizException(429, "密码输错太多次，请 " + minutes + " 分钟后再试，或者用\"忘记密码\"重设");
        }
    }

    public void failed(String email) {
        Instant now = clock.instant();
        if (states.size() > CLEANUP_SIZE) {
            states.values().removeIf(s -> expired(s, now));
        }
        states.compute(email, (k, s) -> {
            if (s == null || expired(s, now)) {
                return new State(1, now, null);
            }
            int failures = s.failures() + 1;
            return new State(failures, s.firstFailure(), failures >= MAX_FAILURES ? now.plus(LOCK) : null);
        });
    }

    /** 登录成功或者重设了密码，清掉记录 */
    public void reset(String email) {
        states.remove(email);
    }

    /** 这一轮已经过去了（没锁、或者锁已经过了） */
    private static boolean expired(State s, Instant now) {
        if (s.lockedUntil() != null) {
            return !now.isBefore(s.lockedUntil());
        }
        return !now.isBefore(s.firstFailure().plus(WINDOW));
    }
}
