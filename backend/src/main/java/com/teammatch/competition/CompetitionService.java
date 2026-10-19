package com.teammatch.competition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.competition.CompetitionDtos.CompetitionDetail;
import com.teammatch.competition.CompetitionDtos.CompetitionOption;
import com.teammatch.competition.CompetitionDtos.CompetitionSummary;
import com.teammatch.competition.CompetitionDtos.EditionBrief;
import com.teammatch.competition.CompetitionDtos.EditionDetail;
import com.teammatch.competition.CompetitionDtos.ListQuery;
import com.teammatch.competition.CompetitionDtos.NodeView;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.competition.CompetitionMappers.EditionMapper;
import com.teammatch.competition.CompetitionMappers.FollowMapper;
import com.teammatch.competition.CompetitionMappers.NodeMapper;
import com.teammatch.competition.CompetitionMappers.RoleTemplateItemMapper;
import com.teammatch.recognition.RecognitionService;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 比赛库：学生端的查询和关注。
 */
@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionMapper competitionMapper;
    private final EditionMapper editionMapper;
    private final NodeMapper nodeMapper;
    private final RoleTemplateItemMapper roleItemMapper;
    private final FollowMapper followMapper;
    private final RecognitionService recognitionService;
    private final UserService userService;
    private final Clock clock;

    public PageResult<CompetitionSummary> list(long userId, ListQuery q) {
        LocalDateTime now = LocalDateTime.now(clock);
        LambdaQueryWrapper<Competition> where = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.keyword())) {
            String kw = q.keyword().trim();
            where.and(w -> w.like(Competition::getName, kw).or().like(Competition::getShortName, kw));
        }
        where.eq(StringUtils.hasText(q.category()), Competition::getCategory, q.category())
                .eq(q.level() != null, Competition::getLevel, q.level());
        if (q.openOnly()) {
            where.exists("SELECT 1 FROM competition_edition e WHERE e.competition_id = competition.id"
                    + " AND e.status = 'PUBLISHED' AND e.deleted = 0 AND e.registration_end > {0}", now);
        }
        if (q.followedOnly()) {
            where.exists("SELECT 1 FROM competition_follow f JOIN competition_edition e ON e.id = f.edition_id"
                    + " WHERE e.competition_id = competition.id AND e.deleted = 0 AND f.user_id = {0}", userId);
        }
        where.orderByDesc(Competition::getInCaheList).orderByAsc(Competition::getId);

        Page<Competition> page = competitionMapper.selectPage(Page.of(q.page(), q.size()), where);
        List<Long> ids = page.getRecords().stream().map(Competition::getId).toList();
        Map<Long, CompetitionEdition> current = currentEditions(ids, now);
        Set<Long> followed = followedAmong(userId, current.values().stream().map(CompetitionEdition::getId).toList());
        Map<Long, String> recognitions = recognitionService.labelsForSchool(userService.getById(userId).getSchoolId(), ids);

        return PageResult.of(page, c -> {
            CompetitionEdition e = current.get(c.getId());
            EditionBrief brief = e == null ? null : new EditionBrief(e.getId(), e.getTitle(), e.getTeamMin(),
                    e.getTeamMax(), e.getRegistrationEnd(), phaseOf(e, now), followed.contains(e.getId()));
            return new CompetitionSummary(c.getId(), c.getName(), c.getShortName(), c.getCategory(), c.getLevel(),
                    Boolean.TRUE.equals(c.getInCaheList()), brief, recognitions.get(c.getId()));
        });
    }

    public List<CompetitionOption> options() {
        return competitionMapper.selectList(new LambdaQueryWrapper<Competition>()
                        .select(Competition::getId, Competition::getName)
                        .orderByAsc(Competition::getName))
                .stream().map(c -> new CompetitionOption(c.getId(), c.getName())).toList();
    }

    /** includeDrafts：管理员看详情时连草稿届次一起返回 */
    public CompetitionDetail detail(long userId, long competitionId, boolean includeDrafts) {
        Competition c = competitionMapper.selectById(competitionId);
        if (c == null) {
            throw new BizException(404, "这个比赛不存在或已下架");
        }
        LocalDateTime now = LocalDateTime.now(clock);

        List<String> roles = c.getRoleTemplateId() == null ? List.of()
                : roleItemMapper.selectList(new LambdaQueryWrapper<RoleTemplateItem>()
                        .eq(RoleTemplateItem::getTemplateId, c.getRoleTemplateId())
                        .orderByAsc(RoleTemplateItem::getSort))
                .stream().map(RoleTemplateItem::getName).toList();

        List<CompetitionEdition> editions = editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                .eq(CompetitionEdition::getCompetitionId, competitionId)
                .eq(!includeDrafts, CompetitionEdition::getStatus, CompetitionEdition.Status.PUBLISHED)
                .orderByDesc(CompetitionEdition::getYear)
                .orderByDesc(CompetitionEdition::getId));
        List<Long> editionIds = editions.stream().map(CompetitionEdition::getId).toList();

        Map<Long, List<CompetitionNode>> nodes = editionIds.isEmpty() ? Map.of()
                : nodeMapper.selectList(new LambdaQueryWrapper<CompetitionNode>()
                        .in(CompetitionNode::getEditionId, editionIds)
                        .orderByAsc(CompetitionNode::getStartAt)
                        .orderByAsc(CompetitionNode::getSort))
                .stream().collect(Collectors.groupingBy(CompetitionNode::getEditionId));
        Set<Long> followed = followedAmong(userId, editionIds);
        Map<Long, Long> followers = editionIds.isEmpty() ? Map.of()
                : followMapper.countFollowers(editionIds).stream()
                .collect(Collectors.toMap(FollowMapper.FollowerCount::editionId, FollowMapper.FollowerCount::followers));

        List<EditionDetail> editionViews = editions.stream().map(e -> new EditionDetail(
                e.getId(), e.getYear(), e.getTitle(), e.getTeamMin(), e.getTeamMax(),
                Boolean.TRUE.equals(e.getSameSchoolRequired()), Boolean.TRUE.equals(e.getAdvisorRequired()),
                Boolean.TRUE.equals(e.getEnglishRequired()), e.getRegistrationStart(), e.getRegistrationEnd(),
                e.getNoticeUrl(), phaseOf(e, now), followed.contains(e.getId()), followers.getOrDefault(e.getId(), 0L),
                nodes.getOrDefault(e.getId(), List.of()).stream().map(NodeView::of).toList())).toList();

        return new CompetitionDetail(c.getId(), c.getName(), c.getShortName(), c.getOrganizer(), c.getCategory(),
                c.getLevel(), Boolean.TRUE.equals(c.getInCaheList()), c.getOfficialUrl(), c.getDescription(),
                roles, editionViews);
    }

    public void follow(long userId, long editionId) {
        CompetitionEdition e = editionMapper.selectById(editionId);
        if (e == null || e.getStatus() != CompetitionEdition.Status.PUBLISHED) {
            throw new BizException(404, "这一届比赛不存在或还没发布");
        }
        followMapper.follow(userId, editionId);
    }

    public void unfollow(long userId, long editionId) {
        followMapper.unfollow(userId, editionId);
    }

    /**
     * 每个比赛挑一届作为"当前届"：优先报名还没截止的、截止时间最近的；都截止了就取最新的一届。
     */
    Map<Long, CompetitionEdition> currentEditions(Collection<Long> competitionIds, LocalDateTime now) {
        if (competitionIds.isEmpty()) {
            return Map.of();
        }
        Comparator<CompetitionEdition> order = Comparator
                .comparing((CompetitionEdition e) -> phaseOf(e, now) == EditionPhase.CLOSED)
                .thenComparing(e -> phaseOf(e, now) == EditionPhase.CLOSED ? null : e.getRegistrationEnd(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CompetitionEdition::getYear, Comparator.reverseOrder())
                .thenComparing(CompetitionEdition::getId, Comparator.reverseOrder());
        return editionMapper.selectList(new LambdaQueryWrapper<CompetitionEdition>()
                        .in(CompetitionEdition::getCompetitionId, competitionIds)
                        .eq(CompetitionEdition::getStatus, CompetitionEdition.Status.PUBLISHED))
                .stream()
                .collect(Collectors.groupingBy(CompetitionEdition::getCompetitionId,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> list.stream().min(order).orElseThrow())))
                .values().stream()
                .collect(Collectors.toMap(CompetitionEdition::getCompetitionId, Function.identity()));
    }

    private Set<Long> followedAmong(long userId, List<Long> editionIds) {
        return editionIds.isEmpty() ? Set.of() : new HashSet<>(followMapper.followedAmong(userId, editionIds));
    }

    static EditionPhase phaseOf(CompetitionEdition e, LocalDateTime now) {
        return EditionPhase.of(e.getRegistrationStart(), e.getRegistrationEnd(), now);
    }
}
