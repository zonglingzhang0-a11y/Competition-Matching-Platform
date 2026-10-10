package com.teammatch.verify;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("verification_request")
public class VerificationRequest {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Method method;
    private Status status;
    private Long schoolId;
    private String eduEmail;
    private String imagePath;
    private String ocrResult;
    private String ocrConfidence;
    private Long reviewerId;
    private String reviewNote;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    public enum Method { EDU_EMAIL, SCREENSHOT }

    public enum Status { PENDING, APPROVED, REJECTED }
}
