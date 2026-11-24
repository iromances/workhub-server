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
    private void reply(ZCodeTaskConversation c, int attempt, String reason, String text) throws Exception {
        c.accept(json.valueToTree(Map.of("id", "wh-agent-format-" + attempt, "result", Map.of("selection", model, "finishReason", reason, "text", text))));
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
        var request = json.readTree(sent.toString());
        String prompt = request.path("params").path("messages").get(0).path("content").asText();
        assertTrue(prompt.contains(path), prompt);
        assertTrue(prompt.contains(json.writeValueAsString(raw)));
        assertTrue(request.path("params").path("tools").isEmpty());
        assertFalse(Files.exists(directory.resolve("result.json")));
        var corrected = json.readTree(v2Report());
        if (defect.equals("extra")) ((tools.jackson.databind.node.ObjectNode) corrected.path("report").path("evidence").get(0))
                .put("limitations", "隔离测试；只检查24小时");
        formatter.accept(json.valueToTree(Map.of("id", "wh-agent-format-1", "result", Map.of(
                "selection", model, "finishReason", "stop", "text", json.writeValueAsString(corrected)))));
        assertEquals(corrected, json.readTree(Files.readString(directory.resolve("result.json"))));
    }

    @Test void v2CorrectionsCannotDowngradeAndStopAfterTwoRequests() throws Exception {
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, "不完整的报告", sent);
        formatter.requireV2(true);
        formatter.start();
        String legacy = "{\"type\":\"RESULT\",\"summary\":\"已完成\"}";
        formatter.accept(json.valueToTree(Map.of("id", "wh-agent-format-1", "result", Map.of(
                "selection", model, "finishReason", "stop", "text", legacy))));
        assertTrue(sent.toString().contains("schemaVersion"));
        var failure = assertThrows(IllegalArgumentException.class, () -> formatter.accept(json.valueToTree(Map.of(
                "id", "wh-agent-format-2", "result", Map.of("selection", model, "finishReason", "stop", "text", legacy)))));
        assertTrue(failure.getMessage().contains("schemaVersion"));
        assertEquals(2, sent.toString().lines().count());
        assertFalse(Files.exists(directory.resolve("result.json")));
    }

    @Test void v2RecoveryAlsoRejectsLegacyDowngrade() throws Exception {
        String invalid = v2Report().replace("\"limitations\": \"隔离测试\"", "\"limitations\": \"隔离测试\", \"limitation_note\": \"未核实\"");
        var formatter = new AgentResultFormatter(json, directory, directory.resolve("result.json"), model,
                cn.aslight.workhub.service.agent.AgentReportContract.SCHEMA, invalid, sent);
        formatter.start();
        formatter.accept(json.valueToTree(Map.of("id", "wh-agent-format-1", "result", Map.of(
                "selection", model, "finishReason", "stop", "text", "{\"type\":\"RESULT\",\"summary\":\"已完成\"}"))));
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals(2, sent.toString().lines().count());
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
        var request = json.readTree(sent.toString());
        assertEquals("workspace/generateText", request.path("method").asText());
        assertFalse(request.path("params").has("maxOutputTokens"));
        assertEquals(0, request.path("params").path("tools").size());
        assertTrue(request.toString().contains("ELI5"));
        reply(c, 1, "stop", "{\"type\":\"NEEDS_INPUT\",\"summary\":\"还需要证据\"}");
        assertFalse(sent.toString().contains("session/send"));
        assertFalse(sent.toString().contains("mcp/list"));
    }

    @Test void lengthContinuationPreservesAllCharacters() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        reply(c, 1, "length", "{\"type\":\"RESULT\",\"summary\":\"第一段");
        assertFalse(Files.exists(directory.resolve("result.json")));
        reply(c, 2, "stop", "第二段\"}");
        assertEquals("第一段第二段", json.readTree(Files.readString(directory.resolve("result.json"))).path("summary").asText());
    }

    @Test void repeatedMalformedOutputFailsWithoutInventingSuccessOrReplayingTools() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        reply(c, 1, "stop", "不是 JSON");
        reply(c, 2, "stop", "{}");
        assertThrows(IllegalArgumentException.class, () -> reply(c, 3, "length", ""));
        assertFalse(Files.exists(directory.resolve("result.json")));
        assertEquals(3, sent.toString().lines().count());
    }

    @Test void toolsAndWrongResponseIdentityAreRejected() throws Exception {
        var c = recover("原始报告", new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> reply(c, 2, "stop", "{}"));
        assertThrows(IllegalArgumentException.class, () -> c.accept(json.valueToTree(Map.of("id", "wh-agent-format-1", "result",
                Map.of("selection", model, "finishReason", "stop", "text", "{}", "toolCalls", List.of(Map.of("name", "write")))))));
    }

    @Test void persistenceFailurePreventsFormattingRequest() {
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model, null, null, "{}");
        c.resultRecovery("原始报告", ignored -> { throw new IllegalStateException("storage unavailable"); });
        assertThrows(IllegalStateException.class, () -> c.start(sent));
        assertEquals(0, sent.size());
    }

    @Test void normalRunUsesOnlyCatalogSupportedLowReasoningForFormatting() throws Exception {
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
        assertEquals("low", request.path("params").path("selection").path("options").path("reasoningLevel").asText());
        assertEquals(List.of("完整原始答复"), saved);
        reply(c, 1, "stop", v2Report());
    }

    @Test void protocolReaderAcceptsLargeAgentReportButStillRejectsIncompleteFrame() throws Exception {
        var c = ZCodeTaskConversation.agent(json, directory, directory.resolve("result.json"), "task", model, null, null, "{}");
        c.resultRecovery("需要整理", ignored -> { });
        c.start(sent);
        String result = json.writeValueAsString(Map.of("type", "RESULT", "summary", "中文事实".repeat(150000)));
        String frame = json.writeValueAsString(Map.of("id", "wh-agent-format-1", "result", Map.of("selection", model, "finishReason", "stop", "text", result)));
        ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream((frame + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)), c, ignored -> { });
        assertEquals(result, Files.readString(directory.resolve("result.json")));
        assertThrows(java.io.IOException.class, () -> ZCodeCliClient.readProtocol(new java.io.ByteArrayInputStream(frame.getBytes(java.nio.charset.StandardCharsets.UTF_8)), c, ignored -> { }));
    }
}
