package com.teammatch.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.auth.AuthDtos.LoginRequest;
import com.teammatch.auth.AuthDtos.LoginResponse;
import com.teammatch.auth.AuthDtos.RegisterRequest;
import com.teammatch.code.CodePurpose;
import com.teammatch.code.EmailCodeService;
import com.teammatch.common.BizException;
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
        user.setRole(SysUser.UserRole.STUDENT);
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
        SysUser user = findByEmail(req.email());
        if (user == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BizException("邮箱或密码不正确");
        }
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

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
