package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.CodexModelCatalogResponse.Model;
import cn.aslight.workhub.model.ai.CodexModelCatalogResponse.ReasoningEffort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 只创建延迟持久化会话并关闭；不发送 session/send 或模型推理请求。 */
final class ZCodeModelCatalogConversation implements CliTaskConversation {
    final CompletableFuture<List<Model>> result = new CompletableFuture<>();
    private final ObjectMapper json;
    private final Path work;
    private OutputStream output;
    private List<Model> models;

    ZCodeModelCatalogConversation(ObjectMapper json, Path work) { this.json = json; this.work = work; }

    @Override public void start(OutputStream output) throws IOException {
        this.output = output;
        send(Map.of("id", "catalog-create", "method", "session/create", "params", Map.of(
                "workspace", Map.of("workspacePath", work.toString(), "workspaceKey", work.toString()),
                "mode", "plan", "persistence", "deferred", "titleGenerationEnabled", false,
                "dynamicWorkflowEnabled", false, "offPeakToolEnabled", false,
                "toolAllowlist", List.of(), "mcpServers", List.of())));
    }

    @Override public String accept(JsonNode message) throws IOException {
        if (message.has("method") && message.has("id")) {
            if ("session/requestRuntimePreferences".equals(message.path("method").asText())) {
                send(Map.of("id", message.get("id"), "result", Map.of("nativeSearchEnhancementsEnabled", false,
                        "subagentRuntimeConfigEnabled", false, "memoryEnabled", false,
                        "askUserQuestionAutoResolutionEnabled", false, "toolSearchMode", "off", "additionalDirectories", List.of())));
            } else send(Map.of("id", message.get("id"), "error", Map.of("code", -32601, "message", "Unsupported catalog capability")));
            return null;
        }
        if (!message.has("id") && message.has("method")) return null;
        if (message.hasNonNull("error")) throw invalid();
        var response = message.path("result");
        switch (message.path("id").asText()) {
            case "catalog-create" -> {
                if (models != null || !"ZCode Protocol".equals(response.path("protocol").path("name").asText())
                        || response.path("protocol").path("version").asInt() != 1) throw invalid();
                models = parseModels(response);
                send(Map.of("id", "catalog-close", "method", "session/close", "params", Map.of(
                        "sessionId", text(response.path("session"), "sessionId", 256), "expectedPersistence", "deferred")));
            }
            case "catalog-close" -> {
                if (models == null || !response.path("closed").asBoolean()) throw invalid();
                result.complete(models);
            }
            default -> throw invalid();
        }
        return null;
    }

    static List<Model> parseModels(JsonNode response) {
        var available = response.path("settings").path("model").path("available");
        var current = response.path("settings").path("model").path("current");
        if (!available.isArray() || available.isEmpty()) throw invalid();
        var models = new ArrayList<Model>();
        var keys = new HashSet<String>();
        for (var item : available) {
            String provider = text(item.path("ref"), "providerId", 128);
            String model = text(item.path("ref"), "modelId", 128);
            String key = provider + "/" + model;
            if (provider.contains("/") || key.length() > 128 || !keys.add(key)) throw invalid();
            var efforts = new ArrayList<ReasoningEffort>();
            var reasoning = item.path("reasoning");
            String defaultEffort = "";
            if (!reasoning.isMissingNode() && !reasoning.isNull()) {
                if (!reasoning.path("levels").isArray()) throw invalid();
                for (var level : reasoning.path("levels")) efforts.add(new ReasoningEffort(
                        text(level, "value", 32), level.path("label").asText("")));
                defaultEffort = text(reasoning, "defaultLevel", 32);
                String expected = defaultEffort;
                if (efforts.stream().noneMatch(e -> e.reasoningEffort().equals(expected))) throw invalid();
            }
            models.add(new Model(key, text(item, "label", 256),
                    provider.equals(current.path("providerId").asText()) && model.equals(current.path("modelId").asText()),
                    defaultEffort, List.copyOf(efforts)));
        }
        return List.copyOf(models);
    }

    private void send(Object message) throws IOException {
        output.write((json.writeValueAsString(message) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        output.flush();
    }
    private static String text(JsonNode node, String field, int max) {
        var value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > max) throw invalid();
        return value.asText();
    }
    private static IllegalStateException invalid() { return new IllegalStateException("ZCode 模型目录响应无效"); }
}
