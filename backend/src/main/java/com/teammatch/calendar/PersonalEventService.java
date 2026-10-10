package com.teammatch.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.calendar.CalendarDtos.EventForm;
import com.teammatch.calendar.CalendarDtos.EventView;
import com.teammatch.calendar.CalendarEntities.PersonalEvent;
import com.teammatch.calendar.CalendarEntities.PersonalEventMapper;
import com.teammatch.calendar.CalendarEntities.RepeatType;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 个人日程：只能看和改自己的。
 */
@Service
@RequiredArgsConstructor
public class PersonalEventService {

    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };
    /** 一次日程最长 31 天，再长就不是"日程"了 */
    private static final Duration MAX_LENGTH = Duration.ofDays(31);

    private final PersonalEventMapper mapper;
    private final JsonMapper json;

    public long create(long userId, EventForm form) {
        PersonalEvent e = new PersonalEvent();
        e.setUserId(userId);
        apply(e, form);
        e.setExceptions("[]");
        mapper.insert(e);
        return e.getId();
    }

    public void update(long userId, long id, EventForm form) {
        PersonalEvent e = require(userId, id);
        apply(e, form);
        mapper.update(new LambdaUpdateWrapper<PersonalEvent>()
                .eq(PersonalEvent::getId, id)
                .set(PersonalEvent::getTitle, e.getTitle())
                .set(PersonalEvent::getNotes, e.getNotes())
                .set(PersonalEvent::getColor, e.getColor())
                .set(PersonalEvent::getStartAt, e.getStartAt())
                .set(PersonalEvent::getEndAt, e.getEndAt())
                .set(PersonalEvent::getAllDay, e.getAllDay())
                .set(PersonalEvent::getRepeatType, e.getRepeatType())
                .set(PersonalEvent::getRepeatWeekdays, e.getRepeatWeekdays())
                .set(PersonalEvent::getRepeatUntil, e.getRepeatUntil())
                .set(PersonalEvent::getBusy, e.getBusy()));
    }

    public void delete(long userId, long id) {
        require(userId, id);
        mapper.deleteById(id);
    }

    /** 重复日程只删某一天；不重复的就整条删掉 */
    public void skip(long userId, long id, LocalDate date) {
        PersonalEvent e = require(userId, id);
        if (e.getRepeatType() == null || e.getRepeatType() == RepeatType.NONE) {
            mapper.deleteById(id);
            return;
        }
        List<String> exceptions = new ArrayList<>(parseExceptions(e));
        if (!exceptions.contains(date.toString())) {
            exceptions.add(date.toString());
        }
        PersonalEvent update = new PersonalEvent();
        update.setId(id);
        update.setExceptions(json.writeValueAsString(exceptions));
        mapper.updateById(update);
    }

    public EventView get(long userId, long id) {
        PersonalEvent e = require(userId, id);
        return new EventView(e.getId(), e.getTitle(), e.getNotes(), e.getColor(), e.getStartAt(), e.getEndAt(),
                Boolean.TRUE.equals(e.getAllDay()), e.getRepeatType(),
                Occurrences.parseWeekdays(e.getRepeatWeekdays()).stream().map(d -> d.getValue()).sorted().toList(),
                e.getRepeatUntil(), Boolean.TRUE.equals(e.getBusy()));
    }

    public List<PersonalEvent> ofUser(long userId) {
        return mapper.selectList(new LambdaQueryWrapper<PersonalEvent>().eq(PersonalEvent::getUserId, userId));
    }

    /** 一批人参与撞期检测的日程，推荐时判断"比赛期间有没有个人安排"用 */
    public Map<Long, List<PersonalEvent>> busyOf(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(new LambdaQueryWrapper<PersonalEvent>()
                        .in(PersonalEvent::getUserId, userIds)
                        .eq(PersonalEvent::getBusy, true))
                .stream().collect(Collectors.groupingBy(PersonalEvent::getUserId));
    }

    /** 转成展开重复用的规则 */
    public Occurrences.Rule rule(PersonalEvent e) {
        return new Occurrences.Rule(e.getStartAt(), e.getEndAt(), Boolean.TRUE.equals(e.getAllDay()),
                e.getRepeatType() == null ? RepeatType.NONE : e.getRepeatType(),
                Occurrences.parseWeekdays(e.getRepeatWeekdays()), e.getRepeatUntil(),
                parseExceptions(e).stream().map(LocalDate::parse).collect(Collectors.toSet()));
    }

    private void apply(PersonalEvent e, EventForm f) {
        RepeatType repeat = f.repeatType() == null ? RepeatType.NONE : f.repeatType();
        if (f.endAt() != null && f.endAt().isBefore(f.startAt())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        if (f.endAt() != null && Duration.between(f.startAt(), f.endAt()).compareTo(MAX_LENGTH) > 0) {
            throw new BizException("一条日程最长 31 天");
        }
        List<Integer> weekdays = f.repeatWeekdays() == null ? List.of() : f.repeatWeekdays();
        if (weekdays.stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw new BizException("星期要在 1 到 7 之间");
        }
        if (repeat == RepeatType.WEEKLY && weekdays.isEmpty()) {
            throw new BizException("每周重复要选星期几");
        }
        if (f.repeatUntil() != null && f.repeatUntil().isBefore(f.startAt().toLocalDate())) {
            throw new BizException("重复截止日期不能早于开始日期");
        }
        e.setTitle(f.title().trim());
        e.setNotes(f.notes() == null || f.notes().isBlank() ? null : f.notes().trim());
        e.setColor(f.color());
        e.setStartAt(f.startAt());
        e.setEndAt(f.endAt());
        e.setAllDay(f.allDay());
        e.setEndMode(CompetitionNode.EndMode.FIXED);
        e.setRepeatType(repeat);
        e.setRepeatWeekdays(repeat == RepeatType.WEEKLY
                ? new HashSet<>(weekdays).stream().sorted().map(String::valueOf).collect(Collectors.joining(","))
                : null);
        e.setRepeatUntil(repeat == RepeatType.NONE ? null : f.repeatUntil());
        e.setBusy(f.busy());
    }

    private PersonalEvent require(long userId, long id) {
        PersonalEvent e = mapper.selectById(id);
        if (e == null || !e.getUserId().equals(userId)) {
            throw new BizException(404, "这条日程不存在");
        }
        return e;
    }

    private List<String> parseExceptions(PersonalEvent e) {
        return e.getExceptions() == null || e.getExceptions().isBlank() ? List.of() : json.readValue(e.getExceptions(), STRINGS);
    }
}
