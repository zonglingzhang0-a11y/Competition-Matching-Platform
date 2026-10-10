package com.teammatch.notification;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 站内信。业务代码在同一个事务里调用 send，业务回滚了通知也跟着回滚。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** 跳转目标：队伍页 */
    public static final String REF_TEAM = "TEAM";

    private final NotificationMapper mapper;
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
    }

    public void markAllRead(long userId) {
        mapper.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .isNull(Notification::getReadAt)
                .set(Notification::getReadAt, LocalDateTime.now(clock)));
    }

    @Mapper
    public interface NotificationMapper extends BaseMapper<Notification> {
    }
}
