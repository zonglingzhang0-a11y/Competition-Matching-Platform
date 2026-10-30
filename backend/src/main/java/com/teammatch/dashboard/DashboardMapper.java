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
     * 推荐准不准：处理完的申请和邀请（同意、婉拒、作废；撤回的不算），按发出时的匹配分每 20 分一段，数一数成了几条。
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

    // ---------- 运营：申请处理、认证审核、爬虫来源、举报 ----------

    @Select("SELECT COUNT(*) FROM team_application WHERE status = 'PENDING' AND created_at < #{before}")
    long pendingApplicationsBefore(@Param("before") LocalDateTime before);

    /** 最近处理完的申请和邀请各等了多少分钟（同意或婉拒的；撤回、作废的不算） */
    @Select("""
            SELECT TIMESTAMPDIFF(MINUTE, created_at, handled_at) FROM team_application
            WHERE status IN ('ACCEPTED', 'REJECTED') AND handled_at >= #{since}
            """)
    List<Long> applicationWaitMinutes(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM team_application WHERE status = 'EXPIRED' AND handled_at >= #{since}")
    long expiredApplicationsSince(@Param("since") LocalDateTime since);

    /** 谁手上压着等了很久的申请或邀请：申请等队长处理，邀请等被邀请的人处理 */
    @Select("""
            SELECT u.id AS userId, u.nickname, a.direction, COUNT(*) AS cnt, MIN(a.created_at) AS oldest
            FROM team_application a
            JOIN team t ON t.id = a.team_id
            JOIN sys_user u ON u.id = IF(a.direction = 'APPLY', t.captain_id, a.user_id)
            WHERE a.status = 'PENDING' AND a.created_at < #{before}
            GROUP BY u.id, u.nickname, a.direction
            ORDER BY cnt DESC, oldest
            LIMIT 5
            """)
    List<BacklogRow> applicationBacklog(@Param("before") LocalDateTime before);

    /** 待审核的认证按学校分：学校没收录的教育邮箱按邮箱后缀分，学生证截图单独一组 */
    @Select("""
            SELECT COALESCE(s.name, IF(v.method = 'EDU_EMAIL', CONCAT('@', SUBSTRING_INDEX(v.edu_email, '@', -1)), '学生证截图')) AS label,
                   v.school_id IS NULL AND v.method = 'EDU_EMAIL' AS unknownDomain,
                   COUNT(*) AS cnt, MIN(v.created_at) AS oldest
            FROM verification_request v LEFT JOIN school s ON s.id = v.school_id
            WHERE v.status = 'PENDING'
            GROUP BY label, unknownDomain
            ORDER BY cnt DESC, oldest
            LIMIT 6
            """)
    List<VerifyGroupRow> pendingVerificationGroups();

    @Select("SELECT MIN(created_at) FROM verification_request WHERE status = 'PENDING'")
    LocalDateTime oldestPendingVerification();

    /** 每个抓取来源：上次抓的时间和结果、还有几篇抽出来的通知等管理员确认 */
    @Select("""
            SELECT s.id, s.name, s.enabled, s.last_run_at AS lastRunAt, s.last_result AS lastResult,
                   (SELECT COUNT(*) FROM crawl_record r WHERE r.source_id = s.id AND r.status IN ('NEW', 'EXTRACTED')) AS toConfirm
            FROM crawl_source s ORDER BY s.id
            """)
    List<SourceRow> crawlSources();

    @Select("SELECT COUNT(*) FROM report WHERE status = 'PENDING'")
    long pendingReports();

    @Select("SELECT MIN(created_at) FROM report WHERE status = 'PENDING'")
    LocalDateTime oldestPendingReport();

    @Select("SELECT TIMESTAMPDIFF(MINUTE, created_at, handled_at) FROM report WHERE handled_at >= #{since}")
    List<Long> reportHandleMinutes(@Param("since") LocalDateTime since);

    @Select("SELECT COUNT(*) FROM user_credit WHERE suspended_until > #{now}")
    long suspendedUsers(@Param("now") LocalDateTime now);

    record BacklogRow(Long userId, String nickname, String direction, Long cnt, LocalDateTime oldest) {
    }

    record VerifyGroupRow(String label, Boolean unknownDomain, Long cnt, LocalDateTime oldest) {
    }

    record SourceRow(Long id, String name, Boolean enabled, LocalDateTime lastRunAt, String lastResult, Long toConfirm) {
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
