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

class IntakeTestingDateMigrationRunnerTest {

    @Test
    void run_shouldRepairHistoricalTestingDateFromSubmitTestingHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = intakeWithTestingStartedDate(1L, "2026/04/10");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(1L, "提交测试"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 9, 11, 0)));
        when(intakeHistoryMapper.findLatestUpdateBySummary(1L, "测试通过"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 10, 16, 0)));

        IntakeTestingDateMigrationRunner runner = new IntakeTestingDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateStructuredData(eq(1L), contains("\"testingStartedDate\":\"2026/04/09\""));
    }

    @Test
    void run_shouldNotOverrideExplicitTestingDateWhenItDoesNotMatchPassTestingHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = intakeWithTestingStartedDate(2L, "2026/04/08");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(2L, "提交测试"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 9, 11, 0)));
        when(intakeHistoryMapper.findLatestUpdateBySummary(2L, "测试通过"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 10, 16, 0)));

        IntakeTestingDateMigrationRunner runner = new IntakeTestingDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper, never()).updateStructuredData(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    private IntakeRecordEntity intakeWithTestingStartedDate(Long id, String testingStartedDate) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"developmentOwnerUserName":null,"approvalCode":"REQ-%s","submittedTime":null,"requirementType":"研发需求","developmentBranchName":null,"zentaoUrl":null,"requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":"%s","actualCompletedTime":null,"acceptanceTime":null,"releasedTime":null,"closedTime":null,"closeReason":null,"projectHint":null,"fields":[],"attachmentSummaries":[],"sqlDraft":null}
                """.formatted(id, testingStartedDate));
        return entity;
    }

    private IntakeHistoryEntity historyAt(LocalDateTime createdAt) {
        IntakeHistoryEntity history = new IntakeHistoryEntity();
        history.setCreatedAt(createdAt);
        return history;
    }
}
