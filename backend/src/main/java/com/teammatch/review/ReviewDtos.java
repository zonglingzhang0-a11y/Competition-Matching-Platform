package com.teammatch.review;

import com.teammatch.review.ReviewEntities.Reason;
import com.teammatch.review.ReviewEntities.ReportStatus;
import com.teammatch.review.ReviewEntities.RoundStatus;
import com.teammatch.review.ReviewEntities.TargetType;
import com.teammatch.user.UserBriefs.UserBrief;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 互评、信用分、举报接口的请求和返回。
 */
public final class ReviewDtos {

    private ReviewDtos() {
    }

    // ---------- 比赛结束 ----------

    public record FinishForm(@Size(max = 32, message = "奖项最多 32 个字") String award) {
    }

    // ---------- 互评 ----------

    /**
     * 我参与的一轮互评。
     *
     * @param submitted 我是不是已经给所有队友打完了分
     * @param raters    已经交了的人数
     */
    public record RoundSummary(Long id, Long teamId, String teamName, String competitionName, String editionTitle,
                               RoundStatus status, LocalDateTime closesAt, LocalDateTime publishedAt,
                               boolean submitted, int raters, int participants) {
    }

    /** 填互评的页面：行为清单、频率档位、每个要评的人和我已经填过的答案 */
    public record RoundForm(RoundSummary round, List<String> items, List<String> frequencies, List<Target> targets,
                            boolean editable) {
    }

    /** self：这是自评，选填，只有自己能看到对比结果 */
    public record Target(UserBrief user, boolean self, List<Integer> answers) {
    }

    public record ReviewInput(@NotNull(message = "缺少被评人") Long rateeId,
                              @NotNull(message = "缺少答案") List<Integer> answers) {
    }

    public record SubmitForm(@NotNull @Valid @Size(min = 1, max = 30) List<ReviewInput> reviews) {
    }

    /** published：交完这一份，所有人都交齐了，结果马上揭晓 */
    public record SubmitResult(boolean published, int raters, int participants) {
    }

    // ---------- 信用 ----------

    /**
     * 我的信用。
     *
     * @param score         0–100，没有互评记录时为空（新用户）
     * @param factor        推荐时乘的承诺系数（信用部分）
     * @param itemsVisible  累计 2 场比赛或 4 个以上评分人之后才显示各项，避免猜出是谁打的
     * @param items         每项行为别人评的平均频率（0–3），没到显示条件时为空
     * @param selfItems     我自评的平均频率，和 items 对照看；没自评过为空
     */
    public record CreditView(Double score, int ratedRounds, double factor, LocalDateTime suspendedUntil,
                             boolean itemsVisible, List<String> itemNames, List<Double> items, List<Double> selfItems,
                             int raters, List<RoundSummary> rounds) {
    }

    // ---------- 举报 ----------

    public record ReportForm(
            @NotNull(message = "请选择举报对象") TargetType targetType,
            @NotNull(message = "请选择举报对象") Long targetId,
            @NotNull(message = "请选择举报原因") Reason reason,
            @Size(max = 1000, message = "说明最多 1000 个字") String detail) {
    }

    public record MyReport(Long id, TargetType targetType, Long targetId, String targetName, Reason reason,
                           String detail, ReportStatus status, String resultNote, LocalDateTime createdAt,
                           LocalDateTime handledAt) {
    }

    /** 管理端的一行。sameTarget：针对同一对象的举报一共几条 */
    public record ReportRow(Long id, String reporter, TargetType targetType, Long targetId, String targetName,
                            String targetSub, Reason reason, String detail, ReportStatus status, String handler,
                            String resultNote, LocalDateTime createdAt, LocalDateTime handledAt, long sameTarget) {
    }

    /**
     * 处理方式：NONE 只记录 / SUSPEND_30 暂停推荐 30 天 / BLOCK 不再出现在推荐里 / BAN 停用账号 / CLOSE_TEAM 关闭队伍招募。
     * 前四种只对举报用户有效，最后一种只对举报队伍有效。
     */
    public enum Action { NONE, SUSPEND_30, BLOCK, BAN, CLOSE_TEAM }

    public record HandleForm(
            @NotNull(message = "请选择处理结果") ReportStatus result,
            Action action,
            @NotBlank(message = "请写处理说明，会发给举报人") @Size(max = 255, message = "说明最多 255 个字") String note) {
    }

    // ---------- 管理端：信用 ----------

    public record SuspendedRow(Long userId, String nickname, String email, Double score, int ratedRounds,
                               LocalDateTime suspendedUntil, boolean permanent) {
    }

    public record FlaggedRow(Long reviewId, Long roundId, String teamName, String rater, String ratee,
                             List<Integer> answers, LocalDateTime submittedAt) {
    }
}
