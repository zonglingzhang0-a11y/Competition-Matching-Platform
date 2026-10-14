package com.teammatch.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 站内信。业务代码在同一个事务里调用 send，业务回滚了通知也跟着回滚。
 * <p>
 * 收信人在线（开着推送连接，见 {@link PushHandler}）时，事务提交后马上推给他：
 * <ul>
 *   <li>新消息：{@code {type: "notification", notification: {...}, unread: 未读数}}</li>
 *   <li>标了已读：{@code {type: "unread", unread: 未读数}}，同一个人开着的其他页面跟着变</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 跳转目标：队伍页 */
    public static final String REF_TEAM = "TEAM";

    private final NotificationMapper mapper;
    private final PushHub hub;
    private final Clock clock;

    public void send(long userId, Notification.Type type, String title, String content, String refType, Long refId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRefType(refType);
        n.setRefId(refId);
        n.setCreatedAt(LocalDateTime.now(clock));
        mapper.insert(n);
        afterCommit(() -> {
            if (hub.online(userId)) {
                hub.send(userId, Map.of("type", "notification", "notification", n, "unread", unreadCount(userId)));
            }
        });
    }

    public PageResult<Notification> list(long userId, boolean unreadOnly, int page, int size) {
        Page<Notification> p = mapper.selectPage(Page.of(page, size), new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(unreadOnly, Notification::getReadAt)
                .orderByDesc(Notification::getId));
        return PageResult.of(p, n -> n);
    }

    public long unreadCount(long userId) {
        return mapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt));
    }

    public void markRead(long userId, long id) {
        int updated = mapper.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now(clock)));
        if (updated == 0 && mapper.selectCount(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getId, id).eq(Notification::getUserId, userId)) == 0) {
            throw new BizException(404, "这条消息不存在");
        }
        if (updated > 0) {
            afterCommit(() -> pushUnread(userId));
        }
    }

    public void markAllRead(long userId) {
        int updated = mapper.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now(clock)));
        if (updated > 0) {
            afterCommit(() -> pushUnread(userId));
        }
    }

    private void pushUnread(long userId) {
        if (hub.online(userId)) {
            hub.send(userId, Map.of("type", "unread", "unread", unreadCount(userId)));
        }
    }

    /** 有事务就等提交后再推（回滚了的消息不能推出去），没有就马上推。推送出错不影响业务 */
    static void afterCommit(Runnable push) {
        Runnable safe = () -> {
            try {
                push.run();
            } catch (RuntimeException e) {
                log.warn("站内信推送失败：{}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }

    @Mapper
    public interface NotificationMapper extends BaseMapper<Notification> {
    }
}
