package com.teammatch.home;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** 主页用的几个汇总查询 */
@Mapper
public interface HomeMapper {

    /** 一批届次的比赛信息（名称、简称、级别、官网、通知） */
    @Select("""
            <script>
            SELECT e.id AS editionId, c.id AS competitionId, c.name AS competitionName,
                   COALESCE(c.short_name, c.name) AS label, c.level AS level, e.title AS editionTitle,
                   c.official_url AS officialUrl, e.notice_url AS noticeUrl, e.team_max AS teamMax,
                   e.registration_end AS registrationEnd
            FROM competition_edition e JOIN competition c ON c.id = e.competition_id
            WHERE e.id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<EditionInfo> editions(@Param("ids") Collection<Long> ids);

    /** 现在正在报名的已发布届次，截止早的在前 */
    @Select("""
            SELECT e.id AS editionId, c.id AS competitionId, c.name AS competitionName,
                   COALESCE(c.short_name, c.name) AS label, c.level AS level, e.title AS editionTitle,
                   c.official_url AS officialUrl, e.notice_url AS noticeUrl, e.team_max AS teamMax,
                   e.registration_end AS registrationEnd
            FROM competition_edition e JOIN competition c ON c.id = e.competition_id
            WHERE e.status = 'PUBLISHED' AND e.deleted = 0 AND c.deleted = 0
              AND e.registration_end >= #{now}
              AND (e.registration_start IS NULL OR e.registration_start <= #{now})
            ORDER BY e.registration_end
            """)
    List<EditionInfo> openEditions(@Param("now") LocalDateTime now);

    /** 每届还在招人的队伍数 */
    @Select("""
            <script>
            SELECT edition_id AS editionId, COUNT(*) AS cnt FROM team
            WHERE status = 'RECRUITING' AND deleted = 0
              AND edition_id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            GROUP BY edition_id
            </script>
            """)
    List<EditionCount> recruitingTeams(@Param("ids") Collection<Long> ids);

    record EditionInfo(Long editionId, Long competitionId, String competitionName, String label, String level,
                       String editionTitle, String officialUrl, String noticeUrl, Integer teamMax,
                       LocalDateTime registrationEnd) {
    }

    record EditionCount(Long editionId, Long cnt) {
    }
}
