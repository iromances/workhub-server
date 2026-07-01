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

        assertEquals("BL000003", existing.getBusinessLine());
        verify(analysisMapper).update(existing);
        verify(executor).execute(any(Runnable.class));
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
