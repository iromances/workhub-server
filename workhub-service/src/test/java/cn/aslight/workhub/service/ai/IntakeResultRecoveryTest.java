package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class IntakeResultRecoveryTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Object> model = Map.of("providerId", "fixture", "modelId", "model", "options", Map.of("reasoningLevel", "low"));
    private static final String SCHEMA = """
        {"type":"object","properties":{"confirmationItems":{"type":"array","items":{"type":"object","properties":{"title":{"type":"string"}},"required":["title"],"additionalProperties":false}},"riskItems":{"type":"array"}},"required":["confirmationItems","riskItems"],"additionalProperties":false}
        """;

    @Test void longClarificationRestoresWithoutAgentEnvelopeOrBusinessToolCalls() throws Exception {
        String report = json.writeValueAsString(Map.of("confirmationItems", List.of(Map.of("title", "完整事实".repeat(25000))), "riskItems", List.of()));
        var saved = new ArrayList<String>(); var stream = new ByteArrayOutputStream();
        var conversation = ZCodeTaskConversation.structuredNoCliSchema(json, directory, directory.resolve("result.json"), "业务任务", model,
                List.of("read_repository_text_file"), "http://fixture/mcp", "token", SCHEMA, List.of());
        conversation.analysisRecovery(new AiResultRecovery.Session(report, saved::add));
        conversation.start(stream);
        assertEquals(report, Files.readString(directory.resolve("result.json")));
        assertEquals(List.of(report), saved);
        assertEquals(0, stream.size());
        assertTrue(conversation.unlimitedResultMessages());
    }

    @Test void missingFieldsAndWrongNestedTypesRequireFormattingAndCanContinue() throws Exception {
        var stream = new ByteArrayOutputStream();
        var conversation = ZCodeTaskConversation.structuredNoCliSchema(json, directory, directory.resolve("result.json"), "task", model,
                List.of("read_repository_text_file"), "http://fixture/mcp", "token", SCHEMA, List.of());
        conversation.analysisRecovery(new AiResultRecovery.Session("{\"confirmationItems\":[{\"title\":42}],\"riskItems\":[]}", ignored -> { }));
        conversation.start(stream);
        assertTrue(stream.toString().contains("workspace/generateText"));
        assertFalse(stream.toString().contains("session/send"));
        assertFalse(stream.toString().contains("maxOutputTokens"));
        assertFalse(stream.toString().contains("NEEDS_INPUT"));
        conversation.accept(json.valueToTree(Map.of("id", "wh-agent-format-1", "result", Map.of("selection", model, "finishReason", "length", "text", "{\"confirmationItems\":[{\"title\":\"问题"))));
        conversation.accept(json.valueToTree(Map.of("id", "wh-agent-format-2", "result", Map.of("selection", model, "finishReason", "stop", "text", "一\"}],\"riskItems\":[]}"))));
        assertEquals("问题一", json.readTree(Files.readString(directory.resolve("result.json"))).path("confirmationItems").get(0).path("title").asText());
    }

    @Test void rawOutputIsMaskedAndPersistenceFailureStopsRecovery() {
        var saved = new HashMap<String, String>();
        var recovery = new AiResultRecovery(saved::get, saved::put);
        recovery.open("fingerprint").save().accept("token=secret-value\n" + "完整内容".repeat(30000));
        assertFalse(saved.get("fingerprint").contains("secret-value"));
        assertTrue(saved.get("fingerprint").endsWith("完整内容"));
        assertEquals(saved.get("fingerprint"), recovery.open("fingerprint").original());
    }

    @Test void fingerprintChangesForInputAndDirtyOrChangedRepository() throws Exception {
        git("init"); Files.writeString(directory.resolve("source.txt"), "first"); git("add", ".");
        git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", "commit", "-m", "fixture");
        var request = AiGatewayRequest.withVariables("intake.clarification.analyze", Map.of(), directory, List.of(directory.toString()), List.of(), SCHEMA);
        String first = AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA);
        assertEquals(first, AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA));
        assertNotEquals(first, AiAnalysisFingerprint.create(request, 1L, "model", "low", "new input", SCHEMA));
        Files.writeString(directory.resolve("source.txt"), "dirty");
        assertNotEquals(first, AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA));
        assertNotEquals(AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA), AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA));
        git("add", "."); git("-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid", "-c", "commit.gpgsign=false", "commit", "-m", "changed");
        assertNotEquals(first, AiAnalysisFingerprint.create(request, 1L, "model", "low", "original", SCHEMA));
    }

    private void git(String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", directory.toString())); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        assertEquals(0, process.waitFor(), output);
    }
}
