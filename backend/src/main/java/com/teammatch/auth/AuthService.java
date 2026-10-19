package com.teammatch.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.auth.AuthDtos.LoginRequest;
import com.teammatch.auth.AuthDtos.LoginResponse;
import com.teammatch.auth.AuthDtos.RegisterRequest;
import com.teammatch.auth.AuthDtos.ResetPasswordRequest;
import com.teammatch.code.CodePurpose;
import com.teammatch.code.EmailCodeService;
import com.teammatch.common.BizException;
import com.teammatch.config.AppProperties;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final EmailCodeService codeService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties props;
    private final LoginThrottle throttle;
    private final Clock clock;

    public void sendRegisterCode(String email) {
        if (findByEmail(email) != null) {
            throw new BizException(409, "这个邮箱已经注册过了，直接登录吧");
        }
        codeService.send(email, CodePurpose.REGISTER);
    }

    @Transactional
    public LoginResponse register(RegisterRequest req) {
        String email = normalize(req.email());
        if (findByEmail(email) != null) {
            throw new BizException(409, "这个邮箱已经注册过了，直接登录吧");
        }
        codeService.verify(email, CodePurpose.REGISTER, req.code());

        SysUser user = new SysUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setNickname(req.nickname().trim());
        user.setRole(props.isAdminEmail(email) ? SysUser.UserRole.ADMIN : SysUser.UserRole.STUDENT);
        user.setStatus(SysUser.UserStatus.ACTIVE);
        user.setVerifyStatus(SysUser.VerifyStatus.NONE);
        user.setLastLoginAt(LocalDateTime.now(clock));
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BizException(409, "这个邮箱已经注册过了，直接登录吧");
        }

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        profileMapper.insert(profile);

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), userService.me(user.getId()));
    }

    public LoginResponse login(LoginRequest req) {
        String email = normalize(req.email());
        throttle.check(email);
        SysUser user = findByEmail(email);
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throttle.failed(email);
            throw new BizException("邮箱或密码不正确");
        }
        throttle.reset(email);
        if (user.getStatus() == SysUser.UserStatus.BANNED) {
            throw new BizException(403, "这个账号已被停用");
        }

        SysUser touch = new SysUser();
        touch.setId(user.getId());
        touch.setLastLoginAt(LocalDateTime.now(clock));
        userMapper.updateById(touch);

        StpUtil.login(user.getId());
        return new LoginResponse(StpUtil.getTokenValue(), userService.me(user.getId()));
    }

    private SysUser findByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, normalize(email)));
    }

    /** 忘记密码：发验证码。没注册的邮箱也照样返回成功（只是不发信），免得被人拿来试哪些邮箱注册过 */
    public void sendResetCode(String email) {
        if (findByEmail(email) != null) {
            codeService.send(email, CodePurpose.RESET_PASSWORD);
        }
    }

    /**
     * 用验证码设新密码。成功后这个账号在所有设备上的登录都失效（防止被别人登着），
     * 输错密码的锁也解开，用新密码重新登录。
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String email = normalize(req.email());
        codeService.verify(email, CodePurpose.RESET_PASSWORD, req.code());
        SysUser user = findByEmail(email);
        if (user == null) {
            throw new BizException("验证码已失效，请重新获取");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(req.password()));
        userMapper.updateById(update);
        throttle.reset(email);
        StpUtil.logout(user.getId());
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
