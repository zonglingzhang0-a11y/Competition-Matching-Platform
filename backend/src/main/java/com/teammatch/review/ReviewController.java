package com.teammatch.review;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.review.ReviewDtos.CreditView;
import com.teammatch.review.ReviewDtos.FinishForm;
import com.teammatch.review.ReviewDtos.MyReport;
import com.teammatch.review.ReviewDtos.ReportForm;
import com.teammatch.review.ReviewDtos.RoundForm;
import com.teammatch.review.ReviewDtos.RoundSummary;
import com.teammatch.review.ReviewDtos.SubmitForm;
import com.teammatch.review.ReviewDtos.SubmitResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学生端：比赛结束、互评、我的信用、举报。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;
    private final ReportService reportService;

    /** 队长标记比赛结束，返回新开的互评 id（单人队没有互评，返回空） */
    @PostMapping("/teams/{id}/finish")
    public Result<Long> finish(@PathVariable long id, @Valid @RequestBody FinishForm form) {
        return Result.ok(reviewService.finishTeam(StpUtil.getLoginIdAsLong(), id, form.award()));
    }

    @GetMapping("/reviews/mine")
    public Result<List<RoundSummary>> myRounds() {
        return Result.ok(reviewService.mine(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/reviews/{id}")
    public Result<RoundForm> form(@PathVariable long id) {
        return Result.ok(reviewService.form(StpUtil.getLoginIdAsLong(), id));
    }

    @PutMapping("/reviews/{id}")
    public Result<SubmitResult> submit(@PathVariable long id, @Valid @RequestBody SubmitForm form) {
        return Result.ok(reviewService.submit(StpUtil.getLoginIdAsLong(), id, form.reviews()));
    }

    @GetMapping("/credit/me")
    public Result<CreditView> myCredit() {
        return Result.ok(reviewService.myCredit(StpUtil.getLoginIdAsLong()));
    }

    @PostMapping("/reports")
    public Result<Long> report(@Valid @RequestBody ReportForm form) {
        return Result.ok(reportService.create(StpUtil.getLoginIdAsLong(), form));
    }

    @GetMapping("/reports/mine")
    public Result<List<MyReport>> myReports() {
        return Result.ok(reportService.mine(StpUtil.getLoginIdAsLong()));
    }
}
