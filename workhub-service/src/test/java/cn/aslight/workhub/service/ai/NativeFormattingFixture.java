package cn.aslight.workhub.service.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class NativeFormattingFixture {
    private static final ObjectMapper JSON = new ObjectMapper();
    @FunctionalInterface interface Receiver { void accept(JsonNode node) throws Exception; }
    record Turn(Receiver receiver, String session, String input, String turn) {
        JsonNode event(String type, long seq, Map<String, Object> payload) {
            return JSON.valueToTree(Map.of("method", "session/event", "params", Map.of("sessionId", session,
                    "turnId", turn, "seq", seq, "type", type, "payload", payload)));
        }
        void finish(String text) throws Exception {
            receiver.accept(event("turn.completed", 2, Map.of("inputId", input, "resultType", "success", "response", text)));
        }
    }
    static JsonNode last(ByteArrayOutputStream sent) {
        return JSON.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
    }
    static Turn start(Receiver receiver, ByteArrayOutputStream sent) throws Exception {
        var create = last(sent);
        assertEquals("session/create", create.path("method").asText());
        var params = create.path("params");
        assertEquals(JSON.createArrayNode(), params.path("toolAllowlist"));
        assertEquals(JSON.createArrayNode(), params.path("mcpServers"));
        assertFalse(params.path("dynamicWorkflowEnabled").asBoolean());
        assertFalse(params.path("offPeakToolEnabled").asBoolean());
        assertFalse(params.path("titleGenerationEnabled").asBoolean());
        String session = UUID.randomUUID().toString();
        receiver.accept(JSON.valueToTree(Map.of("id", create.path("id").asText(), "result", Map.of(
                "protocol", Map.of("name", "ZCode Protocol", "version", 1), "session", Map.of("sessionId", session),
                "settings", Map.of("model", Map.of("current", params.path("model")))))));
        var subscribe = last(sent);
        assertEquals("session/subscribe", subscribe.path("method").asText());
        receiver.accept(JSON.valueToTree(Map.of("id", subscribe.path("id").asText(), "result", Map.of(
                "sessionId", session, "events", List.of(), "eventSeq", 0))));
        var send = last(sent);
        assertEquals("session/send", send.path("method").asText());
        receiver.accept(JSON.valueToTree(Map.of("id", send.path("id").asText(), "result", Map.of("sessionId", session, "accepted", true))));
        assertFalse(sent.toString().contains("maxOutputTokens"));
        assertFalse(sent.toString().contains("workspace/generateText"));
        var result = new Turn(receiver, session, send.path("params").path("inputId").asText(), UUID.randomUUID().toString());
        receiver.accept(result.event("turn.started", 1, Map.of("inputId", result.input)));
        return result;
    }
}
