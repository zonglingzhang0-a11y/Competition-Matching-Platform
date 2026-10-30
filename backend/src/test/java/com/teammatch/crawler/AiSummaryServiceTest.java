package com.teammatch.crawler;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 信息渠道里同一个网址只列一次 */
class AiSummaryServiceTest {

    @Test
    void 不管协议_www_和末尾斜杠都算同一个网址() {
        String key = AiSummaryService.sameSite("https://www.mcm.edu.cn");
        assertThat(AiSummaryService.sameSite("https://www.mcm.edu.cn/")).isEqualTo(key);
        assertThat(AiSummaryService.sameSite("http://mcm.edu.cn//")).isEqualTo(key);
        assertThat(AiSummaryService.sameSite(" HTTPS://WWW.MCM.EDU.CN ")).isEqualTo(key);
    }

    @Test
    void 路径不同的不算同一个() {
        assertThat(AiSummaryService.sameSite("https://www.mcm.edu.cn/notice/1"))
                .isNotEqualTo(AiSummaryService.sameSite("https://www.mcm.edu.cn"));
    }
}
