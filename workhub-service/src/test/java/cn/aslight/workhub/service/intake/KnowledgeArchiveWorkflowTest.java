package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisEntity;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.service.workitem.WorkItemService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowledgeArchiveWorkflowTest {
    private final IntakeMapper intakes = mock(IntakeMapper.class);
    private final DevelopmentAnalysisMapper analyses = mock(DevelopmentAnalysisMapper.class);
    private final IntakeHistoryMapper histories = mock(IntakeHistoryMapper.class);
    private final ProjectKnowledgeBaseService archive = mock(ProjectKnowledgeBaseService.class);
    private final CodexCliDevelopmentAnalysisGenerator ai = mock(CodexCliDevelopmentAnalysisGenerator.class);
    private final WorkItemService workItems = mock(WorkItemService.class);
    private final ObjectMapper json = new ObjectMapper();
    private DevelopmentAnalysisService service;
    private DevelopmentAnalysisEntity saved;
    private DevelopmentAnalysisDraft draft;

    @BeforeEach
    void setUp() {
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(5L);
        intake.setDemandStatus("待评估");
        intake.setStructuredDataJson("{\"requirementType\":\"研发需求\",\"approvalCode\":\"202610180001\",\"requirementName\":\"归档需求\"}");
        when(intakes.findById(5L)).thenReturn(intake);
        draft = json.readValue("""
                {"status":"DRAFT","businessLine":"测试业务线","summary":"旧结论","workItems":[],
                 "totalEstimatedEffort":"2h","testingEstimatedEffort":"1h","developerPool":[],
                 "requirementRawPath":"raw/公司项目/requirements/测试/历史版本/run1/source.md"}
                """, DevelopmentAnalysisDraft.class);
        saved = new DevelopmentAnalysisEntity();
        saved.setId(9L);
        saved.setIntakeId(5L);
        saved.setAnalysisStatus("DRAFT");
        saved.setDraftJson(json.writeValueAsString(draft));
        saved.setUpdatedBy("reviewer");
        when(analyses.findLatestByIntakeId(5L)).thenReturn(saved);
        when(analyses.findById(9L)).thenReturn(saved);
        when(analyses.lockForKnowledgeArchive(9L)).thenReturn(9L);
        when(analyses.updateStatus(eq(9L), anyString(), any(), any(), any(), any()))
                .thenAnswer(call -> { saved.setAnalysisStatus(call.getArgument(1)); return 1; });
        when(archive.publishConfirmedAnalysis(any(), any(), any(), eq(9L), any(), any(), any()))
                .thenReturn(new ProjectKnowledgeBaseService.RequirementKnowledgeLocation(Path.of("/vault"), Path.of("/vault/raw/source.md"),
                        Path.of("/vault/wiki/研发评估.md"), "raw/source.md", "wiki/研发评估.md", "obsidian://confirmed"));
        service = new DevelopmentAnalysisService(intakes, histories, analyses, mock(ProjectMapper.class),
                mock(ProjectInvolvedSystemMapper.class), mock(UserMapper.class), mock(GitlabRepositoryService.class),
                archive, ai, workItems, ignored -> { }, json);
    }

    @Test
    void confirmationSchedulesPublicationInsteadOfWritingBeforeCommit() {
        service.confirm(5L, "reviewer");
        ArgumentCaptor<Runnable> export = ArgumentCaptor.forClass(Runnable.class);
        verify(archive).archiveAfterCommit(export.capture(), any());
        verify(archive, never()).publishConfirmedAnalysis(any(), any(), any(), any(), any(), any(), any());
        export.getValue().run();
        verify(archive).publishConfirmedAnalysis(any(), any(), any(), eq(9L), eq(draft), eq("reviewer"), any());
        assertTrue(saved.getDraftJson().contains("obsidian://confirmed"));
        verifyNoInteractions(ai, workItems);
    }

    @Test
    void lateCallbackKeepsHistoricalAnalysisButCannotOverwriteNewerHumanChanges() {
        service.confirm(5L, "reviewer");
        ArgumentCaptor<Runnable> export = ArgumentCaptor.forClass(Runnable.class);
        verify(archive).archiveAfterCommit(export.capture(), any());
        String newer = saved.getDraftJson().replace("旧结论", "已经人工修订的新结论");
        saved.setDraftJson(newer);
        export.getValue().run();
        assertEquals(newer, saved.getDraftJson());
        verify(archive, never()).publishConfirmedAnalysis(any(), any(), any(), any(), any(), any(), any());
        verify(archive).appendAnalysisNote(eq(5L), eq(9L), contains("迟到"), eq(draft), any(), eq("reviewer"));
        verify(analyses, never()).update(any());
    }

    @Test
    void explicitRetryUsesSavedConfirmationWithoutCreatingTasksOrCallingAi() {
        saved.setAnalysisStatus("CONFIRMED");
        assertTrue(service.retryKnowledgeArchive(5L, "operator").contains("重新归档"));
        assertTrue(service.retryKnowledgeArchive(5L, "operator").contains("重新归档"));
        verify(archive, times(2)).publishConfirmedAnalysis(any(), any(), any(), eq(9L), any(), eq("reviewer"), any());
        verifyNoInteractions(ai, workItems);
        verify(analyses, never()).updateStatus(any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryOfUnconfirmedDraftOnlyAppendsNotes() {
        service.retryKnowledgeArchive(5L, "operator");
        verify(archive).appendAnalysisNote(eq(5L), eq(9L), eq("生成研发评估"), eq(draft), any(), eq("reviewer"));
        verify(archive, never()).publishConfirmedAnalysis(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(ai, workItems);
    }

    @Test
    void failedHistoricalPreservationStopsReevaluationBeforePreviousDraftIsCleared() {
        doThrow(new IllegalStateException("notes 不可写")).when(archive)
                .appendAnalysisNote(any(), any(), contains("重评前"), any(), any(), any());
        assertThrows(IllegalStateException.class, () -> service.analyze(5L, "reviewer"));
        assertEquals("旧结论", json.readValue(saved.getDraftJson(), DevelopmentAnalysisDraft.class).summary());
        verify(analyses, never()).updateExecutionState(any());
        verify(analyses, never()).update(any());
        verifyNoInteractions(ai, workItems);
    }
}
