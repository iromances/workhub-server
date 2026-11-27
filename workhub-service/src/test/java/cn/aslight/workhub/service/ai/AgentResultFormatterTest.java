package cn.aslight.workhub.service.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentResultFormatterTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Object> model = Map.of("providerId", "fixture", "modelId", "model", "options", Map.of("reasoningLevel", "low"));
    private final ByteArrayOutputStream sent = new ByteArrayOutputStream();

    private ZCodeTaskConversation recover(String raw, List<String> saved) throws Exception {
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "不得重做的任务", model, "http://mcp", "fixture", "{}");
        c.resultRecovery(raw, saved::add);
        c.start(sent);
        return c;
    }
    private void reply(ZCodeTaskConversation c, String text) throws Exception {
        NativeFormattingFixture.start(c::accept, sent).finish(text);
    }

    @Test void v2NormalPathExtractsReportAndNeverAsksModelToFormat() throws Exception {
        String raw;
        try (var in = getClass().getResourceAsStream("/agent/report-v2.json")) { raw = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, "巡检完成。\n```json\n" + raw + "\n```", sent);
        formatter.allowModelCorrection(false);
        formatter.start();
        assertEquals(0, sent.size());
        assertEquals(2, json.readTree(Files.readString(directory.resolve("result.json"))).path("schemaVersion").asInt());
    }

    @Test void v2NormalRunDoesNotSilentlyAcceptLegacySummaryOnly() {
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, "{\"type\":\"RESULT\",\"summary\":\"缺少新结构\"}", sent);
        formatter.allowModelCorrection(false);
        assertThrows(IllegalArgumentException.class, formatter::start);
        assertEquals(0, sent.size());
    }

    @Test void v2InvalidNormalResultDoesNotStartSecondModelRequest() {
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, "原文已保存但格式缺字段", sent);
        formatter.allowModelCorrection(false);
        assertThrows(IllegalArgumentException.class, formatter::start);
        assertEquals(0, sent.size());
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    private String v2Report() throws Exception {
        try (var in = getClass().getResourceAsStream("/agent/report-v2.json")) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"extra", "missing", "type", "reference", "assessment"})
    void invalidV2IsCorrectedWithExactErrorAndOriginalContent(String defect) throws Exception {
        var report = json.readTree(v2Report());
        var evidence = (tools.jackson.databind.node.ObjectNode) report.path("report").path("evidence").get(0);
        String path;
        switch (defect) {
            case "extra" -> { evidence.put("limitation_note", "只检查24小时"); path = "evidence[0].limitation_note"; }
            case "missing" -> { evidence.remove("limitations"); path = "evidence[0].limitations"; }
            case "type" -> { evidence.put("limitations", 1); path = "evidence[0].limitations"; }
            case "reference" -> { evidence.put("targetKey", "missing"); path = "evidence.evidence-1"; }
            default -> { ((tools.jackson.databind.node.ObjectNode) report.path("report")).put("assessment", "NORMAL"); path = "report.assessment"; }
        }
        String raw = json.writeValueAsString(report);
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, raw, sent);
        formatter.requireV2(true);
        formatter.start();
        var turn = NativeFormattingFixture.start(formatter::accept, sent);
        var request = NativeFormattingFixture.last(sent);
        String prompt = request.path("params").path("content").asText();
        assertTrue(prompt.contains(path), prompt);
        assertTrue(prompt.contains(json.writeValueAsString(raw)));
        assertFalse(Files.exists(directory.resolve("result.json")));
        var corrected = json.readTree(v2Report());
        if (defect.equals("extra")) ((tools.jackson.databind.node.ObjectNode) corrected.path("report").path("evidence").get(0))
                .put("limitations", "隔离测试；只检查24小时");
        turn.finish(json.writeValueAsString(corrected));
        assertEquals(corrected, json.readTree(Files.readString(directory.resolve("result.json"))));
    }

    @Test void v2CorrectionsCannotDowngradeAndStopAfterTwoRequests() throws Exception {
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, "不完整的报告", sent);
        formatter.requireV2(true);
        formatter.start();
        String legacy = "{\"type\":\"RESULT\",\"summary\":\"已完成\"}";
        NativeFormattingFixture.start(formatter::accept, sent).finish(legacy);
        var second = NativeFormattingFixture.start(formatter::accept, sent);
        var failure = assertThrows(IllegalArgumentException.class, () -> second.finish(legacy));
        assertTrue(failure.getMessage().contains("schemaVersion"));
        assertEquals(2, sent.toString().lines().filter(line -> line.contains("session/send")).count());
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test void v2RecoveryAlsoRejectsLegacyDowngrade() throws Exception {
        String invalid = v2Report().replace("\"limitations\": \"隔离测试\"", "\"limitations\": \"隔离测试\", \"limitation_note\": \"未核实\"");
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, invalid, sent);
        formatter.start();
        NativeFormattingFixture.start(formatter::accept, sent).finish("{\"type\":\"RESULT\",\"summary\":\"已完成\"}");
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals("session/create", NativeFormattingFixture.last(sent).path("method").asText());
    }

    @Test void largeChineseReportAndFencesArePreservedWithoutModelCall() throws Exception {
        String raw = json.writeValueAsString(Map.of("type", "RESULT", "summary", "这是完整的报告。".repeat(20000)));
        var saved = new ArrayList<String>();
        recover("```json\n" + raw + "\n```", saved);
        assertEquals(raw, Files.readString(directory.resolve("result.json")));
        assertEquals(1, saved.size());
        assertEquals(0, sent.size());
    }

    @Test void recoverySavesBeforeRequestAndNeverStartsBusinessSession() throws Exception {
        var saved = new ArrayList<String>();
        var c = recover("已经检查，缺少部分证据。", saved);
        assertEquals(List.of("已经检查，缺少部分证据。"), saved);
        var turn = NativeFormattingFixture.start(c::accept, sent);
        assertTrue(NativeFormattingFixture.last(sent).path("params").path("content").asText().contains("ELI5"));
        assertFalse(Files.exists(directory.resolve("result.json")), "ACK 不能作为终态");
        turn.finish("{\"type\":\"NEEDS_INPUT\",\"summary\":\"还需要证据\"}");
        assertFalse(sent.toString().contains("不得重做的任务"));
        assertFalse(sent.toString().contains("mcp/list"));
    }

    @Test void nativeResultPreservesAllCharactersWithoutWorkhubTokenOrByteLimit() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        String text = json.writeValueAsString(Map.of("type", "RESULT", "summary", "完整事实".repeat(150000)));
        NativeFormattingFixture.start(c::accept, sent).finish(text);
        assertEquals(text, Files.readString(directory.resolve("result.json")));
    }

    @Test void nativeLengthEventsAccumulateOnlyCompletedMainTurnChunks() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        var turn = NativeFormattingFixture.start(c::accept, sent);
        c.accept(turn.event("session.updated", 2, Map.of("querySource", "auxiliary", "stopReason", "length", "content", "忽略")));
        c.accept(turn.event("session.updated", 3, Map.of("querySource", "main_turn", "stopReason", "length",
                "content", "{\"type\":\"RESULT\",\"summary\":\"前半段", "toolCallCount", 0)));
        c.accept(turn.event("session.updated", 4, Map.of("querySource", "main_turn", "stopReason", "stop",
                "content", "后半段\"}", "toolCallCount", 0)));
        c.accept(turn.event("turn.completed", 5, Map.of("inputId", turn.input(), "resultType", "success", "response", "后半段\"}")));
        assertEquals("前半段后半段", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
    }

    @Test void exhaustedNativeLengthCannotBecomeSuccessfulArtifact() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        var turn = NativeFormattingFixture.start(c::accept, sent);
        c.accept(turn.event("session.updated", 2, Map.of("querySource", "main_turn", "stopReason", "length", "content", "{}", "toolCallCount", 0)));
        assertThrows(IllegalArgumentException.class, () -> c.accept(turn.event("turn.completed", 3,
                Map.of("inputId", turn.input(), "resultType", "success", "response", "{}"))));
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test void repeatedMalformedOutputFailsWithoutInventingSuccessOrReplayingTools() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        reply(c, "不是 JSON");
        reply(c, "{}");
        assertThrows(IllegalArgumentException.class, () -> reply(c, ""));
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals(3, sent.toString().lines().filter(line -> line.contains("session/send")).count());
    }

    @Test void toolsWrongIdentityCancellationAndDuplicatesAreRejected() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        var turn = NativeFormattingFixture.start(c::accept, sent);
        assertThrows(IllegalArgumentException.class, () -> c.accept(turn.event("tool.updated", 2,
                Map.of("kind", "scheduled", "toolName", "Bash"))));
        assertThrows(IllegalArgumentException.class, () -> c.accept(turn.event("turn.completed", 3,
                Map.of("inputId", "wrong", "resultType", "success", "response", "{}"))));
        assertThrows(IllegalArgumentException.class, () -> c.accept(turn.event("turn.completed", 4,
                Map.of("inputId", turn.input(), "resultType", "cancelled", "response", "{}"))));
        assertFalse(Files.exists(directory.resolve("result.json")));
        c.accept(turn.event("turn.completed", 5, Map.of("inputId", turn.input(), "resultType", "success",
                "response", "{\"type\":\"RESULT\",\"summary\":\"完成\"}")));
        assertThrows(IllegalArgumentException.class, () -> c.accept(turn.event("turn.completed", 6,
                Map.of("inputId", turn.input(), "resultType", "success", "response", "{}"))));
    }

    @Test void lateAckAndRuntimePreferencesRemainIsolatedDuringCorrection() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        var first = NativeFormattingFixture.start(c::accept, sent);
        String oldSendId = NativeFormattingFixture.last(sent).path("id").asText();
        first.finish("not json");
        c.accept(json.valueToTree(Map.of("id", oldSendId, "result", Map.of("sessionId", first.session(), "accepted", true))));
        var next = NativeFormattingFixture.start(c::accept, sent);
        c.accept(json.valueToTree(Map.of("id", "preferences", "method", "session/requestRuntimePreferences")));
        var preferences = NativeFormattingFixture.last(sent).path("result");
        assertFalse(preferences.path("memoryEnabled").asBoolean());
        assertFalse(preferences.path("nativeSearchEnhancementsEnabled").asBoolean());
        assertFalse(preferences.path("subagentRuntimeConfigEnabled").asBoolean());
        c.accept(json.valueToTree(Map.of("id", "permission", "method", "interaction/requestPermission")));
        assertEquals(-32601, NativeFormattingFixture.last(sent).path("error").path("code").asInt());
        next.finish("{\"type\":\"RESULT\",\"summary\":\"完整结果\"}");
        assertEquals("完整结果", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
    }

    @Test void persistenceFailurePreventsFormattingRequest() {
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model, null, null, "{}");
        c.resultRecovery("原始报告", ignored -> { throw new IllegalStateException("storage unavailable"); });
        assertThrows(IllegalStateException.class, () -> c.start(sent));
        assertEquals(0, sent.size());
    }

    @Test void normalRunPreservesConfiguredReasoningForNativeFormatting() throws Exception {
        var high = Map.<String, Object>of("providerId", "fixture", "modelId", "model", "options", Map.of("reasoningLevel", "high"));
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", high, null, null, cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA);
        var saved = new ArrayList<String>();
        c.resultRecovery(null, saved::add);
        c.start(sent);
        c.accept(json.valueToTree(Map.of("id", "wh-create", "result", Map.of("protocol", Map.of("name", "ZCode Protocol", "version", 1),
                "session", Map.of("sessionId", "s"), "settings", Map.of("model", Map.of("current", high, "available", List.of(
                        Map.of("ref", Map.of("providerId", "fixture", "modelId", "model"), "reasoning", Map.of("levels", List.of(Map.of("value", "low"), Map.of("value", "high")))))))))));
        c.accept(json.valueToTree(Map.of("id", "wh-subscribe", "result", Map.of("sessionId", "s", "eventSeq", 0, "events", List.of()))));
        var submit = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        String input = submit.path("params").path("inputId").asText();
        for (int seq = 1; seq <= 2; seq++) {
            c.accept(json.valueToTree(Map.of("method", "session/event", "params", Map.of("sessionId", "s", "turnId", "t", "seq", seq,
                    "type", seq == 1 ? "turn.started" : "turn.completed", "payload", Map.of("inputId", input, "resultType", "success", "response", "完整原始答复")))));
        }
        var request = json.readTree(sent.toString().lines().reduce((a, b) -> b).orElseThrow());
        assertEquals("high", request.path("params").path("model").path("options").path("reasoningLevel").asText());
        assertEquals(List.of("完整原始答复"), saved);
        reply(c, v2Report());
    }

    @Test void protocolReaderAcceptsLargeAgentReportButStillRejectsIncompleteFrame() throws Exception {
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model, null, null, "{}");
        c.resultRecovery("需要整理", ignored -> { });
        c.start(sent);
        String result = json.writeValueAsString(Map.of("type", "RESULT", "summary", "中文事实".repeat(150000)));
        var turn = NativeFormattingFixture.start(c::accept, sent);
        String frame = json.writeValueAsString(turn.event("turn.completed", 2,
                Map.of("inputId", turn.input(), "resultType", "success", "response", result)));
        ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream((frame + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)), c, ignored -> { });
        assertEquals(result, Files.readString(directory.resolve("result.json")));
        assertThrows(java.io.IOException.class, () -> ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8)), c, ignored -> { }));
    }
}
