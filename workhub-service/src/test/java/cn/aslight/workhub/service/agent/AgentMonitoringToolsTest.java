package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.ops.*;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ops.*;
import cn.aslight.workhub.service.ops.*;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import cn.aslight.workhub.service.autonomy.AutonomyEvidence;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentMonitoringToolsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentAccessService access = mock(AgentAccessService.class);
    private final OpsMonitorService business = mock(OpsMonitorService.class);
    private final OpsMonitorMapper monitors = mock(OpsMonitorMapper.class);
    private final AccountingMonitorService accounting = mock(AccountingMonitorService.class);
    private final AccountingMonitorMapper configs = mock(AccountingMonitorMapper.class);
    private final AccountingRunMapper runs = mock(AccountingRunMapper.class);
    private final AgentObservationService observations = mock(AgentObservationService.class);
    private final AgentMonitorReadMapper history = mock(AgentMonitorReadMapper.class);
    private final SystemAlertService alerts = mock(SystemAlertService.class);
    private final AgentMonitoringTools tools = new AgentMonitoringTools(access, mock(AgentMapper.class), business, monitors,
            accounting, configs, runs, alerts, mock(BusinessLogSearchService.class), json, new AgentToolData(json), observations, history);
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final AgentToolContext context = new AgentToolContext("run", "task", "agent", "owner",
            new AgentConfiguration("巡检", "巡检", 1L, null, List.of(scope), List.of(), 20, 60, 1),
            new AgentTaskSpecification("巡检", "", "报告事实", List.of("REPORT"), List.of(scope), List.of()));

    @BeforeEach void observationCapture() {
        when(observations.capture(any(), anyString(), any())).thenAnswer(invocation -> new AgentToolData(json).sanitize(invocation.getArgument(2)));
    }

    @Test
    void proactiveQueryDistinguishesNeverRunUnimplementedAndOldDataWithoutAlertInput() {
        var never = json.readValue("{\"id\":1,\"name\":\"调度\",\"monitorType\":\"XXL_JOB\",\"enabled\":true}", OpsMonitorResponse.class);
        var unavailable = json.readValue("{\"id\":2,\"name\":\"消息\",\"monitorType\":\"MQ\",\"enabled\":true}", OpsMonitorResponse.class);
        var old = json.readValue(json.writeValueAsString(Map.of("id", 3, "name", "旧采集", "monitorType", "XXL_JOB", "enabled", true,
                "lastStatus", "NORMAL", "lastCheckedAt", LocalDateTime.now().minusDays(3))), OpsMonitorResponse.class);
        when(business.list(null, "BL000012", "TEST", null, false)).thenReturn(List.of(never, unavailable, old));
        JsonNode result = (JsonNode) tools.agentBusinessMonitorTool().execute(context, scopeArguments());
        assertEquals("NEVER_RUN", result.path("data").get(0).path("dataState").asText());
        assertEquals("UNAVAILABLE", result.path("data").get(1).path("dataState").asText());
        assertTrue(result.path("data").get(2).path("dataAgeSeconds").asLong() >= 259199);
        verify(access).requireScope(context, scope);
    }

    @Test
    void noMonitorConfigurationDoesNotBecomeHealthy() {
        when(business.list(null, "BL000012", "TEST", null, false)).thenReturn(List.of());
        JsonNode result = (JsonNode) tools.agentBusinessMonitorTool().execute(context, scopeArguments());
        assertEquals("NO_CONFIGURATION", result.path("coverage").asText());
        assertEquals(0, result.path("data").size());
    }

    @Test
    void accountingFiltersEnvironmentAndPreservesExecutionFailureMeaning() {
        var testConfig = config(1, "TEST");
        var productionConfig = config(2, "PROD");
        var failed = json.readValue("{\"id\":8,\"configId\":1,\"status\":\"FAILED\",\"anomalyCount\":0}", AccountingRunEntity.class);
        when(accounting.dashboard("BL000012")).thenReturn(new AccountingDashboardResponse(null,
                List.of(new AccountingMonitorStatusResponse(testConfig, failed), new AccountingMonitorStatusResponse(productionConfig, null))));
        JsonNode result = (JsonNode) tools.agentAccountingStatusTool().execute(context, scopeArguments());
        assertEquals(1, result.path("data").size());
        assertEquals("EXECUTION_FAILED", result.path("data").get(0).path("dataState").asText());
        assertEquals("FAILED", result.path("data").get(0).path("latestRun").path("status").asText());
    }

    @Test
    void numericIdCannotBypassEnvironmentScopeBeforeLoadingSensitiveResults() {
        var run = json.readValue("{\"id\":8,\"configId\":2,\"status\":\"SUCCESS\"}", AccountingRunEntity.class);
        when(runs.findById(8L)).thenReturn(run);
        when(configs.findById(2L)).thenReturn(config(2, "PROD"));
        doThrow(new AccessDeniedException("范围外")).when(access).requireScope(context, new AgentScope("BL000012", "PROD"));
        assertThrows(AccessDeniedException.class, () -> tools.agentAccountingDetailTool().execute(context, json.readTree("{\"runId\":8}")));
        verifyNoInteractions(history);
    }

    @Test
    void rejectsUnknownAndMalformedToolParametersBeforeBusinessQuery() {
        assertThrows(IllegalArgumentException.class, () -> tools.agentBusinessMonitorTool().execute(context,
                json.readTree("{\"businessLineCode\":\"BL000012\",\"environmentCode\":\"TEST\",\"sql\":\"select 1\"}")));
        assertThrows(IllegalArgumentException.class, () -> tools.agentAccountingDetailTool().execute(context, json.readTree("{\"runId\":\"8\"}")));
        verifyNoInteractions(business, accounting, runs);
    }

    @Test
    void nestedSamplesAndSecretObjectsAreMaskedWithoutChangingFinancialAmount() {
        var masker = new AgentToolData(json);
        JsonNode result = masker.sanitize(Map.of("differenceAmount", 123.45, "sampleJson", "{\"accountNo\":\"6222021234567890123\",\"phone\":\"13812345678\"}",
                "credentials", Map.of("value", "hidden-secret"), "message", "Authorization: Bearer private-value"));
        String text = result.toString();
        assertFalse(text.contains("hidden-secret"));
        assertFalse(text.contains("private-value"));
        assertFalse(text.contains("6222021234567890123"));
        assertFalse(text.contains("13812345678"));
        assertTrue(result.path("differenceAmount").isNumber());
        assertEquals("123.45", result.path("differenceAmount").asText());
    }

    @Test
    void oversizedToolResultExplicitlyReportsIncompleteCoverage() {
        JsonNode result = new AgentToolData(json).sanitize(Map.of("data", "x".repeat(60000)));
        assertTrue(result.path("truncated").asBoolean());
        assertTrue(result.path("message").asText().contains("不能据此判定全量健康"));
    }

    @Test
    void actualXxlLogDtoCanBeRegisteredAsFailureEvidenceAndKeepsRequestedWindow() {
        var monitor = new OpsMonitorEntity(); monitor.setId(7L); monitor.setBusinessLineCode("BL000012"); monitor.setEnvironmentCode("TEST");
        when(monitors.findById(7L)).thenReturn(monitor);
        var page = json.readValue("""
                {"id":7,"status":"UP","total":4,"page":2,"pageSize":2,"logs":[
                 {"jobId":20,"logId":30,"failureType":"EXECUTION_FAILED","handleTime":"2026-10-12 10:00:00.0"}]}
                """, XxlJobLogPageResponse.class);
        when(business.xxlJobLogs(7L, java.time.LocalDate.parse("2026-10-01"), java.time.LocalDate.parse("2026-10-31"), 2, 2, null, null, "FAILED")).thenReturn(page);
        var result = (JsonNode) tools.agentBusinessDetailTool().execute(context, json.readTree("""
                {"monitorId":7,"fromDate":"2026-10-01","toDate":"2026-10-31","page":2,"pageSize":2}
                """));
        assertEquals("2026-10-01", result.at("/query/fromDate").asText());
        assertEquals(4, result.at("/data/total").asInt());
        var extracted = new AutonomyEvidence(json).extract(observation("monitor.business.detail", result), "/data/logs/0", "EXECUTION_FAILURE", null);
        assertEquals("monitor:7:log:30", extracted.sourceEventId());
        verify(business, never()).xxlJobDetail(anyLong(), any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    void failedXxlQueryIsNotReportedAsSuccessfulEmptyCoverage() {
        var monitor = new OpsMonitorEntity(); monitor.setBusinessLineCode("BL000012"); monitor.setEnvironmentCode("TEST");
        when(monitors.findById(7L)).thenReturn(monitor);
        var failed = new XxlJobLogPageResponse(7L, "monitor", "BL000012", "TEST", "调度", "fixture",
                "ERROR", "database unavailable", LocalDateTime.now(), 0, 1, 10, List.of());
        when(business.xxlJobLogs(eq(7L), any(), any(), eq(1), eq(10), isNull(), isNull(), eq("FAILED")))
                .thenReturn(failed);
        var result = (JsonNode) tools.agentBusinessDetailTool().execute(context, json.readTree("{\"monitorId\":7}"));
        assertEquals("QUERY_FAILED", result.path("coverage").asText());
        assertTrue(result.at("/data/message").asText().contains("unavailable"));
    }

    @Test
    void emptyLaterPageAndMissingSpecificMonitorCannotClaimWholeScopeUnconfigured() {
        var monitor = json.readValue("{\"id\":1,\"monitorType\":\"XXL_JOB\",\"enabled\":true}", OpsMonitorResponse.class);
        when(business.list(null, "BL000012", "TEST", null, false)).thenReturn(List.of(monitor));
        var args = (tools.jackson.databind.node.ObjectNode) scopeArguments(); args.put("page", 2).put("pageSize", 1);
        var result = (JsonNode) tools.agentBusinessMonitorTool().execute(context, args);
        assertTrue(result.path("data").isEmpty()); assertEquals(1, result.at("/pagination/total").asInt());
        assertEquals("QUERY_SUCCEEDED", result.path("coverage").asText());
        args.put("monitorId", 9);
        result = (JsonNode) tools.agentBusinessMonitorTool().execute(context, args);
        assertEquals("NO_MATCHING_CONFIGURATION", result.path("coverage").asText());
        var unknown = observation("monitor.business.status", result);
        assertThrows(IllegalArgumentException.class, () -> new AutonomyEvidence(json).extract(unknown, "/data", "MONITORING_GAP", null));
    }

    @Test
    void historicalAccountingFindsFailureBehindLatestSuccessAndKeepsEnvironmentConstraint() {
        var success = run(10, "SUCCESS"); var failed = run(9, "FAILED"); var earlier = run(8, "SUCCESS");
        when(history.accountingRuns("BL000012", "TEST", null, 0, 3)).thenReturn(List.of(success, failed, earlier));
        var args = (tools.jackson.databind.node.ObjectNode) scopeArguments(); args.put("pageSize", 2);
        var result = (JsonNode) tools.agentAccountingRunsTool().execute(context, args);
        assertEquals(2, result.at("/data/runs").size()); assertTrue(result.at("/data/hasMore").asBoolean());
        assertEquals(9, result.at("/data/nextBeforeRunId").asLong());
        assertEquals("CURRENT_MONITOR_CONFIGURATION", result.path("scopeBasis").asText());
        assertFalse(result.path("historicalEnvironmentVerified").asBoolean());
        var evidence = new AutonomyEvidence(json);
        var failure = evidence.extract(observation("monitor.accounting.runs", result), "/data/runs/1", "EXECUTION_FAILURE", null);
        assertEquals("run:9", failure.sourceEventId());
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(observation("monitor.accounting.runs", result), "/data/runs/0", "EXECUTION_FAILURE", null));
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(observation("monitor.accounting.runs", result), "/data/runs/1", "DATA_DISCREPANCY", null));
        verify(access).requireScope(context, scope);
    }

    @Test
    void accountingRulesCanContinuePastFirstPageAndExposeUnfinishedRun() {
        when(runs.findById(8L)).thenReturn(run(8, "RUNNING")); when(configs.findById(1L)).thenReturn(config(1, "TEST"));
        when(history.accountingResults(8L, 20L, 3)).thenReturn(List.of(rule(21), rule(22), rule(23)));
        var result = (JsonNode) tools.agentAccountingDetailTool().execute(context, json.readTree("{\"runId\":8,\"afterResultId\":20,\"pageSize\":2}"));
        assertEquals(21, result.at("/data/results/0/id").asLong()); assertEquals(2, result.at("/data/results").size());
        assertTrue(result.at("/data/hasMore").asBoolean()); assertEquals(22, result.at("/data/nextAfterResultId").asLong());
        assertFalse(result.at("/data/runFinished").asBoolean());
        assertEquals("run:8:result:21", new AutonomyEvidence(json).extract(observation("monitor.accounting.detail", result), "/data/results/0", "DATA_DISCREPANCY", null).sourceEventId());
        verify(accounting, never()).detail(anyLong());
    }

    @Test
    void alertPaginationCanReuseTheExactReturnedTimeWindow() {
        var from = java.time.LocalDateTime.ofInstant(java.time.Instant.parse("2026-10-01T00:00:00Z"), java.time.ZoneId.systemDefault());
        var to = from.plusDays(2);
        when(alerts.dashboard(eq("BL000012"), eq("TEST"), isNull(), isNull(), isNull(), isNull(), eq(from), eq(to), anyInt(), eq(2)))
                .thenReturn(new SystemAlertDashboardResponse(0, 1, 2, List.of(), List.of(), List.of()));
        var args = (tools.jackson.databind.node.ObjectNode) scopeArguments(); args.put("from", "2026-10-01T00:00:00Z").put("to", "2026-10-03T00:00:00Z").put("pageSize", 2);
        var first = (JsonNode) tools.agentSystemAlertsTool().execute(context, args);
        args.put("from", first.at("/query/from").asText()).put("to", first.at("/query/to").asText()).put("page", 2);
        tools.agentSystemAlertsTool().execute(context, args);
        verify(alerts).dashboard("BL000012", "TEST", null, null, null, null, from, to, 2, 2);
    }

    @Test
    void invalidPageAndTimeInputsAreRejectedBeforeReadingSources() {
        var args = (tools.jackson.databind.node.ObjectNode) scopeArguments(); args.put("page", 0);
        assertThrows(IllegalArgumentException.class, () -> tools.agentBusinessMonitorTool().execute(context, args));
        for (String input : List.of("{\"monitorId\":7,\"fromDate\":\"2026-10-01\"}",
                "{\"monitorId\":7,\"fromDate\":\"2026-10-01\",\"toDate\":\"2026-11-02\"}", "{\"monitorId\":7,\"pageSize\":21}")) {
            assertThrows(IllegalArgumentException.class, () -> tools.agentBusinessDetailTool().execute(context, json.readTree(input)));
        }
        assertThrows(IllegalArgumentException.class, () -> tools.agentAccountingDetailTool().execute(context, json.readTree("{\"runId\":8,\"afterResultId\":-1}")));
        args.remove("page"); args.put("from", "2026-10-01T00:00:00Z").put("to", "2026-10-02T00:00:00Z").put("minutes", 10);
        assertThrows(IllegalArgumentException.class, () -> tools.agentSystemAlertsTool().execute(context, args));
        verifyNoInteractions(business, monitors, history, accounting, runs, alerts);
    }

    private AccountingRunEntity run(long id, String status) {
        return json.readValue(json.writeValueAsString(Map.of("id", id, "configId", 1, "status", status)), AccountingRunEntity.class);
    }
    private AccountingResultEntity rule(long id) {
        return json.readValue(json.writeValueAsString(Map.of("id", id, "runId", 8, "ruleCode", "AMOUNT" + id, "status", "WARN", "differenceAmount", 123.45)), AccountingResultEntity.class);
    }
    private AgentObservationEntity observation(String code, JsonNode result) {
        return new AgentObservationEntity("observation", "run", "agent", "call", code, AgentObservationService.sourceType(code, "BL000012"),
                "BL000012", "TEST", result.toString(), "hash", LocalDateTime.now(java.time.ZoneOffset.UTC));
    }

    private JsonNode scopeArguments() { return json.valueToTree(scope); }
    private AccountingMonitorConfigEntity config(long id, String environment) {
        return json.readValue(json.writeValueAsString(Map.of("id", id, "businessLineCode", "BL000012", "environmentCode", environment,
                "enabled", true, "displayName", "账务")), AccountingMonitorConfigEntity.class);
    }
}
