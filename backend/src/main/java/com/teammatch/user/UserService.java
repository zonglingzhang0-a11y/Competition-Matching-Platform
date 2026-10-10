package com.teammatch.user;

import com.teammatch.common.BizException;
import com.teammatch.school.School;
import com.teammatch.school.SchoolMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SchoolMapper schoolMapper;
    private final Clock clock;

    public SysUser getById(long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(401, "账号不存在，请重新登录");
        }
        return user;
    }

    public MeResponse me(long userId) {
        SysUser user = getById(userId);
        MeResponse.SchoolBrief school = null;
        if (user.getSchoolId() != null) {
            School s = schoolMapper.selectById(user.getSchoolId());
            if (s != null) {
                school = new MeResponse.SchoolBrief(s.getId(), s.getName());
            }
        }
        return new MeResponse(user.getId(), user.getEmail(), user.getNickname(), user.getRole(),
                effectiveVerifyStatus(user), user.getVerifiedUntil(), school);
    }

    /** 认证过了有效期就当作未认证 */
    public SysUser.VerifyStatus effectiveVerifyStatus(SysUser user) {
        if (user.getVerifyStatus() == SysUser.VerifyStatus.VERIFIED
                && user.getVerifiedUntil() != null
                && user.getVerifiedUntil().isBefore(LocalDate.now(clock))) {
            return SysUser.VerifyStatus.NONE;
        }
        return user.getVerifyStatus();
    }
}
