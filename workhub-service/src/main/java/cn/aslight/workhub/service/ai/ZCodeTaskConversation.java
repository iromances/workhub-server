package cn.aslight.workhub.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import cn.aslight.workhub.mcp.security.ServerFileContentMasker;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** ZCode 原生 app-server 协议；ACK 与 turn.completed 严格分开。 */
public final class ZCodeTaskConversation implements CliTaskConversation {
    /** 会话内 MCP 服务器名与工具暴露前缀；session/create 不会连接 mcpServers，须显式 mcp/list connect。 */
    private static final String MCP_SERVER_NAME = "workhub_task";
    private static final String MCP_TOOL_PREFIX = "mcp__" + MCP_SERVER_NAME + "__";
    private final ObjectMapper json;
    private final Path directory;
    private final Path output;
    private final String prompt;
    private final Map<String, Object> selection;
    private final List<String> toolNames;
    private final boolean nativeTools;
    private final String mcpUrl;
    private final String token;
    private final String finalResultSchema;
    /** 结构化场景即使不带 CLI schema（仓库读取绕开 schema 阻断）也要求终稿为 JSON 对象，否则强制格式校正。 */
    private final boolean expectJsonObject;
    /** 仓库调查场景：终稿前必须有授权工具调用，零调用时自动续跑一轮（一次为限）。 */
    private final boolean requireToolInvestigation;
    private final List<String> imagePaths;
    private final String inputId = UUID.randomUUID().toString();
    private OutputStream input;
    private String sessionId;
    private String turnId;
    private String phase = "NEW";
    private long lastSequence;
    /** 当前轮输入身份：续跑会开启新 turn，身份校验跟随最新一次 session/send。 */
    private String currentInputId = inputId;
    private int toolCalls;
    private boolean investigationContinued;
    private boolean recoverableAnalysis;
    private String recoveryOriginal;
    private java.util.function.Consumer<String> saveOriginal = ignored -> { };
    private AgentResultFormatter agentFormatter;
    private String requestPrefix = "";
    private TextCompletion textCompletion;
    private final StringBuilder continuedText = new StringBuilder();
    private String modelStopReason;
    @FunctionalInterface interface TextCompletion { void accept(String text) throws IOException; }

    static ZCodeTaskConversation formatting(ObjectMapper json, Path directory, Path output, String prompt,
                                            Map<String, Object> selection, String prefix, TextCompletion completion) {
        var conversation = new ZCodeTaskConversation(json, directory, output, prompt, selection, List.of(), null, null);
        conversation.requestPrefix = prefix;
        conversation.textCompletion = completion;
        return conversation;
    }

    boolean owns(JsonNode message) {
        return message.path("id").asText().startsWith(requestPrefix)
                || sessionId != null && sessionId.equals(message.path("params").path("sessionId").asText());
    }

    @Override public boolean unlimitedResultMessages() { return true; }

    @Override public void resultRecovery(String original, java.util.function.Consumer<String> save) {
        if (!nativeTools) throw invalid("普通 AI 会话不支持数字员工结果恢复");
        recoveryOriginal = original;
        saveOriginal = save;
    }

    public void analysisRecovery(AiResultRecovery.Session recovery) {
        recoverableAnalysis = true;
        recoveryOriginal = recovery.original();
        saveOriginal = recovery.save();
    }

    private void formatAgentResult(String original) throws IOException {
        saveOriginal.accept(original);
        phase = "FORMATTING";
        agentFormatter = new AgentResultFormatter(json, directory, output,
                selection, finalResultSchema, original, input, nativeTools);
        if (nativeTools && finalResultSchema != null && finalResultSchema.contains("schemaVersion"))
            agentFormatter.requireV2(recoveryOriginal == null);
        agentFormatter.start();
    }


    public ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                 Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token) {
        this(json, directory, output, prompt, selection, toolNames, mcpUrl, token, null);
    }

    public ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                 Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token, String finalResultSchema) {
        this(json, directory, output, prompt, selection, toolNames, mcpUrl, token, finalResultSchema, List.of(), finalResultSchema != null, false);
    }

    /** 仓库读取场景：不传 CLI schema（避免阻断工具调用），但终稿仍须是 JSON 对象；且要求先完成工具调查。 */
    public static ZCodeTaskConversation structuredNoCliSchema(ObjectMapper json, Path directory, Path output, String prompt,
                                                              Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token,
                                                              String schemaForFormatting, List<String> imagePaths) {
        return new ZCodeTaskConversation(json, directory, output, prompt, selection, toolNames, mcpUrl, token,
                schemaForFormatting, imagePaths, true, true);
    }

    public ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                 Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token,
                                 String finalResultSchema, List<String> imagePaths) {
        this(json, directory, output, prompt, selection, toolNames, mcpUrl, token, finalResultSchema, imagePaths, false, false);
    }

    public static ZCodeTaskConversation agent(ObjectMapper json, Path directory, Path output, String prompt,
                                               Map<String, Object> selection, String mcpUrl, String token, String finalResultSchema) {
        return new ZCodeTaskConversation(json, directory, output, prompt, selection, List.of(), mcpUrl, token,
                finalResultSchema, List.of(), true, false, true);
    }

    private ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                  Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token,
                                  String finalResultSchema, List<String> imagePaths, boolean expectJsonObject,
                                  boolean requireToolInvestigation) {
        this(json, directory, output, prompt, selection, toolNames, mcpUrl, token, finalResultSchema, imagePaths,
                expectJsonObject, requireToolInvestigation, false);
    }

    private ZCodeTaskConversation(ObjectMapper json, Path directory, Path output, String prompt,
                                  Map<String, Object> selection, List<String> toolNames, String mcpUrl, String token,
                                  String finalResultSchema, List<String> imagePaths, boolean expectJsonObject,
                                  boolean requireToolInvestigation, boolean nativeTools) {
        this.json = json; this.directory = directory; this.output = output; this.prompt = prompt;
        this.selection = selection; this.toolNames = toolNames == null ? List.of() : toolNames.stream().map(ZCodeTaskConversation::exposedToolName).toList();
        this.mcpUrl = mcpUrl; this.token = token;
        this.nativeTools = nativeTools;
        this.finalResultSchema = finalResultSchema;
        this.expectJsonObject = expectJsonObject;
        this.requireToolInvestigation = requireToolInvestigation;
        this.imagePaths = imagePaths == null ? List.of() : List.copyOf(imagePaths);
    }

    @Override
    public synchronized void start(OutputStream stream) throws IOException {
        if (!"NEW".equals(phase)) throw invalid("会话已启动");
        input = stream;
        if (recoveryOriginal != null) { formatAgentResult(recoveryOriginal); return; }
        var parameters = new LinkedHashMap<String, Object>();
        parameters.put("workspace", Map.of("workspacePath", directory.toString(), "workspaceKey", directory.toString()));
        // 数字员工使用原生完整工具；普通 AI 会话仍使用精确白名单。
        parameters.put("mode", "yolo"); parameters.put("model", selection);
        parameters.put("thoughtLevel", json.valueToTree(selection).path("options").path("reasoningLevel").asText());
        parameters.put("titleGenerationEnabled", false);
        if (!nativeTools) {
            parameters.put("dynamicWorkflowEnabled", false); parameters.put("offPeakToolEnabled", false);
            parameters.put("toolAllowlist", toolNames);
        }
        if (mcpUrl != null) parameters.put("mcpServers", List.of(Map.of("name", "workhub_task", "type", "http", "url", mcpUrl,
                "headers", List.of(Map.of("name", "Authorization", "value", "Bearer " + token)),
                "isolation", "session", "timeoutMs", 300000, "alwaysLoad", true)));
        else if (!nativeTools) parameters.put("mcpServers", List.of());
        phase = "CREATE";
        request("wh-create", "session/create", parameters);
    }

    @Override
    public synchronized String accept(JsonNode message) throws IOException {
        if (!message.isObject()) throw invalid("协议消息不是对象");
        if (agentFormatter != null && (agentFormatter.owns(message) || message.has("method") && message.has("id"))) {
            agentFormatter.accept(message);
            return null;
        }
        if (!requestPrefix.isEmpty() && message.has("id") && !message.has("method")) {
            if (!message.path("id").asText().startsWith(requestPrefix)) throw invalid("整理响应身份不匹配");
            var copy = ((tools.jackson.databind.node.ObjectNode) message).deepCopy();
            copy.put("id", message.path("id").asText().substring(requestPrefix.length()));
            message = copy;
        }
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
            if (!currentInputId.equals(payload.path("inputId").asText()) || event.path("turnId").asText().isBlank()) throw invalid("运行身份不匹配");
            turnId = event.path("turnId").asText();
            return "ZCode 已开始执行任务";
        }
        // 原生 runtime 自动续接 length；终态 response 只含最后一段，保留已完成的前缀。
        // ModelComplete 在 app-server 映射为 session.updated；只接收主轮次的完整消息，不拼流式 delta。
        if (textCompletion != null && "session.updated".equals(type)
                && "main_turn".equals(payload.path("querySource").asText()) && payload.has("stopReason")) {
            if ((!"SEND".equals(phase) && !"RUNNING".equals(phase)) || turnId == null
                    || !turnId.equals(event.path("turnId").asText())) throw invalid("整理模型消息不属于当前轮次");
            if (payload.path("toolCallCount").asInt() != 0) throw invalid("整理阶段尝试调用工具");
            modelStopReason = payload.path("stopReason").asText();
            if ("length".equals(modelStopReason)) {
                if (!payload.path("content").isString()) throw invalid("整理续接文本类型错误");
                continuedText.append(payload.path("content").asText());
            }
            return null;
        }
        if ("turn.failed".equals(type)) {
            requireTurn(event); throw invalid("任务执行失败；错误类型 " + safeCode(payload.path("error").path("code").asText()));
        }
        if ("turn.completed".equals(type)) {
            requireTurn(event);
            if (!"success".equals(payload.path("resultType").asText())) throw invalid("任务未成功完成：" + safeCode(payload.path("resultType").asText()));
            String result = requireText(payload.path("response"), "最终产物");
            if (textCompletion != null) {
                if (modelStopReason != null && !"stop".equals(modelStopReason))
                    throw invalid("整理未正常结束，stopReason=" + safeCode(modelStopReason));
                phase = "COMPLETE";
                textCompletion.accept(continuedText + result);
                return null;
            }
            if (nativeTools) {
                formatAgentResult(result);
                return "正在整理报告：原始答复已保存，可单独恢复整理";
            }
            if (requireToolInvestigation && toolCalls == 0 && !investigationContinued) {
                investigationContinued = true;
                phase = "SEND";
                currentInputId = UUID.randomUUID().toString();
                // 模型跳过调查直接交"计划"时给一次纠偏机会；续跑仍零工具则交给上层内容校验拒绝。
                var retryParams = new LinkedHashMap<String, Object>();
                retryParams.put("sessionId", sessionId);
                retryParams.put("inputId", currentInputId);
                retryParams.put("modelSelection", selection);
                retryParams.put("content", "你尚未调用任何授权工具完成代码调查，此前的答复只是过程计划，不能作为终稿。"
                        + "请先用 workhub_task 的只读仓库工具（工具名带 mcp__workhub_task__ 前缀，如 mcp__workhub_task__list_repository_files、"
                        + "mcp__workhub_task__read_repository_text_file、mcp__workhub_task__search_repository_text）"
                        + "完成代码检索，再基于真实代码结论输出符合先前要求的最终 JSON 对象；不要输出过程叙述。");
                request("wh-send", "session/send", retryParams);
                return "ZCode 未执行代码调查，已要求续跑一轮";
            }
            if (recoverableAnalysis) {
                if (requireToolInvestigation && toolCalls == 0) throw invalid("未完成授权代码调查，不能保存或恢复分析结果");
                formatAgentResult(result);
                return "原始分析已保存，正在整理完整结果";
            }
            if ((finalResultSchema != null || expectJsonObject) && !isJsonObject(result)) {
                formatAgentResult(result);
                return "ZCode 正在通过原生会话整理最终结果";
            }
            complete(result);
            return null;
        }
        if ("tool.updated".equals(type) && "scheduled".equals(payload.path("kind").asText())) {
            String name = payload.path("toolName").asText();
            if ("FORMATTING".equals(phase) || (!nativeTools && !toolNames.contains(name))) throw invalid("CLI 尝试调用未授权工具");
            toolCalls++;
            return "ZCode 正在调用 " + name;
        }
        return null;
    }

    private void reverseRequest(JsonNode request) throws IOException {
        if ("session/requestRuntimePreferences".equals(request.path("method").asText())) {
            if (nativeTools) {
                // 子代理使用 CLI 自带配置；不声明桌面宿主专属的配置回调。
                send(Map.of("id", request.get("id"), "result", Map.of("nativeSearchEnhancementsEnabled", true,
                        "memoryEnabled", true, "askUserQuestionAutoResolutionEnabled", true, "toolSearchMode", "auto")));
                return;
            }
            send(Map.of("id", request.get("id"), "result", Map.of("nativeSearchEnhancementsEnabled", false, "subagentRuntimeConfigEnabled", false,
                    "memoryEnabled", false, "askUserQuestionAutoResolutionEnabled", false, "toolSearchMode", "off", "additionalDirectories", List.of())));
            return;
        }
        // 不代替人批准权限、调用桌面、执行 Shell、开启插件或提供额外认证。
        send(Map.of("id", request.get("id"), "error", Map.of("code", -32601, "message", "WorkHub task host does not provide this capability")));
    }

    private void response(JsonNode message) throws IOException {
        if (message.has("error")) throw protocolError(message);
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
                if (mcpUrl == null) {
                    phase = "SEND";
                    sendPrompt();
                    return;
                }
                // CLI 0.17 实测：session/create 的 mcpServers 只登记不连接，模型拿不到工具；
                // 必须显式 mcp/list(mode=connect) 建立连接，工具才会以 mcp__workhub_task__ 前缀暴露。
                phase = "MCP_CONNECT";
                var server = new LinkedHashMap<String, Object>();
                server.put("name", MCP_SERVER_NAME);
                server.put("type", "http");
                server.put("url", mcpUrl);
                server.put("headers", List.of(Map.of("name", "Authorization", "value", "Bearer " + token)));
                server.put("isolation", "session");
                server.put("timeoutMs", 300000);
                server.put("alwaysLoad", true);
                request("wh-mcp", "mcp/list", Map.of(
                        "workspace", Map.of("workspacePath", directory.toString(), "workspaceKey", directory.toString()),
                        "mcpServers", List.of(server), "mode", "connect"));
            }
            case "wh-mcp" -> {
                if (!"MCP_CONNECT".equals(phase)) throw invalid("MCP 连接应答时序错误");
                JsonNode status = result.path("statuses").path(MCP_SERVER_NAME);
                if (!"connected".equals(status.path("status").asText()) || status.path("toolCount").asInt(0) < 1)
                    throw invalid("workhub_task MCP 连接失败（"
                            + (status.isMissingNode() ? "服务器未注册" : "status=" + status.path("status").asText() + ", toolCount=" + status.path("toolCount").asInt(0)) + "）");
                phase = "SEND";
                sendPrompt();
            }
            case "wh-send" -> {
                if (!Set.of("SEND", "FORMATTING", "COMPLETE").contains(phase) || !sessionId.equals(result.path("sessionId").asText()) || !result.path("accepted").asBoolean()) throw invalid("任务未被接受");
                if ("SEND".equals(phase)) phase = "RUNNING";
            }
            default -> throw invalid("收到未知协议响应");
        }
    }

    private String requireText(JsonNode value, String label) {
        if (value.isMissingNode() || value.isNull()) throw invalid(label + "缺失");
        if (!value.isString()) throw invalid(label + "类型错误，type=" + value.getNodeType());
        if (value.asText().isBlank()) throw invalid(label + "为空白文本");
        return value.asText();
    }

    private boolean isJsonObject(String text) {
        try { return json.reader().with(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(text).isObject(); }
        catch (tools.jackson.core.JacksonException ex) { return false; }
    }

    private IllegalArgumentException protocolError(JsonNode message) {
        String method = switch (message.path("id").asText()) {
            case "wh-create" -> "session/create";
            case "wh-subscribe" -> "session/subscribe";
            case "wh-mcp" -> "mcp/list";
            case "wh-send" -> "session/send";
            default -> "unknown";
        };
        JsonNode error = message.path("error");
        String detail = error.path("message").asText("");
        if (token != null && !token.isBlank()) detail = detail.replace(token, "[REDACTED]");
        detail = new ServerFileContentMasker().mask(detail).replaceAll("\\s+", " ").trim();
        if (detail.length() > 120) detail = detail.substring(0, 120) + "…";
        return invalid("协议请求被拒绝，" + method + "，阶段 " + phase + "，错误码 "
                + safeCode(error.path("code").asText()) + (detail.isBlank() ? "" : "：" + detail));
    }

    /** CLI 会话内 MCP 工具以 mcp__<server>__<name> 暴露；调用方传入的裸名统一补前缀（已带前缀的保持原样）。 */
    private static String exposedToolName(String name) {
        if (name == null) return null;
        return name.startsWith(MCP_TOOL_PREFIX) ? name : MCP_TOOL_PREFIX + name;
    }

    private void sendPrompt() throws IOException {
        var sendParams = new LinkedHashMap<String, Object>();
        sendParams.put("sessionId", sessionId);
        sendParams.put("inputId", inputId);
        sendParams.put("modelSelection", selection);
        sendParams.put("content", prompt);
        if (!imagePaths.isEmpty()) sendParams.put("attachments", imageAttachments());
        request("wh-send", "session/send", sendParams);
    }

    /** 图片沿用桌面端 localPath 通路提交为 session/send 附件，不进入协议正文。 */
    private List<Map<String, Object>> imageAttachments() {
        var attachments = new ArrayList<Map<String, Object>>();
        for (String raw : imagePaths) {
            Path path = Path.of(raw).toAbsolutePath().normalize();
            attachments.add(Map.of(
                    "kind", "image",
                    "filename", path.getFileName().toString(),
                    "mimeType", imageMimeType(path.getFileName().toString()),
                    "localPath", path.toString()));
        }
        return attachments;
    }

    private static String imageMimeType(String filename) {
        String lower = filename.toLowerCase(java.util.Locale.ROOT);
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private void complete(String result) throws IOException {
        Files.writeString(output, result, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        phase = "COMPLETE";
        // EOF 触发 CLI 正常资源清理，主执行器仍等待进程及工具全部结束才接受结果。
        input.close();
    }

    private boolean sameModel(JsonNode actual) {
        return selection.get("providerId").equals(actual.path("providerId").asText()) && selection.get("modelId").equals(actual.path("modelId").asText());
    }

    private void requireTurn(JsonNode event) {
        if ((!"RUNNING".equals(phase) && !"SEND".equals(phase)) || turnId == null || !turnId.equals(event.path("turnId").asText())
                || !currentInputId.equals(event.path("payload").path("inputId").asText())) throw invalid("终态不属于本次任务");
    }
    private void request(String id, String method, Object params) throws IOException { send(Map.of("id", requestPrefix + id, "method", method, "params", params)); }
    private void send(Object value) throws IOException { input.write((json.writeValueAsString(value) + "\n").getBytes(StandardCharsets.UTF_8)); input.flush(); }
    private static String safeCode(String code) { return code.matches("[A-Za-z0-9_-]{1,80}") ? code : "unknown"; }
    private static IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("ZCode " + reason); }
}
