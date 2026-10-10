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
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

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

    /** 技能字典 */
    @GetMapping("/skills")
    public Result<List<SkillOption>> skills() {
        return Result.ok(profileService.skillOptions());
    }
}
