package com.teammatch.profile;

import com.teammatch.radar.RadarDtos.Radar;
import com.teammatch.user.MeResponse;
import com.teammatch.user.SysUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** 用户填的链接只收 http(s)：javascript: 这类链接被别人点开会在本站执行脚本。空的也行（选填） */
    public static final String HTTP_URL_OR_EMPTY = "(?i)\\s*(https?://\\S+)?\\s*";

    public record ProfileResponse(
            String email,
            String nickname,
            SysUser.VerifyStatus verifyStatus,
            MeResponse.SchoolBrief school,
            String realName,
            String gender,
            String major,
            Integer enrollYear,
            String degree,
            String bio,
            String contactWechat,
            String contactQq,
            List<SkillLevel> skills,
            /* 六维能力雷达：技能按竞赛里常见的六类能力归并算分，见 RadarMath */
            Radar radar,
            List<Experience> experiences,
            UserPageAccess.HistoryVisibility historyVisibility) {
    }

    public record SkillLevel(Long skillId, String name, String category, Integer level) {
    }

    public record Experience(
            Long id,
            Long competitionId,
            String title,
            Integer year,
            String award,
            String proofUrl,
            UserExperience.Source source,
            boolean verified,
            /* 关联了比赛库的，本校同学确认好的加分认定级别，比如"A 类"；整理综测材料时参考 */
            String recognition) {
    }

    public record ProfileForm(
            @NotBlank(message = "请填写昵称") @Size(max = 16, message = "昵称最多 16 个字") String nickname,
            @Size(max = 32, message = "姓名最多 32 个字") String realName,
            @Pattern(regexp = "|MALE|FEMALE|OTHER", message = "性别选项不对") String gender,
            @Size(max = 64, message = "专业最多 64 个字") String major,
            @Min(value = 2000, message = "入学年份不对") @Max(value = 2100, message = "入学年份不对") Integer enrollYear,
            @Pattern(regexp = "|JUNIOR_COLLEGE|UNDERGRAD|MASTER|PHD", message = "学历选项不对") String degree,
            @Size(max = 500, message = "个人简介最多 500 个字") String bio,
            @Size(max = 64, message = "微信号太长了") String contactWechat,
            @Pattern(regexp = "|\\d{5,12}", message = "QQ 号应该是 5 到 12 位数字") String contactQq) {
    }

    public record SkillLevelInput(
            @NotNull(message = "技能不能为空") Long skillId,
            @NotNull(message = "请选择熟练度") @Min(value = 1, message = "熟练度是 1 到 3") @Max(value = 3, message = "熟练度是 1 到 3")
            Integer level) {
    }

    public record SkillsForm(
            @NotNull @Valid @Size(max = 20, message = "最多填 20 项技能") List<SkillLevelInput> skills) {
    }

    public record ExperienceForm(
            Long competitionId,
            @NotBlank(message = "请填写比赛名称") @Size(max = 100, message = "比赛名称最多 100 个字") String title,
            @NotNull(message = "请填写年份") @Min(value = 2000, message = "年份不对") @Max(value = 2100, message = "年份不对")
            Integer year,
            @Size(max = 32, message = "奖项最多 32 个字") String award,
            @Size(max = 255, message = "链接太长了")
            @Pattern(regexp = HTTP_URL_OR_EMPTY, message = "证明链接要是 http:// 或 https:// 开头的网址") String proofUrl) {
    }

    /** 技能字典里的一项 */
    public record SkillOption(Long id, String name, String category) {
    }
}
