package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.domain.attachment.service.AttachmentService;
import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCliStructuredExtractorTest {

    @Test
    void buildInvocation_shouldAddImageAsInputAndTextSummaryIntoPrompt() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);
        properties.getCodexCli().setCommand("codex");

        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(properties, new ObjectMapper());
        List<AttachmentService.AttachmentFileContext> attachments = List.of(
                new AttachmentService.AttachmentFileContext(1L, "截图", "approval.png", "/tmp/workhub-test/approval.png", "image/png"),
                new AttachmentService.AttachmentFileContext(2L, "附件", "requirement.docx", "/tmp/workhub-test/requirement.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        );
        List<IntakeAttachmentSummary> summaries = List.of(
                new IntakeAttachmentSummary("requirement.docx", "DOCX", "需求名称：绑卡需求\n预估工时：2d")
        );

        CodexCliStructuredExtractor.CodexCliInvocation invocation = extractor.buildInvocation(
                "企业微信审批",
                "审批编号：202603250009",
                attachments,
                summaries
        );

        assertTrue(invocation.imagePaths().contains("/tmp/workhub-test/approval.png"));
        assertTrue(invocation.addDirs().contains("/tmp/workhub-test"));
        assertTrue(invocation.prompt().contains("非图片附件文本摘要"));
        assertTrue(invocation.prompt().contains("requirement.docx"));
        assertTrue(invocation.prompt().contains("需求名称：绑卡需求"));
    }
}
