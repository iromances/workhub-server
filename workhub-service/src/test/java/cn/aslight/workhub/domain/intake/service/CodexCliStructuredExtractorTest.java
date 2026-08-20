package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexCliStructuredExtractorTest {

    @Test
    void buildInvocation_shouldAddImageAsInputAndTextSummaryIntoPrompt() {
        RecordingAiGatewayClient aiGatewayClient = new RecordingAiGatewayClient();
        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(
                aiGatewayClient,
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

        CodexCliStructuredExtractor.CodexCliExtractionResult result = extractor.extract(
                "企业微信审批",
                "审批编号：202603250009",
                attachments,
                summaries
        );

        assertTrue(result.attempted());
        assertEquals("intake.structured.extract", aiGatewayClient.capturedRequest.useCaseCode());
    }

    @Test
    void extract_shouldFailClearlyWhenStructuredExtractionUseCaseIsMissing() {
        AiGatewayClient aiGatewayClient = mock(AiGatewayClient.class);
        when(aiGatewayClient.isConfigured("intake.structured.extract")).thenReturn(false);
        CodexCliStructuredExtractor extractor = new CodexCliStructuredExtractor(
                aiGatewayClient,
                new ObjectMapper()
        );

        CodexCliStructuredExtractor.CodexCliExtractionResult result = extractor.extract(
                "需求录入",
                "需求名称：测试需求",
                List.of(),
                List.of()
        );

        assertTrue(result.attempted());
        assertEquals("AI 场景未配置或已停用: intake.structured.extract", result.failureSummary());
    }

    @Test
    void buildCommand_shouldCarryConfiguredReasoningEffort() {
        AiProperties properties = new AiProperties();
        properties.getCodexCli().setEnabled(true);

        CodexCliClient client = new CodexCliClient(properties);
        List<String> command = client.buildCommand(
                new CodexCliClient.CodexCliRequest(
                        "intake.structured.extract",
                        Path.of("/tmp"),
                        List.of(),
                        List.of(),
                        "/tmp/schema.json",
                        "Return JSON"
                ),
                Path.of("/tmp/schema.json"),
                Path.of("/tmp/output.json"),
                new CodexCliClient.CodexCliExecutionOptions("codex", "gpt-5.5", "low", 600, true, true)
        );

        assertTrue(command.contains("-c"));
        assertTrue(command.contains("model_reasoning_effort=\"low\""));
        assertFalse(command.contains("Return JSON"));
    }

    @Test
    void buildCommand_shouldUseGatewaySceneConfigDirectly() {
        AiProperties properties = new AiProperties();
        CodexCliClient client = new CodexCliClient(properties);
        List<String> command = client.buildCommand(
                new CodexCliClient.CodexCliRequest(
                        "intake.structured.extract",
                        Path.of("/tmp"),
                        List.of(),
                        List.of(),
                        "/tmp/schema.json",
                        "Return JSON"
                ),
                Path.of("/tmp/schema.json"),
                Path.of("/tmp/output.json"),
                new CodexCliClient.CodexCliExecutionOptions("codex", "gpt-5.5", "xhigh", 600, true, true)
        );

        assertTrue(command.contains("gpt-5.5"));
        assertFalse(command.contains("gpt-5.3-codex"));
        assertTrue(command.contains("model_reasoning_effort=\"xhigh\""));
        assertFalse(command.contains("model_reasoning_effort=\"low\""));
    }

    @Test
    void buildCommand_shouldRejectMissingProviderCommandInsteadOfFallingBack() {
        CodexCliClient client = new CodexCliClient(new AiProperties());
        CodexCliClient.CodexCliRequest request = new CodexCliClient.CodexCliRequest(
                "intake.structured.extract",
                Path.of("/tmp"),
                List.of(),
                List.of(),
                "/tmp/schema.json",
                "Return JSON"
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> client.buildCommand(
                        request,
                        Path.of("/tmp/schema.json"),
                        Path.of("/tmp/output.json"),
                        new CodexCliClient.CodexCliExecutionOptions(null, "gpt-5.5", "xhigh", 600, true, true)
                )
        );

        assertTrue(exception.getMessage().contains("CLI 命令"));
    }

    @Test
    void buildSqlDraftPrompt_shouldRequireReadonlySqlAndQuestionsForUnknownSchema() {
        RecordingAiGatewayClient aiGatewayClient = new RecordingAiGatewayClient();
        CodexCliSqlDraftGenerator generator = new CodexCliSqlDraftGenerator(
                aiGatewayClient,
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

        CodexCliSqlDraftGenerator.SqlDraftGenerationResult result = generator.generate(
                entity,
                structuredData,
                List.of(new AttachmentService.AttachmentFileContext(1L, "截图", "demand.png", "/tmp/workhub-sql/demand.png", "image/png"))
        );

        assertTrue(result.succeeded());
        assertEquals("intake.sql-draft.generate", aiGatewayClient.capturedRequest.useCaseCode());
    }

    private static class RecordingAiGatewayClient implements AiGatewayClient {

        private AiGatewayRequest capturedRequest;

        @Override
        public boolean isConfigured(String useCaseCode) {
            return true;
        }

        @Override
        public AiGatewayResult execute(AiGatewayRequest request) {
            return executeStructured(request);
        }

        @Override
        public AiGatewayResult executeStructured(AiGatewayRequest request) {
            this.capturedRequest = request;
            if ("intake.structured.extract".equals(request.useCaseCode())) {
                return AiGatewayResult.succeeded("""
                        {
                          "category": "需求审批",
                          "fields": []
                        }
                        """, "test-provider", "test-model", 1L);
            }
            if ("intake.sql-draft.generate".equals(request.useCaseCode())) {
                return AiGatewayResult.succeeded("""
                        {
                          "dialect": "MySQL",
                          "sql": "SELECT 1",
                          "explanation": "查询示例",
                          "parameters": [],
                          "assumptions": [],
                          "questions": [],
                          "riskWarnings": []
                        }
                        """, "test-provider", "test-model", 1L);
            }
            return AiGatewayResult.failed("unexpected useCaseCode", null, null, 1L);
        }

        @Override
        public AiGatewayResult probe(Long providerId, String model) {
            return AiGatewayResult.failed("not supported", null, model, 1L);
        }
    }
}
