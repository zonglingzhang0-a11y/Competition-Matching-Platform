package com.teammatch.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teammatch.code.CodePurpose;
import com.teammatch.code.EmailCodeService;
import com.teammatch.common.BizException;
import com.teammatch.school.School;
import com.teammatch.school.SchoolResolver;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserService;
import com.teammatch.verify.VerifyDtos.LatestRequest;
import com.teammatch.verify.VerifyDtos.VerifyResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 教育邮箱认证：往学校邮箱发验证码，填对了就算认证通过。
 */
@Service
@RequiredArgsConstructor
public class EduVerifyService {

    private final VerificationRequestMapper requestMapper;
    private final SysUserMapper userMapper;
    private final UserService userService;
    private final ScreenshotVerifyService screenshotService;
    private final SchoolResolver schoolResolver;
    private final EmailCodeService codeService;
    private final Clock clock;

    public void sendCode(long userId, String eduEmail) {
        String email = normalize(eduEmail);
        checkEduEmail(email);
        checkNotTakenByOthers(userId, email);
        codeService.send(email, CodePurpose.EDU_VERIFY);
    }

    @Transactional
    public VerifyResult confirm(long userId, String eduEmail, String code) {
        String email = normalize(eduEmail);
        checkEduEmail(email);
        checkNotTakenByOthers(userId, email);
        codeService.verify(email, CodePurpose.EDU_VERIFY, code);

        LocalDateTime now = LocalDateTime.now(clock);
        Optional<School> school = schoolResolver.resolve(email);

        VerificationRequest request = new VerificationRequest();
        request.setUserId(userId);
        request.setMethod(VerificationRequest.Method.EDU_EMAIL);
        request.setEduEmail(email);

        SysUser update = new SysUser();
        update.setId(userId);

        VerifyResult result;
        if (school.isPresent()) {
            LocalDate until = academicYearEnd(now.toLocalDate());
            request.setStatus(VerificationRequest.Status.APPROVED);
            request.setSchoolId(school.get().getId());
            request.setReviewNote("教育邮箱自动认证");
            request.setReviewedAt(now);
            update.setVerifyStatus(SysUser.VerifyStatus.VERIFIED);
            update.setSchoolId(school.get().getId());
            update.setVerifiedUntil(until);
            result = new VerifyResult(SysUser.VerifyStatus.VERIFIED, school.get().getName(), until, "认证成功");
        } else {
            request.setStatus(VerificationRequest.Status.PENDING);
            SysUser current = userService.getById(userId);
            if (userService.isVerified(current)) {
                // 已经认证过的人换学校：审核期间保留原来的认证，不然这段时间连队伍都动不了
                update = null;
                result = new VerifyResult(SysUser.VerifyStatus.VERIFIED, null, current.getVerifiedUntil(),
                        "邮箱已验证。平台还没收录这所学校的邮箱，管理员确认前你仍保留原来的认证");
            } else {
                update.setVerifyStatus(SysUser.VerifyStatus.PENDING);
                result = new VerifyResult(SysUser.VerifyStatus.PENDING, null, null,
                        "邮箱已验证。平台还没收录这所学校的邮箱，管理员确认后会通知你");
            }
        }
        // 以前还在排队的申请作废（包括截图认证的，原图一并删掉），一个人同时只留一条待审核
        screenshotService.supersedePending(userId, now);
        requestMapper.insert(request);
        if (update != null) {
            userMapper.updateById(update);
        }
        return result;
    }

    /** 最近一次提交的认证申请，页面上用来显示"没通过的原因"；没提交过返回 null */
    public LatestRequest latest(long userId) {
        VerificationRequest r = requestMapper.selectOne(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getUserId, userId)
                .orderByDesc(VerificationRequest::getId)
                .last("LIMIT 1"));
        return r == null ? null
                : new LatestRequest(r.getStatus(), r.getMethod(), r.getEduEmail(), r.getReviewNote(), r.getReviewedAt(),
                r.getCreatedAt());
    }

    /** 认证有效到这一学年结束（8 月 31 日），下一学年要重新认证 */
    public static LocalDate academicYearEnd(LocalDate today) {
        int endYear = today.getMonthValue() >= 9 ? today.getYear() + 1 : today.getYear();
        return LocalDate.of(endYear, 8, 31);
    }

    private static void checkEduEmail(String email) {
        if (!SchoolResolver.isEduEmail(email)) {
            throw new BizException("请使用学校发的 .edu.cn 邮箱");
        }
    }

    /** 一个教育邮箱只能给一个账号认证 */
    private void checkNotTakenByOthers(long userId, String email) {
        boolean taken = requestMapper.exists(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getEduEmail, email)
                .ne(VerificationRequest::getUserId, userId)
                .in(VerificationRequest::getStatus,
                        List.of(VerificationRequest.Status.APPROVED, VerificationRequest.Status.PENDING)));
        if (taken) {
            throw new BizException(409, "这个教育邮箱已经给其他账号认证过了");
        }
    }

    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
