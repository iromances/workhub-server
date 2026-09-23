package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeProcessInfoResponse;
import cn.aslight.workhub.model.intake.IntakeProcessInfoUpdateRequest;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeanWrapperImpl;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntakeProcessInfoTest {
    private final ObjectMapper json = new ObjectMapper();
    private final IntakeMapper mapper = mock(IntakeMapper.class);
    private final IntakeHistoryMapper history = mock(IntakeHistoryMapper.class);
    private final IntakeRecordEntity row = new IntakeRecordEntity();
    private IntakeService service;

    @BeforeEach
    void setUp() {
        row.setId(19L);
        row.setDemandStatus("已完成");
        row.setRequirementType("研发需求");
        row.setActualEffort("16h");
        row.setReleasedDate(LocalDate.of(2026, 9, 18));
        row.setStructuredDataJson("""
                {"requirementType":"研发需求","releasedTime":"2026/09/18","actualEffort":"16h",
                 "fields":[{"label":"实际工时","value":"16h"},{"label":"上线日期","value":"2026/09/18"}],
                 "attachmentSummaries":[]}
                """);
        when(mapper.findById(19L)).thenReturn(row);
        when(mapper.lockProcessInfo(19L)).thenReturn(19L);
        when(mapper.updateProcessInfo(eq(19L), anyMap(), anyString(), nullable(String.class))).thenAnswer(invocation -> {
            Map<String, Object> columns = invocation.getArgument(1);
            var bean = new BeanWrapperImpl(row);
            columns.forEach((column, value) -> {
                String[] parts = column.split("_");
                StringBuilder name = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length; i++) {
                    name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                }
                bean.setPropertyValue(name.toString(), value);
            });
            row.setStructuredDataJson(invocation.getArgument(2));
            row.setProcessEstimatedEffort(invocation.getArgument(3));
            return 1;
        });
        service = new IntakeService(mapper, history, mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(), mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class), json);
    }

    @Test
    void readsLegacyDateTimesAndEmptyMarkersAsBusinessDates() {
        row.setStructuredDataJson("""
                {"developmentStartedDate":"2026/9/1 10:30","testingStartedDate":"2026-09-02T12:00:00",
                 "plannedReleaseDate":"-","scheduledAcceptanceDate":"无","acceptanceTime":" "}
                """);
        var info = service.processInfo(19L);
        assertEquals("2026-09-01", info.values().get("developmentStartedDate"));
        assertEquals("2026-09-02", info.values().get("testingStartedDate"));
        assertNull(info.values().get("plannedReleaseDate"));
        assertNull(info.values().get("scheduledAcceptanceDate"));
        assertNull(info.values().get("acceptanceTime"));
    }

    @Test
    void editsCompletedRequirementAndKeepsHistoryWithoutAdvancingStage() {
        var original = service.processInfo(19L);
        var saved = save(original, Map.of("actualEffort", "1.5d", "releasedTime", "2026-09-20"));
        assertEquals("12h", saved.values().get("actualEffort"));
        assertEquals("2026-09-20", saved.values().get("releasedTime"));
        assertEquals("已完成", row.getDemandStatus());
        assertFalse(saved.estimatedEffortOverridden());
        assertNull(row.getProcessEstimatedEffort());
        assertEquals(saved, service.processInfo(19L));
        var payload = json.readTree(row.getStructuredDataJson());
        assertEquals("12h", payload.path("fields").get(0).path("value").asText());
        assertEquals("2026-09-20", payload.path("fields").get(1).path("value").asText());
        var captured = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(history).insert(captured.capture());
        assertEquals("editor", captured.getValue().getOperatorUserName());
        assertEquals("编辑需求过程信息", captured.getValue().getActionSummary());
        assertTrue(captured.getValue().getDetailText().contains("实际开发工时：16h -> 12h"));
        verify(mapper, never()).updateFormalFields(any());
        verify(mapper, never()).updateManagementFields(any(), any(), any(), any());
        var order = inOrder(mapper);
        order.verify(mapper).lockProcessInfo(19L);
        order.verify(mapper).lockProcessAnalysis(19L);
        order.verify(mapper).updateProcessInfo(eq(19L), anyMap(), anyString(), nullable(String.class));
    }

    @Test
    void supportsEstimatesWithoutDraftAndProtectsOverrideFromLaterEvaluation() {
        var saved = save(service.processInfo(19L), Map.of("developmentEstimatedEffort", "1d", "testingEstimatedEffort", "4h"));
        assertEquals("8h", saved.values().get("developmentEstimatedEffort"));
        assertEquals("12h", saved.values().get("totalEstimatedEffort"));
        assertTrue(saved.estimatedEffortOverridden());
        row.setLatestDevelopmentDraftJson("{\"totalEstimatedEffort\":\"100h\",\"developmentEstimatedEffort\":\"80h\",\"testingEstimatedEffort\":\"20h\"}");
        assertEquals(saved, service.processInfo(19L));
        assertEquals("100h", json.readTree(row.getLatestDevelopmentDraftJson()).path("totalEstimatedEffort").asText());
    }

    @Test
    void clearingDatesAndEstimatesDoesNotResurrectLegacyValues() {
        row.setEstimatedEffort("60h");
        row.setLatestDevelopmentDraftJson("{\"totalEstimatedEffort\":\"12h\",\"developmentEstimatedEffort\":\"8h\",\"testingEstimatedEffort\":\"4h\"}");
        Map<String, String> changes = new LinkedHashMap<>();
        for (String name : new String[]{"releasedTime", "actualEffort", "developmentEstimatedEffort", "testingEstimatedEffort"}) changes.put(name, null);
        var saved = save(service.processInfo(19L), changes);
        assertNull(saved.values().get("totalEstimatedEffort"));
        assertTrue(saved.estimatedEffortOverridden());
        assertEquals(saved, service.processInfo(19L));
        assertNull(row.getReleasedDate());
        assertTrue(json.readTree(row.getStructuredDataJson()).path("releasedTime").isNull());
        assertTrue(json.readTree(row.getStructuredDataJson()).path("fields").get(0).path("value").isNull());
    }

    @Test
    void canExplicitlyClearLegacyTotalWithoutSplitEstimates() {
        row.setEstimatedEffort("60h");
        var original = service.processInfo(19L);
        assertEquals("60h", original.values().get("totalEstimatedEffort"));
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("developmentEstimatedEffort", null);
        changes.put("testingEstimatedEffort", null);
        var saved = save(original, changes);
        assertNull(saved.values().get("totalEstimatedEffort"));
        assertTrue(saved.estimatedEffortOverridden());
        assertEquals(saved, service.processInfo(19L));
    }

    @Test
    void zeroPartialEstimatesAndUnchangedSaveHaveDistinctSemantics() {
        var original = service.processInfo(19L);
        assertEquals(original, save(original, Map.of("actualEffort", "2d")));
        verify(history, never()).insert(any());
        var saved = save(original, Map.of("developmentEstimatedEffort", "0"));
        assertEquals("0h", saved.values().get("totalEstimatedEffort"));
        assertNull(saved.values().get("testingEstimatedEffort"));
        assertEquals(saved, save(original, Map.of("developmentEstimatedEffort", "0")));
        verify(history, times(1)).insert(any());
    }

    @Test
    void keepsOtherEstimateWhenOnlyOneChanges() {
        row.setLatestDevelopmentDraftJson("{\"developmentEstimatedEffort\":\"8h\",\"testingEstimatedEffort\":\"4h\",\"totalEstimatedEffort\":\"12h\"}");
        var saved = save(service.processInfo(19L), Map.of("testingEstimatedEffort", "0.5d"));
        assertFalse(saved.estimatedEffortOverridden());
        saved = save(saved, Map.of("testingEstimatedEffort", "6h"));
        assertEquals("8h", saved.values().get("developmentEstimatedEffort"));
        assertEquals("14h", saved.values().get("totalEstimatedEffort"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1h", "NaN", "1h2h", "3m"})
    void rejectsInvalidEffortWithoutWrites(String input) {
        var original = service.processInfo(19L);
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("actualTestingEffort", input)));
        verify(mapper, never()).updateProcessInfo(any(), any(), any(), any());
        verify(history, never()).insert(any());
    }

    @Test
    void rejectsInvalidDatesOrderingAndReadOnlyFields() {
        var original = service.processInfo(19L);
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("releasedTime", "2026-02-30")));
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("plannedDevelopmentStartDate", "2026-09-20", "plannedReleaseDate", "2026-09-19")));
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("testingStartedDate", "2026-09-20", "actualTestingCompletedDate", "2026-09-19")));
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("demandStatus", "开发中")));
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("totalEstimatedEffort", "100h")));
        verify(mapper, never()).updateProcessInfo(any(), any(), any(), any());
    }

    @Test
    void rejectsStaleSnapshotsAndMissingRecordWithoutHistory() {
        var original = service.processInfo(19L);
        row.setActualEffort("20h");
        var error = assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("releasedTime", "2026-09-21")));
        assertTrue(error.getMessage().contains("重新打开"));
        when(mapper.lockProcessInfo(19L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> save(original, Map.of("actualEffort", "30h")));
        verify(mapper, never()).updateProcessInfo(any(), any(), any(), any());
        verify(history, never()).insert(any());
    }

    @Test
    void failedWriteDoesNotRecordSuccessfulHistory() {
        when(mapper.updateProcessInfo(any(), any(), any(), any())).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> save(service.processInfo(19L), Map.of("actualEffort", "30h")));
        verify(history, never()).insert(any());
    }

    private IntakeProcessInfoResponse save(IntakeProcessInfoResponse original, Map<String, String> changes) {
        return service.updateProcessInfo(19L, new IntakeProcessInfoUpdateRequest(original, changes), "editor");
    }
}
