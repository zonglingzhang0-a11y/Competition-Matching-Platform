package com.teammatch.review;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.review.ReviewDtos.FlaggedRow;
import com.teammatch.review.ReviewDtos.HandleForm;
import com.teammatch.review.ReviewDtos.ReportRow;
import com.teammatch.review.ReviewDtos.SuspendedRow;
import com.teammatch.review.ReviewEntities.ReportStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：举报处理、信用复核（被暂停推荐的人、被标记异常的互评）。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class ReviewAdminController {

    private final ReportService reportService;

    /** status 不传就是全部 */
    @GetMapping("/reports")
    public Result<PageResult<ReportRow>> reports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(reportService.list(status, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/reports/pending-count")
    public Result<Long> pendingReports() {
        return Result.ok(reportService.pendingCount());
    }

    @PostMapping("/reports/{id}/handle")
    public Result<Void> handle(@PathVariable long id, @Valid @RequestBody HandleForm form) {
        reportService.handle(StpUtil.getLoginIdAsLong(), id, form);
        return Result.ok();
    }

    @GetMapping("/credit/suspended")
    public Result<List<SuspendedRow>> suspended() {
        return Result.ok(reportService.suspended());
    }

    @PostMapping("/credit/{userId}/lift")
    public Result<Void> lift(@PathVariable long userId) {
        reportService.lift(userId);
        return Result.ok();
    }

    @GetMapping("/reviews/flagged")
    public Result<List<FlaggedRow>> flagged() {
        return Result.ok(reportService.flagged());
    }
}
