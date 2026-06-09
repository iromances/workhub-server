package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.model.intake.DevelopmentAnalysisDraft;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.model.system.UserOptionResponse;
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
        RecordingCodexCliClient codexCliClient = new RecordingCodexCliClient();
        CodexCliDevelopmentAnalysisGenerator generator = new CodexCliDevelopmentAnalysisGenerator(
                codexCliClient,
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

        DevelopmentAnalysisDraft draft = generator.generate(
                entity,
                structuredData,
                project,
                new GitlabRepositoryService.GitlabRepositoryBundle(
                        "workhub",
                        Path.of("/tmp/workhub-demo"),
                        List.of(
                                new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/demo.git", Path.of("/tmp/workhub-demo/demo")),
                                new GitlabRepositoryService.GitlabRepository("https://gitlab.example.com/workhub/settlement-service.git", Path.of("/tmp/workhub-demo/settlement-service"))
                        )
                ),
                List.of(new UserOptionResponse("dev-a", "研发A", "供应链科技")),
                "知识库需求迭代文件：供应链科技/供应链项目/需求迭代/2026/202603250009-新增绑卡核验规则.md\n\n# 新增绑卡核验规则\n\n## 附件与截图内容\n\n方案.docx：需要增加绑卡核验接口和异常提示。",
                "### 供应链/绑卡说明.md\n嘉泰保理绑卡需要遵循商户号路由规则。"
        );

        assertNotNull(draft);
        assertEquals("2h", draft.totalEstimatedEffort());
        assertEquals(List.of("绑卡需要增加核验规则"), draft.requirementChangePoints());
        assertEquals("实现绑卡核验", draft.workItems().getFirst().title());
        assertEquals("2h", draft.workItems().getFirst().estimatedEffort());
        assertEquals("dev-a", draft.workItems().getFirst().ownerUserName());
        assertTrue(codexCliClient.capturedRequest.prompt().contains("结构化识别字段"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("需求 Markdown 文件"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("知识库需求迭代文件：供应链科技/供应链项目/需求迭代/2026/202603250009-新增绑卡核验规则.md"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("需求所属业务线：供应链科技"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("附件内容摘要"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("方案.docx"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("附件正文摘要：需要增加绑卡核验接口和异常提示。"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("项目知识库参考"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("嘉泰保理绑卡需要遵循商户号路由规则"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("先读需求 Markdown 和补充材料"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("再查业务线 Git 代码"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("title 用 Jira/禅道风格"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("changePoints 表示“改地点”，必须按有序列表顺序列出"));
        assertTrue(codexCliClient.capturedRequest.prompt().contains("relatedFiles/evidenceRefs 写代码或材料证据"));
        assertEquals(2, codexCliClient.capturedRequest.addDirs().size());
    }

    private static class RecordingCodexCliClient extends CodexCliClient {

        private CodexCliRequest capturedRequest;

        RecordingCodexCliClient() {
            super(new AiProperties());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public CodexCliResult execute(CodexCliRequest request) {
            this.capturedRequest = request;
            return CodexCliResult.succeeded("""
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
                    """);
        }
    }
}
