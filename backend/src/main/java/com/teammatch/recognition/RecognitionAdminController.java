package com.teammatch.recognition;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.teammatch.common.PageResult;
import com.teammatch.common.Result;
import com.teammatch.recognition.RecognitionDtos.AdminRow;
import com.teammatch.recognition.RecognitionDtos.RejectForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端：查看各校的加分认定，驳回乱填的。确认靠同校同学，管理员不用一条条审。
 */
@RestController
@RequestMapping("/api/admin/recognitions")
@RequiredArgsConstructor
@SaCheckRole("ADMIN")
public class RecognitionAdminController {

    private final RecognitionService recognitionService;

    /** status 不传就是全部；keyword 按学校名、比赛名搜 */
    @GetMapping
    public Result<PageResult<AdminRow>> list(
            @RequestParam(required = false) Recognition.Status status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.ok(recognitionService.adminList(status, keyword, Math.max(page, 1), Math.clamp(size, 1, 50)));
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable long id, @Valid @RequestBody RejectForm form) {
        recognitionService.reject(id, form.note());
        return Result.ok();
    }

    @PostMapping("/{id}/restore")
    public Result<Void> restore(@PathVariable long id) {
        recognitionService.restore(id);
        return Result.ok();
    }
}
