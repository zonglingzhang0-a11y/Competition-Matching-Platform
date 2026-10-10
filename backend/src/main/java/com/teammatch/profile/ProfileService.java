package com.teammatch.profile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.common.BizException;
import com.teammatch.competition.CompetitionMappers.CompetitionMapper;
import com.teammatch.profile.ProfileDtos.Experience;
import com.teammatch.profile.ProfileDtos.ExperienceForm;
import com.teammatch.profile.ProfileDtos.ProfileForm;
import com.teammatch.profile.ProfileDtos.ProfileResponse;
import com.teammatch.profile.ProfileDtos.SkillLevel;
import com.teammatch.profile.ProfileDtos.SkillLevelInput;
import com.teammatch.profile.ProfileDtos.SkillOption;
import com.teammatch.profile.ProfileMappers.ExperienceMapper;
import com.teammatch.profile.ProfileMappers.SkillMapper;
import com.teammatch.profile.ProfileMappers.UserSkillMapper;
import com.teammatch.user.MeResponse;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 个人档案：基本资料、技能、参赛履历。只能看和改自己的。
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final SkillMapper skillMapper;
    private final UserSkillMapper userSkillMapper;
    private final ExperienceMapper experienceMapper;
    private final CompetitionMapper competitionMapper;

    public ProfileResponse get(long userId) {
        MeResponse me = userService.me(userId);
        UserProfile p = profileOf(userId);
        return new ProfileResponse(me.email(), me.nickname(), me.verifyStatus(), me.school(),
                p.getRealName(), p.getGender(), p.getMajor(), p.getEnrollYear(), p.getDegree(), p.getBio(),
                p.getContactWechat(), p.getContactQq(), skills(userId), experiences(userId));
    }

    @Transactional
    public void update(long userId, ProfileForm form) {
        SysUser user = new SysUser();
        user.setId(userId);
        user.setNickname(form.nickname().trim());
        userMapper.updateById(user);

        profileOf(userId);
        // 用实体更新时 MyBatis-Plus 会跳过空字段，清空选填项就写不进去，所以逐个 set
        profileMapper.update(new LambdaUpdateWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId)
                .set(UserProfile::getRealName, blankToNull(form.realName()))
                .set(UserProfile::getGender, blankToNull(form.gender()))
                .set(UserProfile::getMajor, blankToNull(form.major()))
                .set(UserProfile::getEnrollYear, form.enrollYear())
                .set(UserProfile::getDegree, blankToNull(form.degree()))
                .set(UserProfile::getBio, blankToNull(form.bio()))
                .set(UserProfile::getContactWechat, blankToNull(form.contactWechat()))
                .set(UserProfile::getContactQq, blankToNull(form.contactQq())));
    }

    public List<SkillOption> skillOptions() {
        return skillMapper.selectList(new LambdaQueryWrapper<Skill>().orderByAsc(Skill::getSort))
                .stream().map(s -> new SkillOption(s.getId(), s.getName(), s.getCategory())).toList();
    }

    /** 整体替换自己的技能列表 */
    @Transactional
    public List<SkillLevel> replaceSkills(long userId, List<SkillLevelInput> skills) {
        Set<Long> ids = new HashSet<>();
        for (SkillLevelInput s : skills) {
            if (!ids.add(s.skillId())) {
                throw new BizException("同一项技能填了两次");
            }
        }
        if (!ids.isEmpty() && skillMapper.selectCount(new LambdaQueryWrapper<Skill>().in(Skill::getId, ids)) != ids.size()) {
            throw new BizException("有技能不在技能列表里，请刷新页面后重试");
        }
        userSkillMapper.deleteByUser(userId);
        if (!skills.isEmpty()) {
            userSkillMapper.insertAll(userId, skills);
        }
        return skills(userId);
    }

    public Experience addExperience(long userId, ExperienceForm form) {
        if (form.competitionId() != null && competitionMapper.selectById(form.competitionId()) == null) {
            throw new BizException("选的比赛不存在，请重新选择");
        }
        UserExperience e = new UserExperience();
        e.setUserId(userId);
        e.setCompetitionId(form.competitionId());
        e.setTitle(form.title().trim());
        e.setYear(form.year());
        e.setAward(blankToNull(form.award()));
        e.setProofUrl(blankToNull(form.proofUrl()));
        e.setSource(UserExperience.Source.MANUAL);
        e.setVerified(false);
        experienceMapper.insert(e);
        return toView(e);
    }

    /** 只能删自己手填的；平台生成的履历跟着比赛成绩走，不能删 */
    public void deleteExperience(long userId, long id) {
        UserExperience e = experienceMapper.selectById(id);
        if (e == null || !e.getUserId().equals(userId)) {
            throw new BizException(404, "这条履历不存在");
        }
        if (e.getSource() != UserExperience.Source.MANUAL) {
            throw new BizException("平台组队生成的履历不能删除");
        }
        experienceMapper.deleteById(id);
    }

    public List<SkillLevel> skills(long userId) {
        return userSkillMapper.listByUser(userId).stream()
                .map(r -> new SkillLevel(r.skillId(), r.name(), r.category(), r.level())).toList();
    }

    private List<Experience> experiences(long userId) {
        return experienceMapper.selectList(new LambdaQueryWrapper<UserExperience>()
                        .eq(UserExperience::getUserId, userId)
                        .orderByDesc(UserExperience::getYear)
                        .orderByDesc(UserExperience::getId))
                .stream().map(ProfileService::toView).toList();
    }

    /** 老账号可能没有档案行，查不到就补一行 */
    private UserProfile profileOf(long userId) {
        UserProfile p = profileMapper.selectById(userId);
        if (p == null) {
            p = new UserProfile();
            p.setUserId(userId);
            profileMapper.insert(p);
        }
        return p;
    }

    private static Experience toView(UserExperience e) {
        return new Experience(e.getId(), e.getCompetitionId(), e.getTitle(), e.getYear(), e.getAward(),
                e.getProofUrl(), e.getSource(), Boolean.TRUE.equals(e.getVerified()));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
