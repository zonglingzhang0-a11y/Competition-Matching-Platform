package com.teammatch.crawler;

import com.teammatch.competition.CompetitionAdminDtos.EditionForm;
import com.teammatch.competition.CompetitionAdminDtos.NodeForm;
import com.teammatch.competition.CompetitionEdition;
import com.teammatch.crawler.CrawlEntities.RecordStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知抓取（管理端）的请求和返回。
 */
public final class CrawlDtos {

    private CrawlDtos() {
    }

    public record SourceForm(
            @NotNull(message = "请选择比赛") Long competitionId,
            @NotBlank(message = "请填写来源名称") @Size(max = 64, message = "名称最多 64 个字") String name,
            @NotBlank(message = "请填写网址") @Size(max = 512, message = "网址太长了") String listUrl,
            @NotBlank(message = "请填写通知链接的选择器") @Size(max = 255) String linkSelector,
            @Size(max = 255) String titleKeywords,
            @Size(max = 255) String excludeKeywords,
            @Size(max = 255) String contentSelector,
            boolean allowLlm,
            boolean enabled,
            @Size(max = 255) String note) {
    }

    /** pending：等确认的通知数；total：一共抓到过几条 */
    public record SourceRow(
            Long id,
            Long competitionId,
            String competitionName,
            String name,
            String listUrl,
            String linkSelector,
            String titleKeywords,
            String excludeKeywords,
            String contentSelector,
            boolean allowLlm,
            boolean enabled,
            String note,
            LocalDateTime lastRunAt,
            String lastResult,
            long pending,
            long total) {
    }

    /** 试抓：列表页里每个链接会不会被抓，以及原因 */
    public record PreviewLink(String title, String url, boolean picked, String reason) {
    }

    public record PreviewResult(int linkCount, List<PreviewLink> links) {
    }

    public record RecordRow(
            Long id,
            Long sourceId,
            String sourceName,
            Long competitionId,
            String competitionName,
            String title,
            String sourceUrl,
            RecordStatus status,
            String note,
            Extracted extracted,
            LocalDateTime fetchedAt,
            Long editionId,
            String editionTitle,
            String reviewerName,
            LocalDateTime reviewedAt) {
    }

    /** 确认弹窗用：原文，加上这个比赛已有的届次（可以选择更新哪一届） */
    public record RecordDetail(RecordRow record, String rawText, List<EditionBrief> editions) {
    }

    public record EditionBrief(
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
            CompetitionEdition.Status status) {

        static EditionBrief of(CompetitionEdition e) {
            return new EditionBrief(e.getId(), e.getYear(), e.getTitle(), e.getTeamMin(), e.getTeamMax(),
                    Boolean.TRUE.equals(e.getSameSchoolRequired()), Boolean.TRUE.equals(e.getAdvisorRequired()),
                    Boolean.TRUE.equals(e.getEnglishRequired()), e.getRegistrationStart(), e.getRegistrationEnd(),
                    e.getNoticeUrl(), e.getStatus());
        }
    }

    /**
     * 采用一条通知。
     *
     * @param editionId 空：新建一届；不空：更新这个比赛已有的一届
     * @param nodes     这一届全部的官方时间节点（整体替换），更新已有届次时前端会把原有节点一起带上
     */
    public record ApproveForm(
            Long editionId,
            @NotNull @Valid EditionForm edition,
            @NotNull @Valid @Size(max = 30, message = "节点太多了") List<NodeForm> nodes) {
    }

    public record RunStatus(boolean running, LocalDateTime startedAt, LocalDateTime finishedAt, String summary) {
    }
}
