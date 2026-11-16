package cn.aslight.workhub.mcp.server;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget.ServerProfile;
import cn.aslight.workhub.mcp.security.CommandPolicyGuard;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.ServerFilePolicy;
import cn.aslight.workhub.mcp.security.ServerFileContentMasker;
import cn.aslight.workhub.mcp.ssh.SshTunnel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class ServerDiagnosticService {

    private final McpResourceCatalog catalog;
    private final CommandPolicyGuard commandPolicyGuard;
    private final SecretResolver secretResolver = new SecretResolver();
    private final McpAuditLogger auditLogger;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServerFileContentMasker fileContentMasker = new ServerFileContentMasker();

    public ServerDiagnosticService(McpResourceCatalog catalog,
                                   CommandPolicyGuard commandPolicyGuard,
                                   McpAuditLogger auditLogger) {
        this.catalog = catalog;
        this.commandPolicyGuard = commandPolicyGuard;
        this.auditLogger = auditLogger;
    }

    public Map<String, Object> getServiceStatus(String targetKey, String profileKey, String service) {
        return runDiagnosticCommand(targetKey, profileKey, "service_status", Map.of("service", service));
    }

    public Map<String, Object> listServerDirectory(String targetKey, String profileKey, String path,
                                                  String maxDepth, String maxEntries) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("path", path);
        parameters.put("maxDepth", maxDepth);
        parameters.put("maxEntries", maxEntries);
        return runDiagnosticCommand(targetKey, profileKey, "list_server_directory", parameters);
    }

    public Map<String, Object> readServerFile(String targetKey, String profileKey, String path,
                                             String lines, String maxBytes) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("path", path);
        parameters.put("lines", lines);
        parameters.put("maxBytes", maxBytes);
        return runDiagnosticCommand(targetKey, profileKey, "read_server_file", parameters);
    }

    public Map<String, Object> readServiceLogs(String targetKey,
                                               String profileKey,
                                               String service,
                                               String logPath,
                                               String lines) {
        Map<String, String> parameters = new java.util.LinkedHashMap<>();
        if (lines != null && !lines.isBlank()) {
            parameters.put("lines", lines);
        }
        if (service != null && !service.isBlank()) {
            parameters.put("service", service);
            return runDiagnosticCommand(targetKey, profileKey, "service_logs", parameters);
        }
        parameters.put("logPath", logPath);
        return runDiagnosticCommand(targetKey, profileKey, "tail_log_file", parameters);
    }

    public Map<String, Object> searchServiceLogs(String targetKey,
                                                 String profileKey,
                                                 String logPath,
                                                 String keyword,
                                                 String maxMatches) {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("logPath", logPath);
        parameters.put("keyword", keyword);
        if (maxMatches != null && !maxMatches.isBlank()) {
            parameters.put("maxMatches", maxMatches);
        }
        return runDiagnosticCommand(targetKey, profileKey, "search_log_file", parameters);
    }

    public Map<String, Object> runDiagnosticCommand(String targetKey,
                                                    String profileKey,
                                                    String commandKey,
                                                    Map<String, String> parameters) {
        ServerTarget target = catalog.requireServerTarget(targetKey);
        ServerProfile profile = catalog.requireServerProfile(targetKey, profileKey);
        Instant startedAt = Instant.now();
        List<String> remoteCommand = List.of();
        List<String> sshCommand = List.of();
        Process process = null;
        try {
            remoteCommand = commandPolicyGuard.buildCommand(target, profile, commandKey, parameters);
            List<String> executionCommand = ServerFilePolicy.isPathLogCommand(commandKey)
                    ? ServerFilePolicy.protectLogCommand(target, commandKey, remoteCommand) : remoteCommand;
            try (SshTunnel forward = SshTunnel.open(target.sshTunnel(), target.host(), target.port() <= 0 ? 22 : target.port())) {
                ProcessBuilder processBuilder = sshProcess(target, executionCommand, forward.host(), forward.port());
                sshCommand = processBuilder.command();
                process = processBuilder.start();
                Process runningProcess = process;
                CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return readBoundedOutput(runningProcess.getInputStream(), 2097152);
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                boolean finished = process.waitFor(Math.max(profile.timeoutSeconds(), 1), TimeUnit.SECONDS);
                if (!finished) {
                    throw new IllegalArgumentException("诊断命令执行超时");
                }
                String output = new String(outputFuture.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("targetKey", targetKey);
                result.put("profileKey", profileKey);
                result.put("commandKey", commandKey);
                result.put("exitCode", process.exitValue());
                if (ServerFilePolicy.isFileCommand(commandKey)) {
                    result.putAll(fileResult(output, process.exitValue()));
                } else {
                    if (ServerFilePolicy.isPathLogCommand(commandKey) && process.exitValue() != 0
                            && output.startsWith("{\"error\":")) {
                        fileResult(output, process.exitValue());
                    }
                    result.put("output", fileContentMasker.mask(truncateLines(output, profile.maxOutputLines())));
                }
                audit("run_diagnostic_command", target, profile, commandKey, parameters, remoteCommand, sshCommand,
                        process.exitValue(), startedAt, null);
                return result;
            }
        } catch (Exception ex) {
            audit("run_diagnostic_command", target, profile, commandKey, parameters, remoteCommand, sshCommand,
                    -1, startedAt, ex);
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalArgumentException("服务器诊断失败：" + ex.getMessage(), ex);
        } finally {
            if (process != null) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
            }
        }
    }

    Map<String, Object> fileResult(String output, int exitCode) throws IOException {
        if (exitCode != 0 && !output.stripLeading().startsWith("{")) {
            throw new IllegalArgumentException("文件读取失败，请检查 SSH 连接及目标服务器已安装的 python3");
        }
        Map<String, Object> result;
        try {
            result = objectMapper.readValue(output, new TypeReference<>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            // JSON 解析异常可能包含文件内容片段，不能原样写入审计。
            throw new IllegalArgumentException("文件读取响应格式无效");
        }
        if (exitCode != 0 || result.containsKey("error")) {
            throw new IllegalArgumentException(String.valueOf(result.getOrDefault("error", "文件读取失败")));
        }
        if (result.get("content") instanceof String content) {
            result.put("content", fileContentMasker.mask(content));
        }
        result.put("dataMasked", true);
        return result;
    }

    static byte[] readBoundedOutput(InputStream input, int maximumBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        boolean exceeded = false;
        int count;
        while ((count = input.read(buffer)) != -1) {
            int remaining = maximumBytes - output.size();
            output.write(buffer, 0, Math.min(count, remaining));
            exceeded |= count > remaining;
        }
        if (exceeded) {
            throw new IOException("诊断输出超过字节上限，请缩小读取范围");
        }
        return output.toByteArray();
    }

    ProcessBuilder sshProcess(ServerTarget target, List<String> remoteCommand, String host, int port) {
        List<String> command = new ArrayList<>();
        String password = secretResolver.resolve(target.password());
        boolean passwordAuth = password != null && !password.isBlank();
        if (passwordAuth) {
            command.add("sshpass");
            command.add("-e");
        }
        command.add("ssh");
        command.add("-p");
        command.add(String.valueOf(port <= 0 ? 22 : port));
        if (passwordAuth) {
            command.add("-o");
            command.add("PreferredAuthentications=password,keyboard-interactive");
        } else {
            command.add("-o");
            command.add("BatchMode=yes");
        }
        command.add("-o");
        command.add("StrictHostKeyChecking=accept-new");
        if (target.identityFile() != null && !target.identityFile().isBlank()) {
            command.add("-i");
            command.add(target.identityFile());
        }
        command.add(target.username() + "@" + host);
        command.add("--");
        // OpenSSH 将远端命令交给 shell；必须引用每个参数，不能直接拼接参数列表。
        command.add(formatCommand(remoteCommand));
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        if (passwordAuth) {
            processBuilder.environment().put("SSHPASS", password);
        }
        return processBuilder;
    }

    private String truncateLines(String output, int maxLines) {
        int limit = maxLines <= 0 ? 200 : maxLines;
        String[] lines = output.split("\\R");
        if (lines.length <= limit) {
            return output;
        }
        return String.join(System.lineSeparator(), java.util.Arrays.copyOf(lines, limit))
                + System.lineSeparator() + "...truncated...";
    }

    private void audit(String toolName,
                       ServerTarget target,
                       ServerProfile profile,
                       String commandKey,
                       Map<String, String> parameters,
                       List<String> remoteCommand,
                       List<String> sshCommand,
                       int exitCode,
                       Instant startedAt,
                       Exception error) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("tool", toolName);
        fields.put("resourceType", "SERVER");
        fields.put("businessLineCode", target.businessLineCode());
        fields.put("businessLineCodes", target.businessLineCodes());
        fields.put("environmentCode", target.environmentCode());
        fields.put("targetKey", target.key());
        fields.put("profileKey", profile.key());
        fields.put("commandKey", commandKey);
        if (ServerFilePolicy.isFileCommand(commandKey)) {
            fields.put("path", parameters.get("path"));
            Map<String, String> limits = new LinkedHashMap<>();
            for (String key : List.of("lines", "maxBytes", "maxDepth", "maxEntries")) {
                if (parameters.get(key) != null) {
                    limits.put(key, parameters.get(key));
                }
            }
            fields.put("limits", limits);
        }
        List<String> auditedRemoteCommand = sanitizeCommandForAudit(commandKey, remoteCommand);
        List<String> auditedSshCommand = new ArrayList<>();
        if (!sshCommand.isEmpty()) {
            auditedSshCommand.addAll(sshCommand.subList(0, sshCommand.size() - 1));
            auditedSshCommand.add(formatCommand(auditedRemoteCommand));
        }
        fields.put("command", formatCommand(auditedRemoteCommand));
        fields.put("remoteCommand", auditedRemoteCommand);
        fields.put("sshCommand", formatCommand(auditedSshCommand));
        fields.put("sshCommandArgs", auditedSshCommand);
        fields.put("exitCode", exitCode);
        fields.put("status", error == null ? "SUCCEEDED" : "FAILED");
        fields.put("durationMillis", Duration.between(startedAt, Instant.now()).toMillis());
        fields.put("error", error == null || error.getMessage() == null ? "" : error.getMessage());
        auditLogger.record(fields);
    }

    static List<String> sanitizeCommandForAudit(String commandKey, List<String> command) {
        if (command == null || command.isEmpty()) {
            return List.of();
        }
        if (ServerFilePolicy.isFileCommand(commandKey)) {
            return command.stream().map(argument -> argument.contains("Fixed read-only helper")
                    ? "[FIXED_FILE_READER]" : argument).toList();
        }
        if (!"search_log_file".equals(commandKey) || command.size() < 2) {
            return List.copyOf(command);
        }
        List<String> sanitized = new ArrayList<>(command);
        sanitized.set(sanitized.size() - 2, "[REDACTED]");
        return List.copyOf(sanitized);
    }

    static String formatCommand(List<String> command) {
        if (command == null || command.isEmpty()) {
            return "";
        }
        return command.stream()
                .map(ServerDiagnosticService::shellQuote)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private static String shellQuote(String value) {
        if (value == null) {
            return "''";
        }
        if (value.matches("[A-Za-z0-9_@%+=:,./-]+")) {
            return value;
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
