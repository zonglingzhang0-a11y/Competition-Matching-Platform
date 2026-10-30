package com.teammatch.notification;

import com.teammatch.mail.Mailer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 新消息汇总发邮件，默认每 5 分钟检查一次：
 * <ul>
 *   <li>有消息超过 delay-seconds（默认 10 分钟）还没看才发，这段时间里在网站上看过了就不发；
 *       要发的时候，这个人其余还没看的新消息一起带上，免得隔几秒又来一封；</li>
 *   <li>正开着网站的人（有推送连接）不发，铃铛上已经看得到；</li>
 *   <li>同一个人的几条合成一封；每条消息最多发一次，先记上 emailed_at 再发，多台服务器同时跑也不会重复；</li>
 *   <li>只看最近 3 天的消息；关掉了邮件提醒、或者被停用的账号不发。</li>
 * </ul>
 * 发不出去（邮箱地址不对、SMTP 连不上）只记日志、不重试，站内信照常在。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notify-mail.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationMailJob {

    /** 刚升级、或者后端停了很久再开时，不会把很早的消息翻出来发 */
    static final Duration MAX_AGE = Duration.ofDays(3);
    /** 只是告知、不用处理的消息不发邮件 */
    static final Set<Notification.Type> SKIPPED = EnumSet.of(Notification.Type.MEMBER_JOINED, Notification.Type.MEMBER_LEFT);

    private final MailMapper mapper;
    private final PushHub hub;
    private final Mailer mailer;
    private final NotifyMailProperties props;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.notify-mail.interval-ms:300000}", initialDelayString = "${app.notify-mail.initial-delay-ms:60000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<String> types = Arrays.stream(Notification.Type.values()).filter(t -> !SKIPPED.contains(t)).map(Enum::name).toList();
        Map<Long, List<Pending>> byUser = mapper.pending(now.minusSeconds(props.delaySeconds()), now.minus(MAX_AGE), types)
                .stream().collect(Collectors.groupingBy(Pending::userId, LinkedHashMap::new, Collectors.toList()));
        int sent = 0;
        for (Map.Entry<Long, List<Pending>> e : byUser.entrySet()) {
            if (hub.online(e.getKey())) {
                continue;
            }
            List<Pending> items = e.getValue();
            if (mapper.claim(items.stream().map(Pending::id).toList(), now) == 0) {
                continue;
            }
            Pending first = items.getFirst();
            NotificationMail.Mail mail = NotificationMail.compose(first.nickname(),
                    items.stream().map(p -> new NotificationMail.Item(p.title(), p.content(), p.createdAt())).toList(),
                    props.siteUrl(), props.delaySeconds());
            try {
                mailer.send(first.email(), mail.subject(), mail.text());
                sent++;
            } catch (RuntimeException ex) {
                log.warn("新消息汇总邮件没发出去（用户 {}）：{}", e.getKey(), ex.getMessage());
            }
        }
        if (sent > 0) {
            log.info("发了 {} 封新消息汇总邮件", sent);
        }
    }

    record Pending(Long id, Long userId, String title, String content, LocalDateTime createdAt, String email,
                   String nickname) {
    }

    @Mapper
    public interface MailMapper {

        /**
         * 该发邮件的消息：收信人至少有一条没看、没发过、已经等了 delay 的消息，就把他所有没看、没发过的消息
         * （最近 3 天里的）一起拿出来；收信人要开着邮件提醒、账号正常；按人、按时间排好。
         * 不这样的话，前后差一两秒的两条消息会拆成两封（created_at 只到秒）。
         */
        @Select("""
                <script>
                SELECT n.id AS id, n.user_id AS userId, n.title AS title, n.content AS content, n.created_at AS createdAt,
                       u.email AS email, u.nickname AS nickname
                FROM notification n JOIN sys_user u ON u.id = n.user_id
                WHERE n.emailed_at IS NULL AND n.read_at IS NULL
                  AND n.created_at &gt;= #{since}
                  AND n.type IN <foreach collection="types" item="t" open="(" separator="," close=")">#{t}</foreach>
                  AND u.email_notify = 1 AND u.status = 'ACTIVE'
                  AND EXISTS (SELECT 1 FROM notification d
                              WHERE d.user_id = n.user_id AND d.emailed_at IS NULL AND d.read_at IS NULL
                                AND d.created_at &lt;= #{before} AND d.created_at &gt;= #{since}
                                AND d.type IN <foreach collection="types" item="t2" open="(" separator="," close=")">#{t2}</foreach>)
                ORDER BY n.user_id, n.id
                </script>
                """)
        List<Pending> pending(@Param("before") LocalDateTime before, @Param("since") LocalDateTime since,
                              @Param("types") Collection<String> types);

        /** 记上已发；返回 0 说明别的服务器已经发了，或者刚刚被看了 */
        @Update("""
                <script>
                UPDATE notification SET emailed_at = #{now}
                WHERE emailed_at IS NULL AND read_at IS NULL
                  AND id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                </script>
                """)
        int claim(@Param("ids") Collection<Long> ids, @Param("now") LocalDateTime now);
    }
}
