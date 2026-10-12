package com.teammatch.review;

import com.teammatch.calendar.CalendarEntities.ReminderMapper;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.review.ReviewEntities.ReviewRound;
import com.teammatch.review.ReviewEntities.RoundMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 互评的定时任务，和提醒用同一个间隔：
 * <ul>
 *   <li>到了截止时间还没交齐的互评，按已交的结果揭晓</li>
 *   <li>截止前 24 小时，提醒还没交的人</li>
 * </ul>
 * 揭晓是业务流程的一部分，所以不跟着 app.reminder.enabled 关掉。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewJob {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final RoundMapper roundMapper;
    private final ReviewService reviewService;
    private final ReminderMapper reminderMapper;
    private final NotificationService notifications;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.reminder.interval-ms:600000}", initialDelayString = "${app.reminder.initial-delay-ms:30000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            int published = 0;
            for (Long id : roundMapper.due(now)) {
                if (reviewService.publishIfDue(id)) {
                    published++;
                }
            }
            int reminded = remindClosing(now);
            if (published + reminded > 0) {
                log.info("互评：揭晓 {} 轮，提醒 {} 人", published, reminded);
            }
        } catch (RuntimeException e) {
            log.warn("互评定时任务出错", e);
        }
    }

    int remindClosing(LocalDateTime now) {
        int sent = 0;
        for (ReviewRound round : roundMapper.closingBetween(now, now.plusHours(24))) {
            String team = reviewService.teamName(round.getTeamId());
            for (Long uid : reviewService.notSubmitted(round)) {
                if (reminderMapper.markSent(uid, "REVIEW_DUE", round.getId(), 24) == 1) {
                    notifications.send(uid, Notification.Type.REVIEW_OPEN, "「" + team + "」的互评快截止了",
                            "互评在 " + WHEN.format(round.getClosesAt()) + " 截止，你还没给队友打分。",
                            ReviewService.REF_REVIEW, round.getId());
                    sent++;
                }
            }
        }
        return sent;
    }
}
