package com.teammatch.review;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.Data;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 互评、信用分、举报的表和 Mapper。
 */
public final class ReviewEntities {

    private ReviewEntities() {
    }

    @Data
    @TableName("peer_review_round")
    public static class ReviewRound {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long teamId;
        private LocalDateTime opensAt;
        private LocalDateTime closesAt;
        private RoundStatus status;
        private LocalDateTime publishedAt;
    }

    /** OPEN 互评中 / PUBLISHED 已揭晓 */
    public enum RoundStatus { OPEN, PUBLISHED }

    @Data
    @TableName("peer_review")
    public static class PeerReview {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long roundId;
        private Long raterId;
        private Long rateeId;
        /** JSON 数组，6 项行为的频率 0–3，见 {@link CreditCalculator#ITEMS} */
        private String answers;
        private BigDecimal weight;
        private Boolean flagged;
        private LocalDateTime submittedAt;
    }

    @Data
    @TableName("report")
    public static class Report {
        @TableId(type = IdType.AUTO)
        private Long id;
        private Long reporterId;
        private TargetType targetType;
        private Long targetId;
        private Reason reason;
        private String detail;
        private String evidence;
        private ReportStatus status;
        private Long handledBy;
        private LocalDateTime handledAt;
        private String resultNote;
        private LocalDateTime createdAt;
    }

    public enum TargetType { USER, TEAM }

    /** 抄袭 / 伪造材料 / 辱骂骚扰 / 广告刷屏 / 其他 */
    public enum Reason { PLAGIARISM, FORGERY, ABUSE, SPAM, OTHER }

    public enum ReportStatus { PENDING, CONFIRMED, DISMISSED }

    @Mapper
    public interface RoundMapper extends BaseMapper<ReviewRound> {

        @Select("SELECT * FROM peer_review_round WHERE id = #{id} FOR UPDATE")
        ReviewRound selectForUpdate(@Param("id") long id);

        /** 我参与的互评（我是那支队的在队成员），最新的在前 */
        @Select("""
                SELECT pr.* FROM peer_review_round pr
                JOIN team_member m ON m.team_id = pr.team_id AND m.status = 'ACTIVE'
                WHERE m.user_id = #{userId}
                ORDER BY pr.opens_at DESC, pr.id DESC
                """)
        List<ReviewRound> ofUser(@Param("userId") long userId);

        /** 到了截止时间还没揭晓的 */
        @Select("SELECT id FROM peer_review_round WHERE status = 'OPEN' AND closes_at <= #{now}")
        List<Long> due(@Param("now") LocalDateTime now);

        /** 还开着、在 until 之前截止的 */
        @Select("SELECT * FROM peer_review_round WHERE status = 'OPEN' AND closes_at > #{now} AND closes_at <= #{until}")
        List<ReviewRound> closingBetween(@Param("now") LocalDateTime now, @Param("until") LocalDateTime until);
    }

    @Mapper
    public interface PeerReviewMapper extends BaseMapper<PeerReview> {

        /** 重新提交时覆盖原来的答案（揭晓前可以改） */
        @Insert("""
                INSERT INTO peer_review (round_id, rater_id, ratee_id, answers, submitted_at)
                VALUES (#{roundId}, #{raterId}, #{rateeId}, #{answers}, #{at})
                ON DUPLICATE KEY UPDATE answers = VALUES(answers), submitted_at = VALUES(submitted_at)
                """)
        int upsert(@Param("roundId") long roundId, @Param("raterId") long raterId, @Param("rateeId") long rateeId,
                   @Param("answers") String answers, @Param("at") LocalDateTime at);

        /** 某人在所有已揭晓的轮次里收到的评分（含自评） */
        @Select("""
                SELECT r.* FROM peer_review r
                JOIN peer_review_round pr ON pr.id = r.round_id AND pr.status = 'PUBLISHED'
                WHERE r.ratee_id = #{userId}
                ORDER BY r.round_id
                """)
        List<PeerReview> publishedFor(@Param("userId") long userId);
    }

    /** user_credit 以 user_id 为主键，信用分按轮次滚动平均，直接写 SQL */
    @Mapper
    public interface CreditMapper {

        @Select("SELECT user_id AS userId, score, rated_rounds AS ratedRounds, suspended_until AS suspendedUntil "
                + "FROM user_credit WHERE user_id = #{userId}")
        Credit find(@Param("userId") long userId);

        /** 把本轮得分并进历史平均。赋值从左到右执行，算 score 时 rated_rounds 还是旧值 */
        @Insert("""
                INSERT INTO user_credit (user_id, score, rated_rounds) VALUES (#{userId}, #{score}, 1)
                ON DUPLICATE KEY UPDATE
                  score = ROUND((IFNULL(score, 0) * rated_rounds + VALUES(score)) / (rated_rounds + 1), 2),
                  rated_rounds = rated_rounds + 1
                """)
        int addRound(@Param("userId") long userId, @Param("score") double score);

        @Insert("""
                INSERT INTO user_credit (user_id, rated_rounds, suspended_until) VALUES (#{userId}, 0, #{until})
                ON DUPLICATE KEY UPDATE suspended_until = VALUES(suspended_until)
                """)
        int suspend(@Param("userId") long userId, @Param("until") LocalDateTime until);

        @Update("UPDATE user_credit SET suspended_until = NULL WHERE user_id = #{userId} AND suspended_until IS NOT NULL")
        int lift(@Param("userId") long userId);

        @Select("""
                SELECT c.user_id AS userId, c.score, c.rated_rounds AS ratedRounds, c.suspended_until AS suspendedUntil
                FROM user_credit c WHERE c.suspended_until > #{now} ORDER BY c.suspended_until
                """)
        List<Credit> suspended(@Param("now") LocalDateTime now);

        record Credit(Long userId, BigDecimal score, Integer ratedRounds, LocalDateTime suspendedUntil) {
        }
    }

    @Mapper
    public interface ReportMapper extends BaseMapper<Report> {
    }
}
