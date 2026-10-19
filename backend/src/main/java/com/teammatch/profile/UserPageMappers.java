package com.teammatch.profile;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 个人资料页要用的查询。返回值是 record，按列的顺序对应构造参数，改 SQL 时两边要一起改。
 */
public final class UserPageMappers {

    private UserPageMappers() {
    }

    @Mapper
    public interface UserPageMapper {

        /** 这个人在平台上组过的队，最近的在前。被移出的也算一条，只是不对外区分原因 */
        @Select("""
                SELECT m.team_id AS teamId, t.name AS teamName, c.id AS competitionId, c.name AS competitionName,
                       e.title AS editionTitle, m.role_name AS roleName, m.is_captain AS captain,
                       m.status AS memberStatus, t.status AS teamStatus, t.award AS award,
                       m.joined_at AS joinedAt, m.left_at AS leftAt, t.award_recorded_at AS awardRecordedAt
                FROM team_member m
                JOIN team t ON t.id = m.team_id AND t.deleted = 0
                JOIN competition_edition e ON e.id = m.edition_id
                JOIN competition c ON c.id = e.competition_id
                WHERE m.user_id = #{userId}
                ORDER BY m.joined_at DESC, m.id DESC
                LIMIT 50
                """)
        List<TeamRow> teams(@Param("userId") long userId);

        /** 两人现在是不是在同一支队里 */
        @Select("""
                SELECT COUNT(*) FROM team_member a
                JOIN team_member b ON b.team_id = a.team_id
                WHERE a.user_id = #{viewer} AND b.user_id = #{target}
                  AND a.status = 'ACTIVE' AND b.status = 'ACTIVE'
                """)
        long teammates(@Param("viewer") long viewer, @Param("target") long target);

        /**
         * 对方有没有主动找过来，只有这种情况才算"有关联"：
         * <ul>
         *   <li>对方申请加入了我所在的队（我是队员或队长，要一起商量收不收）</li>
         *   <li>对方所在的队邀请了我（我要看看是哪些人在邀请我）</li>
         * </ul>
         * 反过来不算：我给对方发邀请、或者我去申请对方的队，都不能让我看到对方隐藏的履历，
         * 否则发一个邀请再撤回，就能绕过对方的隐私设置。
         */
        @Select("""
                SELECT COUNT(*) FROM team_application ap
                JOIN team_member m ON m.team_id = ap.team_id AND m.status = 'ACTIVE'
                WHERE ap.status = 'PENDING'
                  AND ((ap.direction = 'APPLY' AND ap.user_id = #{target} AND m.user_id = #{viewer})
                    OR (ap.direction = 'INVITE' AND ap.user_id = #{viewer} AND m.user_id = #{target}))
                """)
        long related(@Param("viewer") long viewer, @Param("target") long target);

        /** 两人都关注了的、还在展示的届次 */
        @Select("""
                SELECT c.id AS competitionId, c.name AS competitionName, e.title AS editionTitle
                FROM competition_follow a
                JOIN competition_follow b ON b.edition_id = a.edition_id AND b.user_id = #{target}
                JOIN competition_edition e ON e.id = a.edition_id AND e.status = 'PUBLISHED' AND e.deleted = 0
                JOIN competition c ON c.id = e.competition_id AND c.deleted = 0
                WHERE a.user_id = #{viewer}
                ORDER BY e.registration_end DESC
                LIMIT 5
                """)
        List<SharedEditionRow> sharedEditions(@Param("viewer") long viewer, @Param("target") long target);

        /** 两人一起待过的队伍；current = 1 表示现在还是队友 */
        @Select("""
                SELECT t.id AS teamId, t.name AS teamName, c.name AS competitionName,
                       MAX(a.status = 'ACTIVE' AND b.status = 'ACTIVE' AND t.status <> 'FINISHED') AS current
                FROM team_member a
                JOIN team_member b ON b.team_id = a.team_id AND b.user_id = #{target}
                JOIN team t ON t.id = a.team_id AND t.deleted = 0
                JOIN competition_edition e ON e.id = t.edition_id
                JOIN competition c ON c.id = e.competition_id
                WHERE a.user_id = #{viewer}
                GROUP BY t.id, t.name, c.name
                ORDER BY MAX(a.joined_at) DESC
                LIMIT 5
                """)
        List<SharedTeamRow> sharedTeams(@Param("viewer") long viewer, @Param("target") long target);

        /** 互评信用；没有互评过就没有这一行 */
        @Select("SELECT score, rated_rounds AS ratedRounds FROM user_credit WHERE user_id = #{userId}")
        CreditRow credit(@Param("userId") long userId);

        record TeamRow(Long teamId, String teamName, Long competitionId, String competitionName, String editionTitle,
                       String roleName, Boolean captain, String memberStatus, String teamStatus, String award,
                       LocalDateTime joinedAt, LocalDateTime leftAt, LocalDateTime awardRecordedAt) {
        }

        record SharedEditionRow(Long competitionId, String competitionName, String editionTitle) {
        }

        record SharedTeamRow(Long teamId, String teamName, String competitionName, Boolean current) {
        }

        record CreditRow(Double score, Integer ratedRounds) {
        }
    }

    /**
     * 隐私设置。没有这一行就当作默认值（履历对所有已认证同学公开）。
     * 表是 2026-10 新增的，老库要先跑 sql/upgrade/2026-10-user-privacy.sql，没跑时读取按默认值处理，见 UserPageService。
     */
    @Mapper
    public interface PrivacyMapper {

        @Select("SELECT history_visibility FROM user_privacy WHERE user_id = #{userId}")
        String historyVisibility(@Param("userId") long userId);

        @Insert("""
                INSERT INTO user_privacy (user_id, history_visibility) VALUES (#{userId}, #{visibility})
                ON DUPLICATE KEY UPDATE history_visibility = #{visibility}
                """)
        int saveHistoryVisibility(@Param("userId") long userId, @Param("visibility") String visibility);
    }
}
