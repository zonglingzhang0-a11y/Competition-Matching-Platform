package com.teammatch.competition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionAdminDtos.CompetitionForm;
import com.teammatch.competition.CompetitionAdminDtos.EditionForm;
import com.teammatch.competition.CompetitionAdminDtos.NodeForm;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 比赛库：管理员的录入和修改。
 */
@Service
@RequiredArgsConstructor
public class CompetitionAdminService {

    private final CompetitionMapper competitionMapper;
    private final EditionMapper editionMapper;
    private final NodeMapper nodeMapper;

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

    public void deleteCompetition(long id) {
        requireCompetition(id);
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

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
