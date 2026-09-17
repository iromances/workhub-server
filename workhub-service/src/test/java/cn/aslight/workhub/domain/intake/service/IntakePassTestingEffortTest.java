package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStageActionRequest;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class IntakePassTestingEffortTest {

    static Stream<Arguments> effortCases() {
        return Stream.of(
                Arguments.of("16h", "66h", "66h"),
                Arguments.of("16h", "66", "66h"),
                Arguments.of("16h", "1.5d", "12h"),
                Arguments.of("16h", " 66.5h ", "66.5h"),
                Arguments.of("16h", "0", "0h"),
                Arguments.of("16h", null, "16h"),
                Arguments.of("16h", "", "16h"),
                Arguments.of("16h", "  ", "16h"),
                Arguments.of(null, "66h", "66h"),
                Arguments.of(null, null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("effortCases")
    void shouldSaveCumulativeEffortAndPreserveMissingLegacyValues(String before, String input, String expected) {
        Fixture fixture = fixture(before, "测试中");
        IntakeStageActionRequest request = request(input);

        fixture.service().advanceStage(190L, request, "tester");

        ArgumentCaptor<IntakeRecordEntity> row = ArgumentCaptor.forClass(IntakeRecordEntity.class);
        verify(fixture.mapper()).updateFormalFields(row.capture());
        assertEquals(expected, row.getValue().getActualEffort());
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(fixture.mapper()).updateManagementFields(eq(190L), json.capture(), any(), eq("待验收"));
        var saved = new ObjectMapper().readTree(json.getValue());
        assertEquals(expected, saved.path("actualEffort").isNull() ? null : saved.path("actualEffort").asText());
        assertEquals("2h", saved.path("actualTestingEffort").asText());
        assertEquals("2026/09/11", saved.path("scheduledAcceptanceDate").asText());
        assertEquals("2026/09/10", saved.path("actualTestingCompletedDate").asText());
        assertEquals("2026/09/07", saved.path("actualCompletedTime").asText());

        ArgumentCaptor<IntakeHistoryEntity> history = ArgumentCaptor.forClass(IntakeHistoryEntity.class);
        verify(fixture.history()).insert(history.capture());
        assertEquals("tester", history.getValue().getOperatorUserName());
        assertTrue(history.getValue().getDetailText().contains("需求状态：测试中 -> 待验收"));
        if (!java.util.Objects.equals(before, expected)) {
            assertTrue(history.getValue().getDetailText().contains(
                    "实际开发工时：" + (before == null ? "-" : before) + " -> " + expected));
        } else {
            assertFalse(history.getValue().getDetailText().contains("实际开发工时："));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1h", "abc", "66m", "NaN", "1h2h"})
    void shouldRejectInvalidEffortBeforeAnyWrite(String input) {
        Fixture fixture = fixture("16h", "测试中");
        var error = assertThrows(IllegalArgumentException.class,
                () -> fixture.service().advanceStage(190L, request(input), "tester"));
        assertTrue(error.getMessage().contains("工时格式不正确"));
        verify(fixture.mapper(), never()).updateFormalFields(any());
        verify(fixture.mapper(), never()).updateManagementFields(any(), any(), any(), any());
        verify(fixture.history(), never()).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"待验收", "开发中", "已完成"})
    void shouldRejectRepeatedOrOutOfStageSubmission(String state) {
        Fixture fixture = fixture("16h", state);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.service().advanceStage(190L, request("66h"), "tester"));
        verify(fixture.mapper(), never()).updateFormalFields(any());
        verify(fixture.history(), never()).insert(any());
    }

    private IntakeStageActionRequest request(String effort) {
        IntakeStageActionRequest request = new IntakeStageActionRequest();
        request.setAction("PASS_TESTING");
        request.setActualEffort(effort);
        request.setScheduledAcceptanceDate("2026/09/11");
        request.setActualTestingEffort("2h");
        request.setActualTestingCompletedDate("2026/09/10");
        return request;
    }

    private Fixture fixture(String effort, String status) {
        IntakeMapper mapper = mock(IntakeMapper.class);
        IntakeHistoryMapper history = mock(IntakeHistoryMapper.class);
        ObjectMapper json = new ObjectMapper();
        IntakeRecordEntity row = new IntakeRecordEntity();
        row.setId(190L);
        row.setDemandStatus(status);
        row.setStructuredDataJson("""
                {"requirementType":"研发需求","actualEffort":%s,
                 "actualCompletedTime":"2026/09/07","fields":[],"attachmentSummaries":[]}
                """.formatted(json.writeValueAsString(effort)));
        when(mapper.findById(190L)).thenReturn(row);
        IntakeService service = new IntakeService(mapper, history, mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(), mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class), json);
        return new Fixture(service, mapper, history);
    }

    private record Fixture(IntakeService service, IntakeMapper mapper, IntakeHistoryMapper history) { }
}
