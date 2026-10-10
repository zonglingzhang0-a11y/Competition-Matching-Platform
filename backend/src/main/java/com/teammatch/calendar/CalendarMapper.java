package com.teammatch.calendar;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 日历和定时提醒要用的几个查询。
 */
@Mapper
public interface CalendarMapper {

    @Select("SELECT edition_id FROM competition_follow WHERE user_id = #{userId}")
    List<Long> followedEditions(@Param("userId") long userId);

    @Select("""
            SELECT m.team_id AS teamId, m.edition_id AS editionId, t.name AS teamName
            FROM team_member m JOIN team t ON t.id = m.team_id
            WHERE m.user_id = #{userId} AND m.status = 'ACTIVE' AND t.deleted = 0
            """)
    List<ActiveTeam> activeTeams(@Param("userId") long userId);

    /** 日历上显示比赛名用简称，没有简称用全称 */
    @Select("""
            <script>
            SELECT e.id AS editionId, c.id AS competitionId, COALESCE(c.short_name, c.name) AS label
            FROM competition_edition e JOIN competition c ON c.id = e.competition_id
            WHERE e.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<EditionLabel> editionLabels(@Param("ids") Collection<Long> ids);

    // ---------- 定时提醒 ----------

    /** 报名截止时间在 [from, to) 之间的已发布届次 */
    @Select("""
            SELECT e.id AS editionId, c.id AS competitionId, COALESCE(c.short_name, c.name) AS label,
                   e.registration_end AS at
            FROM competition_edition e JOIN competition c ON c.id = e.competition_id
            WHERE e.status = 'PUBLISHED' AND e.deleted = 0 AND c.deleted = 0
              AND e.registration_end >= #{from} AND e.registration_end < #{to}
            """)
    List<Upcoming> registrationEnds(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 开始时间在 [from, to) 之间的"比赛期间"节点 */
    @Select("""
            SELECT n.id AS refId, e.id AS editionId, c.id AS competitionId, COALESCE(c.short_name, c.name) AS label,
                   n.title AS title, n.start_at AS at
            FROM competition_node n
            JOIN competition_edition e ON e.id = n.edition_id
            JOIN competition c ON c.id = e.competition_id
            WHERE n.busy = 1 AND e.status = 'PUBLISHED' AND e.deleted = 0
              AND n.start_at >= #{from} AND n.start_at < #{to}
            """)
    List<UpcomingNode> busyNodesStarting(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 时间在 [from, to) 之间、还没完成的队伍里程碑 */
    @Select("""
            SELECT ms.id AS refId, ms.team_id AS teamId, t.name AS teamName, ms.title AS title, ms.start_at AS at
            FROM team_milestone ms JOIN team t ON t.id = ms.team_id
            WHERE ms.deleted = 0 AND ms.done_at IS NULL AND t.deleted = 0
              AND t.status IN ('RECRUITING', 'FULL', 'CLOSED')
              AND ms.start_at >= #{from} AND ms.start_at < #{to}
            """)
    List<UpcomingMilestone> milestonesDue(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 关心这一届的人：关注了的 + 正在这一届某支队里的 */
    @Select("""
            SELECT user_id FROM competition_follow WHERE edition_id = #{editionId}
            UNION
            SELECT user_id FROM team_member WHERE edition_id = #{editionId} AND status = 'ACTIVE'
            """)
    List<Long> editionAudience(@Param("editionId") long editionId);

    @Select("SELECT user_id FROM team_member WHERE team_id = #{teamId} AND status = 'ACTIVE'")
    List<Long> teamMembers(@Param("teamId") long teamId);

    record ActiveTeam(Long teamId, Long editionId, String teamName) {
    }

    record EditionLabel(Long editionId, Long competitionId, String label) {
    }

    record Upcoming(Long editionId, Long competitionId, String label, LocalDateTime at) {
    }

    record UpcomingNode(Long refId, Long editionId, Long competitionId, String label, String title, LocalDateTime at) {
    }

    record UpcomingMilestone(Long refId, Long teamId, String teamName, String title, LocalDateTime at) {
    }
}
