package cn.aslight.workhub.service.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Chat Completions 兼容客户端，覆盖 OpenRouter 和兼容协议供应商。
 */
@Component
public class OpenAiChatClient {

    private final ObjectMapper objectMapper;

    public OpenAiChatClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String execute(AiApiInvocation invocation) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", requireText(invocation.model(), "AI 模型不能为空"));
        body.put("messages", List.of(Map.of(
                "role", "user",
                "content", AiHttpPayloadSupport.chatContent(invocation.prompt(), invocation.imagePaths())
        )));
        String reasoning = AiHttpPayloadSupport.normalizeReasoning(invocation.reasoningLevel());
        if (reasoning != null) {
            body.put("reasoning_effort", reasoning);
        }
        if ("FAST".equalsIgnoreCase(invocation.speedMode())) {
            body.put("service_tier", "priority");
        }
        if (invocation.outputSchema() != null && !invocation.outputSchema().isBlank()) {
            Object schema = objectMapper.readTree(invocation.outputSchema());
            body.put("response_format", Map.of(
                    "type", "json_schema",
                    "json_schema", Map.of(
                            "name", "structured_output",
                            "strict", true,
                            "schema", schema
                    )
            ));
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(AiHttpPayloadSupport.endpoint(
                        invocation.provider().getApiBaseUrl(), "/chat/completions")))
                .header("Authorization", "Bearer " + requireText(invocation.apiKey(), "AI Provider API Key 不能为空"))
                .header("Content-Type", "application/json");
        if (invocation.provider().getSiteUrl() != null && !invocation.provider().getSiteUrl().isBlank()) {
            requestBuilder.header("HTTP-Referer", invocation.provider().getSiteUrl().trim());
        }
        if (invocation.provider().getAppName() != null && !invocation.provider().getAppName().isBlank()) {
            requestBuilder.header("X-OpenRouter-Title", invocation.provider().getAppName().trim());
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = AiHttpExecutor.send(request, invocation.provider(), invocation.timeoutSeconds());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Chat Completions HTTP " + response.statusCode() + ": " + safeError(response.body()));
        }
        JsonNode content = objectMapper.readTree(response.body()).path("choices").path(0).path("message").path("content");
        if (content.isMissingNode() || content.asText().isBlank()) {
            throw new IllegalStateException("Chat Completions 未返回 message.content");
        }
        return content.asText();
    }

    private int positive(Integer value, int defaultValue) {
        return value != null && value > 0 ? value : defaultValue;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String safeError(String value) {
        if (value == null || value.isBlank()) {
            return "无响应正文";
        }
        try {
            String message = objectMapper.readTree(value).path("error").path("message").asText();
            if (!message.isBlank()) {
                String normalized = message.replaceAll("\\s+", " ").trim();
                return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
            }
        } catch (Exception ignored) {
            // 非 JSON 错误正文不透传，避免供应商回显提示词或凭据。
        }
        return "上游返回错误";
    }
}
