package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeClarificationItem;
import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeStructuredField;
import cn.aslight.workhub.model.project.ProjectDetailResponse;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 Codex CLI 的研发需求澄清项生成器。
 */
@Component
public class CodexCliClarificationAnalysisGenerator {

    private static final String USE_CASE_CODE = "intake.clarification.analyze";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "confirmationItems": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "description": { "type": ["string", "null"] },
                      "evidence": { "type": ["string", "null"] }
                    },
                    "required": ["title", "description", "evidence"],
                    "additionalProperties": false
                  }
                },
                "riskItems": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "title": { "type": "string" },
                      "description": { "type": ["string", "null"] },
                      "evidence": { "type": ["string", "null"] }
                    },
                    "required": ["title", "description", "evidence"],
                    "additionalProperties": false
                  }
                }
              },
              "required": ["confirmationItems", "riskItems"],
              "additionalProperties": false
            }
            """;

    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    public CodexCliClarificationAnalysisGenerator(AiGatewayClient aiGatewayClient,
                                                  ObjectMapper objectMapper) {
        this.aiGatewayClient = aiGatewayClient;
        this.objectMapper = objectMapper;
    }

    public List<IntakeClarificationItem> generate(IntakeRecordEntity intake,
                                                  IntakeStructuredData structuredData,
                                                  ProjectDetailResponse project,
                                                  GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                                  String requirementMarkdownContext,
                                                  String knowledgeBaseContext) {
        return generate(intake, structuredData, project, repositoryBundle, requirementMarkdownContext, knowledgeBaseContext, null);
    }

    public List<IntakeClarificationItem> generate(IntakeRecordEntity intake,
                                                  IntakeStructuredData structuredData,
                                                  ProjectDetailResponse project,
                                                  GitlabRepositoryService.GitlabRepositoryBundle repositoryBundle,
                                                  String requirementMarkdownContext,
                                                  String knowledgeBaseContext,
            cn.aslight.workhub.service.ai.AiResultRecovery recovery) {
        if (!aiGatewayClient.isConfigured(USE_CASE_CODE)) {
            throw new IllegalArgumentException("AI 场景未配置或已停用，不能执行需求澄清分析");
        }
        AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.withVariables(
                USE_CASE_CODE,
                buildVariables(intake, structuredData, project, requirementMarkdownContext, knowledgeBaseContext),
                repositoryBundle.localRoot(),
                repositoryBundle.repositories().stream().map(item -> item.localPath().toString()).toList(),
                List.of(),
                OUTPUT_SCHEMA
        ).withResultRecovery(recovery));
        if (!result.succeeded()) {
            throw new IllegalArgumentException(result.failureSummary());
        }
        try {
            RawClarificationAnalysis raw = objectMapper.readValue(result.output(), RawClarificationAnalysis.class);
            return normalize(raw);
        } catch (Exception ex) {
            throw new IllegalArgumentException("需求澄清分析结果解析失败：" + summarizeException(ex), ex);
        }
    }

    Map<String, Object> buildVariables(IntakeRecordEntity intake,
                                       IntakeStructuredData structuredData,
                                       ProjectDetailResponse project,
                                       String requirementMarkdownContext,
                                       String knowledgeBaseContext) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("projectInfo", "- 项目：" + value(project.name())
                + "\n- 业务线：" + value(project.businessLine())
                + "\n- 负责人：" + value(project.ownerUserName()));
        variables.put("requirementMarkdown", value(requirementMarkdownContext));
        variables.put("intakeInfo", "- 审批编号：" + value(structuredData.approvalCode())
                + "\n- 需求名称：" + value(structuredData.requirementNameOrTitle())
                + "\n- 需求描述：" + value(structuredData.requirementSummary())
                + "\n- 业务线：" + value(structuredData.businessLine())
                + "\n- 备注：" + value(structuredData.remark()));
        variables.put("structuredFields", formatStructuredFields(structuredData.fields()));
        variables.put("attachmentSummaries", formatAttachmentSummaries(structuredData.attachmentSummaries()));
        variables.put("knowledgeBase", value(knowledgeBaseContext));
        variables.put("rawContent", value(intake.getRawContent()));
        return variables;
    }

    private List<IntakeClarificationItem> normalize(RawClarificationAnalysis raw) {
        List<IntakeClarificationItem> items = new ArrayList<>();
        int questionIndex = 0;
        for (RawClarificationItem item : raw.confirmationItems() == null ? List.<RawClarificationItem>of() : raw.confirmationItems()) {
            String title = trimToNull(item.title());
            if (title == null) {
                continue;
            }
            items.add(new IntakeClarificationItem(
                    questionIndex++,
                    "QUESTION",
                    title,
                    trimToNull(item.description()),
                    trimToNull(item.evidence()),
                    "PENDING",
                    null,
                    null,
                    null
            ));
        }
        int riskIndex = 0;
        for (RawClarificationItem item : raw.riskItems() == null ? List.<RawClarificationItem>of() : raw.riskItems()) {
            String title = trimToNull(item.title());
            if (title == null) {
                continue;
            }
            items.add(new IntakeClarificationItem(
                    riskIndex++,
                    "RISK",
                    title,
                    trimToNull(item.description()),
                    trimToNull(item.evidence()),
                    "PENDING",
                    null,
                    null,
                    null
            ));
        }
        return items;
    }

    private String formatStructuredFields(List<IntakeStructuredField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeStructuredField field : fields) {
            if (field == null || trimToNull(field.label()) == null) {
                continue;
            }
            builder.append("- ")
                    .append(field.label())
                    .append("：")
                    .append(value(field.value()))
                    .append('\n');
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    private String formatAttachmentSummaries(List<IntakeAttachmentSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "- 无";
        }
        StringBuilder builder = new StringBuilder();
        for (IntakeAttachmentSummary summary : summaries) {
            if (summary == null || trimToNull(summary.summaryText()) == null) {
                continue;
            }
            builder.append("- 来源文件：")
                    .append(value(summary.fileName()))
                    .append("（")
                    .append(value(summary.fileType()))
                    .append("）\n")
                    .append(summary.summaryText().trim())
                    .append("\n");
        }
        return builder.isEmpty() ? "- 无" : builder.toString();
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private record RawClarificationAnalysis(List<RawClarificationItem> confirmationItems,
                                            List<RawClarificationItem> riskItems) {
    }

    private record RawClarificationItem(String title,
                                        String description,
                                        String evidence) {
    }
}
