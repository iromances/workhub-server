package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScope;
import cn.aslight.workhub.model.autonomy.AgentObservationEntity;
import cn.aslight.workhub.service.autonomy.AgentObservationService;
import cn.aslight.workhub.service.autonomy.AutonomyEvidence;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AutonomyEvidenceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AutonomyEvidence evidence = new AutonomyEvidence(json);

    @Test void accountingFailureAndSkippedRulesCannotBecomeFinancialDifferences() {
        for (String status : List.of("FAILED", "SKIPPED", "PASS")) {
            var observation = accounting(status);
            assertThrows(IllegalArgumentException.class, () -> evidence.extract(observation, "/data/results/0", "DATA_DISCREPANCY", null));
        }
        assertNotNull(evidence.extract(accounting("FAILED"), "/data/results/0", "EXECUTION_FAILURE", null));
        assertNotNull(evidence.extract(accounting("SKIPPED"), "/data/results/0", "MONITORING_GAP", null));
        for (String status : List.of("WARN", "ERROR")) {
            var extracted = evidence.extract(accounting(status), "/data/results/0", "DATA_DISCREPANCY", null);
            assertEquals("config:1:rule:AMOUNT", extracted.subjectKey());
            assertEquals("run:8:result:9", extracted.sourceEventId());
            assertEquals(LocalDateTime.parse("2026-11-04T01:00:00"), extracted.occurredAt());
            assertEquals("123.45", extracted.evidence().path("differenceAmount").asText());
        }
    }

    @Test void missingConfigurationAndNeverRunAreCandidatesWithoutInventedEventTimes() {
        var none = observation("monitor.business.status", List.of(), "NO_CONFIGURATION");
        var found = evidence.extract(none, "/data", "MONITORING_GAP", null);
        assertNull(found.occurredAt());
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(none, "/data", "EXECUTION_FAILURE", null));
        var never = observation("monitor.accounting.status", List.of(Map.of("configId", 1, "dataState", "NEVER_RUN", "enabled", true)), "QUERY_SUCCEEDED");
        assertNull(evidence.extract(never, "/data/0", "MONITORING_GAP", null).occurredAt());
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(never, "/data/0", "DATA_DISCREPANCY", null));
        var emptyLogs = observation("workhub.logs", Map.of("items", List.of()), "QUERY_SUCCEEDED");
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(emptyLogs, "/data", "MONITORING_GAP", null));
    }

    @Test void freshnessNeedsAStatedExceededLimitAndKeepsItAsAiJudgment() {
        var status = observation("monitor.business.status", List.of(Map.of("id", 1, "dataState", "HAS_RESULT", "lastCheckedAt", "2026-11-04T11:00:00", "dataAgeSeconds", 3600)), "QUERY_SUCCEEDED");
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(status, "/data/0", "STALE_DATA", null));
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(status, "/data/0", "STALE_DATA", 3600L));
        var accepted = evidence.extract(status, "/data/0", "STALE_DATA", 1800L);
        assertEquals("AI_PROPOSED_LIMIT", accepted.evidence().path("criterion").path("source").asText());
        assertEquals(1800, accepted.evidence().path("criterion").path("staleAfterSeconds").asInt());
        var noTime = observation("monitor.business.status", List.of(Map.of("id", 1, "dataState", "NEVER_RUN")), "QUERY_SUCCEEDED");
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(noTime, "/data/0", "STALE_DATA", 60L));
    }

    @Test void logGroupingUsesStableErrorSignatureButRetainsEachSourceEvent() {
        var a = log("event-1", "3c98d844-c803-4da1-a7c8-6a7d727aa9d1", 21);
        var b = log("event-2", "91174b10-a151-4b01-80df-d3b50c13cd71", 22);
        var first = evidence.extract(a, "/data/items/0", "APPLICATION_ERROR", null);
        var second = evidence.extract(b, "/data/items/0", "APPLICATION_ERROR", null);
        assertEquals(first.subjectKey(), second.subjectKey()); assertNotEquals(first.sourceEventId(), second.sourceEventId());
        assertEquals(LocalDateTime.parse("2026-11-04T02:00:00"), first.occurredAt());
        assertEquals("SELF_LOG", a.sourceType());
        var info = observation("workhub.logs", Map.of("items", List.of(Map.of("sourceEventId", "info", "serviceName", "workhub", "level", "INFO", "message", "started"))), "QUERY_SUCCEEDED");
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(info, "/data/items/0", "APPLICATION_ERROR", null));
    }

    @Test void slowSqlAndFailedJobsUseTheirActualSourceIdentity() {
        var alert = observation("monitor.system.alerts", Map.of("events", List.of(Map.of("id", 55, "serviceName", "settlement", "level", "WARN", "eventCategory", "SLOW_SQL", "message", "SELECT reconciliation slow", "occurredAt", "2026-11-04T10:00:00"))), "QUERY_SUCCEEDED");
        assertEquals("55", evidence.extract(alert, "/data/events/0", "PERFORMANCE", null).sourceEventId());
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(alert, "/data/events/0", "APPLICATION_ERROR", null));
        var job = observation("monitor.business.detail", Map.of("id", 7, "logs", List.of(Map.of("jobId", 20, "logId", 30, "failureType", "SCHEDULE_FAILED", "triggerTime", "2026-11-04 10:00:00.0"))), "QUERY_SUCCEEDED");
        var failed = evidence.extract(job, "/data/logs/0", "EXECUTION_FAILURE", null);
        assertEquals("monitor:7:job:20", failed.subjectKey()); assertEquals("monitor:7:log:30", failed.sourceEventId());
        assertEquals(LocalDateTime.parse("2026-11-04T02:00:00"), failed.occurredAt());
    }

    @Test void evidencePointerCannotSelectNestedTextOrAnotherScopeAndInvalidDatesRemainErrors() {
        var observation = accounting("WARN");
        for (String pointer : List.of("/scope", "/data/results/0/message", "/data/results/01", "/data/results/9", "/data/run")) {
            assertThrows(IllegalArgumentException.class, () -> evidence.extract(observation, pointer, "DATA_DISCREPANCY", null));
        }
        var invalid = observation("monitor.business.status", List.of(Map.of("id", 1, "status", "ERROR", "dataState", "HAS_RESULT", "lastCheckedAt", "not-a-date")), "QUERY_SUCCEEDED");
        assertThrows(IllegalArgumentException.class, () -> evidence.extract(invalid, "/data/0", "EXECUTION_FAILURE", null));
    }

    @Test void sameFailedAccountingRunHasOneIdentityAcrossLatestAndHistoryViews() {
        var run = Map.of("id", 8, "configId", 1, "status", "FAILED", "finishedAt", "2026-11-04T09:00:00", "errorMessage", "执行失败");
        var latest = observation("monitor.accounting.status", List.of(Map.of("configId", 1, "dataState", "EXECUTION_FAILED", "latestRun", run)), "QUERY_SUCCEEDED");
        var history = observation("monitor.accounting.runs", Map.of("runs", List.of(run)), "QUERY_SUCCEEDED");
        var first = evidence.extract(latest, "/data/0", "EXECUTION_FAILURE", null);
        var second = evidence.extract(history, "/data/runs/0", "EXECUTION_FAILURE", null);
        assertEquals(first, second);
    }

    private AgentObservationEntity accounting(String status) {
        return observation("monitor.accounting.detail", json.readTree("""
                {"run":{"id":8,"configId":1},"results":[{"id":9,"runId":8,"ruleCode":"AMOUNT","status":"%s","differenceAmount":123.45,"createdAt":"2026-11-04T09:00:00"}]}
                """.formatted(status)), "QUERY_SUCCEEDED");
    }
    private AgentObservationEntity log(String id, String uuid, int line) {
        return observation("workhub.logs", Map.of("items", List.of(Map.of("sourceEventId", id, "serviceName", "workhub", "level", "ERROR", "errorType", "IllegalStateException", "occurredAt", "2026-11-04T02:00:00Z",
                "stackTrace", "IllegalStateException request=" + uuid + "\n at cn.workhub.Job.run(Job.java:" + line + ")"))), "QUERY_SUCCEEDED");
    }
    private AgentObservationEntity observation(String code, Object value, String coverage) {
        JsonNode payload = json.valueToTree(Map.of("scope", new AgentScope("BL000012", "TEST"), "data", value, "coverage", coverage, "observedAt", "2026-11-04T04:00:00Z", "recordTimeZone", "Asia/Shanghai"));
        return new AgentObservationEntity(UUID.randomUUID().toString(), "run", "agent", "call", code, AgentObservationService.sourceType(code, "BL000012"), "BL000012", "TEST", payload.toString(), "hash", LocalDateTime.parse("2026-11-04T04:00:00"));
    }
}
