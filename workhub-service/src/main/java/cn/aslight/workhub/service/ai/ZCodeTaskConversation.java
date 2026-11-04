package cn.aslight.workhub.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** ZCode 0.17 原生 app-server 协议；ACK 与 turn.completed 严格分开。 */
public final class ZCodeTaskConversation implements CliTaskConversation {
    private final ObjectMapper json;
    private final Path directory;
    private final Path output;
    private final String prompt;
    private final Map<String, Object> selection;
    private final List<String> toolNames;
    private final String mcpUrl;
    private final String token;
    private final String inputId = UUID.randomUUID().toString();
    private OutputStream input;
    private String sessionId;
    private String turnId;
    private String phase = "NEW";
    private long lastSequence;

    public ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                 Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token) {
        this.json = json; this.directory = directory; this.output = output; this.prompt = prompt;
        this.selection = selection; this.toolNames = List.copyOf(toolNames); this.mcpUrl = mcpUrl; this.token = token;
    }

    @Override
    public synchronized void start(OutputStream stream) throws IOException {
        if (!"NEW".equals(phase)) throw invalid("会话已启动");
        input = stream;
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("workspace", Map.of("workspacePath", directory.toString(), "workspaceKey", directory.toString()));
        // yolo 只作用于精确 allowlist 中的任务工具；内置文件、Shell、网络和其他 MCP 均不可见。
        parameters.put("mode", "yolo"); parameters.put("model", selection);
        parameters.put("thoughtLevel", json.valueToTree(selection).path("options").path("reasoningLevel").asText());
        parameters.put("titleGenerationEnabled", false); parameters.put("dynamicWorkflowEnabled", false); parameters.put("offPeakToolEnabled", false);
        parameters.put("toolAllowlist", toolNames);
        if (mcpUrl != null) parameters.put("mcpServers", List.of(Map.of("name", "workhub_task", "type", "http", "url", mcpUrl,
                "headers", List.of(Map.of("name", "Authorization", "value", "Bearer " + token)),
                "isolation", "session", "timeoutMs", 300000, "alwaysLoad", true)));
        else parameters.put("mcpServers", List.of());
        phase = "CREATE";
        request("wh-create", "session/create", parameters);
    }

    @Override
    public synchronized String accept(JsonNode message) throws IOException {
        if (!message.isObject()) throw invalid("协议消息不是对象");
        if (message.has("method") && message.has("id")) {
            reverseRequest(message);
            return null;
        }
        if (message.has("id")) {
            response(message);
            return null;
        }
        if (!"session/event".equals(message.path("method").asText())) return null;
        JsonNode event = message.path("params");
        if (sessionId == null || !sessionId.equals(event.path("sessionId").asText())) throw invalid("事件不属于本次会话");
        if (!event.path("seq").isIntegralNumber() || event.path("seq").asLong() <= lastSequence) throw invalid("事件顺序无效");
        lastSequence = event.path("seq").asLong();
        String type = event.path("type").asText();
        JsonNode payload = event.path("payload");
        if ("turn.started".equals(type)) {
            if (!"SEND".equals(phase) && !"RUNNING".equals(phase)) throw invalid("未提交任务却收到运行事件");
            if (!inputId.equals(payload.path("inputId").asText()) || event.path("turnId").asText().isBlank()) throw invalid("运行身份不匹配");
            turnId = event.path("turnId").asText();
            return "ZCode 已开始执行任务";
        }
        if ("turn.failed".equals(type)) {
            requireTurn(event); throw invalid("任务执行失败；错误类型 " + safeCode(payload.path("error").path("code").asText()));
        }
        if ("turn.completed".equals(type)) {
            requireTurn(event);
            if (!"success".equals(payload.path("resultType").asText())) throw invalid("任务未成功完成：" + safeCode(payload.path("resultType").asText()));
            String result = payload.path("response").asText();
            if (!payload.path("response").isString() || result.isBlank() || result.getBytes(StandardCharsets.UTF_8).length > 24000) throw invalid("最终产物缺失或超长");
            Files.writeString(output, result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            phase = "COMPLETE";
            // EOF 触发 CLI 正常资源清理，主执行器仍等待进程及工具全部结束才接受结果。
            input.close();
            return null;
        }
        if ("tool.updated".equals(type) && "scheduled".equals(payload.path("kind").asText())) {
            String name = payload.path("toolName").asText();
            if (!toolNames.contains(name)) throw invalid("CLI 尝试调用未授权工具");
            return "ZCode 正在调用 " + name;
        }
        return null;
    }

    private void reverseRequest(JsonNode request) throws IOException {
        if ("session/requestRuntimePreferences".equals(request.path("method").asText())) {
            send(Map.of("id", request.get("id"), "result", Map.of("nativeSearchEnhancementsEnabled", false, "subagentRuntimeConfigEnabled", false,
                    "memoryEnabled", false, "askUserQuestionAutoResolutionEnabled", false, "toolSearchMode", "off", "additionalDirectories", List.of())));
            return;
        }
        // 不代替人批准权限、调用桌面、执行 Shell、开启插件或提供额外认证。
        send(Map.of("id", request.get("id"), "error", Map.of("code", -32601, "message", "WorkHub task host does not provide this capability")));
    }

    private void response(JsonNode message) throws IOException {
        if (message.has("error")) throw invalid("协议请求被拒绝，错误码 " + safeCode(message.path("error").path("code").asText()));
        String id = message.path("id").asText(); JsonNode result = message.path("result");
        switch (id) {
            case "wh-create" -> {
                if (!"CREATE".equals(phase) || !"ZCode Protocol".equals(result.path("protocol").path("name").asText())
                        || result.path("protocol").path("version").asInt() != 1) throw invalid("会话创建协议不兼容");
                sessionId = result.path("session").path("sessionId").asText();
                if (sessionId.isBlank() || sessionId.length() > 200) throw invalid("会话标识缺失");
                JsonNode actual = result.path("settings").path("model").path("current");
                // 当前模型选择在 settings 中独立展示；提交时再次携带完整选择，CLI 验证其可执行性。
                if (!actual.isObject() || !sameModel(actual) || !json.valueToTree(selection).path("options").equals(actual.path("options"))) throw invalid("CLI 切换了指定模型");
                phase = "SUBSCRIBE";
                request("wh-subscribe", "session/subscribe", Map.of("sessionId", sessionId, "deliveryKind", "desktop-continuous", "includeSnapshot", false));
            }
            case "wh-subscribe" -> {
                if (!"SUBSCRIBE".equals(phase) || !sessionId.equals(result.path("sessionId").asText()) || !result.path("events").isArray()) throw invalid("事件订阅失败");
                lastSequence = result.path("eventSeq").asLong();
                phase = "SEND";
                request("wh-send", "session/send", Map.of("sessionId", sessionId, "inputId", inputId, "modelSelection", selection, "content", prompt));
            }
            case "wh-send" -> {
                if ((!"SEND".equals(phase) && !"COMPLETE".equals(phase)) || !sessionId.equals(result.path("sessionId").asText()) || !result.path("accepted").asBoolean()) throw invalid("任务未被接受");
                if (!"COMPLETE".equals(phase)) phase = "RUNNING";
            }
            default -> throw invalid("收到未知协议响应");
        }
    }

    private boolean sameModel(JsonNode actual) {
        return selection.get("providerId").equals(actual.path("providerId").asText()) && selection.get("modelId").equals(actual.path("modelId").asText());
    }
    private void requireTurn(JsonNode event) {
        if ((!"RUNNING".equals(phase) && !"SEND".equals(phase)) || turnId == null || !turnId.equals(event.path("turnId").asText())
                || !inputId.equals(event.path("payload").path("inputId").asText())) throw invalid("终态不属于本次任务");
    }
    private void request(String id, String method, Object params) throws IOException { send(Map.of("id", id, "method", method, "params", params)); }
    private void send(Object value) throws IOException { input.write((json.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8)); input.flush(); }
    private static String safeCode(String code) { return code.matches("[A-Za-z0-9_-]{1,80}") ? code : "unknown"; }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("ZCode " + reason); }
}
