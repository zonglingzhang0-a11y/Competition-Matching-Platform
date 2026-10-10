package com.teammatch.notification;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService service;

    @GetMapping
    public Result<PageResult<Notification>> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(service.list(StpUtil.getLoginIdAsLong(), unreadOnly, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.ok(service.unreadCount(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable long id) {
        service.markRead(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @PostMapping("/read-all")
    public Result<Void> markAllRead() {
        service.markAllRead(StpUtil.getLoginIdAsLong());
        return Result.ok();
    }
}
