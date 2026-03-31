package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.domain.attachment.service.AttachmentService;
import cn.aslight.workhub.domain.attachment.service.AttachmentTextExtractionService;
import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import cn.aslight.workhub.domain.intake.dto.IntakeStructuredData;
import cn.aslight.workhub.domain.intake.dto.IntakeStructuredField;
import cn.aslight.workhub.domain.intake.mapper.IntakeMapper;
import cn.aslight.workhub.domain.intake.model.IntakeRecordEntity;
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
                {"category":"需求审批","approvalTitle":null,"approvalCode":"A-001","requirementName":"原始需求","requirementSummary":null,"department":null,"businessLine":null,"remark":null,"estimatedEffort":null,"plannedDueDate":null,"projectHint":null,"fields":[{"label":"审批编号","value":"A-001"}],"attachmentSummaries":[]}
                """);
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
                "增强后的需求",
                null,
                null,
                null,
                null,
                "2d",
                null,
                null,
                List.of(new IntakeStructuredField("需求名称", "增强后的需求")),
                List.of()
        ));

        when(codexCliStructuredExtractor.extract(eq("企业微信审批"), eq("审批编号：A-001"), eq(attachments), eq(extractionBatch.summaries())))
                .thenReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(false, null, null));

        service.scheduleUploadedEnrichment(7L, "企业微信审批", "审批编号：A-001");

        InOrder inOrder = inOrder(intakeMapper);
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.PENDING), eq(null), any(LocalDateTime.class));
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.RUNNING), eq(null), any(LocalDateTime.class));
        inOrder.verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(7L),
                any(String.class),
                eq(IntakeEnrichmentStatus.SUCCEEDED),
                eq(null),
                any(LocalDateTime.class)
        );
        verify(codexCliStructuredExtractor).extract("企业微信审批", "审批编号：A-001", attachments, extractionBatch.summaries());
    }
}
