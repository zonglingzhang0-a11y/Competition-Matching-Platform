package com.teammatch.crawler;

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
 * 调大模型（OpenAI 兼容的 /chat/completions，配置见 {@link LlmProperties}）：爬虫补抽字段、比赛页的 AI 总结都用它。
 * 本机的小模型、DeepSeek 之类的云端模型都行；一次只发一个请求，等回答回来。
 */
@Component
public class LlmClient {

    private final LlmProperties props;
    private final JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public LlmClient(LlmProperties props, JsonMapper json) {
        this.props = props;
        this.json = json;
    }

    /** 打开了并且填了地址 */
    public boolean ready() {
        return props.ready();
    }

    public String model() {
        return props.model();
    }

    public int maxChars() {
        return props.maxChars();
    }

    /** 返回大模型回答的文字；连不上、超时、HTTP 出错时抛 IOException */
    public String chat(String system, String user, int maxTokens) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", props.model());
        body.put("temperature", 0);
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
        HttpResponse<byte[]> res = client.send(req.build(), HttpResponse.BodyHandlers.ofByteArray());
        // 本机引擎的响应头不带 charset，自己按 UTF-8 解
        String raw = new String(res.body(), StandardCharsets.UTF_8);
        if (res.statusCode() != 200) {
            throw new IOException("HTTP " + res.statusCode() + " " + (raw.length() > 200 ? raw.substring(0, 200) : raw));
        }
        try {
            return json.readTree(raw).path("choices").path(0).path("message").path("content").asString("");
        } catch (JacksonException e) {
            throw new IOException("接口返回的不是 JSON");
        }
    }
}
