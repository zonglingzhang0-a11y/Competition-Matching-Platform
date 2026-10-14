package com.teammatch.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.calendar.CalendarDtos.MilestoneForm;
import com.teammatch.calendar.CalendarDtos.MilestoneView;
import com.teammatch.calendar.CalendarDtos.TaskView;
import com.teammatch.calendar.CalendarDtos.TeamTimeline;
import com.teammatch.calendar.CalendarEntities.MilestoneMapper;
import com.teammatch.calendar.CalendarEntities.TeamMilestone;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionDtos.NodeView;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import com.teammatch.competition.CompetitionNode;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.team.Team;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * 队伍时间线：比赛官方节点（所有人可见）+ 队伍里程碑（只有队员可见）。
 * 里程碑由队长增删改，队员都能勾"已完成"。里程碑下的分工待办见 {@link TaskService}。
 */
@Service
@RequiredArgsConstructor
public class MilestoneService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final MilestoneMapper milestoneMapper;
    private final NodeMapper nodeMapper;
    private final CalendarMapper calendarMapper;
    private final TeamSupport teamSupport;
    private final TaskService taskService;
    private final NotificationService notifications;
    private final Clock clock;

    public TeamTimeline timeline(long viewerId, long teamId) {
        Team t = teamSupport.requireTeam(teamId);
        List<NodeView> official = nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                        .eq(CompetitionNode::getEditionId, t.getEditionId())
                        .orderByAsc(CompetitionNode::getStartAt))
                .stream().map(NodeView::of).toList();
        if (!isMember(viewerId, t)) {
            return new TeamTimeline(official, List.of(), false, false, List.of());
        }
        List<TeamMilestone> list = milestoneMapper.selectList(new LambdaQueryWrapper<TeamMilestone>()
                .eq(TeamMilestone::getTeamId, teamId)
                .orderByAsc(TeamMilestone::getStartAt));
        List<Long> memberIds = teamSupport.activeMembers(teamId).stream().map(TeamMember::getUserId).toList();
        Map<Long, List<TaskView>> tasks = taskService.byMilestone(
                list.stream().map(TeamMilestone::getId).toList(), new HashSet<>(memberIds));
        List<MilestoneView> milestones = list.stream()
                .map(m -> toView(m, tasks.getOrDefault(m.getId(), List.of()))).toList();
        return new TeamTimeline(official, milestones, true, t.getCaptainId().equals(viewerId),
                taskService.memberOptions(memberIds));
    }

    @Transactional
    public long create(long captainId, long teamId, MilestoneForm form) {
        Team t = teamSupport.requireCaptain(captainId, teamId);
        TeamMilestone m = new TeamMilestone();
        m.setTeamId(teamId);
        m.setCreatedBy(captainId);
        apply(m, form);
        milestoneMapper.insert(m);
        for (Long uid : calendarMapper.teamMembers(teamId)) {
            if (uid != captainId) {
                notifications.send(uid, Notification.Type.MILESTONE, "队伍时间线有新节点",
                        "「" + t.getName() + "」新增了节点「" + m.getTitle() + "」（" + WHEN.format(m.getStartAt()) + "），已经放进你的日历。",
                        NotificationService.REF_TEAM, teamId);
            }
        }
        return m.getId();
    }

    public void update(long captainId, long id, MilestoneForm form) {
        TeamMilestone m = require(id);
        teamSupport.requireCaptain(captainId, m.getTeamId());
        apply(m, form);
        milestoneMapper.update(new LambdaUpdateWrapper<TeamMilestone>()
                .eq(TeamMilestone::getId, id)
                .set(TeamMilestone::getTitle, m.getTitle())
                .set(TeamMilestone::getNote, m.getNote())
                .set(TeamMilestone::getStartAt, m.getStartAt())
                .set(TeamMilestone::getEndAt, m.getEndAt())
                .set(TeamMilestone::getAllDay, m.getAllDay())
                .set(TeamMilestone::getEndMode, m.getEndMode())
                .set(TeamMilestone::getEndLatestAt, m.getEndLatestAt()));
    }

    public void delete(long captainId, long id) {
        TeamMilestone m = require(id);
        teamSupport.requireCaptain(captainId, m.getTeamId());
        milestoneMapper.deleteById(id);
    }

    /** 队员都能勾完成 / 取消完成 */
    public void setDone(long userId, long id, boolean done) {
        TeamMilestone m = require(id);
        Team t = teamSupport.requireTeam(m.getTeamId());
        if (!isMember(userId, t)) {
            throw new BizException(403, "只有队员能这样操作");
        }
        milestoneMapper.update(new LambdaUpdateWrapper<TeamMilestone>()
                .eq(TeamMilestone::getId, id)
                .set(TeamMilestone::getDoneAt, done ? LocalDateTime.now(clock) : null));
    }

    private boolean isMember(long userId, Team t) {
        TeamMember m = teamSupport.activeMembership(userId, t.getEditionId());
        return m != null && m.getTeamId().equals(t.getId());
    }

    private static void apply(TeamMilestone m, MilestoneForm f) {
        CompetitionNode.EndMode mode = f.endMode() == null ? CompetitionNode.EndMode.FIXED : f.endMode();
        if (mode == CompetitionNode.EndMode.FIXED && f.endAt() != null && f.endAt().isBefore(f.startAt())) {
            throw new BizException("结束时间不能早于开始时间");
        }
        if (mode == CompetitionNode.EndMode.RANGE
                && (f.endAt() == null || f.endLatestAt() == null || f.endLatestAt().isBefore(f.endAt()) || f.endAt().isBefore(f.startAt()))) {
            throw new BizException("结束时间是区间时，要填最早和最晚两个时间，并且最晚不能早于最早");
        }
        m.setTitle(f.title().trim());
        m.setNote(f.note() == null || f.note().isBlank() ? null : f.note().trim());
        m.setStartAt(f.startAt());
        m.setEndAt(mode == CompetitionNode.EndMode.OPEN ? null : f.endAt());
        m.setAllDay(f.allDay());
        m.setEndMode(mode);
        m.setEndLatestAt(mode == CompetitionNode.EndMode.RANGE ? f.endLatestAt() : null);
    }

    private TeamMilestone require(long id) {
        TeamMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BizException(404, "这个节点不存在");
        }
        return m;
    }

    private static MilestoneView toView(TeamMilestone m, List<TaskView> tasks) {
        return new MilestoneView(m.getId(), m.getTitle(), m.getNote(), m.getStartAt(), m.getEndAt(),
                Boolean.TRUE.equals(m.getAllDay()), m.getEndMode(), m.getEndLatestAt(), m.getDoneAt(), tasks);
    }
}
