package com.teammatch.competition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.PageResult;
import com.teammatch.competition.CompetitionAdminDtos.AdminCompetitionRow;
import com.teammatch.competition.CompetitionAdminDtos.AdminEditionRow;
import com.teammatch.competition.CompetitionAdminDtos.CompetitionForm;
import com.teammatch.competition.CompetitionAdminDtos.EditionForm;
import com.teammatch.competition.CompetitionAdminDtos.NodeForm;
import com.teammatch.competition.CompetitionAdminDtos.RoleTemplateView;
import com.teammatch.competition.CompetitionDtos.NodeView;
import com.teammatch.competition.CompetitionMappers.AdminStatMapper;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 比赛库：管理员的录入和修改。
 */
@Service
@RequiredArgsConstructor
public class CompetitionAdminService {

    private final CompetitionMapper competitionMapper;
    private final EditionMapper editionMapper;
    private final NodeMapper nodeMapper;
    private final AdminStatMapper statMapper;
    private final Clock clock;

    /** 管理端列表：包括草稿和已归档的届次，最新录入的比赛在前 */
    public PageResult<AdminCompetitionRow> list(String keyword, int page, int size) {
        LambdaQueryWrapper<Competition> where = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            where.and(w -> w.like(Competition::getName, kw).or().like(Competition::getShortName, kw));
        }
        where.orderByDesc(Competition::getId);
        Page<Competition> p = competitionMapper.selectPage(Page.of(page, size), where);
        List<Long> ids = p.getRecords().stream().map(Competition::getId).toList();
        List<CompetitionEdition> editions = ids.isEmpty() ? List.of()
                : editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                        .in(CompetitionEdition::getCompetitionId, ids)
                        .orderByDesc(CompetitionEdition::getYear)
                        .orderByDesc(CompetitionEdition::getId));
        List<Long> editionIds = editions.stream().map(CompetitionEdition::getId).toList();
        Map<Long, Long> teams = counts(editionIds, statMapper::teamCounts);
        Map<Long, Long> nodes = counts(editionIds, statMapper::nodeCounts);
        LocalDateTime now = LocalDateTime.now(clock);
        Map<Long, List<AdminEditionRow>> byCompetition = editions.stream().collect(Collectors.groupingBy(
                CompetitionEdition::getCompetitionId,
                Collectors.mapping(e -> new AdminEditionRow(e.getId(), e.getYear(), e.getTitle(), e.getTeamMin(),
                        e.getTeamMax(), Boolean.TRUE.equals(e.getSameSchoolRequired()),
                        Boolean.TRUE.equals(e.getAdvisorRequired()), Boolean.TRUE.equals(e.getEnglishRequired()),
                        e.getRegistrationStart(), e.getRegistrationEnd(), e.getNoticeUrl(), e.getStatus(),
                        EditionPhase.of(e.getRegistrationStart(), e.getRegistrationEnd(), now),
                        teams.getOrDefault(e.getId(), 0L), nodes.getOrDefault(e.getId(), 0L)), Collectors.toList())));
        return PageResult.of(p, c -> new AdminCompetitionRow(c.getId(), c.getName(), c.getShortName(), c.getOrganizer(),
                c.getCategory(), c.getLevel(), Boolean.TRUE.equals(c.getInCaheList()), c.getOfficialUrl(),
                c.getDescription(), c.getRoleTemplateId(), c.getMatchPreset(),
                byCompetition.getOrDefault(c.getId(), List.of())));
    }

    public List<RoleTemplateView> roleTemplates() {
        Map<Long, RoleTemplateView> result = new LinkedHashMap<>();
        for (AdminStatMapper.RoleRow r : statMapper.roleTemplates()) {
            RoleTemplateView t = result.computeIfAbsent(r.templateId(),
                    id -> new RoleTemplateView(id, r.templateName(), new ArrayList<>()));
            if (r.roleName() != null) {
                t.roles().add(r.roleName());
            }
        }
        return List.copyOf(result.values());
    }

    /** 一届的官方时间节点，按时间排 */
    public List<NodeView> nodes(long editionId) {
        requireEdition(editionId);
        return nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                        .eq(CompetitionNode::getEditionId, editionId)
                        .orderByAsc(CompetitionNode::getStartAt)
                        .orderByAsc(CompetitionNode::getSort))
                .stream().map(NodeView::of).toList();
    }

    public long createCompetition(CompetitionForm form) {
        Competition c = new Competition();
        apply(c, form);
        try {
            competitionMapper.insert(c);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "已经有同名的比赛了");
        }
        return c.getId();
    }

    public void updateCompetition(long id, CompetitionForm form) {
        Competition c = requireCompetition(id);
        apply(c, form);
        try {
            competitionMapper.updateById(c);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "已经有同名的比赛了");
        }
    }

    /** 有队伍的不能删，只能把届次归档；没有队伍的连同届次一起删（逻辑删除） */
    @Transactional
    public void deleteCompetition(long id) {
        requireCompetition(id);
        List<Long> editionIds = editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                        .select(CompetitionEdition::getId)
                        .eq(CompetitionEdition::getCompetitionId, id))
                .stream().map(CompetitionEdition::getId).toList();
        long teams = counts(editionIds, statMapper::teamCounts).values().stream().mapToLong(Long::longValue).sum();
        if (teams > 0) {
            throw new BizException(409, "这个比赛下已经有 " + teams + " 支队伍，不能删除。不想让学生看到的话，可以把届次改成已归档");
        }
        if (!editionIds.isEmpty()) {
            editionMapper.deleteByIds(editionIds);
        }
        competitionMapper.deleteById(id);
    }

    public long createEdition(long competitionId, EditionForm form) {
        requireCompetition(competitionId);
        CompetitionEdition e = new CompetitionEdition();
        e.setCompetitionId(competitionId);
        e.setSource(CompetitionEdition.Source.MANUAL);
        apply(e, form);
        try {
            editionMapper.insert(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "这个比赛已经有同名的届次了");
        }
        return e.getId();
    }

    public void updateEdition(long id, EditionForm form) {
        CompetitionEdition e = requireEdition(id);
        apply(e, form);
        try {
            editionMapper.updateById(e);
        } catch (DuplicateKeyException ex) {
            throw new BizException(409, "这个比赛已经有同名的届次了");
        }
    }

    public void deleteEdition(long id) {
        requireEdition(id);
        long teams = counts(List.of(id), statMapper::teamCounts).getOrDefault(id, 0L);
        if (teams > 0) {
            throw new BizException(409, "这一届已经有 " + teams + " 支队伍，不能删除。不想让学生看到的话，可以改成已归档");
        }
        editionMapper.deleteById(id);
    }

    /** 整体替换一届的官方时间节点，管理端编辑时一次提交全部节点 */
    @Transactional
    public void replaceNodes(long editionId, List<NodeForm> forms) {
        requireEdition(editionId);
        for (int i = 0; i < forms.size(); i++) {
            validate(forms.get(i), i + 1);
        }
        nodeMapper.delete(new LambdaQueryWrapper<CompetitionNode>().eq(CompetitionNode::getEditionId, editionId));
        for (int i = 0; i < forms.size(); i++) {
            NodeForm f = forms.get(i);
            CompetitionNode n = new CompetitionNode();
            n.setEditionId(editionId);
            n.setTitle(f.title().trim());
            n.setNodeType(f.nodeType());
            n.setStartAt(f.startAt());
            n.setEndAt(endModeOf(f) == CompetitionNode.EndMode.OPEN ? null : f.endAt());
            n.setAllDay(f.allDay());
            n.setStartLatestAt(f.startLatestAt());
            n.setEndMode(endModeOf(f));
            n.setEndLatestAt(endModeOf(f) == CompetitionNode.EndMode.RANGE ? f.endLatestAt() : null);
            n.setBusy(f.busy());
            n.setNote(f.note());
            n.setSort(i);
            nodeMapper.insert(n);
        }
    }

    /** 节点的时间要前后一致；index 从 1 开始，写进提示里 */
    static void validate(NodeForm f, int index) {
        String prefix = "第 " + index + " 个节点：";
        LocalDateTime start = f.startAt();
        if (f.startLatestAt() != null && f.startLatestAt().isBefore(start)) {
            throw new BizException(prefix + "最晚开始时间不能早于开始时间");
        }
        switch (endModeOf(f)) {
            case FIXED -> {
                if (f.endAt() != null && f.endAt().isBefore(start)) {
                    throw new BizException(prefix + "结束时间不能早于开始时间");
                }
            }
            case RANGE -> {
                if (f.endAt() == null || f.endLatestAt() == null) {
                    throw new BizException(prefix + "结束时间是区间时，要填最早和最晚两个时间");
                }
                if (f.endAt().isBefore(start) || f.endLatestAt().isBefore(f.endAt())) {
                    throw new BizException(prefix + "结束区间要在开始之后，并且最晚不能早于最早");
                }
            }
            case OPEN -> {
                // 没有结束时间，不用检查
            }
        }
    }

    private static CompetitionNode.EndMode endModeOf(NodeForm f) {
        return f.endMode() == null ? CompetitionNode.EndMode.FIXED : f.endMode();
    }

    private static void apply(Competition c, CompetitionForm f) {
        c.setName(f.name().trim());
        c.setShortName(blankToNull(f.shortName()));
        c.setOrganizer(blankToNull(f.organizer()));
        c.setCategory(f.category().trim());
        c.setLevel(f.level());
        c.setInCaheList(f.inCaheList());
        c.setOfficialUrl(blankToNull(f.officialUrl()));
        c.setDescription(blankToNull(f.description()));
        c.setRoleTemplateId(f.roleTemplateId());
        c.setMatchPreset(f.matchPreset() == null ? Competition.MatchPreset.DEFAULT : f.matchPreset());
    }

    private static void apply(CompetitionEdition e, EditionForm f) {
        if (f.teamMax() < f.teamMin()) {
            throw new BizException("最多人数不能少于最少人数");
        }
        if (f.registrationStart() != null && f.registrationEnd() != null
                && f.registrationEnd().isBefore(f.registrationStart())) {
            throw new BizException("报名截止时间不能早于报名开始时间");
        }
        e.setYear(f.year());
        e.setTitle(f.title().trim());
        e.setTeamMin(f.teamMin());
        e.setTeamMax(f.teamMax());
        e.setSameSchoolRequired(f.sameSchoolRequired());
        e.setAdvisorRequired(f.advisorRequired());
        e.setEnglishRequired(f.englishRequired());
        e.setRegistrationStart(f.registrationStart());
        e.setRegistrationEnd(f.registrationEnd());
        e.setNoticeUrl(blankToNull(f.noticeUrl()));
        e.setStatus(f.status() == null ? CompetitionEdition.Status.DRAFT : f.status());
    }

    private Competition requireCompetition(long id) {
        Competition c = competitionMapper.selectById(id);
        if (c == null) {
            throw new BizException(404, "这个比赛不存在");
        }
        return c;
    }

    private CompetitionEdition requireEdition(long id) {
        CompetitionEdition e = editionMapper.selectById(id);
        if (e == null) {
            throw new BizException(404, "这一届比赛不存在");
        }
        return e;
    }

    private static Map<Long, Long> counts(List<Long> editionIds,
                                          Function<Collection<Long>, List<AdminStatMapper.EditionCount>> query) {
        if (editionIds.isEmpty()) {
            return Map.of();
        }
        return query.apply(editionIds).stream()
                .collect(Collectors.toMap(AdminStatMapper.EditionCount::editionId, AdminStatMapper.EditionCount::cnt));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
