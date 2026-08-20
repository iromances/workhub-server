package cn.aslight.workhub.service.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AiHttpPayloadSupport {

    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;

    private AiHttpPayloadSupport() {
    }

    static Object responsesInput(String prompt, List<String> imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return prompt;
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "input_text", "text", prompt));
        for (String imagePath : imagePaths) {
            content.add(Map.of("type", "input_image", "image_url", dataUrl(imagePath)));
        }
        return List.of(Map.of("role", "user", "content", content));
    }

    static Object chatContent(String prompt, List<String> imagePaths) throws IOException {
        if (imagePaths == null || imagePaths.isEmpty()) {
            return prompt;
        }
        List<Map<String, Object>> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        for (String imagePath : imagePaths) {
            content.add(Map.of(
                    "type", "image_url",
                    "image_url", Map.of("url", dataUrl(imagePath))
            ));
        }
        return content;
    }

    static Map<String, Object> jsonSchemaFormat(Object schema) {
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "structured_output");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }

    static String endpoint(String baseUrl, String suffix) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("AI Provider Base URL 不能为空");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(suffix)) {
            return normalized;
        }
        return normalized + suffix;
    }

    static String normalizeReasoning(String reasoningLevel) {
        if (reasoningLevel == null || reasoningLevel.isBlank()) {
            return null;
        }
        return switch (reasoningLevel.trim().toUpperCase()) {
            case "NONE" -> "none";
            case "LOW" -> "low";
            case "MEDIUM" -> "medium";
            case "HIGH" -> "high";
            case "ULTRA", "XHIGH" -> "xhigh";
            case "MAX" -> "max";
            default -> throw new IllegalArgumentException("不支持的 AI 推理强度: " + reasoningLevel);
        };
    }

    private static String dataUrl(String imagePath) throws IOException {
        if (imagePath == null || imagePath.isBlank()) {
            throw new IllegalArgumentException("AI 图片路径不能为空");
        }
        Path path = Path.of(imagePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("AI 图片不存在: " + path.getFileName());
        }
        long size = Files.size(path);
        if (size <= 0 || size > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("AI 图片大小必须在 1 字节到 10MB 之间: " + path.getFileName());
        }
        String contentType = Files.probeContentType(path);
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("AI 输入文件不是图片: " + path.getFileName());
        }
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(Files.readAllBytes(path));
    }
}
