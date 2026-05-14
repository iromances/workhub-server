package cn.aslight.workhub.integration.ai;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.model.intake.IntakeAIDraft;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeTaskBreakdownItem;
import cn.aslight.workhub.service.intake.EffortUnitNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
/**
 * AI 草稿生成器。
 */
public class AiDraftGenerator {

    private static final Logger log = LoggerFactory.getLogger(AiDraftGenerator.class);
    private static final String SYSTEM_PROMPT = """
            你是 WorkHub 的待整理箱助手。
            你只能输出严格 JSON，不要输出 Markdown，不要解释。
            JSON 字段固定为：
            titleSuggestion, descriptionSuggestion, typeSuggestion, prioritySuggestion, suggestedProjectCode, acceptanceCriteriaSuggestion, taskBreakdownSuggestions
            约束：
            1. typeSuggestion 只能是 需求、缺陷、运维、任务 四选一
            2. prioritySuggestion 只能是 P1、P2、P3 三选一
            3. 正式工作项不允许使用待整理状态
            4. suggestedProjectCode 尽量给出现有项目编码建议，没有把握时给 PRJ-WH
            5. 必须输出单行合法 JSON，所有字段值都使用双引号字符串；未知时返回空字符串
            6. 不要输出代码块标记，不要输出额外前后缀文字，不要在字符串里保留未转义换行
            7. taskBreakdownSuggestions 必须是数组，每个元素包含 taskName, estimatedEffort, ownerUserName, status, notes
            8. taskBreakdownSuggestions.status 只能使用 待开始、进行中、待验证、已完成 四选一
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiDraftGenerator(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newHttpClient();
    }

    public IntakeAIDraft generate(String rawContent,
                                  String sourceType,
                                  String providerOverride,
                                  IntakeStructuredData structuredData) {
        AiProviderType providerType = providerOverride == null || providerOverride.isBlank()
                ? AiProviderType.fromValue(aiProperties.getProvider())
                : AiProviderType.fromValue(providerOverride);

        try {
            return switch (providerType) {
                case OPENROUTER -> generateByOpenrouter(rawContent, sourceType, structuredData);
                case MINIMAX -> generateByMinimax(rawContent, sourceType, structuredData);
                case HEURISTIC -> heuristicDraft(rawContent, structuredData);
            };
        } catch (Exception ex) {
            log.warn("AI provider call failed, fallback to heuristic draft. provider={}", providerType, ex);
            return heuristicDraft(rawContent, structuredData);
        }
    }

    private IntakeAIDraft generateByOpenrouter(String rawContent,
                                               String sourceType,
                                               IntakeStructuredData structuredData) throws IOException, InterruptedException {
        AiProperties.Openrouter config = aiProperties.getOpenrouter();
        if (!config.isEnabled() || isBlank(config.getApiKey())) {
            throw new IllegalStateException("OpenRouter 未启用或未配置 API Key");
        }

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", config.getModel(),
                "temperature", 0.2,
                "messages", new Object[] {
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", buildUserPrompt(rawContent, sourceType, structuredData))
                }
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(config.getBaseUrl()) + "/chat/completions"))
                .header("Authorization", "Bearer " + config.getApiKey().trim())
                .header("Content-Type", "application/json")
                .header("HTTP-Referer", config.getReferer())
                .header("X-OpenRouter-Title", config.getTitle())
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponseBody(response.body(), config.getModel(), "openrouter");
    }

    private IntakeAIDraft generateByMinimax(String rawContent,
                                            String sourceType,
                                            IntakeStructuredData structuredData) throws IOException, InterruptedException {
        AiProperties.Minimax config = aiProperties.getMinimax();
        if (!config.isEnabled() || isBlank(config.getApiKey())) {
            throw new IllegalStateException("MiniMax 未启用或未配置 API Key");
        }

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "model", config.getModel(),
                "temperature", 0.2,
                "messages", new Object[] {
                        Map.of("role", "system", "name", "WorkHub AI", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "name", "user", "content", buildUserPrompt(rawContent, sourceType, structuredData))
                }
        ));

        HttpRequest request = HttpRequest.newBuilder(URI.create(trimTrailingSlash(config.getBaseUrl()) + "/text/chatcompletion_v2"))
                .header("Authorization", "Bearer " + config.getApiKey().trim())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponseBody(response.body(), config.getModel(), "minimax");
    }

    private IntakeAIDraft parseResponseBody(String responseBody, String model, String provider) throws JacksonException {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.asText().isBlank()) {
            throw new IllegalStateException("AI 响应缺少 message.content");
        }
        String content = contentNode.asText();
        JsonNode contentJson = extractJson(content);
        return new IntakeAIDraft(
                textOrNull(contentJson, "titleSuggestion"),
                textOrNull(contentJson, "descriptionSuggestion"),
                textOrNull(contentJson, "typeSuggestion"),
                textOrNull(contentJson, "prioritySuggestion"),
                textOrNull(contentJson, "suggestedProjectCode"),
                textOrNull(contentJson, "acceptanceCriteriaSuggestion"),
                readTaskBreakdown(contentJson),
                provider,
                model,
                content
        );
    }

    private JsonNode extractJson(String content) throws JacksonException {
        String normalized = normalizeJsonLikeContent(content);
        try {
            return objectMapper.readTree(normalized);
        } catch (JacksonException ignored) {
            int start = normalized.indexOf('{');
            int end = normalized.lastIndexOf('}');
            if (start >= 0 && end > start) {
                return objectMapper.readTree(normalized.substring(start, end + 1));
            }
            throw ignored;
        }
    }

    private String normalizeJsonLikeContent(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.trim();
        if (normalized.startsWith("```")) {
            normalized = normalized.replace("```json", "").replace("```JSON", "").replace("```", "").trim();
        }
        normalized = normalized
                .replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'');
        return normalized;
    }

    private String buildUserPrompt(String rawContent, String sourceType, IntakeStructuredData structuredData) {
        StringBuilder builder = new StringBuilder("""
                来源类型：%s
                原始内容：
                %s
                """.formatted(sourceType, rawContent));
        if (structuredData != null && structuredData.fields() != null && !structuredData.fields().isEmpty()) {
            builder.append("\n结构化字段：\n");
            structuredData.fields().forEach(field -> builder.append("- ")
                    .append(field.label())
                    .append(": ")
                    .append(field.value())
                    .append('\n'));
        }
        if (structuredData != null
                && structuredData.attachmentSummaries() != null
                && !structuredData.attachmentSummaries().isEmpty()) {
            builder.append("\n附件文本摘要：\n");
            structuredData.attachmentSummaries().forEach(summary -> builder.append("- ")
                    .append(summary.fileName())
                    .append(" [")
                    .append(summary.fileType())
                    .append("]: ")
                    .append(summary.summaryText())
                    .append('\n'));
        }
        builder.append("请输出整理建议 JSON。");
        return builder.toString();
    }

    private IntakeAIDraft heuristicDraft(String raw, IntakeStructuredData structuredData) {
        String normalized = raw.toLowerCase();
        String type;
        if (containsAny(raw, normalized, "报错", "异常", "bug", "缺陷", "修复")) {
            type = "缺陷";
        } else if (containsAny(raw, normalized, "运维", "数据库", "告警", "部署", "巡检", "慢 sql", "slow sql")) {
            type = "运维";
        } else if (containsAny(raw, normalized, "任务", "整理", "跟进", "同步")) {
            type = "任务";
        } else {
            type = "需求";
        }

        String priority;
        if (containsAny(raw, normalized, "紧急", "立刻", "尽快", "阻塞", "线上", "告警")) {
            priority = "P1";
        } else if (containsAny(raw, normalized, "本周", "调整", "优化", "补充")) {
            priority = "P2";
        } else {
            priority = "P3";
        }

        String compact = raw.replaceAll("\\s+", " ").trim();
        String title = structuredData != null && !isBlank(structuredData.requirementName())
                ? structuredData.requirementName().trim()
                : compact.length() <= 30 ? compact : compact.substring(0, 30);
        String description = structuredData != null && !isBlank(structuredData.requirementSummary())
                ? structuredData.requirementSummary().trim()
                : raw;
        return new IntakeAIDraft(
                title,
                description,
                type,
                priority,
                buildSuggestedProjectCode(type, structuredData),
                buildAcceptanceCriteria(structuredData),
                buildHeuristicBreakdown(type, structuredData),
                "heuristic",
                null,
                null
        );
    }

    private List<IntakeTaskBreakdownItem> readTaskBreakdown(JsonNode contentJson) {
        JsonNode items = contentJson.path("taskBreakdownSuggestions");
        if (!items.isArray() || items.isEmpty()) {
            return List.of();
        }
        List<IntakeTaskBreakdownItem> suggestions = new ArrayList<>();
        for (JsonNode item : items) {
            String taskName = textOrNull(item, "taskName");
            if (taskName == null) {
                continue;
            }
            suggestions.add(new IntakeTaskBreakdownItem(
                    taskName,
                    EffortUnitNormalizer.normalizeEffort(textOrNull(item, "estimatedEffort")),
                    textOrNull(item, "ownerUserName"),
                    textOrNull(item, "status"),
                    textOrNull(item, "notes")
            ));
        }
        return suggestions;
    }

    private List<IntakeTaskBreakdownItem> buildHeuristicBreakdown(String type, IntakeStructuredData structuredData) {
        String effort = structuredData == null ? null : structuredData.estimatedEffort();
        String plannedDueDate = structuredData == null ? null : structuredData.plannedDueDate();
        List<IntakeTaskBreakdownItem> items = new ArrayList<>();
        items.add(new IntakeTaskBreakdownItem(
                "需求澄清与方案确认",
                effort == null ? "4h" : effort,
                null,
                "待开始",
                structuredData == null ? "结合原始需求和附件澄清范围" : "结合审批字段和附件确认需求边界"
        ));
        items.add(new IntakeTaskBreakdownItem(
                "开发实现与联调",
                "8h",
                null,
                "待开始",
                plannedDueDate == null ? "完成核心逻辑开发并联调" : "预计在 " + plannedDueDate + " 前完成开发和联调"
        ));
        items.add(new IntakeTaskBreakdownItem(
                "验证与上线准备",
                "4h",
                null,
                "待验证",
                "补充回归验证与上线检查项"
        ));
        if ("缺陷".equals(type)) {
            items.set(0, new IntakeTaskBreakdownItem("问题复现与原因定位", "4h", null, "待开始", "先复现场景并确认影响范围"));
        }
        return items;
    }

    private String buildSuggestedProjectCode(String type, IntakeStructuredData structuredData) {
        if ("运维".equals(type)) {
            return "PRJ-OPS";
        }
        return "PRJ-WH";
    }

    private String buildAcceptanceCriteria(IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return "请确认业务背景、验收口径和最终交付边界";
        }
        String requirementName = structuredData.requirementNameOrTitle();
        if (!isBlank(structuredData.requirementSummary()) && !isBlank(requirementName)) {
            return "需求已澄清；交付内容覆盖“" + requirementName + "”；相关联调和验收已完成";
        }
        return "请确认业务背景、验收口径和最终交付边界";
    }

    private boolean containsAny(String raw, String normalized, String... words) {
        for (String word : words) {
            if (raw.contains(word) || normalized.contains(word.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private String trimTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
