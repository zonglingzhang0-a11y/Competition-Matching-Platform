package com.teammatch.profile;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.profile.ProfileDtos.Experience;
import com.teammatch.profile.ProfileDtos.ExperienceForm;
import com.teammatch.profile.ProfileDtos.ProfileForm;
import com.teammatch.profile.ProfileDtos.ProfileResponse;
import com.teammatch.profile.ProfileDtos.SkillLevel;
import com.teammatch.profile.ProfileDtos.SkillOption;
import com.teammatch.profile.ProfileDtos.SkillsForm;
import com.teammatch.profile.UserPageDtos.PrivacyForm;
import com.teammatch.profile.UserPageDtos.UserPage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final UserPageService userPageService;

    @GetMapping("/profile")
    public Result<ProfileResponse> get() {
        return Result.ok(profileService.get(StpUtil.getLoginIdAsLong()));
    }

    @PutMapping("/profile")
    public Result<Void> update(@Valid @RequestBody ProfileForm form) {
        profileService.update(StpUtil.getLoginIdAsLong(), form);
        return Result.ok();
    }

    @PutMapping("/profile/skills")
    public Result<List<SkillLevel>> replaceSkills(@Valid @RequestBody SkillsForm form) {
        return Result.ok(profileService.replaceSkills(StpUtil.getLoginIdAsLong(), form.skills()));
    }

    @PostMapping("/profile/experiences")
    public Result<Experience> addExperience(@Valid @RequestBody ExperienceForm form) {
        return Result.ok(profileService.addExperience(StpUtil.getLoginIdAsLong(), form));
    }

    @DeleteMapping("/profile/experiences/{id}")
    public Result<Void> deleteExperience(@PathVariable long id) {
        profileService.deleteExperience(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    /** 个人资料页上参赛履历和组队记录的可见范围 */
    @PutMapping("/profile/privacy")
    public Result<Void> privacy(@Valid @RequestBody PrivacyForm form) {
        userPageService.setHistoryVisibility(StpUtil.getLoginIdAsLong(), form.historyVisibility());
        return Result.ok();
    }

    /**
     * 看某个人的个人资料页。能看到多少取决于两人的关系，见 UserPageAccess。
     * 看自己时带上 preview=true，返回的是其他已认证同学看到的样子。
     */
    @GetMapping("/users/{id}/profile")
    public Result<UserPage> page(@PathVariable long id, @RequestParam(defaultValue = "false") boolean preview) {
        return Result.ok(userPageService.view(StpUtil.getLoginIdAsLong(), id, preview));
    }

    /** 技能字典 */
    @GetMapping("/skills")
    public Result<List<SkillOption>> skills() {
        return Result.ok(profileService.skillOptions());
    }
}
