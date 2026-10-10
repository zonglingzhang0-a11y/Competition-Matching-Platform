package com.teammatch.competition;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 比赛库接口返回给学生端的数据。
 */
public final class CompetitionDtos {

    private CompetitionDtos() {
    }

    /** 列表里的一行：比赛 + 当前这一届的摘要 */
    public record CompetitionSummary(
            Long id,
            String name,
            String shortName,
            String category,
            Competition.Level level,
            boolean inCaheList,
            EditionBrief currentEdition) {
    }

    public record EditionBrief(
            Long id,
            String title,
            Integer teamMin,
            Integer teamMax,
            LocalDateTime registrationEnd,
            EditionPhase phase,
            boolean followed) {
    }

    public record CompetitionDetail(
            Long id,
            String name,
            String shortName,
            String organizer,
            String category,
            Competition.Level level,
            boolean inCaheList,
            String officialUrl,
            String description,
            /* 队内分工模板，比如：建模、编程、论文写作与排版 */
            List<String> roles,
            List<EditionDetail> editions) {
    }

    public record EditionDetail(
            Long id,
            Integer year,
            String title,
            Integer teamMin,
            Integer teamMax,
            boolean sameSchoolRequired,
            boolean advisorRequired,
            boolean englishRequired,
            LocalDateTime registrationStart,
            LocalDateTime registrationEnd,
            String noticeUrl,
            EditionPhase phase,
            boolean followed,
            long followers,
            List<NodeView> nodes) {
    }

    public record NodeView(
            Long id,
            String title,
            CompetitionNode.NodeType nodeType,
            LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            LocalDateTime startLatestAt,
            CompetitionNode.EndMode endMode,
            LocalDateTime endLatestAt,
            boolean busy,
            String note) {

        static NodeView of(CompetitionNode n) {
            return new NodeView(n.getId(), n.getTitle(), n.getNodeType(), n.getStartAt(), n.getEndAt(),
                    Boolean.TRUE.equals(n.getAllDay()), n.getStartLatestAt(), n.getEndMode(), n.getEndLatestAt(),
                    Boolean.TRUE.equals(n.getBusy()), n.getNote());
        }
    }

    /** 下拉框用：只有 id 和名称 */
    public record CompetitionOption(Long id, String name) {
    }

    /** 列表筛选条件 */
    public record ListQuery(
            String keyword,
            String category,
            Competition.Level level,
            /* 只看报名还没截止的 */
            boolean openOnly,
            /* 只看我关注的 */
            boolean followedOnly,
            int page,
            int size) {
    }
}
