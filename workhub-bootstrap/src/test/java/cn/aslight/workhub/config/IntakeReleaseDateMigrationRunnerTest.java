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

class IntakeReleaseDateMigrationRunnerTest {

    @Test
    void run_shouldRepairReleaseDateFromConfirmReleaseHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = intakeWithReleasedTime(1L, "待验收", "2026/04/09");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(1L, "确认上线"))
                .thenReturn(historyAt(LocalDateTime.of(2026, 4, 10, 15, 30)));

        IntakeReleaseDateMigrationRunner runner = new IntakeReleaseDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateStructuredData(eq(1L), contains("\"releasedTime\":\"2026/04/10\""));
    }

    @Test
    void run_shouldClearReleaseDateBeforeReleaseWhenNoConfirmReleaseHistory() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = intakeWithReleasedTime(2L, "测试中", "2026/04/10");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(2L, "确认上线")).thenReturn(null);

        IntakeReleaseDateMigrationRunner runner = new IntakeReleaseDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateStructuredData(eq(2L), contains("\"releasedTime\":null"));
    }

    @Test
    void run_shouldKeepCompletedReleaseDateWhenConfirmReleaseHistoryIsMissing() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = intakeWithReleasedTime(3L, "已完成", "2026/04/10");
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));
        when(intakeHistoryMapper.findLatestUpdateBySummary(3L, "确认上线")).thenReturn(null);

        IntakeReleaseDateMigrationRunner runner = new IntakeReleaseDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper, never()).updateStructuredData(anyLong(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void run_shouldBackfillOperationsReleaseDateFromActualCompletedTime() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper intakeHistoryMapper = mock(IntakeHistoryMapper.class);
        ObjectMapper objectMapper = new ObjectMapper();

        IntakeRecordEntity entity = operationsIntakeWithActualCompletedTime(4L, "2026/04/12", null);
        when(intakeMapper.findAllForLifecycleMigration()).thenReturn(List.of(entity));

        IntakeReleaseDateMigrationRunner runner = new IntakeReleaseDateMigrationRunner(intakeMapper, intakeHistoryMapper, objectMapper);
        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(intakeMapper).updateStructuredData(eq(4L), contains("\"releasedTime\":\"2026/04/12\""));
    }

    private IntakeRecordEntity intakeWithReleasedTime(Long id, String demandStatus, String releasedTime) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setDemandStatus(demandStatus);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"developmentOwnerUserName":null,"approvalCode":"REQ-%s","submittedTime":null,"requirementType":"研发需求","developmentBranchName":null,"zentaoUrl":null,"requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":null,"acceptanceTime":null,"releasedTime":"%s","closedTime":null,"closeReason":null,"projectHint":null,"fields":[],"attachmentSummaries":[],"sqlDraft":null}
                """.formatted(id, releasedTime));
        return entity;
    }

    private IntakeRecordEntity operationsIntakeWithActualCompletedTime(Long id, String actualCompletedTime, String releasedTime) {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(id);
        entity.setDemandStatus("待验收");
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"developmentOwnerUserName":null,"approvalCode":"OPS-%s","submittedTime":null,"requirementType":"数据提取/运维","developmentBranchName":null,"zentaoUrl":null,"requirementDigest":"摘要","requirementName":"标题","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"plannedDueDate":null,"developmentStartedDate":null,"actualEffort":null,"testingStartedDate":null,"actualCompletedTime":"%s","acceptanceTime":null,"releasedTime":%s,"closedTime":null,"closeReason":null,"projectHint":null,"fields":[],"attachmentSummaries":[],"sqlDraft":null}
                """.formatted(id, actualCompletedTime, releasedTime == null ? "null" : "\"" + releasedTime + "\""));
        return entity;
    }

    private IntakeHistoryEntity historyAt(LocalDateTime createdAt) {
        IntakeHistoryEntity history = new IntakeHistoryEntity();
        history.setCreatedAt(createdAt);
        return history;
    }
}
