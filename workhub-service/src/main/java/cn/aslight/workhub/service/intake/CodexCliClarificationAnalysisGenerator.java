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
import java.util.List;

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
        if (!aiGatewayClient.isConfigured(USE_CASE_CODE)) {
            throw new IllegalArgumentException("AI 场景未配置或已停用，不能执行需求澄清分析");
        }
        AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.structured(
                USE_CASE_CODE,
                buildPrompt(intake, structuredData, project, requirementMarkdownContext, knowledgeBaseContext),
                repositoryBundle.localRoot(),
                repositoryBundle.repositories().stream().map(item -> item.localPath().toString()).toList(),
                List.of(),
                OUTPUT_SCHEMA
        ));
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

    private String buildPrompt(IntakeRecordEntity intake,
                               IntakeStructuredData structuredData,
                               ProjectDetailResponse project,
                               String requirementMarkdownContext,
                               String knowledgeBaseContext) {
        return """
                你是 WorkHub 的研发需求澄清助手。
                请结合需求材料、结构化字段、项目知识库和代码仓库，列出澄清阶段需要人工逐项确认的问题和风险。

                输出要求：
                1. confirmationItems 只放需要业务或产品逐项回复的待确认项。
                2. riskItems 只放需要人工知晓并确认接受的风险项。
                3. 每一项必须独立、可回复，不要把多个问题合在一个条目里。
                4. 不要输出研发任务，不要估算工时，不要修改代码。
                5. 如果需求已经明确，可以返回空数组；不要为了凑数制造问题。
                6. evidence 写明来自需求材料、结构化字段、知识库或代码的依据；证据不足时说明“不足”。

                项目信息：
                - 项目：%s
                - 业务线：%s
                - 负责人：%s

                需求 Markdown 文件：
                %s

                需求信息：
                - 审批编号：%s
                - 需求名称：%s
                - 需求描述：%s
                - 业务线：%s
                - 备注：%s

                结构化识别字段：
                %s

                附件内容摘要：
                %s

                项目知识库参考：
                %s

                原始内容：
                %s
                """.formatted(
                value(project.name()),
                value(project.businessLine()),
                value(project.ownerUserName()),
                value(requirementMarkdownContext),
                value(structuredData.approvalCode()),
                value(structuredData.requirementNameOrTitle()),
                value(structuredData.requirementSummary()),
                value(structuredData.businessLine()),
                value(structuredData.remark()),
                formatStructuredFields(structuredData.fields()),
                formatAttachmentSummaries(structuredData.attachmentSummaries()),
                value(knowledgeBaseContext),
                value(intake.getRawContent())
        );
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
