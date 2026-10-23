package com.teammatch.ai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 调 OpenAI 兼容的 chat/completions 接口。只负责把一段对话发出去、把回答的正文取回来；
 * 提示词、JSON 解析、证据核对都在 {@link AiAnalysisService} 和 {@link AiReplyParser} 里。
 */
@Slf4j
@Component
public class AiClient {

    private final AiProperties props;
    private final JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public AiClient(AiProperties props, JsonMapper json) {
        this.props = props;
        this.json = json;
    }

    public boolean ready() {
        return props.ready();
    }

    public String model() {
        return props.model();
    }

    /**
     * @return 模型回答的正文；接口报错、超时、返回的不是 JSON 时抛 IOException，调用方退回本地规则
     */
    public String chat(String system, String user, int maxTokens) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("temperature", 0.2);
        body.put("max_tokens", maxTokens);
        body.put("messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(props.baseUrl() + "/chat/completions"))
                .timeout(Duration.ofMillis(props.timeoutMs()))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body), StandardCharsets.UTF_8));
        if (props.apiKey() != null && !props.apiKey().isBlank()) {
            req.header("Authorization", "Bearer " + props.apiKey().strip());
        }
        HttpResponse<byte[]> res;
        try {
            res = client.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("请求被中断");
        }
        // 有些本机引擎的响应头不带 charset，统一按 UTF-8 解
        String raw = new String(res.body(), StandardCharsets.UTF_8);
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " " + cut(raw, 200));
        }
        try {
            String content = json.readTree(raw).path("choices").path(0).path("message").path("content").asString("");
            if (content.isBlank()) {
                throw new IOException("模型返回了空内容");
            }
            return content;
        } catch (JacksonException e) {
            throw new IOException("接口返回的不是 chat/completions 的 JSON");
        }
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
