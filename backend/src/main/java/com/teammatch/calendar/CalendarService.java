package com.teammatch.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.calendar.CalendarDtos.CalendarEvent;
import com.teammatch.calendar.CalendarDtos.CalendarView;
import com.teammatch.calendar.CalendarDtos.Conflict;
import com.teammatch.calendar.CalendarDtos.Layer;
import com.teammatch.calendar.CalendarEntities.MilestoneMapper;
import com.teammatch.calendar.CalendarEntities.PersonalEvent;
import com.teammatch.calendar.CalendarEntities.TeamMilestone;
import com.teammatch.calendar.CalendarMapper.ActiveTeam;
import com.teammatch.calendar.CalendarMapper.EditionLabel;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import com.teammatch.competition.CompetitionNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 日历：把比赛官方节点、所在队伍的里程碑、个人日程合到一起，并标出撞期。
 * 日历本身不存数据，每次按时间范围查出来（见 docs/数据库设计.md 第 4 节）。
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private static final long MAX_DAYS = 100;

    private final CalendarMapper calendarMapper;
    private final NodeMapper nodeMapper;
    private final MilestoneMapper milestoneMapper;
    private final PersonalEventService eventService;

    public CalendarView view(long userId, LocalDate fromDate, LocalDate toDate) {
        if (toDate.isBefore(fromDate) || ChronoUnit.DAYS.between(fromDate, toDate) > MAX_DAYS) {
            throw new BizException("日期范围不对，一次最多查 " + MAX_DAYS + " 天");
        }
        LocalDateTime from = fromDate.atStartOfDay();
        LocalDateTime to = toDate.plusDays(1).atStartOfDay();

        List<ActiveTeam> teams = calendarMapper.activeTeams(userId);
        Set<Long> joined = teams.stream().map(ActiveTeam::editionId).collect(Collectors.toSet());
        Set<Long> editions = new HashSet<>(calendarMapper.followedEditions(userId));
        editions.addAll(joined);
        Map<Long, EditionLabel> labels = editions.isEmpty() ? Map.of()
                : calendarMapper.editionLabels(editions).stream()
                .collect(Collectors.toMap(EditionLabel::editionId, Function.identity()));

        List<Item> items = new ArrayList<>();

        // 比赛官方节点
        if (!editions.isEmpty()) {
            for (CompetitionNode n : nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                    .in(CompetitionNode::getEditionId, editions))) {
                LocalDateTime end = displayEnd(n.getEndMode(), n.getEndAt(), n.getEndLatestAt());
                if (!Occurrences.overlaps(n.getStartAt(), end, from, to)) {
                    continue;
                }
                EditionLabel l = labels.get(n.getEditionId());
                String label = l == null ? "比赛" : l.label();
                items.add(new Item("node-" + n.getId(), Layer.OFFICIAL, "edition-" + n.getEditionId(), label,
                        n.getTitle(), n.getStartAt(), end, Boolean.TRUE.equals(n.getAllDay()),
                        fuzzy(n.getEndMode(), n.getStartLatestAt()), Boolean.TRUE.equals(n.getBusy()), null,
                        "COMPETITION", l == null ? null : l.competitionId(), null, joined.contains(n.getEditionId())));
            }
        }

        // 队伍里程碑
        Map<Long, String> teamNames = teams.stream().collect(Collectors.toMap(ActiveTeam::teamId, ActiveTeam::teamName));
        if (!teamNames.isEmpty()) {
            for (TeamMilestone m : milestoneMapper.selectList(new LambdaQueryWrapper<TeamMilestone>()
                    .in(TeamMilestone::getTeamId, teamNames.keySet()))) {
                LocalDateTime end = displayEnd(m.getEndMode(), m.getEndAt(), m.getEndLatestAt());
                if (Occurrences.overlaps(m.getStartAt(), end, from, to)) {
                    items.add(new Item("milestone-" + m.getId(), Layer.TEAM, "team-" + m.getTeamId(),
                            teamNames.get(m.getTeamId()), m.getTitle(), m.getStartAt(), end,
                            Boolean.TRUE.equals(m.getAllDay()), fuzzy(m.getEndMode(), m.getStartLatestAt()), false,
                            m.getDoneAt() != null, "TEAM", m.getTeamId(), null, false));
                }
            }
        }

        // 个人日程（重复的展开成每一次）
        for (PersonalEvent e : eventService.ofUser(userId)) {
            for (Occurrences.Occurrence o : Occurrences.between(eventService.rule(e), from, to)) {
                items.add(new Item("event-" + e.getId() + "@" + o.date(), Layer.PERSONAL, "personal", "个人",
                        e.getTitle(), o.start(), o.end(), Boolean.TRUE.equals(e.getAllDay()), false,
                        Boolean.TRUE.equals(e.getBusy()), null, "EVENT", e.getId(), o.date(), false));
            }
        }

        List<Conflict> conflicts = markConflicts(items);
        items.sort(Comparator.comparing(Item::start));
        return new CalendarView(items.stream().map(Item::toEvent).toList(), conflicts);
    }

    /**
     * 撞期：个人日程和比赛期间重叠；或者已经参加的两场比赛，比赛期间互相重叠。
     * 只看标了"参与撞期检测"的（busy）。
     */
    static List<Conflict> markConflicts(List<Item> items) {
        List<Item> official = items.stream().filter(i -> i.layer == Layer.OFFICIAL && i.busy).toList();
        List<Item> personal = items.stream().filter(i -> i.layer == Layer.PERSONAL && i.busy).toList();
        List<Conflict> out = new ArrayList<>();
        for (Item p : personal) {
            for (Item o : official) {
                if (Occurrences.overlaps(p.start, p.end, o.start, o.end)) {
                    p.conflictWith = o.layerName + "「" + o.title + "」";
                    o.conflictWith = "个人日程「" + p.title + "」";
                    out.add(new Conflict("个人日程「" + p.title + "」", o.layerName + "「" + o.title + "」", p.start));
                }
            }
        }
        for (int i = 0; i < official.size(); i++) {
            for (int j = i + 1; j < official.size(); j++) {
                Item a = official.get(i);
                Item b = official.get(j);
                if (a.joined && b.joined && !a.layerKey.equals(b.layerKey)
                        && Occurrences.overlaps(a.start, a.end, b.start, b.end)) {
                    a.conflictWith = b.layerName + "「" + b.title + "」";
                    b.conflictWith = a.layerName + "「" + a.title + "」";
                    out.add(new Conflict(a.layerName + "「" + a.title + "」", b.layerName + "「" + b.title + "」",
                            a.start.isBefore(b.start) ? b.start : a.start));
                }
            }
        }
        return out;
    }

    /** 显示到什么时候：结束是区间时取最晚的那个，没有结束时间为空 */
    static LocalDateTime displayEnd(CompetitionNode.EndMode mode, LocalDateTime end, LocalDateTime endLatest) {
        if (mode == CompetitionNode.EndMode.OPEN) {
            return null;
        }
        if (mode == CompetitionNode.EndMode.RANGE && endLatest != null) {
            return endLatest;
        }
        return end;
    }

    private static boolean fuzzy(CompetitionNode.EndMode mode, LocalDateTime startLatest) {
        return (mode != null && mode != CompetitionNode.EndMode.FIXED) || startLatest != null;
    }

    /** 拼装时用的可变中间结果，撞期检测要回头给两边都打上标记 */
    static final class Item {
        final String id;
        final Layer layer;
        final String layerKey;
        final String layerName;
        final String title;
        final LocalDateTime start;
        final LocalDateTime end;
        final boolean allDay;
        final boolean fuzzy;
        final boolean busy;
        final Boolean done;
        final String refType;
        final Long refId;
        final LocalDate occurrenceDate;
        /** 只有已经参加的比赛之间才算互相撞期，光关注的不算 */
        final boolean joined;
        String conflictWith;

        Item(String id, Layer layer, String layerKey, String layerName, String title, LocalDateTime start,
             LocalDateTime end, boolean allDay, boolean fuzzy, boolean busy, Boolean done, String refType, Long refId,
             LocalDate occurrenceDate, boolean joined) {
            this.id = id;
            this.layer = layer;
            this.layerKey = layerKey;
            this.layerName = layerName;
            this.title = title;
            this.start = start;
            this.end = end;
            this.allDay = allDay;
            this.fuzzy = fuzzy;
            this.busy = busy;
            this.done = done;
            this.refType = refType;
            this.refId = refId;
            this.occurrenceDate = occurrenceDate;
            this.joined = joined;
        }

        LocalDateTime start() {
            return start;
        }

        CalendarEvent toEvent() {
            return new CalendarEvent(id, layer, layerKey, layerName, title, start, end, allDay, fuzzy, busy, done,
                    conflictWith, refType, refId, occurrenceDate);
        }
    }
}
