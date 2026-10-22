package com.teammatch.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 数据看板的统计 SQL。
 * <p>
 * "还在找队的人"和队长页的"可邀请的人"口径一样：关注了这一届、已认证、账号正常、这一届还没进队。
 */
@Mapper
public interface DashboardMapper {

    /** 认证有效的条件，几处统计共用 */
    String VERIFIED = " (u.status = 'ACTIVE' AND u.verify_status = 'VERIFIED'"
            + " AND (u.verified_until IS NULL OR u.verified_until >= #{today})) ";

    @Select("SELECT COUNT(*) FROM sys_user")
    long users();

    @Select("SELECT COUNT(*) FROM sys_user u WHERE" + VERIFIED)
    long verifiedUsers(@Param("today") LocalDate today);

    @Select("SELECT COUNT(*) FROM sys_user WHERE created_at >= #{since}")
    long usersSince(@Param("since") LocalDateTime since);

    /** 填过通用问卷的人数（当前版本） */
    @Select("""
            SELECT COUNT(*) FROM survey_response r JOIN survey_version v ON v.id = r.version_id AND v.active = 1
            WHERE r.edition_id IS NULL
            """)
    long surveyDone();

    @Select("SELECT status AS k, COUNT(*) AS v FROM team WHERE deleted = 0 GROUP BY status")
    List<KeyCount> teamsByStatus();

    @Select("SELECT status AS k, COUNT(*) AS v FROM team_application GROUP BY status")
    List<KeyCount> applicationsByStatus();

    /** 还在招的队伍一共还差几个人 */
    @Select("""
            SELECT COALESCE(SUM(n.headcount - n.filled), 0) FROM team_need n
            JOIN team t ON t.id = n.team_id AND t.deleted = 0 AND t.status = 'RECRUITING'
            WHERE n.status = 'OPEN'
            """)
    long openSlots();

    @Select("SELECT COUNT(*) FROM competition WHERE deleted = 0")
    long competitions();

    /** 已发布、报名还没截止的届次 */
    @Select("""
            SELECT COUNT(*) FROM competition_edition e JOIN competition c ON c.id = e.competition_id AND c.deleted = 0
            WHERE e.deleted = 0 AND e.status = 'PUBLISHED' AND (e.registration_end IS NULL OR e.registration_end > #{now})
            """)
    long openEditions(@Param("now") LocalDateTime now);

    /** 每一届的组队情况（已发布的届次） */
    @Select("""
            SELECT e.id AS editionId, c.name AS competitionName, e.title AS editionTitle,
                   e.registration_start AS registrationStart, e.registration_end AS registrationEnd,
                   (SELECT COUNT(*) FROM team t WHERE t.edition_id = e.id AND t.deleted = 0
                      AND t.status = 'RECRUITING') AS recruitingTeams,
                   (SELECT COUNT(*) FROM team t WHERE t.edition_id = e.id AND t.deleted = 0
                      AND t.status IN ('FULL', 'CLOSED', 'FINISHED')) AS formedTeams,
                   (SELECT COALESCE(SUM(n.headcount - n.filled), 0) FROM team_need n
                      JOIN team t ON t.id = n.team_id AND t.deleted = 0 AND t.status = 'RECRUITING'
                      WHERE t.edition_id = e.id AND n.status = 'OPEN') AS openSlots,
                   (SELECT COUNT(*) FROM competition_follow f JOIN sys_user u ON u.id = f.user_id
                      WHERE f.edition_id = e.id AND """ + VERIFIED + """
                      AND NOT EXISTS (SELECT 1 FROM team_member m WHERE m.edition_id = e.id
                                      AND m.user_id = u.id AND m.status = 'ACTIVE')) AS seekers,
                   (SELECT COUNT(*) FROM competition_follow f WHERE f.edition_id = e.id) AS followers
            FROM competition_edition e JOIN competition c ON c.id = e.competition_id AND c.deleted = 0
            WHERE e.deleted = 0 AND e.status = 'PUBLISHED'
            """)
    List<EditionRow> editions(@Param("today") LocalDate today);

    /**
     * 每种技能还差几个人：还在招的队伍里、没招满的需求要求的技能，按剩余人数累加。
     * editionId 为空时汇总所有报名还没截止的届次。
     */
    @Select("""
            <script>
            SELECT ns.skill_id AS skillId, SUM(n.headcount - n.filled) AS cnt
            FROM team_need n
            JOIN team_need_skill ns ON ns.need_id = n.id
            JOIN team t ON t.id = n.team_id AND t.deleted = 0 AND t.status = 'RECRUITING'
            JOIN competition_edition e ON e.id = t.edition_id AND e.deleted = 0 AND e.status = 'PUBLISHED'
            WHERE n.status = 'OPEN'
            <choose>
              <when test="editionId != null">AND e.id = #{editionId}</when>
              <otherwise>AND (e.registration_end IS NULL OR e.registration_end &gt; #{now})</otherwise>
            </choose>
            GROUP BY ns.skill_id
            </script>
            """)
    List<SkillCount> skillDemand(@Param("editionId") Long editionId, @Param("now") LocalDateTime now);

    /**
     * 还在找队的人里，各技能、各熟练度有多少人。跨届汇总时一个人在两届都在找队算两次。
     */
    @Select("""
            <script>
            SELECT us.skill_id AS skillId, us.level AS level, COUNT(*) AS cnt
            FROM competition_follow f
            JOIN competition_edition e ON e.id = f.edition_id AND e.deleted = 0 AND e.status = 'PUBLISHED'
            JOIN sys_user u ON u.id = f.user_id
            JOIN user_skill us ON us.user_id = u.id
            WHERE u.status = 'ACTIVE' AND u.verify_status = 'VERIFIED'
              AND (u.verified_until IS NULL OR u.verified_until &gt;= #{today})
              AND NOT EXISTS (SELECT 1 FROM team_member m WHERE m.edition_id = f.edition_id
                              AND m.user_id = u.id AND m.status = 'ACTIVE')
            <choose>
              <when test="editionId != null">AND e.id = #{editionId}</when>
              <otherwise>AND (e.registration_end IS NULL OR e.registration_end &gt; #{now})</otherwise>
            </choose>
            GROUP BY us.skill_id, us.level
            </script>
            """)
    List<SkillLevelCount> skillSupply(@Param("editionId") Long editionId, @Param("now") LocalDateTime now,
                                      @Param("today") LocalDate today);

    /**
     * 一届比赛的组队漏斗。人群是关注了这一届、或者已经在这一届队里的人（队长不一定点过关注）。
     * 申请或邀请过：不管结果，有过一条就算；建了队、已经在队里的也算。队伍组好了：状态是满员、停止招募或已结束。
     */
    @Select("""
            SELECT COUNT(*) AS people,
                   COALESCE(SUM(EXISTS (SELECT 1 FROM survey_response r
                                        JOIN survey_version v ON v.id = r.version_id AND v.active = 1
                                        WHERE r.user_id = p.user_id AND r.edition_id IS NULL)), 0) AS surveyed,
                   COALESCE(SUM(EXISTS (SELECT 1 FROM team_application a JOIN team t ON t.id = a.team_id AND t.deleted = 0
                                        WHERE a.user_id = p.user_id AND t.edition_id = #{editionId})
                                OR EXISTS (SELECT 1 FROM team_member m WHERE m.edition_id = #{editionId}
                                           AND m.user_id = p.user_id AND m.status = 'ACTIVE')), 0) AS engaged,
                   COALESCE(SUM(EXISTS (SELECT 1 FROM team_member m WHERE m.edition_id = #{editionId}
                                        AND m.user_id = p.user_id AND m.status = 'ACTIVE')), 0) AS joined,
                   COALESCE(SUM(EXISTS (SELECT 1 FROM team_member m JOIN team t ON t.id = m.team_id AND t.deleted = 0
                                          AND t.status IN ('FULL', 'CLOSED', 'FINISHED')
                                        WHERE m.edition_id = #{editionId} AND m.user_id = p.user_id
                                          AND m.status = 'ACTIVE')), 0) AS formed
            FROM (SELECT user_id FROM competition_follow WHERE edition_id = #{editionId}
                  UNION
                  SELECT user_id FROM team_member WHERE edition_id = #{editionId} AND status = 'ACTIVE') p
            """)
    FunnelRow funnel(@Param("editionId") long editionId);

    record FunnelRow(Long people, Long surveyed, Long engaged, Long joined, Long formed) {
    }

    /**
     * 推荐准不准：处理完的申请和邀请（同意、拒绝、过期；撤回的不算），按发出时的匹配分每 20 分一段，数一数成了几条。
     * bucket 0–4 对应 0–20、20–40……80–100 分。
     */
    @Select("""
            SELECT LEAST(FLOOR(match_score / 20), 4) AS bucket, COUNT(*) AS total,
                   SUM(status = 'ACCEPTED') AS accepted
            FROM team_application
            WHERE match_score IS NOT NULL AND status IN ('ACCEPTED', 'REJECTED', 'EXPIRED')
            GROUP BY bucket
            """)
    List<BucketRow> calibration();

    /** 处理完了、但发出时没记匹配分的（V9 之前的老记录） */
    @Select("""
            SELECT COUNT(*) FROM team_application
            WHERE match_score IS NULL AND status IN ('ACCEPTED', 'REJECTED', 'EXPIRED')
            """)
    long unscoredHandled();

    record BucketRow(Integer bucket, Long total, Long accepted) {
    }

    record KeyCount(String k, Long v) {
    }

    record EditionRow(Long editionId, String competitionName, String editionTitle, LocalDateTime registrationStart,
                      LocalDateTime registrationEnd, Long recruitingTeams, Long formedTeams, Long openSlots,
                      Long seekers, Long followers) {
    }

    record SkillCount(Long skillId, Long cnt) {
    }

    record SkillLevelCount(Long skillId, Integer level, Long cnt) {
    }
}
