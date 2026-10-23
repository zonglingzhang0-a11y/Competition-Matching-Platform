package com.teammatch.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 调大模型（OpenAI 兼容的 /chat/completions，配置见 {@link LlmProperties}）：爬虫补抽字段、比赛页的 AI 整理、
 * AI 能力分析都用它。本机的小模型、DeepSeek 之类的云端模型都行。
 * <p>
 * 本机模型一次只能处理一个请求，所以这里同一时间只放一个请求出去，其余的排队；排队超过 wait-ms 就当这次没连上，
 * 各功能照常退回规则。
 */
@Slf4j
@Component
public class LlmClient {

    private final LlmProperties props;
    private final JsonMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Semaphore oneAtATime = new Semaphore(1, true);

    public LlmClient(LlmProperties props, JsonMapper json, Environment env) {
        this.props = props;
        this.json = json;
        if (env.containsProperty("app.crawler.llm.enabled") || env.containsProperty("app.crawler.llm.base-url")) {
            log.warn("大模型配置改名了：app.crawler.llm 下面的设置要挪到 app.llm 下面（爬虫、AI 整理、AI 能力分析共用），旧名字已经不起作用");
        }
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

    /** 返回大模型回答的文字；排队等不到、连不上、超时、HTTP 出错时抛 IOException */
    public String chat(String system, String user, int maxTokens) throws IOException, InterruptedException {
        if (!oneAtATime.tryAcquire(props.waitMs(), TimeUnit.MILLISECONDS)) {
            throw new IOException("大模型正在回答别的请求，排队超过 " + props.waitMs() / 1000 + " 秒");
        }
        try {
            return send(system, user, maxTokens);
        } finally {
            oneAtATime.release();
        }
    }

    private String send(String system, String user, int maxTokens) throws IOException, InterruptedException {
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
