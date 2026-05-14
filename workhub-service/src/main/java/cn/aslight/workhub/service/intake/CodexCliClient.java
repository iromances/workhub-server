package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.service.system.SysConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
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
    private static final String CONFIG_GROUP_CODEX_CLI = "ai.codexCli";
    private static final String CONFIG_KEY_MODEL = "model";
    private static final String CONFIG_KEY_REASONING_EFFORT = "reasoningEffort";

    private final AiProperties aiProperties;
    private final SysConfigService sysConfigService;

    public CodexCliClient(AiProperties aiProperties) {
        this(aiProperties, null);
    }

    @Autowired
    public CodexCliClient(AiProperties aiProperties, SysConfigService sysConfigService) {
        this.aiProperties = aiProperties;
        this.sysConfigService = sysConfigService;
    }

    public boolean isEnabled() {
        return aiProperties.getCodexCli().isEnabled();
    }

    public CodexCliResult execute(CodexCliRequest request) {
        AiProperties.CodexCli config = aiProperties.getCodexCli();
        EffectiveCodexCliConfig effectiveConfig = resolveEffectiveConfig(config);
        Path outputSchema = null;
        Path outputFile = null;
        Path isolatedCodexHome = null;
        StringBuffer processOutput = new StringBuffer();
        Thread outputPump = null;
        long startedAt = System.nanoTime();
        try {
            outputSchema = Files.createTempFile("workhub-codex-schema-", ".json");
            outputFile = Files.createTempFile("workhub-codex-output-", ".json");
            Files.writeString(outputSchema, request.outputSchema(), StandardCharsets.UTF_8);
            isolatedCodexHome = prepareIsolatedCodexHome(effectiveConfig);

            List<String> command = buildCommand(request, outputSchema, outputFile, effectiveConfig);
            log.info("Codex CLI execution started. executable={}, model={}, reasoningEffort={}, workingDirectory={}, addDirCount={}, imageCount={}, promptLength={}",
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
            if (isolatedCodexHome != null) {
                env.put("CODEX_HOME", isolatedCodexHome.toString());
            }
            log.info("Codex CLI environment prepared. codexHome={}, authSource={}",
                    env.get("CODEX_HOME"),
                    locateCodexAuthFile());

            Process process = processBuilder.start();
            writePrompt(process, request.prompt());
            outputPump = startOutputPump(process, processOutput);
            boolean finished = waitForProcess(process, config.getTimeoutSeconds());
            if (!finished) {
                process.destroyForcibly();
                awaitOutputPump(outputPump);
                log.warn("Codex CLI parsing timed out after {} seconds. processOutput={}",
                        config.getTimeoutSeconds(),
                        truncate(processOutput.toString(), 4000));
                return CodexCliResult.failed("Codex CLI 解析超时");
            }
            awaitOutputPump(outputPump);
            if (process.exitValue() != 0) {
                log.warn("Codex CLI parsing failed. exitCode={}, durationMs={}, processOutput={}",
                        process.exitValue(),
                        elapsedMillis(startedAt),
                        truncate(processOutput.toString(), 4000));
                return CodexCliResult.failed("Codex CLI 执行失败");
            }
            if (!Files.exists(outputFile) || Files.size(outputFile) == 0) {
                log.warn("Codex CLI returned no structured output. durationMs={}, processOutput={}",
                        elapsedMillis(startedAt),
                        truncate(processOutput.toString(), 4000));
                return CodexCliResult.failed("Codex CLI 未返回结构化结果");
            }
            String outputJson = Files.readString(outputFile, StandardCharsets.UTF_8);
            log.info("Codex CLI execution completed. exitCode=0, durationMs={}, outputPreview={}",
                    elapsedMillis(startedAt),
                    truncate(outputJson, 2000));
            return CodexCliResult.succeeded(outputJson);
        } catch (Exception ex) {
            log.warn("Codex CLI execution failed. processOutput={}", truncate(processOutput.toString(), 4000), ex);
            return CodexCliResult.failed("Codex CLI 解析异常: " + summarizeException(ex));
        } finally {
            deleteIfExists(outputFile);
            deleteIfExists(outputSchema);
            deleteDirectoryIfExists(isolatedCodexHome);
        }
    }

    List<String> buildCommand(CodexCliRequest request, Path outputSchema, Path outputFile) {
        AiProperties.CodexCli config = aiProperties.getCodexCli();
        return buildCommand(request, outputSchema, outputFile, resolveEffectiveConfig(config));
    }

    private List<String> buildCommand(CodexCliRequest request,
                                      Path outputSchema,
                                      Path outputFile,
                                      EffectiveCodexCliConfig effectiveConfig) {
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
        command.add("--dangerously-bypass-approvals-and-sandbox");
        command.add("-C");
        command.add(request.workingDirectory().toString());
        for (String addDir : request.addDirs()) {
            command.add("--add-dir");
            command.add(addDir);
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

    private Path prepareIsolatedCodexHome(EffectiveCodexCliConfig config) {
        Path sourceAuthFile = locateCodexAuthFile();
        if (sourceAuthFile == null || !Files.exists(sourceAuthFile)) {
            log.warn("Codex auth file not found. Falling back to inherited CODEX_HOME.");
            return null;
        }
        try {
            Path isolatedHome = Files.createTempDirectory("workhub-codex-home-");
            Files.copy(sourceAuthFile, isolatedHome.resolve("auth.json"), StandardCopyOption.REPLACE_EXISTING);
            String minimalConfig = """
                    model = "%s"
                    model_reasoning_effort = "%s"
                    personality = "pragmatic"
                    """.formatted(
                    safeTomlString(config.model()),
                    safeTomlString(config.reasoningEffort())
            );
            Files.writeString(isolatedHome.resolve("config.toml"), minimalConfig, StandardCharsets.UTF_8);
            return isolatedHome;
        } catch (Exception ex) {
            log.warn("Failed to prepare isolated CODEX_HOME. Falling back to inherited configuration.", ex);
            return null;
        }
    }

    Path locateCodexAuthFile() {
        String codexHome = System.getenv("CODEX_HOME");
        if (codexHome != null && !codexHome.isBlank()) {
            Path candidate = Path.of(codexHome).resolve("auth.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            return null;
        }
        Path fallback = Path.of(userHome, ".codex", "auth.json");
        return Files.exists(fallback) ? fallback : null;
    }

    private String safeTomlString(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private EffectiveCodexCliConfig resolveEffectiveConfig(AiProperties.CodexCli config) {
        String model = trimToNull(readSystemConfig(CONFIG_KEY_MODEL));
        String reasoningEffort = trimToNull(readSystemConfig(CONFIG_KEY_REASONING_EFFORT));
        return new EffectiveCodexCliConfig(
                model == null ? trimToNull(config.getModel()) : model,
                reasoningEffort == null ? trimToNull(config.getReasoningEffort()) : reasoningEffort
        );
    }

    private String readSystemConfig(String configKey) {
        if (sysConfigService == null) {
            return null;
        }
        try {
            return sysConfigService.findPlainValue(CONFIG_GROUP_CODEX_CLI, configKey);
        } catch (Exception ex) {
            log.warn("Failed to read system config {}.{}. Falling back to application config.",
                    CONFIG_GROUP_CODEX_CLI,
                    configKey,
                    ex);
            return null;
        }
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
                    if (!processOutput.isEmpty()) {
                        processOutput.append('\n');
                    }
                    processOutput.append(line);
                    log.info("Codex CLI> {}", line);
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

    private String truncate(String value) {
        return truncate(value, 500);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
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

    private void deleteDirectoryIfExists(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(this::deleteIfExists);
        } catch (Exception ex) {
            log.debug("Failed to delete temp directory: {}", path, ex);
        }
    }

    record CodexCliRequest(Path workingDirectory,
                           List<String> addDirs,
                           List<String> imagePaths,
                           String outputSchema,
                           String prompt) {
    }

    record CodexCliResult(boolean succeeded, String outputJson, String failureSummary) {

        static CodexCliResult succeeded(String outputJson) {
            return new CodexCliResult(true, outputJson, null);
        }

        static CodexCliResult failed(String failureSummary) {
            return new CodexCliResult(false, null, failureSummary);
        }
    }

    private record EffectiveCodexCliConfig(String model, String reasoningEffort) {
    }
}
