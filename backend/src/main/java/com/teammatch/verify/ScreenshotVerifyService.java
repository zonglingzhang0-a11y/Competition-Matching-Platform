package com.teammatch.verify;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.teammatch.common.BizException;
import com.teammatch.school.School;
import com.teammatch.school.SchoolMapper;
import com.teammatch.user.SysUser;
import com.teammatch.user.SysUserMapper;
import com.teammatch.user.UserProfile;
import com.teammatch.user.UserProfileMapper;
import com.teammatch.user.UserService;
import com.teammatch.verify.ScreenshotMatcher.Claimed;
import com.teammatch.verify.ScreenshotMatcher.Matched;
import com.teammatch.verify.VerifyDtos.ScreenshotResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 截图认证：没有教育邮箱的同学上传学信网学籍页截图或学生证照片。
 * <ol>
 *   <li>本地 OCR 认出图里的文字，和学生填的姓名、学校、入学年份比对，结果记成"一致 / 有出入 / 没认出来"</li>
 *   <li>进认证审核队列，管理员看原图和比对结果，选定学校后通过或驳回</li>
 *   <li>审核完删除原图，只留认出来的文字</li>
 * </ol>
 * 截图可以 P，所以比对结果只是帮管理员省力，最终人工审核。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScreenshotVerifyService {

    /** 和 application.yml 里的 spring.servlet.multipart.max-file-size 一致 */
    static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String SUBDIR = "verify";

    private final VerificationRequestMapper requestMapper;
    private final SysUserMapper userMapper;
    private final UserProfileMapper profileMapper;
    private final UserService userService;
    private final SchoolMapper schoolMapper;
    private final OcrService ocrService;
    private final ScreenshotProperties props;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    /** 存在 ocr_result 里的东西：学生填的、比对结果、认出来的文字、图里出现的已收录学校 */
    public record OcrRecord(Claimed claimed, Matched matched, List<String> lines, Long suggestedSchoolId) {
    }

    public record ImageData(byte[] bytes, String contentType) {
    }

    public ScreenshotResult submit(long userId, MultipartFile file, String realName, String schoolName, Integer enrollYear) {
        Claimed claimed = checkForm(realName, schoolName, enrollYear);
        byte[] bytes = readImage(file);
        String ext = imageExt(bytes);
        LocalDateTime now = LocalDateTime.now(clock);
        long today = requestMapper.selectCount(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getUserId, userId)
                .eq(VerificationRequest::getMethod, VerificationRequest.Method.SCREENSHOT)
                .ge(VerificationRequest::getCreatedAt, now.toLocalDate().atStartOfDay()));
        if (today >= props.dailyLimit()) {
            throw new BizException(429, "今天已经提交 " + today + " 次了，明天再试吧");
        }

        // 识别要一两秒，放在事务外面，不占数据库连接
        List<String> lines = ocrService.recognize(bytes, ext);
        Matched matched = lines == null ? null : ScreenshotMatcher.match(lines, claimed);
        School suggested = lines == null ? null
                : ScreenshotMatcher.suggestSchool(lines, schoolMapper.selectList(null), School::getName);
        String confidence = matched == null ? null : matched.all() ? "HIGH" : "LOW";

        String relative = SUBDIR + "/" + UUID.randomUUID() + "." + ext;
        Path target = root().resolve(relative);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, bytes);
        } catch (IOException e) {
            log.error("截图保存失败", e);
            throw new BizException(500, "图片保存失败，请稍后再试");
        }

        try {
            Boolean stillVerified = tx.execute(status -> {
                supersedePending(userId, now);
                VerificationRequest r = new VerificationRequest();
                r.setUserId(userId);
                r.setMethod(VerificationRequest.Method.SCREENSHOT);
                r.setStatus(VerificationRequest.Status.PENDING);
                r.setImagePath(relative);
                r.setOcrResult(json.writeValueAsString(new OcrRecord(claimed, matched, lines,
                        suggested == null ? null : suggested.getId())));
                r.setOcrConfidence(confidence);
                requestMapper.insert(r);

                // 填的姓名和入学年份顺便存进档案，管理员审核和队友都能看到
                profileMapper.update(new LambdaUpdateWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId)
                        .set(UserProfile::getRealName, claimed.realName())
                        .set(UserProfile::getEnrollYear, claimed.enrollYear()));

                SysUser user = userService.getById(userId);
                boolean verified = userService.isVerified(user);
                if (!verified) {
                    userMapper.update(new LambdaUpdateWrapper<SysUser>()
                            .eq(SysUser::getId, userId)
                            .set(SysUser::getVerifyStatus, SysUser.VerifyStatus.PENDING));
                }
                return verified;
            });
            return new ScreenshotResult(Boolean.TRUE.equals(stillVerified) ? SysUser.VerifyStatus.VERIFIED
                    : SysUser.VerifyStatus.PENDING, confidence, matched, message(matched, Boolean.TRUE.equals(stillVerified)));
        } catch (RuntimeException e) {
            deleteQuietly(target);
            throw e;
        }
    }

    /**
     * 以前还在排队的申请作废，一个人同时只留一条待审核；作废的截图顺便删掉。
     * 教育邮箱认证提交时也调用。调用方负责事务。
     */
    public void supersedePending(long userId, LocalDateTime now) {
        List<VerificationRequest> pending = requestMapper.selectList(new LambdaQueryWrapper<VerificationRequest>()
                .eq(VerificationRequest::getUserId, userId)
                .eq(VerificationRequest::getStatus, VerificationRequest.Status.PENDING));
        for (VerificationRequest old : pending) {
            requestMapper.update(new LambdaUpdateWrapper<VerificationRequest>()
                    .eq(VerificationRequest::getId, old.getId())
                    .set(VerificationRequest::getStatus, VerificationRequest.Status.REJECTED)
                    .set(VerificationRequest::getReviewNote, "已重新提交认证")
                    .set(VerificationRequest::getReviewedAt, now));
            deleteImage(old);
        }
    }

    /** 管理员审核时看原图 */
    public ImageData image(long requestId) {
        VerificationRequest r = requestMapper.selectById(requestId);
        if (r == null || r.getImagePath() == null) {
            throw new BizException(404, "这张图已经删除了（审核完会删掉原图）");
        }
        try {
            byte[] bytes = Files.readAllBytes(root().resolve(r.getImagePath()));
            return new ImageData(bytes, r.getImagePath().endsWith(".png") ? "image/png" : "image/jpeg");
        } catch (IOException e) {
            throw new BizException(404, "原图找不到了");
        }
    }

    /** 审核完删除原图，只留认出来的文字 */
    public void deleteImage(VerificationRequest r) {
        if (r.getImagePath() == null) {
            return;
        }
        deleteQuietly(root().resolve(r.getImagePath()));
        requestMapper.update(new LambdaUpdateWrapper<VerificationRequest>()
                .eq(VerificationRequest::getId, r.getId())
                .set(VerificationRequest::getImagePath, null));
    }

    public OcrRecord parse(String ocrResult) {
        return ocrResult == null ? null : json.readValue(ocrResult, OcrRecord.class);
    }

    // ---------- 内部 ----------

    private static Claimed checkForm(String realName, String schoolName, Integer enrollYear) {
        String name = realName == null ? "" : realName.trim();
        String school = schoolName == null ? "" : schoolName.trim();
        if (name.length() < 2 || name.length() > 32) {
            throw new BizException("请填写真实姓名（2 到 32 个字），要和图片上的一致");
        }
        if (school.length() < 2 || school.length() > 64) {
            throw new BizException("请填写学校全称，要和图片上的一致");
        }
        int thisYear = Year.now().getValue();
        if (enrollYear == null || enrollYear < thisYear - 12 || enrollYear > thisYear + 1) {
            throw new BizException("请填写入学年份");
        }
        return new Claimed(name, school, enrollYear);
    }

    private static byte[] readImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("请选择一张图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BizException("图片太大了，最多 5 MB");
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BizException("图片读取失败，请重新选择");
        }
    }

    /** 按文件头判断格式，不信文件名和浏览器给的类型 */
    static String imageExt(byte[] b) {
        if (b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "png";
        }
        if (b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        throw new BizException("只支持 jpg、png 格式的图片");
    }

    private static String message(Matched m, boolean stillVerified) {
        String keep = stillVerified ? "审核期间你仍保留原来的认证。" : "";
        if (m == null) {
            return "已提交，管理员会人工审核。" + keep;
        }
        if (m.all()) {
            return "已提交。图片上认出了你填的姓名、学校和入学年份，管理员会尽快审核。" + keep;
        }
        List<String> missing = new ArrayList<>();
        if (!m.realName()) {
            missing.add("姓名");
        }
        if (!m.schoolName()) {
            missing.add("学校");
        }
        if (!m.enrollYear()) {
            missing.add("入学年份");
        }
        return "已提交。图片上没能认出你填的" + String.join("、", missing) + "，管理员会人工核对。"
                + "如果是填错了，可以改好重新提交。" + keep;
    }

    private Path root() {
        return Path.of(props.uploadDir()).toAbsolutePath();
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            log.warn("删除截图失败：{}", p, e);
        }
    }
}
