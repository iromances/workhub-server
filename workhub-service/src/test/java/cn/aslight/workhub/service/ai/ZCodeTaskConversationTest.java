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
        var submit = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        inputId = submit.path("params").path("inputId").asText();
        assertEquals(json.valueToTree(model), submit.path("params").path("modelSelection"));
        assertEquals("执行指定任务", submit.path("params").path("content").asText());
    }
    private void response(String id, Object value) throws Exception { conversation.accept(json.valueToTree(Map.of("id", id, "result", value))); }
    private tools.jackson.databind.JsonNode event(String type, long sequence, Map<String, Object> payload) {
        return json.valueToTree(Map.of("method", "session/event", "params", Map.of("sessionId", "session-1", "turnId", "turn-1",
                "seq", sequence, "type", type, "payload", payload)));
    }
    private void started() throws Exception { conversation.accept(event("turn.started", 1, Map.of("inputId", inputId))); }

    @Test void malformedStructuredOutputGetsOneToolFreeCorrectionAndNoTaskReplay() throws Exception {
        create("{\"type\":\"object\"}"); started();
        String progress = conversation.accept(event("turn.completed", 2,
                Map.of("inputId", inputId, "resultType", "success", "response", "这里只整理给定文本，负责人未提供。")));
        assertTrue(progress.contains("校正"));
        assertFalse(Files.exists(directory.resolve("result.json")));
        var correction = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        assertEquals("workspace/generateText", correction.path("method").asText());
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
}
