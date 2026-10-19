package com.teammatch.recognition;

import com.teammatch.profile.ProfileDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class RecognitionDtos {

    private RecognitionDtos() {
    }

    /**
     * 比赛详情页的"加分认定"：本校的几种说法（含还没确认够的），加上其他学校已经确认的。
     * 不返回是谁提交、谁确认的，只告诉本人"这条是你的""你确认过"。
     *
     * @param mySchool       没有学校（没认证）时为空
     * @param confirmsNeeded 除提交人外要几位同学确认
     */
    public record Board(MySchool mySchool, List<OtherSchool> others, int confirmsNeeded) {
    }

    /**
     * @param canContribute 认证有效才能标注和确认
     * @param shownId       现在对外显示的是哪一条，没有确认够的就是空
     * @param hasMine       自己提交过（有的话不能再确认别人的）
     */
    public record MySchool(Long schoolId, String schoolName, boolean canContribute, Long shownId, boolean hasMine,
                           List<Entry> entries) {
    }

    public record Entry(Long id, String gradeLabel, String note, String sourceUrl, int confirmCount,
                        Recognition.Status status, boolean mine, boolean confirmedByMe, LocalDateTime createdAt) {
    }

    /** 其他学校对外显示的那一条 */
    public record OtherSchool(Long schoolId, String schoolName, String gradeLabel, String note, String sourceUrl,
                              int confirmCount) {
    }

    public record MineForm(
            @NotBlank(message = "请填写认定级别") @Size(max = 32, message = "认定级别最多 32 个字") String gradeLabel,
            @Size(max = 255, message = "说明最多 255 个字") String note,
            @Size(max = 255, message = "链接太长了")
            @Pattern(regexp = ProfileDtos.HTTP_URL_OR_EMPTY, message = "学校文件的链接要是 http:// 或 https:// 开头的网址")
            String sourceUrl) {
    }

    /**
     * 管理端的一行。
     *
     * @param variants 同校同比赛下没被驳回的说法有几种（大于 1 说明有分歧）
     */
    public record AdminRow(Long id, Long schoolId, String schoolName, Long competitionId, String competitionName,
                           String gradeLabel, String note, String sourceUrl, int confirmCount, Recognition.Status status,
                           Long submitterId, String submitterName, LocalDateTime createdAt, long variants) {
    }

    public record RejectForm(@NotBlank(message = "请写驳回原因，会发给提交的同学")
                             @Size(max = 200, message = "原因最多 200 个字") String note) {
    }
}
