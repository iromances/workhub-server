package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCliStructuredExtractorTest {

    @Test
    void buildInvocation_shouldAddImageAsInputAndTextSummaryIntoPrompt() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);
        properties.getCodexCli().setCommand("codex");

        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(
                new CodexCliClient(properties),
                new ObjectMapper()
        );
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

    @Test
    void buildCommand_shouldCarryConfiguredReasoningEffort() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);
        properties.getCodexCli().setCommand("codex");
        properties.getCodexCli().setReasoningEffort("low");

        CodexCliClient client = new CodexCliClient(properties);
        List<String> command = client.buildCommand(
                new CodexCliClient.CodexCliRequest(
                        Path.of("/tmp"),
                        List.of(),
                        List.of(),
                        "/tmp/schema.json",
                        "Return JSON"
                ),
                Path.of("/tmp/schema.json"),
                Path.of("/tmp/output.json")
        );

        assertTrue(command.contains("-c"));
        assertTrue(command.contains("model_reasoning_effort=\"low\""));
        assertFalse(command.contains("Return JSON"));
    }
}
