package com.photogai.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.photogai.modules.ai.dto.QuoteResponse;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * LLM 客户端：对接 OpenAI 兼容 {@code /v1/chat/completions}（默认 DeepSeek）。
 *
 * <p>密钥缺失时抛出 {@link IllegalStateException}，由上层降级为纯规则计算，不崩溃。
 */
@Slf4j
@Component
public class LlmClient {

    private static final String SYSTEM_PROMPT = "你是专业摄影报价顾问。根据用户输入返回严格 JSON："
            + "{\"priceLow\":number,\"priceHigh\":number,\"basis\":string,\"script\":string}。"
            + "价格单位元；priceLow<=priceHigh；script 是给客户的报价话术（中文，亲切专业）。";

    /** 安全输入上限（字符近似，远低于常见模型上下文，避免上游 400 input length too long）。 */
    private static final int MAX_PROMPT_CHARS = 8000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.base-url}")
    private String baseUrl;

    @Value("${deepseek.api-key}")
    private String apiKey;

    @Value("${deepseek.model}")
    private String model;

    public LlmClient(RestClient llmRestClient) {
        this.restClient = llmRestClient;
    }

    /**
     * 调用 LLM 生成报价。
     *
     * @param prompt 由 {@code AiQuoteService} 基于规则系数拼装的用户级 Prompt
     * @throws IllegalStateException 当 api-key 未配置（用于触发规则降级）
     */
    public QuoteResponse complete(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM api-key 未配置，降级为规则计算");
        }

        String safePrompt = guardPromptLength(SYSTEM_PROMPT, prompt);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", safePrompt)),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.7);

        String raw;
        try {
            raw = restClient.post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("LLM 上游调用失败：HTTP {}", status);
            throw new IllegalStateException("LLM 上游服务异常（HTTP " + status + "），已降级");
        }

        return parse(raw);
    }

    /**
     * 自由文本对话（沟通助手 / 复购话术复用）：返回模型消息正文（非 JSON）。
     *
     * <p>密钥缺失或调用/解析失败时抛出 {@link IllegalStateException}，由上层降级为规则模板。
     */
    public String chat(String systemPrompt, String userPrompt) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("LLM api-key 未配置，降级为规则模板");
        }

        String safeUser = guardPromptLength(systemPrompt, userPrompt);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", safeUser)),
                "temperature", 0.7);

        String raw;
        try {
            raw = restClient.post()
                    .uri(baseUrl + "/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            log.warn("LLM 上游调用失败：HTTP {}", status);
            throw new IllegalStateException("LLM 上游服务异常（HTTP " + status + "），已降级");
        }

        return extractContent(raw);
    }

    /**
     * 输入长度守卫：以字符数近似 token，超出安全上限时截断 user 内容（system prompt 保持完整）。
     *
     * <p>目的是从源头避免上游返回 {@code 400 input length too long}。
     *
     * @param system system prompt（不截断，仅计入总长度）
     * @param user   用户级 prompt（超长时被截断）
     * @return 可安全发送的 user prompt
     */
    private String guardPromptLength(String system, String user) {
        int total = (system == null ? 0 : system.length()) + (user == null ? 0 : user.length());
        if (total <= MAX_PROMPT_CHARS) {
            return user;
        }
        log.warn("LLM 输入超长（{} 字符），已截断至安全上限以避免上游 400", total);
        int budget = Math.max(0, MAX_PROMPT_CHARS - (system == null ? 0 : system.length()));
        return (user == null || user.length() <= budget) ? user : user.substring(0, budget);
    }

    private String extractContent(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
            }
            return content;
        } catch (Exception e) {
            throw new IllegalStateException("LLM 响应解析失败：" + e.getMessage(), e);
        }
    }

    private QuoteResponse parse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText();
            // 兼容 ```json 包裹
            content = content.trim();
            if (content.startsWith("```")) {
                content = content.replaceAll("^```[a-zA-Z]*", "").replaceAll("```$", "").trim();
            }
            JsonNode node = objectMapper.readTree(content);
            return QuoteResponse.builder()
                    .priceLow(node.path("priceLow").decimalValue())
                    .priceHigh(node.path("priceHigh").decimalValue())
                    .basis(node.path("basis").asText())
                    .script(node.path("script").asText())
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("LLM 响应解析失败：" + e.getMessage(), e);
        }
    }
}
