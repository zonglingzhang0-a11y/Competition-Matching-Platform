package com.teammatch.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.notification.Notification;
import com.teammatch.notification.NotificationService;
import com.teammatch.school.School;
import com.teammatch.school.SchoolAdminService;
import com.teammatch.school.SchoolAdminService.SchoolForm;
import com.teammatch.school.SchoolMapper;
import com.teammatch.school.SchoolResolver;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.verify.VerifyAdminDtos.ApproveForm;
import com.teammatch.verify.VerifyAdminDtos.ApproveResult;
import com.teammatch.verify.VerifyAdminDtos.RejectForm;
import com.teammatch.verify.VerifyAdminDtos.ReviewItem;
import com.teammatch.verify.VerifyAdminDtos.ReviewUser;
import com.teammatch.verify.VerifyAdminDtos.SchoolRef;
import com.teammatch.verify.VerifyAdminDtos.ScreenshotInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证审核：学校还没收录的教育邮箱，由管理员确认是哪所学校。
 * <p>
 * 通过时顺便把邮箱后缀收录进来，以后同校的人就能自动认证；排队中的同校申请也一起通过。
 */
@Service
@RequiredArgsConstructor
public class VerifyReviewService {

    private final VerificationRequestMapper requestMapper;
    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final SchoolMapper schoolMapper;
    private final SchoolAdminService schoolAdmin;
    private final SchoolResolver schoolResolver;
    private final NotificationService notifications;
    private final ScreenshotVerifyService screenshotService;
    private final Clock clock;

    /** 待审核的按提交顺序排（先来先审），已处理的最新的在前 */
    public PageResult<ReviewItem> list(VerificationRequest.Status status, int page, int size) {
        boolean pending = status == VerificationRequest.Status.PENDING;
        Page<VerificationRequest> p = requestMapper.selectPage(Page.of(page, size),
                new LambdaQueryWrapper<VerificationRequest>()
                        .eq(status != null, VerificationRequest::getStatus, status)
                        .orderBy(true, pending, VerificationRequest::getId));
        List<VerificationRequest> rows = p.getRecords();

        Set<Long> userIds = new HashSet<>();
        rows.forEach(r -> {
            userIds.add(r.getUserId());
            if (r.getReviewerId() != null) {
                userIds.add(r.getReviewerId());
            }
        });
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream().collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, UserProfile> profiles = userIds.isEmpty() ? Map.of()
                : profileMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity()));
        Map<Long, ScreenshotVerifyService.OcrRecord> ocr = new HashMap<>();
        rows.stream().filter(r -> r.getMethod() == VerificationRequest.Method.SCREENSHOT && r.getOcrResult() != null)
                .forEach(r -> ocr.put(r.getId(), screenshotService.parse(r.getOcrResult())));
        Set<Long> schoolIds = rows.stream().map(VerificationRequest::getSchoolId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        ocr.values().stream().map(ScreenshotVerifyService.OcrRecord::suggestedSchoolId).filter(Objects::nonNull)
                .forEach(schoolIds::add);
        Map<Long, School> schools = schoolIds.isEmpty() ? Map.of()
                : schoolMapper.selectByIds(schoolIds).stream().collect(Collectors.toMap(School::getId, Function.identity()));

        return PageResult.of(p, r -> {
            SysUser u = users.get(r.getUserId());
            UserProfile prof = profiles.get(r.getUserId());
            SysUser reviewer = r.getReviewerId() == null ? null : users.get(r.getReviewerId());
            School school = r.getSchoolId() == null ? null : schools.get(r.getSchoolId());
            return new ReviewItem(r.getId(), r.getMethod(), r.getStatus(), r.getEduEmail(),
                    SchoolResolver.candidateDomains(r.getEduEmail()),
                    new ReviewUser(r.getUserId(), u == null ? null : u.getNickname(), u == null ? null : u.getEmail(),
                            prof == null ? null : prof.getRealName(), prof == null ? null : prof.getMajor(),
                            prof == null ? null : prof.getEnrollYear()),
                    school == null ? null : new SchoolRef(school.getId(), school.getName()),
                    reviewer == null ? null : reviewer.getNickname(),
                    r.getReviewNote(), r.getReviewedAt(), r.getCreatedAt(),
                    r.getMethod() == VerificationRequest.Method.SCREENSHOT
                            ? screenshotInfo(r, ocr.get(r.getId()), schools) : null);
        });
    }

    private static ScreenshotInfo screenshotInfo(VerificationRequest r, ScreenshotVerifyService.OcrRecord o,
                                                 Map<Long, School> schools) {
        School suggested = o == null || o.suggestedSchoolId() == null ? null : schools.get(o.suggestedSchoolId());
        return new ScreenshotInfo(
                o == null ? null : o.claimed().realName(),
                o == null ? null : o.claimed().schoolName(),
                o == null ? null : o.claimed().enrollYear(),
                r.getOcrConfidence(),
                o == null ? null : o.matched(),
                o == null || o.lines() == null ? List.of() : o.lines(),
                suggested == null ? null : new SchoolRef(suggested.getId(), suggested.getName()),
                r.getImagePath() != null);
    }

    public long pendingCount() {
        return requestMapper.selectCount(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getStatus, VerificationRequest.Status.PENDING));
    }

    @Transactional
    public ApproveResult approve(long reviewerId, long id, ApproveForm form) {
        VerificationRequest r = requirePending(id);
        boolean useExisting = form.schoolId() != null;
        boolean createNew = StringUtils.hasText(form.newSchoolName());
        if (useExisting == createNew) {
            throw new BizException("请选一所已有的学校，或者填写新学校的名称（二选一）");
        }
        // 教育邮箱：顺便收录后缀；截图认证没有邮箱，只确定学校
        String domain = null;
        if (r.getMethod() == VerificationRequest.Method.EDU_EMAIL) {
            if (!StringUtils.hasText(form.domain())) {
                throw new BizException("请选择要收录的邮箱后缀");
            }
            domain = SchoolAdminService.normalizeDomain(form.domain());
            List<String> candidates = SchoolResolver.candidateDomains(r.getEduEmail());
            if (!candidates.contains(domain)) {
                throw new BizException("后缀要从这个邮箱里取：" + String.join(" 或 ", candidates));
            }
        }

        School school = useExisting ? schoolAdmin.require(form.schoolId())
                : schoolAdmin.create(new SchoolForm(form.newSchoolName(), form.province(), form.city()));
        if (domain != null) {
            schoolAdmin.ensureDomain(school.getId(), domain);
        }
        String note = StringUtils.hasText(form.note()) ? form.note().trim() : "管理员审核通过";
        if (!approveOne(r, school, reviewerId, note)) {
            throw new BizException(409, "这条申请已经被处理过了，请刷新");
        }
        return new ApproveResult(school.getName(), approveResolvable(reviewerId));
    }

    @Transactional
    public void reject(long reviewerId, long id, RejectForm form) {
        VerificationRequest r = requirePending(id);
        String note = form.note().trim();
        int updated = requestMapper.update(new LambdaUpdateWrapper<VerificationRequest>()
                .eq(VerificationRequest::getId, r.getId())
                .eq(VerificationRequest::getStatus, VerificationRequest.Status.PENDING)
                .set(VerificationRequest::getStatus, VerificationRequest.Status.REJECTED)
                .set(VerificationRequest::getReviewerId, reviewerId)
                .set(VerificationRequest::getReviewNote, note)
                .set(VerificationRequest::getReviewedAt, LocalDateTime.now(clock)));
        if (updated == 0) {
            throw new BizException(409, "这条申请已经被处理过了，请刷新");
        }
        screenshotService.deleteImage(r);
        // 只有还停在"待确认"时才改回未认证：已经认证过的人换学校没通过，原来的认证照旧
        int downgraded = userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, r.getUserId())
                .eq(SysUser::getVerifyStatus, SysUser.VerifyStatus.PENDING)
                .set(SysUser::getVerifyStatus, SysUser.VerifyStatus.NONE));
        notifications.send(r.getUserId(), Notification.Type.VERIFY_RESULT, "身份认证没有通过",
                "原因：" + note + (downgraded > 0 ? "。可以换一个学校邮箱重新认证。" : "。你原来的认证不受影响。"), null, null);
    }

    /** 给学校收录一个邮箱后缀，返回顺带自动通过的待审核申请条数 */
    @Transactional
    public int addDomain(long reviewerId, long schoolId, String domain) {
        schoolAdmin.require(schoolId);
        schoolAdmin.ensureDomain(schoolId, domain);
        return approveResolvable(reviewerId);
    }

    /**
     * 把现在能自动对上学校的待审核申请都通过，返回通过了几条。
     * 管理员刚收录了新的邮箱后缀时调用。
     */
    @Transactional
    public int approveResolvable(Long reviewerId) {
        List<VerificationRequest> pending = requestMapper.selectList(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getStatus, VerificationRequest.Status.PENDING)
                .eq(VerificationRequest::getMethod, VerificationRequest.Method.EDU_EMAIL));
        int approved = 0;
        for (VerificationRequest r : pending) {
            Optional<School> school = schoolResolver.resolve(r.getEduEmail());
            if (school.isPresent() && approveOne(r, school.get(), reviewerId, "邮箱后缀已收录，自动通过")) {
                approved++;
            }
        }
        return approved;
    }

    /** 申请改成通过、用户改成已认证、发通知。申请已经被别人处理了就返回 false，什么都不改 */
    private boolean approveOne(VerificationRequest r, School school, Long reviewerId, String note) {
        LocalDateTime now = LocalDateTime.now(clock);
        int updated = requestMapper.update(new LambdaUpdateWrapper<VerificationRequest>()
                .eq(VerificationRequest::getId, r.getId())
                .eq(VerificationRequest::getStatus, VerificationRequest.Status.PENDING)
                .set(VerificationRequest::getStatus, VerificationRequest.Status.APPROVED)
                .set(VerificationRequest::getSchoolId, school.getId())
                .set(VerificationRequest::getReviewerId, reviewerId)
                .set(VerificationRequest::getReviewNote, note)
                .set(VerificationRequest::getReviewedAt, now));
        if (updated == 0) {
            return false;
        }
        screenshotService.deleteImage(r);
        LocalDate until = EduVerifyService.academicYearEnd(now.toLocalDate());
        userMapper.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, r.getUserId())
                .set(SysUser::getVerifyStatus, SysUser.VerifyStatus.VERIFIED)
                .set(SysUser::getSchoolId, school.getId())
                .set(SysUser::getVerifiedUntil, until));
        notifications.send(r.getUserId(), Notification.Type.VERIFY_RESULT, "身份认证通过",
                "已确认你是「" + school.getName() + "」的学生，认证有效到 " + until + "。现在可以建队和申请入队了。",
                null, null);
        return true;
    }

    private VerificationRequest requirePending(long id) {
        VerificationRequest r = requestMapper.selectById(id);
        if (r == null) {
            throw new BizException(404, "这条认证申请不存在");
        }
        if (r.getStatus() != VerificationRequest.Status.PENDING) {
            throw new BizException(409, "这条申请已经被处理过了，请刷新");
        }
        return r;
    }
}
