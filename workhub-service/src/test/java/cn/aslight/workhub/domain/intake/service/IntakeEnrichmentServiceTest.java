package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTextExtractionService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        entity.setApprovalCode("202603240005");
        entity.setSubmittedAt(LocalDateTime.of(2026, 3, 24, 11, 54));
        entity.setRequirementType("研发需求");
        entity.setRequirementDigest("原始需求");
        entity.setRequirementName("原始需求");
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
                "新增分账核验规则并改造接口",
                "里易二轮车换电项目迭代优化（三期）——新增分账核验规则",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new IntakeStructuredField("需求名称", "增强后的需求")),
                List.of(),
                null
        ));

        when(codexCliStructuredExtractor.extract(eq("企业微信审批"), eq("审批编号：A-001"), eq(attachments), eq(extractionBatch.summaries())))
                .thenReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(false, null, null));

        service.scheduleUploadedEnrichment(7L, "企业微信审批", "审批编号：A-001");

        InOrder inOrder = inOrder(intakeMapper);
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.PENDING), eq(null), eq(null), any(LocalDateTime.class));
        inOrder.verify(intakeMapper).updateEnrichmentState(eq(7L), eq(IntakeEnrichmentStatus.RUNNING), eq(null), eq(null), any(LocalDateTime.class));
        ArgumentCaptor<IntakeRecordEntity> entityCaptor = ArgumentCaptor.forClass(IntakeRecordEntity.class);
        inOrder.verify(intakeMapper).updateFormalFields(entityCaptor.capture());
        inOrder.verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(7L),
                any(String.class),
                eq("已收录"),
                eq(IntakeEnrichmentStatus.SUCCEEDED),
                eq(null),
                any(LocalDateTime.class)
        );
        assertEquals("feature/liyi_ebike_split_202603240005", entityCaptor.getValue().getDevelopmentBranchName());
        verify(codexCliStructuredExtractor).extract("企业微信审批", "审批编号：A-001", attachments, extractionBatch.summaries());
    }

    @Test
    void scheduleUploadedEnrichment_shouldMarkFailedWhenCodexFailsEvenIfAttachmentParserProducedData() {
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
        entity.setId(10L);
        entity.setStructuredDataJson(null);
        entity.setDemandStatus(null);
        when(intakeMapper.findById(10L)).thenReturn(entity);

        List<AttachmentService.AttachmentFileContext> attachments = List.of(
                new AttachmentService.AttachmentFileContext(1L, "附件", "requirement.docx", "/tmp/requirement.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        when(attachmentService.listIntakeFileContexts(10L)).thenReturn(attachments);

        AttachmentTextExtractionService.AttachmentExtractionBatch extractionBatch =
                new AttachmentTextExtractionService.AttachmentExtractionBatch(
                        List.of(new IntakeAttachmentSummary("requirement.docx", "DOCX", "申请人：顾佳青\n需求描述：趣学呗变更平台主体账户信息")),
                        List.of()
                );
        when(attachmentTextExtractionService.extractSummaries(attachments)).thenReturn(extractionBatch);
        when(structuredDataExtractor.extract(any())).thenReturn(new IntakeStructuredData(
                "需求审批",
                null,
                "顾佳青",
                null,
                null,
                null,
                "研发需求",
                null,
                null,
                "趣学呗主体账户变更",
                null,
                "趣学呗需要调整平台收款账户。",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new IntakeStructuredField("申请人", "顾佳青")),
                List.of(),
                null
        ));
        when(codexCliStructuredExtractor.extract(eq("需求录入"), eq("需求截图：image.png"), eq(attachments), eq(extractionBatch.summaries())))
                .thenReturn(new CodexCliStructuredExtractor.CodexCliExtractionResult(true, null, "Codex CLI 执行失败"));

        service.scheduleUploadedEnrichment(10L, "需求录入", "需求截图：image.png");

        verify(intakeMapper).updateFormalFields(any(IntakeRecordEntity.class));
        verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(10L),
                any(String.class),
                eq(null),
                eq(IntakeEnrichmentStatus.FAILED),
                eq("Codex CLI 执行失败"),
                any(LocalDateTime.class)
        );
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

        verify(intakeMapper).updateFormalFields(any(IntakeRecordEntity.class));
        verify(intakeMapper).updateStructuredDataAndEnrichment(
                eq(9L),
                any(String.class),
                eq(null),
                eq(IntakeEnrichmentStatus.FAILED),
                eq("Codex CLI 解析超时"),
                any(LocalDateTime.class)
        );
    }
}
