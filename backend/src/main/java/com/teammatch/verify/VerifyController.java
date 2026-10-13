package com.teammatch.verify;

import cn.dev33.satoken.stp.StpUtil;
import com.teammatch.common.Result;
import com.teammatch.verify.VerifyDtos.EduCodeRequest;
import com.teammatch.verify.VerifyDtos.EduConfirmRequest;
import com.teammatch.verify.VerifyDtos.LatestRequest;
import com.teammatch.verify.VerifyDtos.ScreenshotResult;
import com.teammatch.verify.VerifyDtos.VerifyResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final EduVerifyService eduVerifyService;
    private final ScreenshotVerifyService screenshotService;

    /** 最近一次认证申请，没有时 data 为空 */
    @GetMapping("/latest")
    public Result<LatestRequest> latest() {
        return Result.ok(eduVerifyService.latest(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 截图认证：上传学信网学籍页截图或学生证照片（jpg / png，5 MB 以内），
     * 加上和图片上一致的真实姓名、学校全称、入学年份。
     */
    @PostMapping(value = "/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ScreenshotResult> screenshot(
            @RequestPart("file") MultipartFile file,
            @RequestParam String realName,
            @RequestParam String schoolName,
            @RequestParam Integer enrollYear) {
        return Result.ok(screenshotService.submit(StpUtil.getLoginIdAsLong(), file, realName, schoolName, enrollYear));
    }

    /** 往教育邮箱发验证码 */
    @PostMapping("/edu/code")
    public Result<Void> sendEduCode(@Valid @RequestBody EduCodeRequest req) {
        eduVerifyService.sendCode(StpUtil.getLoginIdAsLong(), req.eduEmail());
        return Result.ok();
    }

    /** 填验证码完成认证 */
    @PostMapping("/edu/confirm")
    public Result<VerifyResult> confirmEdu(@Valid @RequestBody EduConfirmRequest req) {
        return Result.ok(eduVerifyService.confirm(StpUtil.getLoginIdAsLong(), req.eduEmail(), req.code()));
    }
}
