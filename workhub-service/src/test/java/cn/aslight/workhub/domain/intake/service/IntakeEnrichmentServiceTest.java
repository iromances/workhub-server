package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntakeEnrichmentServiceTest {

    @Test
    void scheduleUploadedEnrichment_shouldTransitPendingRunningAndSucceeded() throws Exception {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentTextExtractionService attachmentTextExtractionService = mock(AttachmentTextExtractionService.class);
        IntakeStructuredDataExtractor structuredDataExtractor = mock(IntakeStructuredDataExtractor.class);
        CodexCliStructuredExtractor codexCliStructuredExtractor = mock(CodexCliStructuredExtractor.class);
        Executor executor = Runnable::run;

        IntakeEnrichmentService service = new IntakeEnrichmentService(
                intakeMapper,
                attachmentService,
                attachmentTextExtractionService,
                structuredDataExtractor,
                codexCliStructuredExtractor,
                executor,
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(7L);
        entity.setStructuredDataJson("""
                {"category":"需求审批","approvalTitle":null,"proposerName":null,"approvalCode":"A-001","submittedTime":null,"requirementType":"研发需求","requirementDigest":"原始需求","requirementName":"原始需求","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"estimatedEffort":null,"plannedDueDate":null,"actualEffort":null,"actualCompletedTime":null,"acceptanceTime":null,"projectHint":null,"fields":[{"label":"审批编号","value":"A-001"}],"attachmentSummaries":[]}
                """);
        entity.setDemandStatus(null);
        when(intakeMapper.findById(7L)).thenReturn(entity);

        List<AttachmentService.AttachmentFileContext> attachments = List.of(
                new AttachmentService.AttachmentFileContext(1L, "附件", "requirement.docx", "/tmp/requirement.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        when(attachmentService.listIntakeFileContexts(7L)).thenReturn(attachments);

        AttachmentTextExtractionService.AttachmentExtractionBatch extractionBatch =
                new AttachmentTextExtractionService.AttachmentExtractionBatch(
                        List.of(new IntakeAttachmentSummary("requirement.docx", "DOCX", "需求名称：增强后的需求\n预估工时：2d")),
                        List.of()
                );
        when(attachmentTextExtractionService.extractSummaries(attachments)).thenReturn(extractionBatch);

        when(structuredDataExtractor.extract(any())).thenReturn(new IntakeStructuredData(
                "需求审批",
                null,
                null,
                null,
                null,
                null,
                "研发需求",
                null,
                null,
                "增强后的需求",
                "增强后的需求",
                null,
                null,
                null,
                null,
                "2d",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new IntakeStructuredField("需求名称", "增强后的需求")),
                List.of()
        ));

        when(codexCliStructuredExtractor.extract(eq("企业微信审批"), eq("审批编号：A-001"), eq(attachments), eq(extractionBatch.summaries())))
                .thenReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(false, null, null));

        service.scheduleUploadedEnrichment(7L, "企业微信审批", "审批编号：A-001");

        InOrder inOrder = inOrder(intakeMapper);
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.PENDING), eq("已收录"), eq(null), any(LocalDateTime.class));
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.RUNNING), eq(null), eq(null), any(LocalDateTime.class));
        inOrder.verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(7L),
                any(String.class),
                eq("已收录"),
                eq(IntakeEnrichmentStatus.SUCCEEDED),
                eq(null),
                any(LocalDateTime.class)
        );
        verify(codexCliStructuredExtractor).extract("企业微信审批", "审批编号：A-001", attachments, extractionBatch.summaries());
    }

    @Test
    void scheduleUploadedEnrichment_shouldMarkFailedWhenCodexFailsAndNoMeaningfulDataProduced() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        AttachmentTextExtractionService attachmentTextExtractionService = mock(AttachmentTextExtractionService.class);
        IntakeStructuredDataExtractor structuredDataExtractor = mock(IntakeStructuredDataExtractor.class);
        CodexCliStructuredExtractor codexCliStructuredExtractor = mock(CodexCliStructuredExtractor.class);
        Executor executor = Runnable::run;

        IntakeEnrichmentService service = new IntakeEnrichmentService(
                intakeMapper,
                attachmentService,
                attachmentTextExtractionService,
                structuredDataExtractor,
                codexCliStructuredExtractor,
                executor,
                new ObjectMapper()
        );

        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(9L);
        entity.setStructuredDataJson(null);
        entity.setDemandStatus(null);
        when(intakeMapper.findById(9L)).thenReturn(entity);
        when(attachmentService.listIntakeFileContexts(9L)).thenReturn(List.of());
        when(attachmentTextExtractionService.extractSummaries(List.of()))
                .thenReturn(new AttachmentTextExtractionService.AttachmentExtractionBatch(List.of(), List.of()));
        when(structuredDataExtractor.extract(any())).thenReturn(null);
        when(codexCliStructuredExtractor.extract(eq("需求截图录入"), eq("需求截图：test.png"), eq(List.of()), eq(List.of())))
                .thenReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(true, null, "Codex CLI 解析超时"));

        service.scheduleUploadedEnrichment(9L, "需求截图录入", "需求截图：test.png");

        verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(9L),
                any(String.class),
                eq(null),
                eq(IntakeEnrichmentStatus.FAILED),
                eq("Codex CLI 解析超时"),
                any(LocalDateTime.class)
        );
        verify(intakeMapper, never()).updateEnrichmentState(eq(9L), eq(IntakeEnrichmentStatus.FAILED), any(), any(), any(LocalDateTime.class));
    }
}
