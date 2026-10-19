package com.teammatch.recognition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.competition.Competition;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.recognition.RecognitionDtos.AdminRow;
import com.teammatch.recognition.RecognitionDtos.Board;
import com.teammatch.recognition.RecognitionDtos.Entry;
import com.teammatch.recognition.RecognitionDtos.MineForm;
import com.teammatch.recognition.RecognitionDtos.MySchool;
import com.teammatch.recognition.RecognitionDtos.OtherSchool;
import com.teammatch.recognition.RecognitionMappers.ConfirmMapper;
import com.teammatch.recognition.RecognitionMappers.RecognitionMapper;
import com.teammatch.school.School;
import com.teammatch.school.SchoolMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 各校的加分认定：同校同学标注本校把某个比赛认定成什么级别、附上学校文件，另外几位同校同学确认后才对外显示。
 * <p>
 * 规则：
 * <ul>
 *   <li>认证有效、有学校的人才能标注和确认，只能管自己学校的；</li>
 *   <li>一个人对一个比赛只有一种说法：要么自己提交一条，要么确认别人的一条（改确认别人的，原来的确认自动撤掉）；</li>
 *   <li>改了级别或文件链接，等于换了一种说法，之前的确认清零；</li>
 *   <li>同一个学校有几条都确认够了，显示确认人数最多的那条；</li>
 *   <li>管理员可以驳回乱填的，驳回的不显示、不能再确认。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RecognitionService {

    private final RecognitionMapper recognitionMapper;
    private final ConfirmMapper confirmMapper;
    private final CompetitionMapper competitionMapper;
    private final SchoolMapper schoolMapper;
    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RecognitionProperties props;
    private final Clock clock;

    /** 同一个学校的几条里，排在最前面的是对外显示的：确认够了的、人数多的、先提交的 */
    private static final Comparator<Recognition> DISPLAY_ORDER = Comparator
            .comparing((Recognition r) -> r.getStatus() != Recognition.Status.CONFIRMED)
            .thenComparing(Recognition::getConfirmCount, Comparator.reverseOrder())
            .thenComparing(Recognition::getId);

    // ---------- 学生端 ----------

    public Board board(long userId, long competitionId) {
        requireCompetition(competitionId);
        SysUser me = userService.getById(userId);
        List<Recognition> all = recognitionMapper.selectList(new LambdaQueryWrapper<Recognition>()
                .eq(Recognition::getCompetitionId, competitionId));
        Map<Long, List<Recognition>> bySchool = all.stream().collect(Collectors.groupingBy(Recognition::getSchoolId));
        Map<Long, School> schools = schools(bySchool.keySet(), me.getSchoolId());

        MySchool mine = null;
        if (me.getSchoolId() != null && schools.containsKey(me.getSchoolId())) {
            List<Recognition> own = bySchool.getOrDefault(me.getSchoolId(), List.of()).stream()
                    .filter(r -> r.getStatus() != Recognition.Status.REJECTED || r.getSubmittedBy() == userId)
                    .sorted(DISPLAY_ORDER).toList();
            Set<Long> confirmed = own.isEmpty() ? Set.of()
                    : new HashSet<>(confirmMapper.confirmedAmong(userId, own.stream().map(Recognition::getId).toList()));
            Recognition shown = shown(own);
            mine = new MySchool(me.getSchoolId(), schools.get(me.getSchoolId()).getName(), userService.isVerified(me),
                    shown == null ? null : shown.getId(), own.stream().anyMatch(r -> r.getSubmittedBy() == userId),
                    own.stream().map(r -> new Entry(r.getId(), r.getGradeLabel(), r.getNote(), r.getSourceUrl(),
                            r.getConfirmCount(), r.getStatus(), r.getSubmittedBy() == userId,
                            confirmed.contains(r.getId()), r.getCreatedAt())).toList());
        }

        List<OtherSchool> others = bySchool.entrySet().stream()
                .filter(e -> !e.getKey().equals(me.getSchoolId()))
                .map(e -> shown(e.getValue()))
                .filter(Objects::nonNull)
                .map(r -> new OtherSchool(r.getSchoolId(), schools.get(r.getSchoolId()).getName(), r.getGradeLabel(),
                        r.getNote(), r.getSourceUrl(), r.getConfirmCount()))
                .sorted(Comparator.comparing(OtherSchool::schoolName))
                .toList();
        return new Board(mine, others, props.confirmsNeeded());
    }

    /** 比赛列表上给每个比赛标出本校的认定级别（只有确认够了的） */
    public Map<Long, String> labelsForSchool(Long schoolId, Collection<Long> competitionIds) {
        if (schoolId == null || competitionIds.isEmpty()) {
            return Map.of();
        }
        return recognitionMapper.selectList(new LambdaQueryWrapper<Recognition>()
                        .eq(Recognition::getSchoolId, schoolId)
                        .in(Recognition::getCompetitionId, competitionIds)
                        .eq(Recognition::getStatus, Recognition.Status.CONFIRMED))
                .stream()
                .collect(Collectors.groupingBy(Recognition::getCompetitionId,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> list.stream().min(DISPLAY_ORDER).orElseThrow().getGradeLabel())));
    }

    /** 提交或修改自己的那条 */
    @Transactional
    public void saveMine(long userId, long competitionId, MineForm form) {
        Competition c = requireCompetition(competitionId);
        SysUser me = requireContributor(userId);
        // 加锁顺序：先锁人（同一个人的几个请求排队），再按 id 锁认定
        profileMapper.lockUser(userId);
        String label = form.gradeLabel().trim();
        String note = blankToNull(form.note());
        String url = blankToNull(form.sourceUrl());

        Recognition r = findMine(me, competitionId);
        if (r == null) {
            r = new Recognition();
            r.setSchoolId(me.getSchoolId());
            r.setCompetitionId(c.getId());
            r.setGradeLabel(label);
            r.setNote(note);
            r.setSourceUrl(url);
            r.setSubmittedBy(userId);
            r.setConfirmCount(0);
            r.setStatus(Recognition.Status.PENDING);
            r.setCreatedAt(LocalDateTime.now(clock));
            lockInOrder(confirmMapper.confirmedInPair(userId, me.getSchoolId(), competitionId));
            try {
                recognitionMapper.insert(r);
            } catch (DuplicateKeyException e) {
                throw new BizException(409, "你已经提交过了，请刷新页面");
            }
        } else {
            List<Long> lock = new ArrayList<>(confirmMapper.confirmedInPair(userId, me.getSchoolId(), competitionId));
            lock.add(r.getId());
            lockInOrder(lock);
            // 锁住以后重新读：之前读到的是事务开始时的快照，人数和状态可能已经被别人改了
            r = recognitionMapper.lockById(r.getId());
            if (r == null) {
                throw new BizException(409, "你的标注刚被撤回了，请刷新页面");
            }
            boolean newClaim = !label.equals(r.getGradeLabel()) || !Objects.equals(url, r.getSourceUrl());
            r.setGradeLabel(label);
            r.setNote(note);
            r.setSourceUrl(url);
            if (newClaim) {
                // 别人确认的是原来的说法，改了就要重新确认；被驳回的改了级别或链接也重新开始
                confirmMapper.deleteAll(r.getId());
                r.setConfirmCount(0);
                r.setStatus(Recognition.Status.PENDING);
            }
            recognitionMapper.updateById(r);
        }
        // 自己提交了一种说法，之前对别人那条的确认就撤掉
        dropOtherConfirms(userId, me.getSchoolId(), competitionId, r.getId());
    }

    /** 撤回自己的那条，别人对它的确认一起删掉 */
    @Transactional
    public void withdrawMine(long userId, long competitionId) {
        SysUser me = userService.getById(userId);
        profileMapper.lockUser(userId);
        Recognition r = me.getSchoolId() == null ? null : findMine(me, competitionId);
        if (r == null) {
            throw new BizException(404, "你还没有标注过这个比赛");
        }
        recognitionMapper.lockById(r.getId());
        confirmMapper.deleteAll(r.getId());
        recognitionMapper.deleteById(r.getId());
    }

    @Transactional
    public void confirm(long userId, long recognitionId) {
        SysUser me = requireContributor(userId);
        profileMapper.lockUser(userId);
        Recognition peek = recognitionMapper.selectById(recognitionId);
        if (peek == null) {
            throw new BizException(404, "这条认定已经撤回了，请刷新页面");
        }
        // 这一条和自己之前确认的那条都要改人数，按 id 从小到大锁，两个人互相换着确认时不会死锁
        List<Long> lock = new ArrayList<>(confirmMapper.confirmedInPair(userId, peek.getSchoolId(), peek.getCompetitionId()));
        lock.add(recognitionId);
        lockInOrder(lock);
        // 用加锁读拿最新的状态：普通查询读的是事务开始时的快照，两个人同时确认时都会以为是自己让它确认够的
        Recognition r = recognitionMapper.lockById(recognitionId);
        if (r == null) {
            throw new BizException(404, "这条认定已经撤回了，请刷新页面");
        }
        if (!r.getSchoolId().equals(me.getSchoolId())) {
            throw new BizException(403, "只能确认自己学校的认定");
        }
        if (r.getSubmittedBy() == userId) {
            throw new BizException(400, "这条是你自己提交的，要别的同学来确认");
        }
        if (r.getStatus() == Recognition.Status.REJECTED) {
            throw new BizException(400, "这条已经被管理员驳回了");
        }
        if (findMine(me, r.getCompetitionId()) != null) {
            throw new BizException(409, "你自己标注过这个比赛；同意这一条的话，先撤回你自己的");
        }
        dropOtherConfirms(userId, me.getSchoolId(), r.getCompetitionId(), r.getId());
        confirmMapper.insert(r.getId(), userId);
        Recognition.Status before = r.getStatus();
        recognitionMapper.recount(r.getId(), props.confirmsNeeded());
        Recognition after = recognitionMapper.selectById(r.getId());
        if (before != Recognition.Status.CONFIRMED && after.getStatus() == Recognition.Status.CONFIRMED) {
            Competition c = competitionMapper.selectById(r.getCompetitionId());
            notificationService.send(r.getSubmittedBy(), Notification.Type.RECOGNITION,
                    "你标注的加分认定确认好了",
                    "「" + c.getName() + "」在本校认定为「" + r.getGradeLabel() + "」，已有 " + after.getConfirmCount()
                            + " 位同学确认，现在对大家显示。",
                    "COMPETITION", c.getId());
        }
    }

    @Transactional
    public void unconfirm(long userId, long recognitionId) {
        Recognition r = recognitionMapper.lockById(recognitionId);
        if (r == null || confirmMapper.delete(recognitionId, userId) == 0) {
            return;
        }
        recognitionMapper.recount(recognitionId, props.confirmsNeeded());
    }

    // ---------- 管理端 ----------

    public PageResult<AdminRow> adminList(Recognition.Status status, String keyword, int page, int size) {
        LambdaQueryWrapper<Recognition> where = new LambdaQueryWrapper<Recognition>()
                .eq(status != null, Recognition::getStatus, status)
                .orderByDesc(Recognition::getId);
        if (StringUtils.hasText(keyword)) {
            String kw = "%" + keyword.trim() + "%";
            where.apply("(EXISTS (SELECT 1 FROM school s WHERE s.id = school_id AND s.name LIKE {0})"
                    + " OR EXISTS (SELECT 1 FROM competition c WHERE c.id = competition_id"
                    + " AND (c.name LIKE {0} OR c.short_name LIKE {0})))", kw);
        }
        Page<Recognition> p = recognitionMapper.selectPage(Page.of(page, size), where);
        List<Recognition> rows = p.getRecords();
        Map<Long, School> schools = schools(rows.stream().map(Recognition::getSchoolId).collect(Collectors.toSet()), null);
        Set<Long> competitionIds = rows.stream().map(Recognition::getCompetitionId).collect(Collectors.toSet());
        Map<Long, String> competitions = competitionIds.isEmpty() ? Map.of()
                : competitionMapper.selectByIds(competitionIds).stream()
                .collect(Collectors.toMap(Competition::getId, Competition::getName));
        Set<Long> userIds = rows.stream().map(Recognition::getSubmittedBy).collect(Collectors.toSet());
        Map<Long, String> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        return PageResult.of(p, r -> new AdminRow(r.getId(), r.getSchoolId(),
                schools.containsKey(r.getSchoolId()) ? schools.get(r.getSchoolId()).getName() : null,
                r.getCompetitionId(), competitions.getOrDefault(r.getCompetitionId(), "（已删除的比赛）"),
                r.getGradeLabel(), r.getNote(), r.getSourceUrl(), r.getConfirmCount(), r.getStatus(),
                r.getSubmittedBy(), users.get(r.getSubmittedBy()), r.getCreatedAt(),
                recognitionMapper.selectObjs(new LambdaQueryWrapper<Recognition>()
                                .select(Recognition::getGradeLabel)
                                .eq(Recognition::getSchoolId, r.getSchoolId())
                                .eq(Recognition::getCompetitionId, r.getCompetitionId())
                                .ne(Recognition::getStatus, Recognition.Status.REJECTED))
                        .stream().distinct().count()));
    }

    @Transactional
    public void reject(long id, String note) {
        Recognition r = recognitionMapper.lockById(id);
        if (r == null) {
            throw new BizException(404, "这条认定已经撤回了");
        }
        if (r.getStatus() == Recognition.Status.REJECTED) {
            return;
        }
        r.setStatus(Recognition.Status.REJECTED);
        recognitionMapper.updateById(r);
        Competition c = competitionMapper.selectById(r.getCompetitionId());
        String name = c == null ? "比赛" : "「" + c.getName() + "」";
        notificationService.send(r.getSubmittedBy(), Notification.Type.RECOGNITION, "你标注的加分认定被驳回了",
                name + "的认定「" + r.getGradeLabel() + "」没有通过：" + note.trim() + "。改了级别或文件链接后可以重新让同学确认。",
                c == null ? null : "COMPETITION", c == null ? null : c.getId());
    }

    /** 驳回错了，恢复后按确认人数重新算状态 */
    @Transactional
    public void restore(long id) {
        Recognition r = recognitionMapper.lockById(id);
        if (r == null) {
            throw new BizException(404, "这条认定已经撤回了");
        }
        if (r.getStatus() != Recognition.Status.REJECTED) {
            return;
        }
        r.setStatus(Recognition.Status.PENDING);
        recognitionMapper.updateById(r);
        recognitionMapper.recount(id, props.confirmsNeeded());
    }

    // ---------- 内部 ----------

    private Competition requireCompetition(long competitionId) {
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null) {
            throw new BizException(404, "这个比赛不存在或已下架");
        }
        return c;
    }

    private SysUser requireContributor(long userId) {
        SysUser me = userService.requireVerified(userId);
        if (me.getSchoolId() == null) {
            throw new BizException(403, "请先完成学校认证");
        }
        return me;
    }

    private Recognition findMine(SysUser me, long competitionId) {
        return recognitionMapper.selectOne(new LambdaQueryWrapper<Recognition>()
                .eq(Recognition::getSchoolId, me.getSchoolId())
                .eq(Recognition::getCompetitionId, competitionId)
                .eq(Recognition::getSubmittedBy, me.getId()));
    }

    private void lockInOrder(Collection<Long> ids) {
        ids.stream().distinct().sorted().forEach(recognitionMapper::lockById);
    }

    /** 撤掉这个人在同校同比赛下对别的几条的确认，并重新算那几条的人数（调用前已经锁好） */
    private void dropOtherConfirms(long userId, long schoolId, long competitionId, long keepId) {
        for (Long other : confirmMapper.confirmedInPair(userId, schoolId, competitionId)) {
            if (other != keepId) {
                confirmMapper.delete(other, userId);
                recognitionMapper.recount(other, props.confirmsNeeded());
            }
        }
    }

    private static Recognition shown(List<Recognition> list) {
        return list.stream().filter(r -> r.getStatus() == Recognition.Status.CONFIRMED).min(DISPLAY_ORDER).orElse(null);
    }

    private Map<Long, School> schools(Set<Long> ids, Long extra) {
        Set<Long> all = new HashSet<>(ids);
        if (extra != null) {
            all.add(extra);
        }
        return all.isEmpty() ? Map.of()
                : schoolMapper.selectByIds(all).stream().collect(Collectors.toMap(School::getId, Function.identity()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
