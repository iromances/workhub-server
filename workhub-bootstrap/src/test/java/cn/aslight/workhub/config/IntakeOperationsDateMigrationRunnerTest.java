package cn.aslight.workhub.config;

import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeOperationsDateMigrationRunnerTest {

    @Test
    void run_shouldBackfillOperationsDatesFromStageHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = operationsIntake(1L, null, null, null);
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(1L, "开始处理"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 2, 9, 30)));
        when(intakeHistoryMapper.findLatestUpdateBySummary(1L, "提交验收"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 3, 18, 0)));

        IntakeOperationsDateMigrationRunner runner = new IntakeOperationsDateMigrationRunner(
                intakeMapper,
                intakeHistoryMapper,
                objectMapper
        );
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateStructuredData(eq(1L), contains("\"developmentStartedDate\":\"2026/04/02\""));
        verify(intakeMapper).updateStructuredData(eq(1L), contains("\"testingStartedDate\":\"2026/04/03\""));
    }

    @Test
    void run_shouldNotOverrideExistingOperationsDates() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = operationsIntake(2L, "2026/04/01", "2026/04/02", "2026/04/02");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));

        IntakeOperationsDateMigrationRunner runner = new IntakeOperationsDateMigrationRunner(
                intakeMapper,
                intakeHistoryMapper,
                objectMapper
        );
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper, never()).updateStructuredData(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private IntakeRecordEntity operationsIntake(Long id,
                                                String developmentStartedDate,
                                                String testingStartedDate,
                                                String actualCompletedTime) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"developmentOwnerUserName":null,"approvalCode":"OPS-%s","submittedTime":null,"requirementType":"数据提取/运维","developmentBranchName":null,"zentaoUrl":null,"requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"plannedDueDate":null,"plannedDevelopmentStartDate":null,"plannedTestingStartDate":null,"plannedReleaseDate":null,"developmentStartedDate":%s,"actualEffort":"5h","testingStartedDate":%s,"actualCompletedTime":%s,"scheduledAcceptanceDate":null,"actualTestingEffort":null,"actualTestingCompletedDate":null,"acceptanceTime":null,"releasedTime":null,"closedTime":null,"closeReason":null,"projectHint":null,"fields":[],"attachmentSummaries":[],"sqlDraft":null}
                """.formatted(id, jsonString(developmentStartedDate), jsonString(testingStartedDate), jsonString(actualCompletedTime)));
        return entity;
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private IntakeHistoryEntity historyAt(LocalDateTime createdAt) {
        IntakeHistoryEntity history = new IntakeHistoryEntity();
        history.setCreatedAt(createdAt);
        return history;
    }
}
