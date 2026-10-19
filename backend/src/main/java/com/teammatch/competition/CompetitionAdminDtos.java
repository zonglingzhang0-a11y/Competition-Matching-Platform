package com.teammatch.competition;

import com.teammatch.profile.ProfileDtos;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员维护比赛库时提交的表单。
 */
public final class CompetitionAdminDtos {

    private CompetitionAdminDtos() {
    }

    public record CompetitionForm(
            @NotBlank(message = "请填写比赛名称") @Size(max = 100, message = "比赛名称最多 100 个字") String name,
            @Size(max = 32, message = "简称最多 32 个字") String shortName,
            @Size(max = 200, message = "主办单位最多 200 个字") String organizer,
            @NotBlank(message = "请选择学科类别") String category,
            @NotNull(message = "请选择比赛级别") Competition.Level level,
            boolean inCaheList,
            @Size(max = 255) @Pattern(regexp = ProfileDtos.HTTP_URL_OR_EMPTY, message = "官网要是 http:// 或 https:// 开头的网址")
            String officialUrl,
            String description,
            Long roleTemplateId,
            Competition.MatchPreset matchPreset) {
    }

    public record EditionForm(
            @NotNull(message = "请填写年份") @Min(value = 2000, message = "年份不对") @Max(value = 2100, message = "年份不对")
            Integer year,
            @NotBlank(message = "请填写届次名称") @Size(max = 64) String title,
            @NotNull(message = "请填写最少人数") @Min(value = 1, message = "最少 1 人") Integer teamMin,
            @NotNull(message = "请填写最多人数") @Min(value = 1, message = "最少 1 人") @Max(value = 30, message = "人数太多了")
            Integer teamMax,
            boolean sameSchoolRequired,
            boolean advisorRequired,
            boolean englishRequired,
            LocalDateTime registrationStart,
            LocalDateTime registrationEnd,
            @Size(max = 255) @Pattern(regexp = ProfileDtos.HTTP_URL_OR_EMPTY, message = "通知链接要是 http:// 或 https:// 开头的网址")
            String noticeUrl,
            CompetitionEdition.Status status) {
    }

    public record NodeForm(
            @NotBlank(message = "请填写节点名称") @Size(max = 64) String title,
            @NotNull(message = "请选择节点类型") CompetitionNode.NodeType nodeType,
            @NotNull(message = "请填写开始时间") LocalDateTime startAt,
            LocalDateTime endAt,
            boolean allDay,
            LocalDateTime startLatestAt,
            CompetitionNode.EndMode endMode,
            LocalDateTime endLatestAt,
            boolean busy,
            @Size(max = 255) String note) {
    }

    public record NodesForm(@NotNull @Valid @Size(max = 30, message = "节点太多了") List<NodeForm> nodes) {
    }

    /** 管理端列表的一行：比赛的全部字段（编辑表单直接用）+ 所有届次 */
    public record AdminCompetitionRow(
            Long id,
            String name,
            String shortName,
            String organizer,
            String category,
            Competition.Level level,
            boolean inCaheList,
            String officialUrl,
            String description,
            Long roleTemplateId,
            Competition.MatchPreset matchPreset,
            List<AdminEditionRow> editions) {
    }

    /** teams：这一届建了几支队（不含已解散的）；nodes：官方时间节点个数 */
    public record AdminEditionRow(
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
            CompetitionEdition.Status status,
            EditionPhase phase,
            long teams,
            long nodes) {
    }

    public record RoleTemplateView(Long id, String name, List<String> roles) {
    }
}
