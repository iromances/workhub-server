package cn.aslight.workhub.service.ai;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI Responses 协议客户端。
 */
@Component
public class OpenAiResponsesClient {

    private final ObjectMapper objectMapper;

    public OpenAiResponsesClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String execute(AiApiInvocation invocation) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", requireText(invocation.model(), "AI 模型不能为空"));
        body.put("input", AiHttpPayloadSupport.responsesInput(invocation.prompt(), invocation.imagePaths()));
        body.put("store", false);
        String reasoning = AiHttpPayloadSupport.normalizeReasoning(invocation.reasoningLevel());
        if (reasoning != null) {
            body.put("reasoning", Map.of("effort", reasoning));
        }
        if ("FAST".equalsIgnoreCase(invocation.speedMode())) {
            body.put("service_tier", "priority");
        }
        if (invocation.outputSchema() != null && !invocation.outputSchema().isBlank()) {
            Object schema = objectMapper.readTree(invocation.outputSchema());
            body.put("text", Map.of("format", AiHttpPayloadSupport.jsonSchemaFormat(schema)));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(AiHttpPayloadSupport.endpoint(
                        invocation.provider().getApiBaseUrl(), "/responses")))
                .header("Authorization", "Bearer " + requireText(invocation.apiKey(), "AI Provider API Key 不能为空"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = AiHttpExecutor.send(request, invocation.provider(), invocation.timeoutSeconds());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Responses API HTTP " + response.statusCode() + ": " + safeError(response.body()));
        }
        return parseOutputText(response.body());
    }

    String parseOutputText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        List<String> texts = new ArrayList<>();
        JsonNode output = root.path("output");
        if (output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText()) && !part.path("text").asText().isBlank()) {
                        texts.add(part.path("text").asText());
                    }
                }
            }
        }
        if (texts.isEmpty()) {
            throw new IllegalStateException("Responses API 未返回 output_text");
        }
        return String.join("\n", texts);
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
