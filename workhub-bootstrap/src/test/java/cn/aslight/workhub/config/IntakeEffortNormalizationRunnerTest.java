package cn.aslight.workhub.config;

import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeEffortNormalizationRunnerTest {

    @Test
    void run_shouldNormalizeHistoricalEffortUnits() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(1L);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"approvalCode":"A-1","submittedTime":null,"requirementType":"研发需求","requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"estimatedEffort":"2d","plannedDueDate":null,"developmentStartedDate":null,"actualEffort":"1.5d","testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":null,"fields":[],"attachmentSummaries":[]}
                """);
        entity.setAiDraftJson("""
                {"titleSuggestion":"标题","descriptionSuggestion":null,"typeSuggestion":null,"prioritySuggestion":null,"suggestedProjectCode":null,"acceptanceCriteriaSuggestion":null,"taskBreakdownSuggestions":[{"taskName":"开发","estimatedEffort":"0.5d","ownerUserName":null,"status":null,"notes":null}],"provider":"heuristic","model":null,"rawResponse":null}
                """);

        when(intakeMapper.findAllForEffortNormalization()).thenReturn(List.of(entity));

        IntakeEffortNormalizationRunner runner = new IntakeEffortNormalizationRunner(intakeMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateEffortPayloads(eq(1L), contains("\"actualEffort\":\"12h\""), contains("\"estimatedEffort\":\"4h\""));
    }

    @Test
    void run_shouldSkipWhenNoNormalizationNeeded() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(2L);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"approvalCode":"A-2","submittedTime":null,"requirementType":"研发需求","requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"estimatedEffort":"16h","plannedDueDate":null,"developmentStartedDate":null,"actualEffort":"8h","testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"projectHint":null,"fields":[],"attachmentSummaries":[]}
                """);

        when(intakeMapper.findAllForEffortNormalization()).thenReturn(List.of(entity));

        IntakeEffortNormalizationRunner runner = new IntakeEffortNormalizationRunner(intakeMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper, never()).updateEffortPayloads(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
