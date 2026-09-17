package cn.aslight.workhub.mcp.server;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget;
import cn.aslight.workhub.mcp.config.McpResourceCatalog.ServerTarget.ServerProfile;
import cn.aslight.workhub.mcp.security.CommandPolicyGuard;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.ssh.SshTunnel;

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
        try {
            remoteCommand = commandPolicyGuard.buildCommand(target, profile, commandKey, parameters);
            try (SshTunnel forward = SshTunnel.open(target.sshTunnel(), target.host(), target.port() <= 0 ? 22 : target.port())) {
                ProcessBuilder processBuilder = sshProcess(target, remoteCommand, forward.host(), forward.port());
                sshCommand = processBuilder.command();
                Process process = processBuilder.start();
                CompletableFuture<byte[]> outputFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return process.getInputStream().readAllBytes();
                    } catch (Exception ex) {
                        throw new IllegalStateException(ex);
                    }
                });
                boolean finished = process.waitFor(Math.max(profile.timeoutSeconds(), 1), TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IllegalArgumentException("诊断命令执行超时");
                }
                String output = new String(outputFuture.get(1, TimeUnit.SECONDS), StandardCharsets.UTF_8);
                String truncated = truncateLines(output, profile.maxOutputLines());
                audit("run_diagnostic_command", target, profile, commandKey, remoteCommand, sshCommand,
                        process.exitValue(), startedAt, null);
                return Map.of(
                        "targetKey", targetKey,
                        "profileKey", profileKey,
                        "commandKey", commandKey,
                        "exitCode", process.exitValue(),
                        "output", truncated
                );
            }
        } catch (Exception ex) {
            audit("run_diagnostic_command", target, profile, commandKey, remoteCommand, sshCommand,
                    -1, startedAt, ex);
            throw new IllegalArgumentException("服务器诊断失败：" + ex.getMessage(), ex);
        }
    }

    private ProcessBuilder sshProcess(ServerTarget target, List<String> remoteCommand, String host, int port) {
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
        command.addAll(remoteCommand);
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
        List<String> auditedRemoteCommand = sanitizeCommandForAudit(commandKey, remoteCommand);
        List<String> auditedSshCommand = sanitizeCommandForAudit(commandKey, sshCommand);
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
