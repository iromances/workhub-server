package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.IntakeClarificationAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.intake.IntakeClarificationAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeClarificationAnalysisServiceTest {

    @Test
    void analyze_shouldRetryStalePendingAnalysis() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeClarificationAnalysisMapper analysisMapper = mock(IntakeClarificationAnalysisMapper.class);
        Executor executor = mock(Executor.class);
        IntakeClarificationAnalysisService service = newService(intakeMapper, analysisMapper, executor);

        when(intakeMapper.findById(92L)).thenReturn(clarifyingDevelopmentIntake());
        IntakeClarificationAnalysisEntity existing = pendingAnalysis(LocalDateTime.now().minusMinutes(31));
        when(analysisMapper.findLatestByIntakeId(92L)).thenReturn(existing);

        service.analyze(92L, "admin");

        verify(analysisMapper).update(existing);
        verify(executor).execute(any(Runnable.class));
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

        verify(analysisMapper, never()).update(any());
        verify(executor, never()).execute(any(Runnable.class));
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

        assertEquals("BL000003", existing.getBusinessLineCode());
        assertEquals(null, existing.getBusinessLine());
        verify(analysisMapper).update(existing);
        verify(executor).execute(any(Runnable.class));
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
        entity.setAnalysisMessage("需求澄清分析已提交，系统正在后台拉取 GitLab 最新代码并调用 AI 分析。");
        entity.setCreatedBy("admin");
        entity.setUpdatedBy("admin");
        entity.setUpdatedAt(updatedAt);
        return entity;
    }
}
