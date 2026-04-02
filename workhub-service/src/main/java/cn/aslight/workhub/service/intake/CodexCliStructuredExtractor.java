package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Codex CLI 的结构化增强服务。
 */
@Component
public class CodexCliStructuredExtractor {

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
                "estimatedEffort": { "type": ["string", "null"] },
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
                "estimatedEffort",
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

    private final CodexCliClient codexCliClient;
    private final ObjectMapper objectMapper;

    public CodexCliStructuredExtractor(CodexCliClient codexCliClient, ObjectMapper objectMapper) {
        this.codexCliClient = codexCliClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return codexCliClient.isEnabled();
    }

    public CodexCliExtractionResult extract(String sourceChannel,
                                            String rawContent,
                                            List<AttachmentService.AttachmentFileContext> attachments,
                                            List<IntakeAttachmentSummary> attachmentSummaries) {
        if (!codexCliClient.isEnabled()) {
            return CodexCliExtractionResult.skipped();
        }

        CodexCliInvocation invocation = buildInvocation(sourceChannel, rawContent, attachments, attachmentSummaries);
        if (invocation.imagePaths().isEmpty() && invocation.attachmentSummaries().isEmpty() && isBlank(rawContent)) {
            return CodexCliExtractionResult.skipped();
        }

        try {
            CodexCliClient.CodexCliResult result = codexCliClient.execute(new CodexCliClient.CodexCliRequest(
                    invocation.workingDirectory(),
                    invocation.addDirs(),
                    invocation.imagePaths(),
                    OUTPUT_SCHEMA,
                    invocation.prompt()
            ));
            if (!result.succeeded()) {
                return CodexCliExtractionResult.failed(result.failureSummary());
            }
            IntakeStructuredData structuredData = objectMapper.readValue(result.outputJson(), IntakeStructuredData.class);
            return CodexCliExtractionResult.succeeded(structuredData);
        } catch (Exception ex) {
            return CodexCliExtractionResult.failed("Codex CLI 解析异常: " + summarizeException(ex));
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
                buildPrompt(sourceChannel, rawContent, safeAttachments, safeSummaries)
        );
    }

    String buildPrompt(String sourceChannel,
                       String rawContent,
                       List<AttachmentService.AttachmentFileContext> attachments,
                       List<IntakeAttachmentSummary> attachmentSummaries) {
        StringBuilder prompt = new StringBuilder("""
                你是 WorkHub 的需求结构化助手。
                请结合人工补充说明、截图、附件内容，抽取审批型需求字段，并严格按给定 JSON Schema 输出。
                要求：
                1. 只输出事实，不要猜测无法确认的信息。
                2. 能从截图或附件中确认的字段，优先写入标准字段。
                3. fields 必须保留你最终识别到的结构化字段清单。
                4. 如果图片或附件中能识别到更准确的值，可以覆盖人工说明里的模糊内容。
                5. 非图片附件已经预先提取为纯文本摘要，请优先基于这些摘要判断，不要猜测如何打开二进制文件。
                6. requirementType 只能输出“数据提取/运维”或“研发需求”。
                7. requirementDigest 输出给列表展示的极短摘要，尽量不超过 24 个字。
                8. 如果 requirementType 为“研发需求”，请在 developmentBranchName 中给出推荐研发分支名，格式优先使用 feature/req-审批编号；若无审批编号可用则用 feature/req-时间戳。
                9. 如果材料里没有禅道地址，zentaoUrl 返回 null。

                来源渠道：%s
                人工补充说明：
                %s
                """.formatted(defaultValue(sourceChannel), defaultValue(rawContent)));

        List<AttachmentService.AttachmentFileContext> safeAttachments = attachments == null ? List.of() : attachments;
        List<IntakeAttachmentSummary> safeSummaries = attachmentSummaries == null ? List.of() : attachmentSummaries;

        if (!safeAttachments.isEmpty()) {
            prompt.append("\n上传文件清单：\n");
            for (AttachmentService.AttachmentFileContext attachment : safeAttachments) {
                prompt.append("- [")
                        .append(attachment.category())
                        .append("] ")
                        .append(defaultValue(attachment.fileName()))
                        .append(" (")
                        .append(defaultValue(attachment.contentType()))
                        .append(")\n");
            }
        }

        if (!safeSummaries.isEmpty()) {
            prompt.append("\n非图片附件文本摘要：\n");
            for (IntakeAttachmentSummary summary : safeSummaries) {
                prompt.append("### 文件：")
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
        prompt.append("请输出整理建议 JSON。");
        return prompt.toString();
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
                                     String prompt) {
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
