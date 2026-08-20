package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexCliDevelopmentAnalysisGeneratorTest {

    @Test
    void generate_shouldIncludeStructuredFieldsAndAttachmentSummariesInPrompt() {
        RecordingAiGatewayClient aiGatewayClient = new RecordingAiGatewayClient();
        CodexCliDevelopmentAnalysisGenerator generator = new CodexCliDevelopmentAnalysisGenerator(
                aiGatewayClient,
                new ChinaWorkdayCalendar(),
                new ObjectMapper()
        );
        IntakeRecordEntity entity = new IntakeRecordEntity();
        entity.setId(9L);
        entity.setRawContent("原始需求：需要新增绑卡核验规则");
        IntakeStructuredData structuredData = new IntakeStructuredData(
                "需求审批",
                "绑卡核验规则",
                "周拓",
                null,
                "202603250009",
                "2026/3/25 16:17",
                "研发需求",
                "feature/bind_card_verify_20260325",
                null,
                "绑卡核验",
                "新增绑卡核验规则",
                "客户绑卡至嘉泰保理时增加核验规则。",
                "供应链业务部",
                "供应链科技",
                "最高优先级",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "供应链科技",
                List.of(new IntakeStructuredField("需求所属业务线", "供应链科技")),
                List.of(new IntakeAttachmentSummary("方案.docx", "DOCX", "附件正文摘要：需要增加绑卡核验接口和异常提示。")),
                null
        );
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
                        List.of(
                                new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/demo.git", Path.of("/tmp/workhub-demo/demo")),
                                new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/settlement-service.git", Path.of("/tmp/workhub-demo/settlement-service"))
                        )
                );
        List<UserOptionResponse> developers = List.of(new UserOptionResponse("dev-a", "研发A", "供应链科技"));

        DevelopmentAnalysisDraft draft = generator.generate(
                entity,
                structuredData,
                project,
                repositoryBundle,
                developers,
                "知识库需求迭代文件：供应链科技/供应链项目/需求迭代/2026/202603250009-新增绑卡核验规则.md\n\n# 新增绑卡核验规则\n\n## 附件与截图内容\n\n方案.docx：需要增加绑卡核验接口和异常提示。",
                "### 供应链/绑卡说明.md\n嘉泰保理绑卡需要遵循商户号路由规则。"
        );

        assertNotNull(draft);
        assertEquals("2h", draft.totalEstimatedEffort());
        assertEquals(List.of("绑卡需要增加核验规则"), draft.requirementChangePoints());
        assertEquals("实现绑卡核验", draft.workItems().getFirst().title());
        assertEquals("2h", draft.workItems().getFirst().estimatedEffort());
        assertEquals("dev-a", draft.workItems().getFirst().ownerUserName());
        String capturedPrompt = String.valueOf(aiGatewayClient.capturedRequest.variables().get("prompt"));
        assertTrue(capturedPrompt.contains("结构化识别字段"));
        assertTrue(capturedPrompt.contains("需求 Markdown 文件"));
        assertTrue(capturedPrompt.contains("知识库需求迭代文件：供应链科技/供应链项目/需求迭代/2026/202603250009-新增绑卡核验规则.md"));
        assertTrue(capturedPrompt.contains("需求所属业务线：供应链科技"));
        assertTrue(capturedPrompt.contains("附件内容摘要"));
        assertTrue(capturedPrompt.contains("方案.docx"));
        assertTrue(capturedPrompt.contains("附件正文摘要：需要增加绑卡核验接口和异常提示。"));
        assertTrue(capturedPrompt.contains("项目知识库参考"));
        assertTrue(capturedPrompt.contains("嘉泰保理绑卡需要遵循商户号路由规则"));
        assertTrue(capturedPrompt.contains("先读需求 Markdown 和补充材料"));
        assertTrue(capturedPrompt.contains("再查业务线 Git 代码"));
        assertTrue(capturedPrompt.contains("title 用 Jira/禅道风格"));
        assertTrue(capturedPrompt.contains("changePoints 表示“改地点”，必须按有序列表顺序列出"));
        assertTrue(capturedPrompt.contains("relatedFiles/evidenceRefs 写代码或材料证据"));
        assertEquals("intake.development.analyze", aiGatewayClient.capturedRequest.useCaseCode());
        assertEquals(2, aiGatewayClient.capturedRequest.allowedDirectories().size());

        generator.adjust(draft, "保留现有代码证据并调整任务描述", project, repositoryBundle, developers);

        assertEquals("intake.development.adjust", aiGatewayClient.capturedRequest.useCaseCode());
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
                      "summary": "新增绑卡核验规则",
                      "requirementChangePoints": ["绑卡需要增加核验规则"],
                      "impactedModules": ["binding"],
                      "risks": [],
                      "questions": [],
                      "workItems": [
                        {
                          "title": "实现绑卡核验",
                          "description": "增加绑卡核验接口和异常提示",
                          "estimatedEffort": "2h",
                          "ownerUserName": "dev-a"
                        }
                      ]
                    }
                    """, "test-provider", "test-model", 1L);
        }

        @Override
        public AiGatewayResult probe(Long providerId, String model) {
            return AiGatewayResult.failed("not supported", null, model, 1L);
        }
    }
}
