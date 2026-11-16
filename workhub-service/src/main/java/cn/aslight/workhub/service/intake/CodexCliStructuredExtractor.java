package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.attachment.AttachmentTemporaryFileSet;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.ai.AiGatewayRequest;
import cn.aslight.workhub.service.ai.AiGatewayResult;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 Codex CLI 的结构化增强服务。
 */
@Component
public class CodexCliStructuredExtractor {

    private static final String USE_CASE_CODE = "intake.structured.extract";
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "category": { "type": ["string", "null"] },
                "approvalTitle": { "type": ["string", "null"] },
                "proposerName": { "type": ["string", "null"] },
                "approvalCode": { "type": ["string", "null"] },
                "submittedTime": { "type": ["string", "null"] },
                "requirementType": { "type": ["string", "null"], "enum": ["数据提取/运维", "研发需求", null] },
                "developmentBranchName": { "type": ["string", "null"] },
                "zentaoUrl": { "type": ["string", "null"] },
                "requirementDigest": { "type": ["string", "null"] },
                "requirementName": { "type": ["string", "null"] },
                "requirementSummary": { "type": ["string", "null"] },
                "department": { "type": ["string", "null"] },
                "businessLine": { "type": ["string", "null"] },
                "remark": { "type": ["string", "null"] },
                "plannedDueDate": { "type": ["string", "null"] },
                "actualEffort": { "type": ["string", "null"] },
                "actualCompletedTime": { "type": ["string", "null"] },
                "acceptanceTime": { "type": ["string", "null"] },
                "projectHint": { "type": ["string", "null"] },
                "fields": {
                  "type": "array",
                  "items": {
                    "type": "object",
                    "properties": {
                      "label": { "type": "string" },
                      "value": { "type": "string" }
                    },
                    "required": ["label", "value"],
                    "additionalProperties": false
                  }
                }
              },
              "required": [
                "category",
                "approvalTitle",
                "proposerName",
                "approvalCode",
                "submittedTime",
                "requirementType",
                "developmentBranchName",
                "zentaoUrl",
                "requirementDigest",
                "requirementName",
                "requirementSummary",
                "department",
                "businessLine",
                "remark",
                "plannedDueDate",
                "actualEffort",
                "actualCompletedTime",
                "acceptanceTime",
                "projectHint",
                "fields"
              ],
              "additionalProperties": false
            }
            """;

    private final AiGatewayClient aiGatewayClient;
    private final ObjectMapper objectMapper;

    public CodexCliStructuredExtractor(AiGatewayClient aiGatewayClient, ObjectMapper objectMapper) {
        this.aiGatewayClient = aiGatewayClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return aiGatewayClient.isConfigured(USE_CASE_CODE);
    }

    public CodexCliExtractionResult extract(String sourceChannel,
                                            String rawContent,
                                            List<AttachmentService.AttachmentFileContext> attachments,
                                            List<IntakeAttachmentSummary> attachmentSummaries) {
        if (!aiGatewayClient.isConfigured(USE_CASE_CODE)) {
            return CodexCliExtractionResult.failed("AI 场景未配置或已停用: " + USE_CASE_CODE);
        }

        try (AttachmentTemporaryFileSet temporaryFiles = AttachmentTemporaryFileSet.materialize(attachments)) {
            CodexCliInvocation invocation = buildInvocation(
                    sourceChannel,
                    rawContent,
                    temporaryFiles.contexts(),
                    attachmentSummaries
            );
            if (invocation.imagePaths().isEmpty()
                    && invocation.attachmentSummaries().isEmpty()
                    && isBlank(rawContent)) {
                return CodexCliExtractionResult.skipped();
            }
            AiGatewayResult result = aiGatewayClient.executeStructured(AiGatewayRequest.withVariables(
                    USE_CASE_CODE,
                    invocation.variables(),
                    invocation.workingDirectory(),
                    invocation.addDirs(),
                    invocation.imagePaths(),
                    OUTPUT_SCHEMA
            ));
            if (!result.succeeded()) {
                return CodexCliExtractionResult.failed(result.failureSummary());
            }
            IntakeStructuredData structuredData = objectMapper.readValue(result.output(), IntakeStructuredData.class);
            return CodexCliExtractionResult.succeeded(structuredData);
        } catch (Exception ex) {
            return CodexCliExtractionResult.failed("AI 网关解析异常: " + summarizeException(ex));
        }
    }

    CodexCliInvocation buildInvocation(String sourceChannel,
                                       String rawContent,
                                       List<AttachmentService.AttachmentFileContext> attachments,
                                       List<IntakeAttachmentSummary> attachmentSummaries) {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        List<AttachmentService.AttachmentFileContext> safeAttachments = attachments == null ? List.of() : attachments;
        List<IntakeAttachmentSummary> safeSummaries = attachmentSummaries == null ? List.of() : attachmentSummaries;

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
        return new CodexCliInvocation(
                workingDirectory,
                List.copyOf(addDirs),
                imagePaths,
                safeSummaries,
                buildVariables(sourceChannel, rawContent, safeAttachments, safeSummaries)
        );
    }

    Map<String, Object> buildVariables(String sourceChannel,
                                       String rawContent,
                                       List<AttachmentService.AttachmentFileContext> attachments,
                                       List<IntakeAttachmentSummary> attachmentSummaries) {
        List<AttachmentService.AttachmentFileContext> safeAttachments = attachments == null ? List.of() : attachments;
        List<IntakeAttachmentSummary> safeSummaries = attachmentSummaries == null ? List.of() : attachmentSummaries;

        StringBuilder attachmentList = new StringBuilder();
        if (!safeAttachments.isEmpty()) {
            attachmentList.append("\n上传文件清单：\n");
            for (AttachmentService.AttachmentFileContext attachment : safeAttachments) {
                attachmentList.append("- [")
                        .append(attachment.category())
                        .append("] ")
                        .append(defaultValue(attachment.fileName()))
                        .append(" (")
                        .append(defaultValue(attachment.contentType()))
                        .append(")\n");
            }
        }

        StringBuilder attachmentSummaryText = new StringBuilder();
        if (!safeSummaries.isEmpty()) {
            attachmentSummaryText.append("\n非图片附件文本摘要：\n");
            for (IntakeAttachmentSummary summary : safeSummaries) {
                attachmentSummaryText.append("### 文件：")
                        .append(defaultValue(summary.fileName()))
                        .append('\n')
                        .append("类型：")
                        .append(defaultValue(summary.fileType()))
                        .append('\n')
                        .append("正文摘要：\n")
                        .append(defaultValue(summary.summaryText()))
                        .append("\n\n");
            }
        }
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("sourceChannel", defaultValue(sourceChannel));
        variables.put("manualNotes", defaultValue(rawContent));
        variables.put("attachmentList", attachmentList.toString());
        variables.put("attachmentSummaries", attachmentSummaryText.toString());
        return variables;
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

    private String defaultValue(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 120 ? message : message.substring(0, 120);
    }

    public record CodexCliInvocation(Path workingDirectory,
                                     List<String> addDirs,
                                     List<String> imagePaths,
                                     List<IntakeAttachmentSummary> attachmentSummaries,
                                     Map<String, Object> variables) {
    }

    public record CodexCliExtractionResult(boolean attempted,
                                           IntakeStructuredData structuredData,
                                           String failureSummary) {

        static CodexCliExtractionResult skipped() {
            return new CodexCliExtractionResult(false, null, null);
        }

        static CodexCliExtractionResult succeeded(IntakeStructuredData structuredData) {
            return new CodexCliExtractionResult(true, structuredData, null);
        }

        static CodexCliExtractionResult failed(String failureSummary) {
            return new CodexCliExtractionResult(true, null, failureSummary);
        }
    }
}
