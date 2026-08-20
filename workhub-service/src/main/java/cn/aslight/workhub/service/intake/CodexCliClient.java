package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Codex CLI 客户端，负责执行本地 codex 进程并返回结构化输出。
 */
@Component
public class CodexCliClient {

    private static final Logger log = LoggerFactory.getLogger(CodexCliClient.class);
    private static final List<String> RESTRICTED_SCENE_DISABLED_FEATURES = List.of(
            "browser_use",
            "browser_use_external",
            "computer_use",
            "plugins",
            "shell_snapshot",
            "shell_tool",
            "unified_exec"
    );
    private final AiProperties aiProperties;

    public CodexCliClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public boolean isEnabled() {
        return aiProperties.getCodexCli().isEnabled();
    }

    /**
     * 由统一 AI 网关按数据库场景配置执行本地 CLI 通道。
     */
    public CodexCliResult execute(CodexCliRequest request, CodexCliExecutionOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("Codex CLI 执行配置不能为空");
        }
        return executeInternal(request, resolveEffectiveConfig(request.useCaseCode(), options));
    }

    private CodexCliResult executeInternal(CodexCliRequest request,
                                           EffectiveCodexCliConfig effectiveConfig) {
        Path outputSchema = null;
        Path outputFile = null;
        StringBuffer processOutput = new StringBuffer();
        Thread outputPump = null;
        long startedAt = System.nanoTime();
        try {
            outputSchema = Files.createTempFile("workhub-codex-schema-", ".json");
            outputFile = Files.createTempFile("workhub-codex-output-", ".json");
            Files.writeString(outputSchema, request.outputSchema(), StandardCharsets.UTF_8);
            List<String> command = buildCommand(request, outputSchema, outputFile, effectiveConfig);
            log.info("Codex CLI execution started. useCase={}, executable={}, model={}, reasoningEffort={}, workingDirectory={}, addDirCount={}, imageCount={}, promptLength={}",
                    request.useCaseCode(),
                    command.isEmpty() ? null : command.getFirst(),
                    effectiveConfig.model(),
                    effectiveConfig.reasoningEffort(),
                    request.workingDirectory(),
                    request.addDirs().size(),
                    request.imagePaths().size(),
                    request.prompt() == null ? 0 : request.prompt().length());
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(request.workingDirectory().toFile());
            processBuilder.redirectErrorStream(true);

            Map<String, String> env = processBuilder.environment();
            env.remove("CODEX_SANDBOX_NETWORK_DISABLED");
            env.remove("CODEX_SANDBOX");
            env.remove("CODEX_SHELL");
            env.remove("CODEX_THREAD_ID");
            env.remove("CODEX_CI");
            env.remove("CODEX_INTERNAL_ORIGINATOR_OVERRIDE");
            log.info("Codex CLI environment prepared. useCase={}", request.useCaseCode());

            Process process = processBuilder.start();
            writePrompt(process, request.prompt());
            outputPump = startOutputPump(process, processOutput);
            boolean finished = waitForProcess(process, effectiveConfig.timeoutSeconds());
            if (!finished) {
                process.destroyForcibly();
                awaitOutputPump(outputPump);
                log.warn("Codex CLI parsing timed out. useCase={}, timeoutSeconds={}, processOutputLength={}",
                        request.useCaseCode(),
                        effectiveConfig.timeoutSeconds(),
                        processOutput.length());
                return CodexCliResult.failed("Codex CLI 解析超时");
            }
            awaitOutputPump(outputPump);
            if (process.exitValue() != 0) {
                log.warn("Codex CLI parsing failed. useCase={}, exitCode={}, durationMs={}, processOutputLength={}",
                        request.useCaseCode(),
                        process.exitValue(),
                        elapsedMillis(startedAt),
                        processOutput.length());
                return CodexCliResult.failed("Codex CLI 执行失败");
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("Codex CLI returned no structured output. useCase={}, durationMs={}, processOutputLength={}",
                        request.useCaseCode(),
                        elapsedMillis(startedAt), processOutput.length());
                return CodexCliResult.failed("Codex CLI 未返回结构化结果");
            }
            String outputJson = Files.readString(outputFile, StandardCharsets.UTF_8);
            log.info("Codex CLI execution completed. useCase={}, exitCode=0, durationMs={}, outputLength={}",
                    request.useCaseCode(),
                    elapsedMillis(startedAt), outputJson.length());
            return CodexCliResult.succeeded(outputJson);
        } catch (Exception ex) {
            log.warn("Codex CLI execution failed. useCase={}, processOutputLength={}",
                    request == null ? null : request.useCaseCode(), processOutput.length(), ex);
            return CodexCliResult.failed("Codex CLI 解析异常: " + summarizeException(ex));
        } finally {
            deleteIfExists(outputFile);
            deleteIfExists(outputSchema);
        }
    }

    List<String> buildCommand(CodexCliRequest request,
                              Path outputSchema,
                              Path outputFile,
                              CodexCliExecutionOptions executionOptions) {
        return buildCommand(request, outputSchema, outputFile,
                resolveEffectiveConfig(request.useCaseCode(), executionOptions));
    }

    List<String> buildCommand(CodexCliRequest request,
                              Path outputSchema,
                              Path outputFile,
                              EffectiveCodexCliConfig effectiveConfig) {
        List<String> command = new ArrayList<>();
        command.add(effectiveConfig.command());
        command.add("exec");
        if (effectiveConfig.disablePlugins()) {
            command.add("--disable");
            command.add("plugins");
            command.add("--disable");
            command.add("shell_snapshot");
        }
        command.add("--skip-git-repo-check");
        command.add("--ephemeral");
        if (effectiveConfig.allowLocalTools()) {
            command.add("--dangerously-bypass-approvals-and-sandbox");
        } else {
            command.add("--sandbox");
            command.add("read-only");
            command.add("-c");
            command.add("approval_policy=\"never\"");
            command.add("-c");
            command.add("web_search=\"disabled\"");
            for (String feature : RESTRICTED_SCENE_DISABLED_FEATURES) {
                command.add("--disable");
                command.add(feature);
            }
        }
        command.add("-C");
        command.add(request.workingDirectory().toString());
        if (effectiveConfig.allowLocalTools()) {
            for (String addDir : request.addDirs()) {
                command.add("--add-dir");
                command.add(addDir);
            }
        }
        command.add("--color");
        command.add("never");
        if (effectiveConfig.model() != null && !effectiveConfig.model().trim().isEmpty()) {
            command.add("-m");
            command.add(effectiveConfig.model().trim());
        }
        if (effectiveConfig.reasoningEffort() != null && !effectiveConfig.reasoningEffort().trim().isEmpty()) {
            command.add("-c");
            command.add("model_reasoning_effort=\"" + effectiveConfig.reasoningEffort().trim() + "\"");
        }
        command.add("--output-schema");
        command.add(outputSchema.toString());
        command.add("-o");
        command.add(outputFile.toString());
        for (String imagePath : request.imagePaths()) {
            command.add("-i");
            command.add(imagePath);
        }
        return command;
    }

    private void writePrompt(Process process, String prompt) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            writer.write(prompt == null ? "" : prompt);
            writer.flush();
        }
    }

    private EffectiveCodexCliConfig resolveEffectiveConfig(String useCaseCode,
                                                           CodexCliExecutionOptions executionOptions) {
        if (executionOptions == null) {
            throw new IllegalStateException("AI 场景未配置 CLI 执行参数: " + useCaseCode);
        }
        String command = trimToNull(executionOptions.command());
        if (command == null) {
            throw new IllegalStateException("AI Provider 未配置 CLI 命令: " + useCaseCode);
        }
        String model = trimToNull(executionOptions.model());
        if (model == null) {
            throw new IllegalStateException("AI 场景未配置 CLI 模型: " + useCaseCode);
        }
        Integer timeoutSeconds = executionOptions.timeoutSeconds();
        if (timeoutSeconds == null || timeoutSeconds <= 0) {
            throw new IllegalStateException("AI 场景未配置有效的 CLI 超时时间: " + useCaseCode);
        }
        return new EffectiveCodexCliConfig(
                command,
                model,
                trimToNull(executionOptions.reasoningEffort()),
                timeoutSeconds,
                executionOptions.disablePlugins(),
                executionOptions.allowLocalTools()
        );
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Thread startOutputPump(Process process, StringBuffer processOutput) {
        Thread thread = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (processOutput.length() >= 65_536) {
                        continue;
                    }
                    if (!processOutput.isEmpty()) {
                        processOutput.append('\n');
                    }
                    int remaining = 65_536 - processOutput.length();
                    processOutput.append(line, 0, Math.min(line.length(), remaining));
                }
            } catch (Exception ex) {
                log.debug("Failed to read Codex CLI output stream", ex);
            }
        }, "workhub-codex-cli-output");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void awaitOutputPump(Thread outputPump) {
        if (outputPump == null) {
            return;
        }
        try {
            outputPump.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while waiting Codex CLI output pump to finish", ex);
        }
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }

    private boolean waitForProcess(Process process, int timeoutSeconds) throws InterruptedException {
        if (timeoutSeconds <= 0) {
            process.waitFor();
            return true;
        }
        return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() <= 160 ? message : message.substring(0, 160);
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (Exception ex) {
            log.debug("Failed to delete temp file: {}", path, ex);
        }
    }

    public record CodexCliRequest(String useCaseCode,
                           Path workingDirectory,
                           List<String> addDirs,
                           List<String> imagePaths,
                           String outputSchema,
                           String prompt) {
    }

    public record CodexCliResult(boolean succeeded, String outputJson, String failureSummary) {

        public static CodexCliResult succeeded(String outputJson) {
            return new CodexCliResult(true, outputJson, null);
        }

        public static CodexCliResult failed(String failureSummary) {
            return new CodexCliResult(false, null, failureSummary);
        }
    }

    public record CodexCliExecutionOptions(String command,
                                           String model,
                                           String reasoningEffort,
                                           Integer timeoutSeconds,
                                           boolean disablePlugins,
                                           boolean allowLocalTools) {
    }

    record EffectiveCodexCliConfig(String command,
                                   String model,
                                   String reasoningEffort,
                                   int timeoutSeconds,
                                   boolean disablePlugins,
                                   boolean allowLocalTools) {
    }
}
