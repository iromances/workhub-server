package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void buildCommand_shouldPreferSystemConfigModelAndReasoningEffort() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);
        properties.getCodexCli().setCommand("codex");
        properties.getCodexCli().setModel("gpt-5.3-codex");
        properties.getCodexCli().setReasoningEffort("low");
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("ai.codexCli", "model")).thenReturn("gpt-5.5");
        when(sysConfigService.findPlainValue("ai.codexCli", "reasoningEffort")).thenReturn("xhigh");

        CodexCliClient client = new CodexCliClient(properties, sysConfigService);
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

        assertTrue(command.contains("gpt-5.5"));
        assertFalse(command.contains("gpt-5.3-codex"));
        assertTrue(command.contains("model_reasoning_effort=\"xhigh\""));
        assertFalse(command.contains("model_reasoning_effort=\"low\""));
    }

    @Test
    void buildSqlDraftPrompt_shouldRequireReadonlySqlAndQuestionsForUnknownSchema() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);

        CodexCliSqlDraftGenerator generator = new CodexCliSqlDraftGenerator(
                new CodexCliClient(properties),
                new ObjectMapper()
        );
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(1L);
        entity.setSourceType("需求截图附件录入");
        entity.setSourceChannel("需求录入");
        entity.setSenderName("ops-user");
        entity.setReceivedAt(LocalDateTime.of(2026, 4, 2, 10, 0));
        entity.setRawContent("导出 4 月支付失败订单");
        IntakeStructuredData structuredData = new IntakeStructuredData(
                "需求审批",
                "支付失败订单导出",
                "运维同学",
                null,
                "OPS-001",
                "2026/4/2 10:00",
                "数据提取/运维",
                null,
                null,
                "导出支付失败订单",
                "支付失败订单导出",
                "需要导出 4 月支付失败订单",
                null,
                "资产业务",
                "人工执行",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "资产业务",
                List.of(),
                List.of(new IntakeAttachmentSummary("说明.docx", "DOCX", "筛选条件：支付失败，时间为 4 月")),
                null
        );

        CodexCliSqlDraftGenerator.SqlDraftInvocation invocation = generator.buildInvocation(
                entity,
                structuredData,
                List.of(new AttachmentService.AttachmentFileContext(1L, "截图", "demand.png", "/tmp/workhub-sql/demand.png", "image/png"))
        );

        assertTrue(invocation.prompt().contains("只能生成只读 SELECT 查询"));
        assertTrue(invocation.prompt().contains("questions"));
        assertTrue(invocation.prompt().contains("支付失败订单导出"));
        assertTrue(invocation.prompt().contains("筛选条件：支付失败"));
        assertTrue(invocation.imagePaths().contains("/tmp/workhub-sql/demand.png"));
        assertTrue(invocation.addDirs().contains("/tmp/workhub-sql"));
    }
}
