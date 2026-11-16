package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSqlDraft;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTemporaryFileSet;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Codex CLI 的数据运维 SQL 草稿生成器。
 */
@Component
public class CodexCliSqlDraftGenerator {

    private static final String USE_CASE_CODE = "intake.sql-draft.generate";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "dialect": { "type": ["string", "null"] },
                "sql": { "type": ["string", "null"] },
                "explanation": { "type": ["string", "null"] },
                "parameters": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "assumptions": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "questions": {
                  "type": "array",
                  "items": { "type": "string" }
                },
                "riskWarnings": {
                  "type": "array",
                  "items": { "type": "string" }
                }
              },
              "required": [
                "dialect",
                "sql",
                "explanation",
                "parameters",
                "assumptions",
                "questions",
                "riskWarnings"
              ],
              "additionalProperties": false
            }
            """;

    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    public CodexCliSqlDraftGenerator(AiGatewayClient aiGatewayClient, ObjectMapper objectMapper) {
        this.aiGatewayClient = aiGatewayClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return aiGatewayClient.isConfigured(USE_CASE_CODE);
    }

    /**
     * 根据需求内容生成 SQL 草稿。该方法只调用 AI 生成文本，不连接数据库、不执行 SQL。
     *
     * @param entity         需求主记录
     * @param structuredData 结构化需求数据
     * @param attachments    需求附件上下文，用于图片输入
     * @return SQL 草稿生成结果
     */
    public SqlDraftGenerationResult generate(IntakeRecordEntity entity,
                                             IntakeStructuredData structuredData,
                                             List<AttachmentService.AttachmentFileContext> attachments) {
        if (!aiGatewayClient.isConfigured(USE_CASE_CODE)) {
            return SqlDraftGenerationResult.failed("AI 场景未配置或已停用");
        }
        try (AttachmentTemporaryFileSet temporaryFiles = AttachmentTemporaryFileSet.materialize(attachments)) {
            SqlDraftInvocation invocation = buildInvocation(entity, structuredData, temporaryFiles.contexts());
            AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.withVariables(
                    USE_CASE_CODE,
                    invocation.variables(),
                    invocation.workingDirectory(),
                    invocation.addDirs(),
                    invocation.imagePaths(),
                    OUTPUT_SCHEMA
            ));
            if (!result.succeeded()) {
                return SqlDraftGenerationResult.failed(result.failureSummary());
            }
            IntakeSqlDraft draft = objectMapper.readValue(result.output(), IntakeSqlDraft.class);
            draft = new IntakeSqlDraft(
                    defaultValue(draft.dialect(), "MySQL"),
                    trimToNull(draft.sql()),
                    trimToNull(draft.explanation()),
                    safeList(draft.parameters()),
                    safeList(draft.assumptions()),
                    safeList(draft.questions()),
                    safeList(draft.riskWarnings()),
                    LocalDateTime.now().toString(),
                    result.providerCode() == null ? "AI Gateway" : result.providerCode()
            );
            return SqlDraftGenerationResult.succeeded(draft);
        } catch (Exception ex) {
            return SqlDraftGenerationResult.failed("SQL 草稿解析失败: " + summarizeException(ex));
        }
    }

    SqlDraftInvocation buildInvocation(IntakeRecordEntity entity,
                                       IntakeStructuredData structuredData,
                                       List<AttachmentService.AttachmentFileContext> attachments) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<AttachmentService.AttachmentFileContext> safeAttachments = attachments == null ? List.of() : attachments;

        List<String> imagePaths = new ArrayList<>();
        Set<String> addDirs = new LinkedHashSet<>();
        for (AttachmentService.AttachmentFileContext attachment : safeAttachments) {
            if (!isImage(attachment)) {
                continue;
            }
            Path imagePath = Path.of(attachment.storagePath()).toAbsolutePath().normalize();
            imagePaths.add(imagePath.toString());
            if (!imagePath.startsWith(workingDirectory)) {
                Path parent = imagePath.getParent();
                if (parent != null) {
                    addDirs.add(parent.toString());
                }
            }
        }
        return new SqlDraftInvocation(
                workingDirectory,
                List.copyOf(addDirs),
                imagePaths,
                buildVariables(entity, structuredData, safeAttachments)
        );
    }

    Map<String, Object> buildVariables(IntakeRecordEntity entity,
                                       IntakeStructuredData structuredData,
                                       List<AttachmentService.AttachmentFileContext> attachments) {
        StringBuilder intakeRecord = new StringBuilder()
                .append("- ID：").append(entity == null ? "" : entity.getId()).append('\n')
                .append("- 来源：").append(entity == null ? "" : defaultValue(entity.getSourceType(), ""))
                .append(" / ").append(entity == null ? "" : defaultValue(entity.getSourceChannel(), "")).append('\n')
                .append("- 发送人：").append(entity == null ? "" : defaultValue(entity.getSenderName(), "")).append('\n')
                .append("- 接收时间：")
                .append(entity == null || entity.getReceivedAt() == null ? "" : entity.getReceivedAt());

        StringBuilder structuredDataText = new StringBuilder();
        if (structuredData != null) {
            structuredDataText.append("\n结构化需求：\n");
            appendLine(structuredDataText, "审批编号", structuredData.approvalCode());
            appendLine(structuredDataText, "提出人", structuredData.proposerName());
            appendLine(structuredDataText, "提交时间", structuredData.submittedTime());
            appendLine(structuredDataText, "需求类型", structuredData.requirementType());
            appendLine(structuredDataText, "需求名称", structuredData.requirementName());
            appendLine(structuredDataText, "需求摘要", structuredData.requirementDigest());
            appendLine(structuredDataText, "需求描述", structuredData.requirementSummary());
            appendLine(structuredDataText, "业务线", structuredData.businessLine());
            appendLine(structuredDataText, "备注", structuredData.remark());
        }

        StringBuilder attachmentSummaryText = new StringBuilder();
        List<IntakeAttachmentSummary> summaries = structuredData == null ? null : structuredData.attachmentSummaries();
        if (summaries != null && !summaries.isEmpty()) {
            attachmentSummaryText.append("\n附件正文摘要：\n");
            for (IntakeAttachmentSummary summary : summaries) {
                attachmentSummaryText.append("### ")
                        .append(defaultValue(summary.fileName(), "未命名附件"))
                        .append(" / ")
                        .append(defaultValue(summary.fileType(), "未知类型"))
                        .append('\n')
                        .append(defaultValue(summary.summaryText(), ""))
                        .append("\n\n");
            }
        }

        StringBuilder attachmentList = new StringBuilder();
        if (attachments != null && !attachments.isEmpty()) {
            attachmentList.append("\n附件清单：\n");
            for (AttachmentService.AttachmentFileContext attachment : attachments) {
                attachmentList.append("- ")
                        .append(defaultValue(attachment.fileName(), "未命名附件"))
                        .append(" / ")
                        .append(defaultValue(attachment.contentType(), "未知类型"))
                        .append('\n');
            }
        }

        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("intakeRecord", intakeRecord.toString());
        variables.put("rawContent", defaultValue(entity == null ? null : entity.getRawContent(), "") + "\n");
        variables.put("structuredData", structuredDataText.toString());
        variables.put("attachmentSummaries", attachmentSummaryText.toString());
        variables.put("attachmentList", attachmentList.toString());
        return variables;
    }

    private void appendLine(StringBuilder prompt, String label, String value) {
        String normalized = trimToNull(value);
        if (normalized != null) {
            prompt.append("- ").append(label).append("：").append(normalized).append('\n');
        }
    }

    private boolean isImage(AttachmentService.AttachmentFileContext attachment) {
        String contentType = attachment.contentType();
        if (contentType != null && contentType.startsWith("image/")) {
            return true;
        }
        String fileName = attachment.fileName() == null ? "" : attachment.fileName().toLowerCase();
        return fileName.endsWith(".png")
                || fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")
                || fileName.endsWith(".gif")
                || fileName.endsWith(".bmp")
                || fileName.endsWith(".webp");
    }

    private String defaultValue(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(this::trimToNull)
                .filter(value -> value != null)
                .toList();
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    record SqlDraftInvocation(Path workingDirectory,
                              List<String> addDirs,
                              List<String> imagePaths,
                              Map<String, Object> variables) {
    }

    public record SqlDraftGenerationResult(boolean succeeded,
                                           IntakeSqlDraft draft,
                                           String failureSummary) {

        static SqlDraftGenerationResult succeeded(IntakeSqlDraft draft) {
            return new SqlDraftGenerationResult(true, draft, null);
        }

        static SqlDraftGenerationResult failed(String failureSummary) {
            return new SqlDraftGenerationResult(false, null, failureSummary);
        }
    }
}
