package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeClarificationItem;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCliClarificationAnalysisGeneratorTest {

    @Test
    void generate_shouldPassStableUseCaseCode() throws Exception {
        RecordingAiGatewayClient aiGatewayClient = new RecordingAiGatewayClient();
        ObjectMapper objectMapper = new ObjectMapper();
        CodexCliClarificationAnalysisGenerator generator = new CodexCliClarificationAnalysisGenerator(
                aiGatewayClient,
                objectMapper
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setRawContent("新增绑卡核验规则");
        IntakeStructuredData structuredData = objectMapper.readValue("""
                {
                  "approvalCode": "REQ-001",
                  "requirementType": "研发需求",
                  "requirementName": "绑卡核验规则",
                  "requirementSummary": "新增绑卡核验规则",
                  "fields": [],
                  "attachmentSummaries": []
                }
                """, IntakeStructuredData.class);
        ProjectDetailResponse project = new ProjectDetailResponse(
                1L,
                "SUPPLY",
                "供应链项目",
                "研发",
                "供应链科技",
                "owner",
                "ACTIVE",
                "供应链项目代码",
                LocalDateTime.now(),
                LocalDateTime.now()
        );
        GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle =
                new GitlabRepositoryService.GitlabRepositoryBundle(
                        "workhub",
                        Path.of("/tmp/workhub-demo"),
                        List.of(new GitlabRepositoryService.GitlabRepository(
                                "https://gitlab.example.com/workhub/demo.git",
                                Path.of("/tmp/workhub-demo/demo")
                        ))
                );

        List<IntakeClarificationItem> items = generator.generate(
                intake,
                structuredData,
                project,
                repositoryBundle,
                "# 绑卡核验规则",
                "项目知识库"
        );

        assertTrue(items.isEmpty());
        assertEquals("intake.clarification.analyze", aiGatewayClient.capturedRequest.useCaseCode());
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
            return AiGatewayResult.succeeded("""
                    {
                      "confirmationItems": [],
                      "riskItems": []
                    }
                    """, "test-provider", "test-model", 1L);
        }

        @Override
        public AiGatewayResult probe(Long providerId, String model) {
            return AiGatewayResult.failed("not supported", null, model, 1L);
        }
    }
}
