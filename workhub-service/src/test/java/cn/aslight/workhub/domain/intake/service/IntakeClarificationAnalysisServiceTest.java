package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeClarificationAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeHistoryEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeClarificationAnalysisServiceTest {

    @Test
    void analyze_shouldSupersedeStalePendingAndInsertNewRun() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, executor);

        when(intakeMapper.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        IntakeClarificationAnalysisEntity existing = pendingAnalysis(LocalDateTime.now().minusMinutes(31));
        when(analysisMapper.findLatestByIntakeId(92L)).thenReturn(existing);

        service.analyze(92L, "admin");

        verify(analysisMapper).failInFlightByIntakeId(eq(92L), any(), eq("admin"));
        ArgumentCaptor<IntakeClarificationAnalysisEntity> insertCaptor = forClass(IntakeClarificationAnalysisEntity.class);
        verify(analysisMapper).insert(insertCaptor.capture());
        assertEquals("PENDING", insertCaptor.getValue().getAnalysisStatus());
        verify(executor).execute(any(Runnable.class));
        verify(analysisMapper, never()).update(any());
    }

    @Test
    void analyze_shouldReturnFreshPendingAnalysisWithoutDuplicateExecution() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, executor);

        when(intakeMapper.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        IntakeClarificationAnalysisEntity existing = pendingAnalysis(LocalDateTime.now().minusMinutes(5));
        when(analysisMapper.findLatestByIntakeId(92L)).thenReturn(existing);

        service.analyze(92L, "admin");

        verify(analysisMapper, never()).failInFlightByIntakeId(any(), any(), any());
        verify(analysisMapper, never()).insert(any());
        verify(analysisMapper, never()).update(any());
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void analyze_shouldRetryFailedAnalysisWithFreshRun() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, executor);

        when(intakeMapper.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        IntakeClarificationAnalysisEntity existing = failedAnalysis(LocalDateTime.now().minusMinutes(1));
        when(analysisMapper.findLatestByIntakeId(92L)).thenReturn(existing);

        service.analyze(92L, "admin");

        verify(analysisMapper).failInFlightByIntakeId(eq(92L), any(), eq("admin"));
        verify(analysisMapper).insert(any(IntakeClarificationAnalysisEntity.class));
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void analyze_shouldMarkFailedAndRethrowWhenExecutorRejects() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeHistoryMapper historyMapper = mock(IntakeHistoryMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        doThrow(new RejectedExecutionException("pool saturated")).when(executor).execute(any(Runnable.class));
        IntakeClarificationAnalysisService service = new IntakeClarificationAnalysisService(
                intakeMapper,
                historyMapper,
                analysisMapper,
                mock(ProjectMapper.class),
                mock(GitlabRepositoryService.class),
                mock(ProjectKnowledgeBaseService.class),
                mock(CodexCliClarificationAnalysisGenerator.class),
                mock(DevelopmentAnalysisService.class),
                executor,
                new ObjectMapper()
        );

        when(intakeMapper.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        IntakeClarificationAnalysisEntity inserted = pendingAnalysis(LocalDateTime.now());
        when(analysisMapper.findLatestByIntakeId(92L)).thenReturn(null, inserted);
        when(analysisMapper.findById(2L)).thenReturn(inserted);

        assertThrows(IllegalStateException.class, () -> service.analyze(92L, "admin"));

        ArgumentCaptor<IntakeClarificationAnalysisEntity> failedCaptor = forClass(IntakeClarificationAnalysisEntity.class);
        verify(analysisMapper).updateExecutionState(failedCaptor.capture());
        assertEquals("FAILED", failedCaptor.getValue().getAnalysisStatus());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(historyMapper).insert(historyCaptor.capture());
        assertEquals("需求澄清分析失败", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void analyze_shouldUseBusinessLineCodeBeforeProjectHintWhenQueueingAnalysis() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, executor);

        when(intakeMapper.findById(119L)).thenReturn(clarifyingDevelopmentIntakeWithSystemProjectHint());
        IntakeClarificationAnalysisEntity existing = pendingAnalysis(LocalDateTime.now().minusMinutes(31));
        when(analysisMapper.findLatestByIntakeId(119L)).thenReturn(existing);

        service.analyze(119L, "admin");

        ArgumentCaptor<IntakeClarificationAnalysisEntity> insertCaptor = forClass(IntakeClarificationAnalysisEntity.class);
        verify(analysisMapper).insert(insertCaptor.capture());
        assertEquals("BL000003", insertCaptor.getValue().getBusinessLineCode());
        assertEquals(null, insertCaptor.getValue().getBusinessLine());
        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void executeAnalysis_shouldMarkFailedWhenThrowableEscapesGeneration() {
        IntakeMapper intakes = mock(IntakeMapper.class);
        IntakeHistoryMapper histories = mock(IntakeHistoryMapper.class);
        IntakeClarificationAnalysisMapper analyses = mock(IntakeClarificationAnalysisMapper.class);
        ProjectKnowledgeBaseService archive = mock(ProjectKnowledgeBaseService.class);
        IntakeClarificationAnalysisService service = new IntakeClarificationAnalysisService(intakes,
                histories, analyses, mock(ProjectMapper.class), mock(GitlabRepositoryService.class), archive,
                mock(CodexCliClarificationAnalysisGenerator.class), mock(DevelopmentAnalysisService.class),
                ignored -> {
                }, new ObjectMapper());

        when(intakes.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        var entity = pendingAnalysis(LocalDateTime.now());
        when(analyses.findById(2L)).thenReturn(entity);
        when(archive.buildContext(any(), any())).thenThrow(new AssertionError("jvm broken"));

        service.executeAnalysis(2L, 92L, "reviewer");

        ArgumentCaptor<IntakeClarificationAnalysisEntity> failedCaptor = forClass(IntakeClarificationAnalysisEntity.class);
        verify(analyses, org.mockito.Mockito.times(2)).updateExecutionState(failedCaptor.capture());
        assertEquals("FAILED", failedCaptor.getAllValues().getLast().getAnalysisStatus());
        verify(analyses, never()).update(any());
        ArgumentCaptor<IntakeHistoryEntity> historyCaptor = forClass(IntakeHistoryEntity.class);
        verify(histories).insert(historyCaptor.capture());
        assertEquals("需求澄清分析失败", historyCaptor.getValue().getActionSummary());
    }

    @Test
    void detail_shouldExposeRetryableByStatusAndStaleness() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, ignored -> {
        });

        when(analysisMapper.findLatestByIntakeId(92L))
                .thenReturn(pendingAnalysis(LocalDateTime.now().minusMinutes(5)));
        assertFalse(service.detail(92L).retryable());

        when(analysisMapper.findLatestByIntakeId(92L))
                .thenReturn(pendingAnalysis(LocalDateTime.now().minusMinutes(31)));
        assertTrue(service.detail(92L).retryable());

        when(analysisMapper.findLatestByIntakeId(92L))
                .thenReturn(failedAnalysis(LocalDateTime.now().minusMinutes(1)));
        assertTrue(service.detail(92L).retryable());
    }

    @Test
    void history_shouldListAllRunsInIdOrder() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, ignored -> {
        });

        when(analysisMapper.listByIntakeId(92L)).thenReturn(List.of(
                pendingAnalysis(LocalDateTime.now()),
                failedAnalysis(LocalDateTime.now().minusHours(1))));

        var responses = service.history(92L);

        assertEquals(2, responses.size());
        assertEquals("PENDING", responses.get(0).status());
        assertFalse(responses.get(0).retryable());
        assertEquals("FAILED", responses.get(1).status());
        assertTrue(responses.get(1).retryable());
    }

    @Test
    void successfulClarificationQueuesNotesOnlyAfterDatabaseResultIsSaved() {
        IntakeMapper intakes = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analyses = mock(IntakeClarificationAnalysisMapper.class);
        ProjectKnowledgeBaseService archive = mock(ProjectKnowledgeBaseService.class);
        GitlabRepositoryService repositories = mock(GitlabRepositoryService.class);
        CodexCliClarificationAnalysisGenerator generator = mock(CodexCliClarificationAnalysisGenerator.class);
        when(intakes.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        var entity = pendingAnalysis(LocalDateTime.now());
        when(analyses.findById(2L)).thenReturn(entity);
        var items = java.util.List.of(new cn.aslight.workhub.model.intake.IntakeClarificationItem(
                1, "QUESTION", "待确认", "规则未明确", "需求资料", "OPEN", null, null, null));
        when(archive.upsertRequirementIterationNote(any(), any(), any())).thenReturn(
                new ProjectKnowledgeBaseService.RequirementKnowledgeNote("raw/公司项目/requirements/历史版本/run1/source.md",
                        null, null, "原始需求", true));
        when(archive.buildContext(any(), any())).thenReturn("现状");
        when(generator.generate(any(), any(), any(), any(), any(), any())).thenReturn(items);
        IntakeClarificationAnalysisService service = new IntakeClarificationAnalysisService(intakes,
                mock(IntakeHistoryMapper.class), analyses, mock(ProjectMapper.class), repositories, archive,
                generator, mock(DevelopmentAnalysisService.class), ignored -> { }, new ObjectMapper());
        service.executeAnalysis(2L, 92L, "reviewer");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(analyses, archive);
        order.verify(analyses).updateExecutionState(entity);
        order.verify(archive).upsertRequirementIterationNote(any(), any(), any());
        order.verify(archive).buildContext(any(), any());
        order.verify(analyses).update(entity);
        org.mockito.ArgumentCaptor<Runnable> callback = org.mockito.ArgumentCaptor.forClass(Runnable.class);
        order.verify(archive).archiveAfterCommit(callback.capture(), any());
        org.mockito.Mockito.verify(archive, never()).appendAnalysisNote(any(), any(), any(), any(), any(), any());
        callback.getValue().run();
        org.mockito.Mockito.verify(archive).appendAnalysisNote(org.mockito.ArgumentMatchers.eq(92L), org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.eq("生成需求澄清项"), org.mockito.ArgumentMatchers.eq(items),
                org.mockito.ArgumentMatchers.contains("历史版本/run1"), org.mockito.ArgumentMatchers.eq("reviewer"));
    }

    private IntakeClarificationAnalysisService newService(IntakeMapper intakeMapper,
                                                          IntakeClarificationAnalysisMapper analysisMapper,
                                                          Executor executor) {
        return new IntakeClarificationAnalysisService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                analysisMapper,
                mock(ProjectMapper.class),
                mock(GitlabRepositoryService.class),
                mock(ProjectKnowledgeBaseService.class),
                mock(CodexCliClarificationAnalysisGenerator.class),
                mock(DevelopmentAnalysisService.class),
                executor,
                new ObjectMapper()
        );
    }

    private IntakeRecordEntity clarifyingDevelopmentIntake() {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(92L);
        entity.setDemandStatus("待澄清");
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202605250005","requirementType":"研发需求","requirementName":"驻车电池项目风控准入规则变更","projectHint":"智能柜","fields":[],"attachmentSummaries":[]}
                """);
        return entity;
    }

    private IntakeRecordEntity clarifyingDevelopmentIntakeWithSystemProjectHint() {
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(119L);
        entity.setDemandStatus("待澄清");
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalCode":"202606220001","requirementType":"研发需求","requirementName":"沃橙商贸、极石商贸订单支付需求-0622","businessLine":"嘉泰资产平台","businessLineCode":"BL000003","projectHint":"嘉泰资产平台；消费分期业务系统（信易通平台）；先行通资产平台","fields":[],"attachmentSummaries":[]}
                """);
        return entity;
    }

    private IntakeClarificationAnalysisEntity pendingAnalysis(LocalDateTime updatedAt) {
        IntakeClarificationAnalysisEntity entity = new IntakeClarificationAnalysisEntity();
        entity.setId(2L);
        entity.setIntakeId(92L);
        entity.setBusinessLine("智能柜");
        entity.setAnalysisStatus("PENDING");
        entity.setAnalysisMessage(null);
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");
        entity.setUpdatedAt(updatedAt);
        return entity;
    }

    private IntakeClarificationAnalysisEntity failedAnalysis(LocalDateTime updatedAt) {
        IntakeClarificationAnalysisEntity entity = pendingAnalysis(updatedAt);
        entity.setAnalysisStatus("FAILED");
        entity.setAnalysisMessage("后台执行中断（历史遗留），可重新分析");
        return entity;
    }
}
