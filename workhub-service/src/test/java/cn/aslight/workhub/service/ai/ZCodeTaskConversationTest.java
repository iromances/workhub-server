package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ZCodeTaskConversationTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Object> model = AiCliType.selection("provider/glm-4.6", "disabled");
    private final ByteArrayOutputStream sent = new ByteArrayOutputStream();
    private ZCodeTaskConversation conversation;
    private String inputId;

    private void create() throws Exception { create(null); }
    private void create(String finalResultSchema) throws Exception {
        conversation = new ZCodeTaskConversation(json, directory, directory.resolve("result.json"), "执行指定任务", model,
                List.of("mcp__workhub_task__monitor_read"), "http://127.0.0.1:12345/mcp", "ephemeral-token", finalResultSchema);
        conversation.start(sent);
        var request = json.readTree(sent.toString().lines().findFirst().orElseThrow());
        assertEquals("session/create", request.path("method").asText());
        assertEquals(json.valueToTree(List.of("mcp__workhub_task__monitor_read")), request.path("params").path("toolAllowlist"));
        assertFalse(request.path("params").path("titleGenerationEnabled").asBoolean());
        assertFalse(request.path("params").path("dynamicWorkflowEnabled").asBoolean());
        assertFalse(request.path("params").path("offPeakToolEnabled").asBoolean());
        response("wh-create", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-1"), "settings", Map.of("model", Map.of("current", model))));
        response("wh-subscribe", Map.of("sessionId", "session-1", "eventSeq", 0, "events", List.of()));
        var connect = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        assertEquals("mcp/list", connect.path("method").asText());
        assertEquals("connect", connect.path("params").path("mode").asText());
        assertEquals("http://127.0.0.1:12345/mcp", connect.path("params").path("mcpServers").get(0).path("url").asText());
        assertEquals("Bearer ephemeral-token",
                connect.path("params").path("mcpServers").get(0).path("headers").get(0).path("value").asText());
        response("wh-mcp", Map.of("statuses", Map.of("workhub_task", Map.of("status", "connected", "toolCount", 29))));
        var submit = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        inputId = submit.path("params").path("inputId").asText();
        assertEquals(json.valueToTree(model), submit.path("params").path("modelSelection"));
        assertEquals("执行指定任务", submit.path("params").path("content").asText());
        assertFalse(submit.path("params").has("attachments"), "无图片会话不得携带 attachments");
    }
    private void response(String id, Object value) throws Exception { conversation.accept(json.valueToTree(Map.of("id", id, "result", value))); }
    private tools.jackson.databind.JsonNode event(String type, long sequence, Map<String, Object> payload) {
        return event(type, sequence, payload, "turn-1");
    }
    private tools.jackson.databind.JsonNode event(String type, long sequence, Map<String, Object> payload, String turnId) {
        return json.valueToTree(Map.of("method", "session/event", "params", Map.of("sessionId", "session-1", "turnId", turnId,
                "seq", sequence, "type", type, "payload", payload)));
    }
    private void started() throws Exception { conversation.accept(event("turn.started", 1, Map.of("inputId", inputId))); }

    /** 仓库调查会话（structuredNoCliSchema）：要求终稿前有授权工具调用。 */
    private void createInvestigation() throws Exception {
        conversation = ZCodeTaskConversation.structuredNoCliSchema(json, directory, directory.resolve("result.json"), "执行研发评估", model,
                List.of("mcp__workhub_task__search_repository_text"), "http://127.0.0.1:12345/mcp", "ephemeral-token", "{\"type\":\"object\"}", List.of());
        conversation.start(sent);
        response("wh-create", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-1"), "settings", Map.of("model", Map.of("current", model))));
        response("wh-subscribe", Map.of("sessionId", "session-1", "eventSeq", 0, "events", List.of()));
        response("wh-mcp", Map.of("statuses", Map.of("workhub_task", Map.of("status", "connected", "toolCount", 29))));
        var submit = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        inputId = submit.path("params").path("inputId").asText();
        assertEquals("session/send", submit.path("method").asText());
    }
    private tools.jackson.databind.JsonNode lastSent() throws Exception {
        return json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
    }
    @Test void oversizedStructuredResultIsCompactedOnceWithoutReplayingInvestigation() throws Exception {
        create("{\"type\":\"object\"}"); started();
        String original = "{\"summary\":\"" + "中".repeat(8000) + "\"}";
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", original)));
        assertEquals("workspace/generateText", lastSent().path("method").asText());
        assertTrue(lastSent().path("params").path("prompt").asText().contains("24000 UTF-8"));
        response("wh-format-result", Map.of("selection", model, "finishReason", "stop", "text", "{\"summary\":\"精简\"}"));
        assertTrue(Files.readString(directory.resolve("result.json")).contains("精简"));
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("\"session/send\"")).count());
    }

    @Test void utf8FinalBoundaryIsEnforcedForPlainResults() throws Exception {
        create(); started();
        var error = assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "中".repeat(8000) + "a"))));
        assertTrue(error.getMessage().contains("bytes=24001"));
        assertTrue(error.getMessage().contains("limit=24000"));
        conversation.accept(event("turn.completed", 3, Map.of("inputId", inputId, "resultType", "success", "response", "中".repeat(8000))));
        assertEquals(24000, Files.size(directory.resolve("result.json")));
    }

    @Test void formattingInputBoundaryRejectsBeforeCallingModel() throws Exception {
        create("{\"type\":\"object\"}"); started();
        var error = assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "中".repeat(32000) + "a"))));
        assertTrue(error.getMessage().contains("bytes=96001"));
        assertFalse(sent.toString().contains("workspace/generateText"));
        conversation.accept(event("turn.completed", 3, Map.of("inputId", inputId, "resultType", "success", "response", "中".repeat(32000))));
        assertEquals("workspace/generateText", lastSent().path("method").asText());
    }

    @Test void missingWrongTypeAndBlankHaveDistinctDiagnostics() throws Exception {
        create(); started();
        var missing = assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success"))));
        var wrong = assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 3,
                Map.of("inputId", inputId, "resultType", "success", "response", Map.of()))));
        var blank = assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 4,
                Map.of("inputId", inputId, "resultType", "success", "response", " "))));
        assertTrue(missing.getMessage().contains("缺失"));
        assertTrue(wrong.getMessage().contains("type=OBJECT"));
        assertTrue(blank.getMessage().contains("空白"));
    }

    @Test void formattingFailureIncludesFinishReasonAndRejectsOversizedCorrection() throws Exception {
        create("{\"type\":\"object\"}"); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "text")));
        var error = assertThrows(IllegalArgumentException.class, () -> response("wh-format-result",
                Map.of("selection", model, "finishReason", "length", "text", "中")));
        assertTrue(error.getMessage().contains("finishReason=length"));
        assertTrue(error.getMessage().contains("textBytes=3"));
        var oversized = assertThrows(IllegalArgumentException.class, () -> response("wh-format-result",
                Map.of("selection", model, "finishReason", "stop", "text", "中".repeat(8001))));
        assertTrue(oversized.getMessage().contains("bytes=24003"));
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("\"workspace/generateText\"")).count());
    }

    @Test void agentAcceptsNativeAndWorkhubToolsInOneSession() throws Exception {
        conversation = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model,
                "http://localhost/mcp", "fixture-token", "{\"type\":\"object\"}");
        conversation.start(sent);
        assertFalse(lastSent().path("params").has("toolAllowlist"));
        response("wh-create", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-1"), "settings", Map.of("model", Map.of("current", model))));
        response("wh-subscribe", Map.of("sessionId", "session-1", "eventSeq", 0, "events", List.of()));
        assertEquals("mcp/list", lastSent().path("method").asText());
        response("wh-mcp", Map.of("statuses", Map.of("workhub_task", Map.of("status", "connected", "toolCount", 1))));
        inputId = lastSent().path("params").path("inputId").asText();
        started();
        for (var entry : Map.of(2L, "Bash", 3L, "Write", 4L, "mcp__workhub_task__run_readonly_query").entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            assertNotNull(conversation.accept(event("tool.updated", entry.getKey(), Map.of("kind", "scheduled", "toolName", entry.getValue()))));
        }
        conversation.accept(event("turn.completed", 5, Map.of("inputId", inputId, "resultType", "success", "response", "{\"type\":\"RESULT\",\"summary\":\"完成\"}")));
        assertTrue(Files.exists(directory.resolve("result.json")));
    }

    @Test void agentWithoutMcpKeepsNativeToolsAndNativePreferences() throws Exception {
        conversation = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model, null, null, null);
        conversation.start(sent);
        assertFalse(lastSent().path("params").has("toolAllowlist"));
        assertFalse(lastSent().path("params").has("mcpServers"));
        conversation.accept(json.valueToTree(Map.of("id", "preferences", "method", "session/requestRuntimePreferences")));
        assertTrue(lastSent().path("result").path("nativeSearchEnhancementsEnabled").asBoolean());
        assertTrue(lastSent().path("result").path("memoryEnabled").asBoolean());
        assertFalse(lastSent().path("result").has("subagentRuntimeConfigEnabled"));
    }
    private long sentCount(String methodFragment) {
        return sent.toString().lines().filter(line -> line.contains(methodFragment)).count();
    }

    @Test void bareRuntimeToolNamesAreExposedWithWorkhubTaskPrefix() throws Exception {
        conversation = new ZCodeTaskConversation(json, directory, directory.resolve("result.json"), "任务", model,
                List.of("search_repository_text", "mcp__workhub_task__already_prefixed"), "http://127.0.0.1:12345/mcp", "token");
        conversation.start(sent);
        var request = json.readTree(sent.toString().lines().findFirst().orElseThrow());
        assertEquals(json.valueToTree(List.of("mcp__workhub_task__search_repository_text", "mcp__workhub_task__already_prefixed")),
                request.path("params").path("toolAllowlist"));
    }

    @Test void protocolErrorRetainsFormattingStageAndProviderMessage() throws Exception {
        create("{\"type\":\"object\"}"); started();
        conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "已有调查结论")));
        var error = assertThrows(IllegalArgumentException.class, () -> conversation.accept(json.valueToTree(Map.of(
                "id", "wh-format-result", "error", Map.of("code", -32603,
                        "message", "Provider request timed out", "data", Map.of("stack", "private stack"))))));
        assertTrue(error.getMessage().contains("workspace/generateText"));
        assertTrue(error.getMessage().contains("FORMATTING"));
        assertTrue(error.getMessage().contains("-32603"));
        assertTrue(error.getMessage().contains("Provider request timed out"));
        assertFalse(error.getMessage().contains("private stack"));
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test void protocolErrorMasksCredentialsBeforeBoundingMessage() throws Exception {
        create();
        var error = assertThrows(IllegalArgumentException.class, () -> conversation.accept(json.valueToTree(Map.of(
                "id", "wh-send", "error", Map.of("code", -32603, "message",
                        "failed ephemeral-token Authorization: Bearer abc-secret api_key=other-secret\n" + "x".repeat(500))))));
        assertTrue(error.getMessage().contains("session/send"));
        assertTrue(error.getMessage().contains("[REDACTED]"));
        assertFalse(error.getMessage().contains("ephemeral-token"));
        assertFalse(error.getMessage().contains("abc-secret"));
        assertFalse(error.getMessage().contains("other-secret"));
        assertFalse(error.getMessage().contains("\n"));
        assertTrue(error.getMessage().length() < 240);
    }

    @Test void protocolErrorWithoutMessageStillIdentifiesRequest() throws Exception {
        create();
        var error = assertThrows(IllegalArgumentException.class, () -> conversation.accept(json.valueToTree(Map.of(
                "id", "wh-mcp", "error", Map.of("code", -32603)))));
        assertTrue(error.getMessage().contains("mcp/list"));
        assertTrue(error.getMessage().contains("-32603"));
    }

    @Test void repositoryTaskFailsFastWhenWorkhubTaskMcpCannotConnect() throws Exception {
        conversation = ZCodeTaskConversation.structuredNoCliSchema(json, directory, directory.resolve("result.json"), "执行研发评估", model,
                List.of("search_repository_text"), "http://127.0.0.1:12345/mcp", "token", "{\"type\":\"object\"}", List.of());
        conversation.start(sent);
        response("wh-create", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-1"), "settings", Map.of("model", Map.of("current", model))));
        response("wh-subscribe", Map.of("sessionId", "session-1", "eventSeq", 0, "events", List.of()));
        var ex = assertThrows(IllegalArgumentException.class, () -> response("wh-mcp", Map.of("statuses", Map.of())));
        assertTrue(ex.getMessage().contains("MCP 连接失败"));
        assertFalse(sent.toString().contains("执行研发评估"), "MCP 未连接时不得下发业务提示词");
    }

    @Test void zeroToolCompletionInRepositoryInvestigationContinuesOnceThenAcceptsInvestigatedResult() throws Exception {
        createInvestigation(); started();
        String progress = conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "{\"summary\":\"我先梳理需求\",\"workItems\":[]}")));
        assertTrue(progress.contains("续跑"));
        assertFalse(Files.exists(directory.resolve("result.json")));
        var retry = lastSent();
        assertEquals("session/send", retry.path("method").asText());
        String retryInputId = retry.path("params").path("inputId").asText();
        assertNotEquals(inputId, retryInputId);
        assertTrue(retry.path("params").path("content").asText().contains("代码调查"));
        response("wh-send", Map.of("sessionId", "session-1", "accepted", true));
        conversation.accept(event("turn.started", 3, Map.of("inputId", retryInputId), "turn-2"));
        conversation.accept(event("tool.updated", 4, Map.of("kind", "scheduled", "toolName", "mcp__workhub_task__search_repository_text"), "turn-2"));
        conversation.accept(event("turn.completed", 5,
                Map.of("inputId", retryInputId, "resultType", "success", "response", "{\"summary\":\"已调查\",\"workItems\":[{\"title\":\"任务一\"}]}"), "turn-2"));
        assertEquals("已调查", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
        assertEquals(2, sentCount("\"session/send\""));
    }

    @Test void continuationRunsOnlyOnceAndSecondZeroToolCompletionFinishesNormally() throws Exception {
        createInvestigation(); started();
        conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "{\"summary\":\"我先看看\",\"workItems\":[]}")));
        var retry = lastSent();
        String retryInputId = retry.path("params").path("inputId").asText();
        response("wh-send", Map.of("sessionId", "session-1", "accepted", true));
        conversation.accept(event("turn.started", 3, Map.of("inputId", retryInputId), "turn-2"));
        // 续跑轮仍零工具：不再发第三次 session/send，按普通终态收尾（空内容由上层生成器校验拒绝）。
        conversation.accept(event("turn.completed", 4,
                Map.of("inputId", retryInputId, "resultType", "success", "response", "{\"summary\":\"叙述\",\"workItems\":[]}"), "turn-2"));
        assertEquals("叙述", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
        assertEquals(2, sentCount("\"session/send\""));
    }

    @Test void malformedStructuredOutputGetsOneToolFreeCorrectionAndNoTaskReplay() throws Exception {
        create("{\"type\":\"object\"}"); started();
        String progress = conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "这里只整理给定文本，负责人未提供。")));
        assertTrue(progress.contains("校正"));
        assertFalse(Files.exists(directory.resolve("result.json")));
        var correction = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        assertEquals("workspace/generateText", correction.path("method").asText());
        // 带 operationId 走 CLI 可显式取消通道；缺省时 CLI 挂 60 秒内部超时，慢响应即被取消。
        assertTrue(correction.path("params").path("operationId").asText().startsWith("wh-format-result-"));
        assertEquals(json.valueToTree(model), correction.path("params").path("selection"));
        assertEquals(json.createArrayNode(), correction.path("params").path("tools"));
        assertEquals(8192, correction.path("params").path("maxOutputTokens").asInt());
        assertFalse(correction.path("params").has("mcpServers"));
        assertFalse(correction.path("params").path("prompt").asText().contains("执行指定任务"));
        // 原任务的快速终态先于 ACK 时，ACK 不得把格式阶段退回 RUNNING。
        response("wh-send", Map.of("sessionId", "session-1", "accepted", true));
        response("wh-format-result", Map.of("selection", model, "finishReason", "stop", "text",
                "{\"type\":\"RESULT\",\"summary\":\"负责人待确认\"}", "toolCalls", List.of()));
        assertEquals("负责人待确认", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("\"session/send\"")).count());
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("\"workspace/generateText\"")).count());
    }

    @Test void correctionCannotLoopOrAcceptTruncatedOutput() throws Exception {
        create("{\"type\":\"object\"}"); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "plain text")));
        assertThrows(IllegalArgumentException.class, () -> response("wh-format-result",
                Map.of("selection", model, "finishReason", "length", "text", "{}")));
        assertThrows(IllegalArgumentException.class, () -> response("wh-format-result",
                Map.of("selection", model, "finishReason", "stop", "text", "still not json")));
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("\"workspace/generateText\"")).count());
    }

    @Test void correctionCannotChangeModelOrCallEvenAnOriginallyAuthorizedTool() throws Exception {
        create("{\"type\":\"object\"}"); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "plain text")));
        assertThrows(IllegalArgumentException.class, () -> response("wh-format-result", Map.of("selection",
                AiCliType.selection("another/model", "low"), "finishReason", "stop", "text", "{}")));
        assertThrows(IllegalArgumentException.class, () -> response("wh-format-result", Map.of("selection",
                model, "finishReason", "stop", "text", "{}", "toolCalls", List.of(Map.of("toolName", "monitor_read")))));
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("tool.updated", 3,
                Map.of("kind", "scheduled", "toolName", "mcp__workhub_task__monitor_read"))));
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test void validStructuredOutputDoesNotRequestCorrection() throws Exception {
        create("{\"type\":\"object\"}"); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success",
                "response", "{\"type\":\"NEEDS_INPUT\",\"summary\":\"缺少资料\"}")));
        assertEquals("NEEDS_INPUT", json.readTree(Files.readString(directory.resolve("result.json"))).path("type").asText());
        assertFalse(sent.toString().contains("workspace/generateText"));
    }

    @Test void acknowledgementDoesNotCompleteAndOnlyMatchingSuccessfulTerminalWritesResult() throws Exception {
        create(); response("wh-send", Map.of("sessionId", "session-1", "accepted", true));
        assertFalse(Files.exists(directory.resolve("result.json")));
        started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "{\"status\":\"OK\"}")));
        assertEquals("{\"status\":\"OK\"}", Files.readString(directory.resolve("result.json")));
    }
    @Test void fastCompletionBeforeAcknowledgementIsAcceptedWithoutStartingAnotherTask() throws Exception {
        create(); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "done")));
        response("wh-send", Map.of("sessionId", "session-1", "accepted", true));
        assertEquals(1, sent.toString().lines().filter(line -> line.contains("session/send")).count());
    }
    @Test void cancelledOrBudgetFailureCannotBecomeSuccessfulArtifact() throws Exception {
        create(); started();
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "cancelled", "response", "pretend success"))));
        assertFalse(Files.exists(directory.resolve("result.json")));
    }
    @Test void wrongInputAndWrongSessionAreRejected() throws Exception {
        create(); started();
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 2,
                Map.of("inputId", "old-input", "resultType", "success", "response", "old result"))));
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(json.readTree("{\"method\":\"session/event\",\"params\":{\"sessionId\":\"other\"}}")));
    }
    @Test void lateRepeatedTerminalCannotOverwriteArtifact() throws Exception {
        create(); started();
        conversation.accept(event("turn.completed", 2, Map.of("inputId", inputId, "resultType", "success", "response", "original")));
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("turn.completed", 3, Map.of("inputId", inputId, "resultType", "success", "response", "replacement"))));
        assertEquals("original", Files.readString(directory.resolve("result.json")));
    }
    @Test void toolOutsideTaskAllowlistFailsBeforeAcceptingCliCompletion() throws Exception {
        create(); started();
        assertThrows(IllegalArgumentException.class, () -> conversation.accept(event("tool.updated", 2, Map.of("kind", "scheduled", "toolName", "Bash"))));
    }
    @Test void reverseRequestsDoNotApproveShellPermissionsOrEnableMemory() throws Exception {
        create();
        conversation.accept(json.readTree("{\"id\":21,\"method\":\"session/requestRuntimePreferences\"}"));
        var preferences = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow()).path("result");
        assertFalse(preferences.path("memoryEnabled").asBoolean()); assertFalse(preferences.path("subagentRuntimeConfigEnabled").asBoolean());
        conversation.accept(json.readTree("{\"id\":22,\"method\":\"interaction/requestPermission\"}"));
        assertEquals(-32601, json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow()).path("error").path("code").asInt());
    }
    @Test void modelMismatchDoesNotSubmitBusinessPrompt() throws Exception {
        conversation = new ZCodeTaskConversation(json, directory, directory.resolve("result.json"), "secret task", model, List.of(), null, null);
        conversation.start(sent);
        assertThrows(IllegalArgumentException.class, () -> response("wh-create", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-1"), "settings", Map.of("model", Map.of("current", AiCliType.selection("other/model", "low"))))));
        assertFalse(sent.toString().contains("secret task"));
    }

    @Test void imagePathsAreSubmittedAsSessionSendAttachmentsWithMappedMimeTypes() throws Exception {
        Path snapshot = directory.resolve("需求截图.png");
        var withImages = new ZCodeTaskConversation(json, directory, directory.resolve("result-images.json"), "带图任务", model,
                List.of(), null, null, null, List.of(snapshot.toString()));
        ByteArrayOutputStream images = new ByteArrayOutputStream();
        withImages.start(images);
        withImages.accept(json.valueToTree(Map.of("id", "wh-create", "result", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "session-9"), "settings", Map.of("model", Map.of("current", model))))));
        withImages.accept(json.valueToTree(Map.of("id", "wh-subscribe", "result", Map.of("sessionId", "session-9", "eventSeq", 0, "events", List.of()))));
        var submit = json.readTree(images.toString().lines().reduce((a, b) -> b).orElseThrow());
        assertEquals("session/send", submit.path("method").asText());
        var attachments = submit.path("params").path("attachments");
        assertEquals(1, attachments.size());
        assertEquals("image", attachments.get(0).path("kind").asText());
        assertEquals("需求截图.png", attachments.get(0).path("filename").asText());
        assertEquals("image/png", attachments.get(0).path("mimeType").asText());
        assertEquals(snapshot.toAbsolutePath().normalize().toString(), attachments.get(0).path("localPath").asText());
    }
}
