package com.teammatch.notification;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationMailTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 10, 16, 14, 5);

    @Test
    void oneMessage() {
        NotificationMail.Mail mail = NotificationMail.compose("陈一舟",
                List.of(new NotificationMail.Item("孙悦申请加入「数模冲刺队」", "想以论文的身份加入", T)),
                "http://localhost:5173", 600);
        assertThat(mail.subject()).isEqualTo("【竞搭】孙悦申请加入「数模冲刺队」");
        assertThat(mail.text())
                .startsWith("陈一舟，你好：")
                .contains("你有 1 条新消息还没看", "· 孙悦申请加入「数模冲刺队」", "  想以论文的身份加入", "  10月16日 14:05")
                .contains("打开竞搭查看：http://localhost:5173/notifications")
                .contains("超过 10 分钟没看", "关掉「邮件提醒」");
    }

    @Test
    void severalMessagesAreListedUpToTen() {
        List<NotificationMail.Item> items = IntStream.rangeClosed(1, 12)
                .mapToObj(i -> new NotificationMail.Item("消息 " + i, i == 2 ? "长".repeat(300) : null, T.plusMinutes(i)))
                .toList();
        NotificationMail.Mail mail = NotificationMail.compose("林晓", items, "https://jingda.example", 3600);
        assertThat(mail.subject()).isEqualTo("【竞搭】消息 1 等 12 条新消息");
        assertThat(mail.text())
                .contains("· 消息 10", "还有 2 条，到网站上看", "超过 1 小时没看")
                .doesNotContain("· 消息 11")
                .contains("长".repeat(200) + "……")
                .doesNotContain("长".repeat(201));
    }
}
