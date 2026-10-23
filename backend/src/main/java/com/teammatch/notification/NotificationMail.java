package com.teammatch.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 新消息汇总邮件的内容（纯函数，见 NotificationMailJob） */
final class NotificationMail {

    /** 一封邮件里最多列几条，多的让人到网站上看 */
    static final int MAX_ITEMS = 10;
    private static final int MAX_CONTENT = 200;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private NotificationMail() {
    }

    record Item(String title, String content, LocalDateTime createdAt) {
    }

    record Mail(String subject, String text) {
    }

    /** items 不能为空，按时间先后排好 */
    static Mail compose(String nickname, List<Item> items, String siteUrl, int delaySeconds) {
        int n = items.size();
        String first = items.getFirst().title();
        String subject = n == 1 ? "【竞搭】" + first : "【竞搭】" + first + " 等 " + n + " 条新消息";

        StringBuilder text = new StringBuilder();
        text.append(nickname).append("，你好：\n\n");
        text.append("你有 ").append(n).append(" 条新消息还没看：\n\n");
        for (Item it : items.subList(0, Math.min(n, MAX_ITEMS))) {
            text.append("· ").append(it.title()).append('\n');
            if (it.content() != null && !it.content().isBlank()) {
                String c = it.content().strip();
                text.append("  ").append(c.length() > MAX_CONTENT ? c.substring(0, MAX_CONTENT) + "……" : c).append('\n');
            }
            text.append("  ").append(WHEN.format(it.createdAt())).append("\n\n");
        }
        if (n > MAX_ITEMS) {
            text.append("还有 ").append(n - MAX_ITEMS).append(" 条，到网站上看。\n\n");
        }
        text.append("打开竞搭查看：").append(siteUrl).append("/notifications\n\n");
        text.append("——\n有消息超过 ").append(waitText(delaySeconds))
                .append("没看，你当时也不在网站上，所以把还没看的一起发到邮箱提醒一下。不想收到的话，在竞搭的消息页关掉「邮件提醒」。\n");
        return new Mail(subject, text.toString());
    }

    private static String waitText(int seconds) {
        if (seconds >= 3600 && seconds % 3600 == 0) {
            return seconds / 3600 + " 小时";
        }
        return seconds >= 60 ? seconds / 60 + " 分钟" : seconds + " 秒";
    }
}
