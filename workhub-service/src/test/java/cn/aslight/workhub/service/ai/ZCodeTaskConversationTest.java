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

    private void create() throws Exception {
        conversation = new ZCodeTaskConversation(json, directory, directory.resolve("result.json"), "执行指定任务", model,
                List.of("mcp__workhub_task__monitor_read"), "http://127.0.0.1:12345/mcp", "ephemeral-token");
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
