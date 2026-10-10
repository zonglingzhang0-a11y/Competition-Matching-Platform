package com.teammatch.match;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 推荐时要批量查的几样东西。
 */
@Mapper
public interface MatchMapper {

    @Select("""
            <script>
            SELECT user_id AS userId, COUNT(*) AS cnt FROM user_experience
            WHERE user_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY user_id
            </script>
            """)
    List<CountRow> experienceCounts(@Param("ids") Collection<Long> ids);

    @Select("""
            <script>
            SELECT user_id AS userId, score, suspended_until AS suspendedUntil FROM user_credit
            WHERE user_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<CreditRow> credits(@Param("ids") Collection<Long> ids);

    /** 某一届里参与撞期检测的时间段 */
    @Select("""
            SELECT start_at AS startAt, end_at AS endAt FROM competition_node
            WHERE edition_id = #{editionId} AND busy = 1
            """)
    List<BusyRow> busyNodes(@Param("editionId") long editionId);

    /** 这些人已经参加的其他届比赛里，参与撞期检测的时间段 */
    @Select("""
            <script>
            SELECT m.user_id AS userId, c.name AS competitionName, n.start_at AS startAt, n.end_at AS endAt
            FROM team_member m
            JOIN competition_edition e ON e.id = m.edition_id
            JOIN competition c ON c.id = e.competition_id
            JOIN competition_node n ON n.edition_id = e.id AND n.busy = 1
            WHERE m.status = 'ACTIVE' AND m.edition_id &lt;&gt; #{editionId}
              AND m.user_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<UserBusyRow> otherBusyNodes(@Param("ids") Collection<Long> ids, @Param("editionId") long editionId);

    /** 我关注的、报名还没截止的届次，最近关注的在前 */
    @Select("""
            SELECT f.edition_id FROM competition_follow f
            JOIN competition_edition e ON e.id = f.edition_id
            WHERE f.user_id = #{userId} AND e.status = 'PUBLISHED' AND e.deleted = 0
              AND (e.registration_end IS NULL OR e.registration_end > #{now})
            ORDER BY f.created_at DESC
            LIMIT #{limit}
            """)
    List<Long> followedOpenEditions(@Param("userId") long userId, @Param("now") LocalDateTime now, @Param("limit") int limit);

    record CountRow(Long userId, Long cnt) {
    }

    record CreditRow(Long userId, Double score, LocalDateTime suspendedUntil) {
    }

    record BusyRow(LocalDateTime startAt, LocalDateTime endAt) {
    }

    record UserBusyRow(Long userId, String competitionName, LocalDateTime startAt, LocalDateTime endAt) {
    }
}
