package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.model.intake.IntakeAttachmentSummary;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSqlDraft;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.service.attachment.AttachmentService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 基于 Codex CLI 的数据运维 SQL 草稿生成器。
 */
@Component
public class CodexCliSqlDraftGenerator {

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

    private final CodexCliClient codexCliClient;
    private final ObjectMapper objectMapper;

    public CodexCliSqlDraftGenerator(CodexCliClient codexCliClient, ObjectMapper objectMapper) {
        this.codexCliClient = codexCliClient;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return codexCliClient.isEnabled();
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
        if (!codexCliClient.isEnabled()) {
            return SqlDraftGenerationResult.failed("Codex CLI 未启用");
        }
        SqlDraftInvocation invocation = buildInvocation(entity, structuredData, attachments);
        CodexCliClient.CodexCliResult result = codexCliClient.execute(new CodexCliClient.CodexCliRequest(
                invocation.workingDirectory(),
                invocation.addDirs(),
                invocation.imagePaths(),
                OUTPUT_SCHEMA,
                invocation.prompt()
        ));
        if (!result.succeeded()) {
            return SqlDraftGenerationResult.failed(result.failureSummary());
        }
        try {
            IntakeSqlDraft draft = objectMapper.readValue(result.outputJson(), IntakeSqlDraft.class);
            draft = new IntakeSqlDraft(
                    defaultValue(draft.dialect(), "MySQL"),
                    trimToNull(draft.sql()),
                    trimToNull(draft.explanation()),
                    safeList(draft.parameters()),
                    safeList(draft.assumptions()),
                    safeList(draft.questions()),
                    safeList(draft.riskWarnings()),
                    LocalDateTime.now().toString(),
                    "Codex CLI"
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
                buildPrompt(entity, structuredData, safeAttachments)
        );
    }

    String buildPrompt(IntakeRecordEntity entity,
                       IntakeStructuredData structuredData,
                       List<AttachmentService.AttachmentFileContext> attachments) {
        StringBuilder prompt = new StringBuilder("""
                你是 WorkHub 的数据运维 SQL 草稿助手。
                当前任务：根据数据提取/运维类需求，生成给人工复制执行的 SQL 草稿。

                严格要求：
                1. 只生成 SQL 草稿，不要声称已经连接数据库或已经执行。
                2. 默认 SQL 方言为 MySQL。
                3. 只能生成只读 SELECT 查询，不要生成 INSERT、UPDATE、DELETE、DDL、CALL 或多语句。
                4. 如果能根据需求自行推断表名、字段名和筛选条件，可以直接生成 SQL，并把推断写入 assumptions。
                5. 如果关键表名、字段名或业务口径无法判断，请在 questions 中列出需要向需求方确认的问题；sql 可给出带 TODO 占位的草稿。
                6. 对时间范围、商户号、用户编号、状态值等可变条件，优先使用具名占位，例如 :start_time、:end_time、:merchant_no。
                7. riskWarnings 必须提示人工执行前确认库表环境、字段口径、数据权限和导出范围。
                8. 不要输出 Markdown，只返回符合 JSON Schema 的对象。

                需求主记录：
                - ID：%s
                - 来源：%s / %s
                - 发送人：%s
                - 接收时间：%s

                原始内容：
                %s
                """.formatted(
                entity == null ? "" : entity.getId(),
                entity == null ? "" : defaultValue(entity.getSourceType(), ""),
                entity == null ? "" : defaultValue(entity.getSourceChannel(), ""),
                entity == null ? "" : defaultValue(entity.getSenderName(), ""),
                entity == null || entity.getReceivedAt() == null ? "" : entity.getReceivedAt(),
                entity == null ? "" : defaultValue(entity.getRawContent(), "")
        ));

        appendStructuredData(prompt, structuredData);
        appendAttachmentSummaries(prompt, structuredData == null ? null : structuredData.attachmentSummaries());
        appendAttachmentList(prompt, attachments);
        prompt.append("\n请生成 SQL 草稿 JSON。");
        return prompt.toString();
    }

    private void appendStructuredData(StringBuilder prompt, IntakeStructuredData structuredData) {
        if (structuredData == null) {
            return;
        }
        prompt.append("""

                结构化需求：
                """);
        appendLine(prompt, "审批编号", structuredData.approvalCode());
        appendLine(prompt, "提出人", structuredData.proposerName());
        appendLine(prompt, "提交时间", structuredData.submittedTime());
        appendLine(prompt, "需求类型", structuredData.requirementType());
        appendLine(prompt, "需求名称", structuredData.requirementName());
        appendLine(prompt, "需求摘要", structuredData.requirementDigest());
        appendLine(prompt, "需求描述", structuredData.requirementSummary());
        appendLine(prompt, "业务线", structuredData.businessLine());
        appendLine(prompt, "备注", structuredData.remark());
    }

    private void appendAttachmentSummaries(StringBuilder prompt, List<IntakeAttachmentSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return;
        }
        prompt.append("\n附件正文摘要：\n");
        for (IntakeAttachmentSummary summary : summaries) {
            prompt.append("### ")
                    .append(defaultValue(summary.fileName(), "未命名附件"))
                    .append(" / ")
                    .append(defaultValue(summary.fileType(), "未知类型"))
                    .append('\n')
                    .append(defaultValue(summary.summaryText(), ""))
                    .append("\n\n");
        }
    }

    private void appendAttachmentList(StringBuilder prompt, List<AttachmentService.AttachmentFileContext> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        prompt.append("\n附件清单：\n");
        for (AttachmentService.AttachmentFileContext attachment : attachments) {
            prompt.append("- ")
                    .append(defaultValue(attachment.fileName(), "未命名附件"))
                    .append(" / ")
                    .append(defaultValue(attachment.contentType(), "未知类型"))
                    .append('\n');
        }
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
                              String prompt) {
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
