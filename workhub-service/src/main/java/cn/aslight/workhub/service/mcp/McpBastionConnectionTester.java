package cn.aslight.workhub.service.mcp;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class McpBastionConnectionTester {

    private static final int TIMEOUT_SECONDS = 8;
    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(TIMEOUT_SECONDS);
    private static final long POLL_INTERVAL_MILLIS = 50L;
    private static final int MAX_OUTPUT_BYTES = 8 * 1024;

    private final ProcessLauncher processLauncher;
    private final long timeoutMillis;
    private final ControlSocketProbe controlSocketProbe;

    public McpBastionConnectionTester() {
        this((command, environment) -> {
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
            builder.environment().putAll(environment);
            return builder.start();
        }, TIMEOUT_MILLIS, Files::exists);
    }

    McpBastionConnectionTester(ProcessLauncher processLauncher) {
        this(processLauncher, TIMEOUT_MILLIS, Files::exists);
    }

    McpBastionConnectionTester(ProcessLauncher processLauncher,
                               long timeoutMillis,
                               ControlSocketProbe controlSocketProbe) {
        this.processLauncher = processLauncher;
        this.timeoutMillis = timeoutMillis;
        this.controlSocketProbe = controlSocketProbe;
    }

    public Result test(String host,
                       int port,
                       String username,
                       String password,
                       String identityFile) {
        long startedAt = System.nanoTime();
        boolean passwordAuth = password != null && !password.isBlank();
        Path controlSocket = controlSocketPath();
        List<String> command = command(host, port, username, passwordAuth, identityFile, controlSocket);
        Map<String, String> environment = new HashMap<>();
        if (passwordAuth) {
            environment.put("SSHPASS", password);
        }

        Process process = null;
        try {
            process = processLauncher.start(command, environment);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            while (System.nanoTime() < deadline) {
                if (controlSocketProbe.isReady(controlSocket)) {
                    return new Result(true, elapsedMillis(startedAt), "连接成功");
                }
                if (!process.isAlive()) {
                    return failure(startedAt, classify(readOutput(process)));
                }
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                Thread.sleep(Math.max(1L, Math.min(POLL_INTERVAL_MILLIS, remainingMillis)));
            }
            return failure(startedAt, "连接超时");
        } catch (IOException ex) {
            return failure(startedAt, unavailableMessage(ex, passwordAuth));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failure(startedAt, "连接测试被中断");
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                Files.deleteIfExists(controlSocket);
            } catch (IOException ignored) {
                // Control socket is an ephemeral test artifact and must not affect the result.
            }
        }
    }

    private List<String> command(String host,
                                 int port,
                                 String username,
                                 boolean passwordAuth,
                                 String identityFile,
                                 Path controlSocket) {
        List<String> command = new ArrayList<>();
        if (passwordAuth) {
            command.add("sshpass");
            command.add("-e");
        }
        command.add("ssh");
        command.add("-N");
        command.add("-p");
        command.add(String.valueOf(port));
        command.add("-o");
        command.add("ConnectTimeout=" + TIMEOUT_SECONDS);
        command.add("-o");
        command.add("ConnectionAttempts=1");
        command.add("-o");
        command.add("StrictHostKeyChecking=accept-new");
        command.add("-o");
        command.add("ControlMaster=yes");
        command.add("-o");
        command.add("ControlPath=" + controlSocket);
        if (passwordAuth) {
            command.add("-o");
            command.add("PreferredAuthentications=password,keyboard-interactive");
            command.add("-o");
            command.add("PubkeyAuthentication=no");
        } else {
            command.add("-o");
            command.add("BatchMode=yes");
            command.add("-o");
            command.add("IdentitiesOnly=yes");
        }
        if (identityFile != null && !identityFile.isBlank()) {
            command.add("-i");
            command.add(identityFile);
        }
        command.add("--");
        command.add(username + "@" + host);
        return command;
    }

    private Path controlSocketPath() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return Path.of("/tmp", "workhub-ssh-" + suffix + ".sock");
    }

    private Result failure(long startedAt, String message) {
        return new Result(false, elapsedMillis(startedAt), message);
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    private String readOutput(Process process) {
        try (InputStream input = process.getInputStream()) {
            return new String(input.readNBytes(MAX_OUTPUT_BYTES), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private String classify(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (normalized.contains("permission denied")
                || normalized.contains("authentication failed")) {
            return "认证失败";
        }
        if (normalized.contains("connection timed out")
                || normalized.contains("operation timed out")) {
            return "连接超时";
        }
        if (normalized.contains("connection refused")) {
            return "连接被拒绝";
        }
        if (normalized.contains("could not resolve hostname")
                || normalized.contains("name or service not known")) {
            return "堡垒机地址无法解析";
        }
        if (normalized.contains("no route to host")
                || normalized.contains("network is unreachable")) {
            return "堡垒机网络不可达";
        }
        if (normalized.contains("host key verification failed")) {
            return "主机密钥校验失败";
        }
        if (normalized.contains("identity file")
                && (normalized.contains("not accessible") || normalized.contains("no such file"))) {
            return "私钥文件不可用";
        }
        return "SSH 连接失败";
    }

    private String unavailableMessage(IOException exception, boolean passwordAuth) {
        String message = exception.getMessage() == null ? "" : exception.getMessage();
        if (passwordAuth && message.contains("sshpass")) {
            return "服务环境未安装 sshpass，无法测试密码认证";
        }
        if (message.contains("ssh")) {
            return "服务环境 SSH 命令不可用";
        }
        return "SSH 连接测试启动失败";
    }

    @FunctionalInterface
    interface ProcessLauncher {
        Process start(List<String> command, Map<String, String> environment) throws IOException;
    }

    @FunctionalInterface
    interface ControlSocketProbe {
        boolean isReady(Path controlSocket);
    }

    public record Result(boolean success, long durationMs, String message) {
    }
}
