package com.teammatch.recognition;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 加分认定的配置（application.yml 里的 app.recognition）。
 *
 * @param confirmsNeeded 除了提交人，还要几位同校同学确认才对外显示
 */
@ConfigurationProperties(prefix = "app.recognition")
public record RecognitionProperties(Integer confirmsNeeded) {

    public RecognitionProperties {
        confirmsNeeded = confirmsNeeded == null || confirmsNeeded < 1 ? 2 : confirmsNeeded;
    }
}
