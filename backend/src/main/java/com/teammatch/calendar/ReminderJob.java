package com.teammatch.calendar;

import com.teammatch.calendar.CalendarEntities.ReminderMapper;
import com.teammatch.calendar.CalendarEntities.TaskMapper;
import com.teammatch.calendar.CalendarMapper.Upcoming;
import com.teammatch.calendar.CalendarMapper.UpcomingMilestone;
import com.teammatch.calendar.CalendarMapper.UpcomingNode;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 定时提醒，默认每 10 分钟跑一次：
 * <ul>
 *   <li>报名截止前 72 小时、24 小时，提醒关注了这一届、或者已经在这一届组队的人</li>
 *   <li>"比赛期间"节点开始前 24 小时，提醒同样这些人</li>
 *   <li>队伍里程碑到期前 24 小时，提醒队员</li>
 * </ul>
 * 每条提醒先往 reminder_sent 里记一笔，记上了才发，所以重复跑、多台服务器同时跑都不会重复提醒。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.reminder.enabled", havingValue = "true", matchIfMissing = true)
public class ReminderJob {

    public static final String REF_COMPETITION = "COMPETITION";

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final CalendarMapper calendarMapper;
    private final ReminderMapper reminderMapper;
    private final TaskMapper taskMapper;
    private final NotificationService notifications;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${app.reminder.interval-ms:600000}", initialDelayString = "${app.reminder.initial-delay-ms:30000}")
    public void run() {
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            int sent = registrationDeadlines(now) + contestStarts(now) + milestonesDue(now);
            if (sent > 0) {
                log.info("发出 {} 条提醒", sent);
            }
        } catch (RuntimeException e) {
            // 提醒失败不能影响别的功能，记下来等下一轮
            log.warn("定时提醒出错", e);
        }
    }

    int registrationDeadlines(LocalDateTime now) {
        int sent = 0;
        for (Upcoming u : calendarMapper.registrationEnds(now, now.plusHours(72))) {
            int offset = hoursUntil(now, u.at()) <= 24 ? 24 : 72;
            for (Long uid : calendarMapper.editionAudience(u.editionId())) {
                if (reminderMapper.markSent(uid, "REGISTRATION_END", u.editionId(), offset) == 1) {
                    notifications.send(uid, Notification.Type.DEADLINE, "「" + u.label() + "」报名快截止了",
                            "报名在 " + WHEN.format(u.at()) + " 截止，还剩" + left(now, u.at()) + "。还没组好队的抓紧。",
                            REF_COMPETITION, u.competitionId());
                    sent++;
                }
            }
        }
        return sent;
    }

    int contestStarts(LocalDateTime now) {
        int sent = 0;
        for (UpcomingNode n : calendarMapper.busyNodesStarting(now, now.plusHours(24))) {
            for (Long uid : calendarMapper.editionAudience(n.editionId())) {
                if (reminderMapper.markSent(uid, "NODE_START", n.refId(), 24) == 1) {
                    notifications.send(uid, Notification.Type.DEADLINE, "「" + n.label() + "」快开始了",
                            "「" + n.title() + "」在 " + WHEN.format(n.at()) + " 开始，还剩" + left(now, n.at()) + "。",
                            REF_COMPETITION, n.competitionId());
                    sent++;
                }
            }
        }
        return sent;
    }

    int milestonesDue(LocalDateTime now) {
        int sent = 0;
        for (UpcomingMilestone m : calendarMapper.milestonesDue(now, now.plusHours(24))) {
            for (Long uid : calendarMapper.teamMembers(m.teamId())) {
                if (reminderMapper.markSent(uid, "MILESTONE_DUE", m.refId(), 24) == 1) {
                    List<String> open = taskMapper.openTitles(m.refId(), uid);
                    String tasks = open.isEmpty() ? ""
                            : "你负责的" + open.stream().map(t -> "「" + t + "」").collect(Collectors.joining("")) + "还没完成。";
                    notifications.send(uid, Notification.Type.MILESTONE, "队伍节点快到了",
                            "「" + m.teamName() + "」的「" + m.title() + "」在 " + WHEN.format(m.at()) + "，还剩" + left(now, m.at()) + "。" + tasks,
                            NotificationService.REF_TEAM, m.teamId());
                    sent++;
                }
            }
        }
        return sent;
    }

    private static long hoursUntil(LocalDateTime now, LocalDateTime at) {
        return Duration.between(now, at).toHours();
    }

    /** "2 天 5 小时" / "3 小时" / "不到 1 小时" */
    static String left(LocalDateTime now, LocalDateTime at) {
        long minutes = Duration.between(now, at).toMinutes();
        if (minutes < 60) {
            return "不到 1 小时";
        }
        long days = minutes / (60 * 24);
        long hours = minutes / 60 % 24;
        return days > 0 ? " " + days + " 天" + (hours > 0 ? " " + hours + " 小时" : "") : " " + hours + " 小时";
    }
}
