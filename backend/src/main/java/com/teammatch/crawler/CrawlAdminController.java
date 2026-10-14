package com.teammatch.crawler;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.BizException;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.crawler.CrawlAdminService.Tab;
import com.teammatch.crawler.CrawlDtos.ApproveForm;
import com.teammatch.crawler.CrawlDtos.PreviewResult;
import com.teammatch.crawler.CrawlDtos.RecordDetail;
import com.teammatch.crawler.CrawlDtos.RecordRow;
import com.teammatch.crawler.CrawlDtos.RunStatus;
import com.teammatch.crawler.CrawlDtos.SourceForm;
import com.teammatch.crawler.CrawlDtos.SourceRow;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端：爬虫抓到的比赛通知和抓取来源。
 */
@RestController
@RequestMapping("/api/admin/crawl")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class CrawlAdminController {

    private final CrawlAdminService adminService;
    private final CrawlService crawlService;

    @GetMapping("/records")
    public Result<PageResult<RecordRow>> records(
            @RequestParam(defaultValue = "PENDING") Tab tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(adminService.records(tab, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @GetMapping("/records/pending-count")
    public Result<Long> pendingCount() {
        return Result.ok(adminService.pendingCount());
    }

    @GetMapping("/records/{id}")
    public Result<RecordDetail> detail(@PathVariable long id) {
        return Result.ok(adminService.detail(id));
    }

    /** 返回生成或更新的届次 id */
    @PostMapping("/records/{id}/approve")
    public Result<Long> approve(@PathVariable long id, @Valid @RequestBody ApproveForm form) {
        return Result.ok(adminService.approve(StpUtil.getLoginIdAsLong(), id, form));
    }

    @PostMapping("/records/{id}/ignore")
    public Result<Void> ignore(@PathVariable long id) {
        adminService.ignore(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @GetMapping("/sources")
    public Result<List<SourceRow>> sources() {
        return Result.ok(adminService.sources());
    }

    @PostMapping("/sources")
    public Result<Long> createSource(@Valid @RequestBody SourceForm form) {
        return Result.ok(adminService.createSource(form));
    }

    @PutMapping("/sources/{id}")
    public Result<Void> updateSource(@PathVariable long id, @Valid @RequestBody SourceForm form) {
        adminService.updateSource(id, form);
        return Result.ok();
    }

    @DeleteMapping("/sources/{id}")
    public Result<Void> deleteSource(@PathVariable long id) {
        adminService.deleteSource(id);
        return Result.ok();
    }

    /** 试抓：只看列表页里哪些链接会被抓，不保存 */
    @PostMapping("/sources/preview")
    public Result<PreviewResult> preview(@Valid @RequestBody SourceForm form) {
        return Result.ok(crawlService.preview(form));
    }

    /** 马上抓全部启用的来源（在后台跑，进度看 /status） */
    @PostMapping("/run")
    public Result<Void> runAll() {
        requireStarted(crawlService.startAll());
        return Result.ok();
    }

    @PostMapping("/sources/{id}/run")
    public Result<Void> runOne(@PathVariable long id) {
        requireStarted(crawlService.startOne(id));
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<RunStatus> status() {
        return Result.ok(crawlService.status());
    }

    private static void requireStarted(boolean started) {
        if (!started) {
            throw new BizException(409, "正在抓取，等这一次结束再试");
        }
    }
}
