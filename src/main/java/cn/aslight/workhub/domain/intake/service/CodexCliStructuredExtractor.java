package cn.aslight.workhub.domain.intake.service;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.domain.attachment.service.AttachmentService;
import cn.aslight.workhub.domain.intake.dto.IntakeAttachmentSummary;
import cn.aslight.workhub.domain.intake.dto.IntakeStructuredData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
public class CodexCliStructuredExtractor {

    private static final Logger log = LoggerFactory.getLogger(CodexCliStructuredExtractor.class);
    private static final String OUTPUT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "category": { "type": ["string", "null"] },
                "approvalTitle": { "type": ["string", "null"] },
                "approvalCode": { "type": ["string", "null"] },
                "requirementName": { "type": ["string", "null"] },
                "requirementSummary": { "type": ["string", "null"] },
                "department": { "type": ["string", "null"] },
                "businessLine": { "type": ["string", "null"] },
                "remark": { "type": ["string", "null"] },
                "estimatedEffort": { "type": ["string", "null"] },
                "plannedDueDate": { "type": ["string", "null"] },
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
                "approvalCode",
                "requirementName",
                "requirementSummary",
                "department",
                "businessLine",
                "remark",
                "estimatedEffort",
                "plannedDueDate",
                "projectHint",
                "fields"
              ],
              "additionalProperties": false
            }
            """;

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public CodexCliStructuredExtractor(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return aiProperties.getCodexCli().isEnabled();
    }

    public CodexCliExtractionResult extract(String sourceChannel,
                                            String rawContent,
                                            List<AttachmentService.AttachmentFileContext> attachments,
                                            List<IntakeAttachmentSummary> attachmentSummaries) {
        AiProperties.CodexCli config = aiProperties.getCodexCli();
        if (!config.isEnabled()) {
            return CodexCliExtractionResult.skipped();
        }

        CodexCliInvocation invocation = buildInvocation(sourceChannel, rawContent, attachments, attachmentSummaries);
        if (invocation.imagePaths().isEmpty() && invocation.attachmentSummaries().isEmpty() && isBlank(rawContent)) {
            return CodexCliExtractionResult.skipped();
        }

        Path codexHome = null;
        Path outputSchema = null;
        Path outputFile = null;
        Path processLog = null;
        try {
            codexHome = Files.createTempDirectory("workhub-codex-home-");
            outputSchema = Files.createTempFile("workhub-codex-schema-", ".json");
            outputFile = Files.createTempFile("workhub-codex-output-", ".json");
            processLog = Files.createTempFile("workhub-codex-process-", ".log");
            Files.writeString(outputSchema, OUTPUT_SCHEMA, StandardCharsets.UTF_8);

            List<String> command = buildCommand(invocation, outputSchema, outputFile);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(invocation.workingDirectory().toFile());
            processBuilder.redirectErrorStream(true);
            processBuilder.redirectOutput(processLog.toFile());

            Map<String, String> env = processBuilder.environment();
            env.put("CODEX_HOME", codexHome.toString());
            env.remove("CODEX_SANDBOX_NETWORK_DISABLED");
            env.remove("CODEX_SANDBOX");
            env.remove("CODEX_SHELL");
            env.remove("CODEX_THREAD_ID");
            env.remove("CODEX_CI");
            env.remove("CODEX_INTERNAL_ORIGINATOR_OVERRIDE");

            Process process = processBuilder.start();
            boolean finished = process.waitFor(config.getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return CodexCliExtractionResult.failed("Codex CLI 解析超时");
            }
            if (process.exitValue() != 0) {
                String logs = processLog == null || !Files.exists(processLog)
                        ? null
                        : Files.readString(processLog, StandardCharsets.UTF_8);
                log.warn("Codex CLI parsing failed. exitCode={}, logs={}", process.exitValue(), truncate(logs));
                return CodexCliExtractionResult.failed("Codex CLI 执行失败");
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                return CodexCliExtractionResult.failed("Codex CLI 未返回结构化结果");
            }
            IntakeStructuredData structuredData = objectMapper.readValue(outputFile.toFile(), IntakeStructuredData.class);
            return CodexCliExtractionResult.succeeded(structuredData);
        } catch (Exception ex) {
            log.warn("Codex CLI structured parsing failed", ex);
            return CodexCliExtractionResult.failed("Codex CLI 解析异常: " + summarizeException(ex));
        } finally {
            deleteIfExists(processLog);
            deleteIfExists(outputFile);
            deleteIfExists(outputSchema);
            deleteIfExists(codexHome);
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

    List<String> buildCommand(CodexCliInvocation invocation, Path outputSchema, Path outputFile) {
        AiProperties.CodexCli config = aiProperties.getCodexCli();
        List<String> command = new ArrayList<>();
        command.add(config.getCommand());
        command.add("exec");
        if (config.isDisablePlugins()) {
            command.add("--disable");
            command.add("plugins");
            command.add("--disable");
            command.add("shell_snapshot");
        }
        command.add("--skip-git-repo-check");
        command.add("--ephemeral");
        command.add("-C");
        command.add(invocation.workingDirectory().toString());
        for (String addDir : invocation.addDirs()) {
            command.add("--add-dir");
            command.add(addDir);
        }
        command.add("--color");
        command.add("never");
        if (!isBlank(config.getModel())) {
            command.add("-m");
            command.add(config.getModel().trim());
        }
        command.add("--output-schema");
        command.add(outputSchema.toString());
        command.add("-o");
        command.add(outputFile.toString());
        for (String imagePath : invocation.imagePaths()) {
            command.add("-i");
            command.add(imagePath);
        }
        command.add(invocation.prompt());
        return command;
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

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.debug("Failed to delete temporary file {}", path, ex);
        }
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
