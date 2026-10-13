package com.teammatch.verify;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 截图认证的配置（application.yml 里的 app.screenshot）。
 *
 * @param uploadDir  原图存放目录，相对路径相对于启动目录。审核完就删
 * @param ocrEnabled 关掉就只存图、不识别，全靠管理员人工看
 * @param dailyLimit 每人每天最多提交几次（识别一次要占一两秒 CPU）
 */
@ConfigurationProperties(prefix = "app.screenshot")
public record ScreenshotProperties(String uploadDir, Boolean ocrEnabled, Integer dailyLimit) {

    public ScreenshotProperties {
        uploadDir = uploadDir == null || uploadDir.isBlank() ? "uploads" : uploadDir;
        ocrEnabled = ocrEnabled == null || ocrEnabled;
        dailyLimit = dailyLimit == null || dailyLimit < 1 ? 3 : dailyLimit;
    }
}
