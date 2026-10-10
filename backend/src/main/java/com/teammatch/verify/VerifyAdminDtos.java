package com.teammatch.verify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 认证审核接口的请求和返回。
 */
public final class VerifyAdminDtos {

    private VerifyAdminDtos() {
    }

    /**
     * 审核列表的一行。
     *
     * @param domains 这个邮箱可以收录的后缀，从长到短，比如 [mail.stu.xxx.edu.cn, stu.xxx.edu.cn, xxx.edu.cn]
     */
    public record ReviewItem(
            Long id,
            VerificationRequest.Method method,
            VerificationRequest.Status status,
            String eduEmail,
            List<String> domains,
            ReviewUser user,
            SchoolRef school,
            String reviewer,
            String reviewNote,
            LocalDateTime reviewedAt,
            LocalDateTime createdAt) {
    }

    /** 申请人。真实姓名、专业、入学年份来自个人档案，审核时对照用 */
    public record ReviewUser(Long id, String nickname, String email, String realName, String major, Integer enrollYear) {
    }

    public record SchoolRef(Long id, String name) {
    }

    /**
     * 通过：选已有的学校（schoolId），或者新建一所（newSchoolName + 省市），二选一。
     * domain 是要收录的邮箱后缀，必须是申请邮箱的后缀之一。
     */
    public record ApproveForm(
            Long schoolId,
            @Size(max = 64, message = "学校名称最多 64 个字") String newSchoolName,
            @Size(max = 16, message = "省份最多 16 个字") String province,
            @Size(max = 32, message = "城市最多 32 个字") String city,
            @NotBlank(message = "请选择要收录的邮箱后缀") String domain,
            @Size(max = 255, message = "备注最多 255 个字") String note) {
    }

    public record RejectForm(
            @NotBlank(message = "请写明不通过的原因") @Size(max = 255, message = "原因最多 255 个字") String note) {
    }

    /** alsoApproved：收录后缀后，顺带自动通过的其他同校申请条数 */
    public record ApproveResult(String schoolName, int alsoApproved) {
    }
}
