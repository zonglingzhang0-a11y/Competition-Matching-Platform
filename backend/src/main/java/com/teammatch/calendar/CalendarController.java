package com.teammatch.calendar;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.calendar.CalendarDtos.CalendarView;
import com.teammatch.calendar.CalendarDtos.DoneForm;
import com.teammatch.calendar.CalendarDtos.EventForm;
import com.teammatch.calendar.CalendarDtos.EventView;
import com.teammatch.calendar.CalendarDtos.MilestoneForm;
import com.teammatch.calendar.CalendarDtos.MyTask;
import com.teammatch.calendar.CalendarDtos.TaskForm;
import com.teammatch.calendar.CalendarDtos.TeamTimeline;
import com.teammatch.common.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final PersonalEventService eventService;
    private final MilestoneService milestoneService;
    private final TaskService taskService;
    private final CalendarFeedService feedService;

    private static long me() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 日历：from、to 都包含，格式 2026-10-01 */
    @GetMapping("/calendar")
    public Result<CalendarView> calendar(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return Result.ok(calendarService.view(me(), from, to));
    }

    // ---------- 个人日程 ----------

    @PostMapping("/events")
    public Result<Long> createEvent(@Valid @RequestBody EventForm form) {
        return Result.ok(eventService.create(me(), form));
    }

    @GetMapping("/events/{id}")
    public Result<EventView> getEvent(@PathVariable long id) {
        return Result.ok(eventService.get(me(), id));
    }

    @PutMapping("/events/{id}")
    public Result<Void> updateEvent(@PathVariable long id, @Valid @RequestBody EventForm form) {
        eventService.update(me(), id, form);
        return Result.ok();
    }

    @DeleteMapping("/events/{id}")
    public Result<Void> deleteEvent(@PathVariable long id) {
        eventService.delete(me(), id);
        return Result.ok();
    }

    /** 重复日程只删某一天 */
    @PostMapping("/events/{id}/skip")
    public Result<Void> skipOccurrence(@PathVariable long id,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        eventService.skip(me(), id, date);
        return Result.ok();
    }

    // ---------- 队伍时间线 ----------

    @GetMapping("/teams/{id}/timeline")
    public Result<TeamTimeline> timeline(@PathVariable long id) {
        return Result.ok(milestoneService.timeline(me(), id));
    }

    @PostMapping("/teams/{id}/milestones")
    public Result<Long> createMilestone(@PathVariable long id, @Valid @RequestBody MilestoneForm form) {
        return Result.ok(milestoneService.create(me(), id, form));
    }

    @PutMapping("/milestones/{id}")
    public Result<Void> updateMilestone(@PathVariable long id, @Valid @RequestBody MilestoneForm form) {
        milestoneService.update(me(), id, form);
        return Result.ok();
    }

    @DeleteMapping("/milestones/{id}")
    public Result<Void> deleteMilestone(@PathVariable long id) {
        milestoneService.delete(me(), id);
        return Result.ok();
    }

    @PutMapping("/milestones/{id}/done")
    public Result<Void> setDone(@PathVariable long id, @RequestBody DoneForm form) {
        milestoneService.setDone(me(), id, form.done());
        return Result.ok();
    }

    // ---------- 里程碑下的分工待办（权限规则见 TaskService） ----------

    @PostMapping("/milestones/{id}/tasks")
    public Result<Long> createTask(@PathVariable long id, @Valid @RequestBody TaskForm form) {
        return Result.ok(taskService.create(me(), id, form));
    }

    @PutMapping("/tasks/{id}")
    public Result<Void> updateTask(@PathVariable long id, @Valid @RequestBody TaskForm form) {
        taskService.update(me(), id, form);
        return Result.ok();
    }

    @PostMapping("/tasks/{id}/claim")
    public Result<Void> claimTask(@PathVariable long id) {
        taskService.claim(me(), id);
        return Result.ok();
    }

    @PostMapping("/tasks/{id}/release")
    public Result<Void> releaseTask(@PathVariable long id) {
        taskService.release(me(), id);
        return Result.ok();
    }

    @PutMapping("/tasks/{id}/done")
    public Result<Void> setTaskDone(@PathVariable long id, @RequestBody DoneForm form) {
        taskService.setDone(me(), id, form.done());
        return Result.ok();
    }

    @DeleteMapping("/tasks/{id}")
    public Result<Void> deleteTask(@PathVariable long id) {
        taskService.delete(me(), id);
        return Result.ok();
    }

    // ---------- 订阅到手机日历 ----------

    /** 我的订阅链接（第一次调用时生成）；path 拼上网站地址就是完整链接 */
    @GetMapping("/calendar/feed")
    public Result<Map<String, String>> feed() {
        return Result.ok(Map.of("path", "/api/ics/" + feedService.token(me()) + ".ics"));
    }

    /** 链接泄露了就重置，旧链接马上失效 */
    @PostMapping("/calendar/feed/reset")
    public Result<Map<String, String>> resetFeed() {
        return Result.ok(Map.of("path", "/api/ics/" + feedService.reset(me()) + ".ics"));
    }

    /** 日历应用来取的地址，不用登录，靠链接里的 token 认人 */
    @GetMapping(value = "/ics/{token}.ics")
    public ResponseEntity<String> ics(@PathVariable String token) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "calendar", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"jingda.ics\"")
                .body(feedService.ics(token));
    }

    /** 分给我、还没做完的任务 */
    @GetMapping("/tasks/mine")
    public Result<List<MyTask>> myTasks() {
        return Result.ok(taskService.mine(me()));
    }
}
