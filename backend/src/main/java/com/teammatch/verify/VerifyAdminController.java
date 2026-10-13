package com.teammatch.verify;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.school.SchoolAdminService;
import com.teammatch.school.SchoolAdminService.DomainForm;
import com.teammatch.school.SchoolAdminService.SchoolForm;
import com.teammatch.school.SchoolAdminService.SchoolRow;
import com.teammatch.verify.VerifyAdminDtos.ApproveForm;
import com.teammatch.verify.VerifyAdminDtos.ApproveResult;
import com.teammatch.verify.VerifyAdminDtos.RejectForm;
import com.teammatch.verify.VerifyAdminDtos.ReviewItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：认证审核，以及学校和邮箱后缀的维护。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class VerifyAdminController {

    private final VerifyReviewService reviewService;
    private final SchoolAdminService schoolAdmin;
    private final ScreenshotVerifyService screenshotService;

    /** status 不传就是全部 */
    @GetMapping("/verifications")
    public Result<PageResult<ReviewItem>> list(
            @RequestParam(required = false) VerificationRequest.Status status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reviewService.list(status, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/verifications/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(reviewService.pendingCount());
    }

    /** 截图认证的原图，只给管理员看；审核完会删掉 */
    @GetMapping("/verifications/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable long id) {
        ScreenshotVerifyService.ImageData img = screenshotService.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(img.bytes());
    }

    @PostMapping("/verifications/{id}/approve")
    public Result<ApproveResult> approve(@PathVariable long id, @Valid @RequestBody ApproveForm form) {
        return Result.ok(reviewService.approve(StpUtil.getLoginIdAsLong(), id, form));
    }

    @PostMapping("/verifications/{id}/reject")
    public Result<Void> reject(@PathVariable long id, @Valid @RequestBody RejectForm form) {
        reviewService.reject(StpUtil.getLoginIdAsLong(), id, form);
        return Result.ok();
    }

    @GetMapping("/schools")
    public Result<PageResult<SchoolRow>> schools(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(schoolAdmin.list(keyword, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @PostMapping("/schools")
    public Result<Long> createSchool(@Valid @RequestBody SchoolForm form) {
        return Result.ok(schoolAdmin.create(form).getId());
    }

    /** 收录后缀，返回顺带自动通过的待审核申请条数 */
    @PostMapping("/schools/{id}/domains")
    public Result<Integer> addDomain(@PathVariable long id, @Valid @RequestBody DomainForm form) {
        return Result.ok(reviewService.addDomain(StpUtil.getLoginIdAsLong(), id, form.domain()));
    }

    @DeleteMapping("/school-domains/{id}")
    public Result<Void> removeDomain(@PathVariable long id) {
        schoolAdmin.removeDomain(id);
        return Result.ok();
    }
}
