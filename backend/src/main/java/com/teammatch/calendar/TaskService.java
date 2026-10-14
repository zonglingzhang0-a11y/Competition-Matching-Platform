package com.teammatch.calendar;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.calendar.CalendarDtos.MemberOption;
import com.teammatch.calendar.CalendarDtos.MyTask;
import com.teammatch.calendar.CalendarDtos.TaskForm;
import com.teammatch.calendar.CalendarDtos.TaskView;
import com.teammatch.calendar.CalendarEntities.MilestoneMapper;
import com.teammatch.calendar.CalendarEntities.TaskMapper;
import com.teammatch.calendar.CalendarEntities.TeamMilestone;
import com.teammatch.calendar.CalendarEntities.TeamTask;
import com.teammatch.common.BizException;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.team.Team;
import com.teammatch.team.TeamMember;
import com.teammatch.team.TeamSupport;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 里程碑下的分工待办。
 * <ul>
 *   <li>队员：加任务（分给自己或先空着）、认领没人负责的、勾自己的任务完成、放弃自己的任务</li>
 *   <li>队长：另外能把任务指派给任何队员、改任务名、删任务；指派给别人时对方收到站内信</li>
 * </ul>
 * 负责人离队后，他没做完的任务算没人负责，谁都可以认领。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /** 一个里程碑下最多这么多任务 */
    static final int MAX_PER_MILESTONE = 30;

    private final TaskMapper taskMapper;
    private final MilestoneMapper milestoneMapper;
    private final TeamSupport teamSupport;
    private final SysUserMapper userMapper;
    private final NotificationService notifications;
    private final Clock clock;

    /** 一支队伍的任务，按里程碑分好组。activeMemberIds 用来判断负责人是不是已经离队 */
    Map<Long, List<TaskView>> byMilestone(Collection<Long> milestoneIds, Set<Long> activeMemberIds) {
        if (milestoneIds.isEmpty()) {
            return Map.of();
        }
        List<TeamTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<TeamTask>()
                .in(TeamTask::getMilestoneId, milestoneIds)
                .orderByAsc(TeamTask::getSort)
                .orderByAsc(TeamTask::getId));
        Map<Long, String> names = nicknames(tasks.stream().map(TeamTask::getAssigneeId).toList());
        return tasks.stream().collect(Collectors.groupingBy(TeamTask::getMilestoneId, Collectors.mapping(t -> new TaskView(
                t.getId(), t.getTitle(), t.getAssigneeId(), names.get(t.getAssigneeId()),
                t.getAssigneeId() != null && !activeMemberIds.contains(t.getAssigneeId()), t.getDoneAt()),
                Collectors.toList())));
    }

    /** 分任务时可选的人（按传入的顺序，队长在前） */
    List<MemberOption> memberOptions(List<Long> memberIds) {
        Map<Long, String> names = nicknames(memberIds);
        return memberIds.stream().map(id -> new MemberOption(id, names.get(id))).toList();
    }

    public List<MyTask> mine(long userId) {
        return taskMapper.mine(userId);
    }

    @Transactional
    public long create(long userId, long milestoneId, TaskForm form) {
        Ctx c = context(userId, requireMilestone(milestoneId));
        c.requireMember();
        Long assignee = form.assigneeId();
        if (assignee != null) {
            if (!c.captain && !assignee.equals(userId)) {
                throw new BizException(403, "只有队长能把任务分给别人，你可以先分给自己或空着");
            }
            c.requireActive(assignee);
        }
        long count = taskMapper.selectCount(new LambdaQueryWrapper<TeamTask>().eq(TeamTask::getMilestoneId, milestoneId));
        if (count >= MAX_PER_MILESTONE) {
            throw new BizException("一个节点下最多 " + MAX_PER_MILESTONE + " 个任务");
        }
        TeamTask t = new TeamTask();
        t.setMilestoneId(milestoneId);
        t.setTitle(form.title().trim());
        t.setAssigneeId(assignee);
        t.setSort((int) count);
        taskMapper.insert(t);
        notifyAssigned(c, t, userId);
        return t.getId();
    }

    /** 队长改任务名、换负责人 */
    @Transactional
    public void update(long userId, long taskId, TaskForm form) {
        TeamTask t = requireTask(taskId);
        Ctx c = context(userId, requireMilestone(t.getMilestoneId()));
        c.requireCaptain();
        Long assignee = form.assigneeId();
        if (assignee != null) {
            c.requireActive(assignee);
        }
        boolean reassigned = !Objects.equals(assignee, t.getAssigneeId());
        taskMapper.update(new LambdaUpdateWrapper<TeamTask>()
                .eq(TeamTask::getId, taskId)
                .set(TeamTask::getTitle, form.title().trim())
                .set(TeamTask::getAssigneeId, assignee));
        t.setTitle(form.title().trim());
        t.setAssigneeId(assignee);
        if (reassigned) {
            notifyAssigned(c, t, userId);
        }
    }

    /** 认领没人负责的任务（负责人离队了的也算） */
    public void claim(long userId, long taskId) {
        TeamTask t = requireTask(taskId);
        Ctx c = context(userId, requireMilestone(t.getMilestoneId()));
        c.requireMember();
        Long current = t.getAssigneeId();
        if (Objects.equals(current, userId)) {
            return;
        }
        if (current != null && c.memberIds.contains(current)) {
            throw new BizException(409, "这个任务已经有人负责了");
        }
        // 条件更新：两个人同时认领，只有一个能成功
        int updated = taskMapper.update(new LambdaUpdateWrapper<TeamTask>()
                .eq(TeamTask::getId, taskId)
                .and(w -> {
                    if (current == null) {
                        w.isNull(TeamTask::getAssigneeId);
                    } else {
                        w.eq(TeamTask::getAssigneeId, current);
                    }
                })
                .set(TeamTask::getAssigneeId, userId));
        if (updated == 0) {
            throw new BizException(409, "这个任务刚被别人认领了");
        }
    }

    /** 放弃自己负责的任务，变回没人负责 */
    public void release(long userId, long taskId) {
        TeamTask t = requireTask(taskId);
        context(userId, requireMilestone(t.getMilestoneId())).requireMember();
        if (!Objects.equals(t.getAssigneeId(), userId)) {
            throw new BizException(403, "只能放弃自己负责的任务");
        }
        taskMapper.update(new LambdaUpdateWrapper<TeamTask>()
                .eq(TeamTask::getId, taskId)
                .eq(TeamTask::getAssigneeId, userId)
                .set(TeamTask::getAssigneeId, null));
    }

    /**
     * 勾完成 / 取消完成：负责人和队长可以。
     * 没人负责的任务，队员直接勾完成就算是他做的（顺手认领）。
     */
    public void setDone(long userId, long taskId, boolean done) {
        TeamTask t = requireTask(taskId);
        Ctx c = context(userId, requireMilestone(t.getMilestoneId()));
        c.requireMember();
        boolean nobody = t.getAssigneeId() == null || !c.memberIds.contains(t.getAssigneeId());
        if (!c.captain && !Objects.equals(t.getAssigneeId(), userId) && !nobody) {
            throw new BizException(403, "这是 " + nicknames(List.of(t.getAssigneeId())).get(t.getAssigneeId())
                    + " 负责的任务，只有他自己或队长能勾");
        }
        LambdaUpdateWrapper<TeamTask> w = new LambdaUpdateWrapper<TeamTask>()
                .eq(TeamTask::getId, taskId)
                .set(TeamTask::getDoneAt, done ? LocalDateTime.now(clock) : null);
        if (done && nobody && !c.captain) {
            w.set(TeamTask::getAssigneeId, userId);
        }
        taskMapper.update(w);
    }

    public void delete(long userId, long taskId) {
        TeamTask t = requireTask(taskId);
        context(userId, requireMilestone(t.getMilestoneId())).requireCaptain();
        taskMapper.deleteById(taskId);
    }

    /** 队长把任务分给了别人：通知对方（分给自己不用通知） */
    private void notifyAssigned(Ctx c, TeamTask t, long operatorId) {
        Long assignee = t.getAssigneeId();
        if (assignee == null || assignee == operatorId) {
            return;
        }
        notifications.send(assignee, Notification.Type.TASK, "你有一个新任务",
                "「" + c.team.getName() + "」在「" + c.milestone.getTitle() + "」下把「" + t.getTitle() + "」分给了你。",
                NotificationService.REF_TEAM, c.team.getId());
    }

    private Ctx context(long userId, TeamMilestone m) {
        Team team = teamSupport.requireTeam(m.getTeamId());
        Set<Long> memberIds = teamSupport.activeMembers(team.getId()).stream()
                .map(TeamMember::getUserId).collect(Collectors.toSet());
        return new Ctx(m, team, memberIds, memberIds.contains(userId), team.getCaptainId().equals(userId));
    }

    private Map<Long, String> nicknames(List<Long> ids) {
        List<Long> distinct = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectByIds(distinct).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));
    }

    private TeamMilestone requireMilestone(long id) {
        TeamMilestone m = milestoneMapper.selectById(id);
        if (m == null) {
            throw new BizException(404, "这个节点不存在");
        }
        return m;
    }

    private TeamTask requireTask(long id) {
        TeamTask t = taskMapper.selectById(id);
        if (t == null) {
            throw new BizException(404, "这个任务不存在");
        }
        return t;
    }

    /** 一次操作要用到的：任务所在的里程碑和队伍、现在的队员、操作人是不是队员 / 队长 */
    private record Ctx(TeamMilestone milestone, Team team, Set<Long> memberIds, boolean member, boolean captain) {

        void requireMember() {
            if (!member) {
                throw new BizException(403, "只有队员能这样操作");
            }
        }

        void requireCaptain() {
            if (!captain) {
                throw new BizException(403, "只有队长能这样操作");
            }
        }

        void requireActive(long userId) {
            if (!memberIds.contains(userId)) {
                throw new BizException("只能分给现在的队员");
            }
        }
    }
}
