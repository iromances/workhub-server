package cn.aslight.workhub.mcp.ssh;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.security.SecretResolver;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Optional local SSH port forward backed by the local ssh command.
 */
public final class SshTunnel implements AutoCloseable {

    private final String host;
    private final int port;
    private final Process process;
    private final Path controlSocket;

    private SshTunnel(String host, int port, Process process, Path controlSocket) {
        this.host = host;
        this.port = port;
        this.process = process;
        this.controlSocket = controlSocket;
    }

    public static SshTunnel open(DatabaseTarget target) throws IOException {
        if (target.sshTunnel() == null) {
            return new SshTunnel(target.host(), target.port(), null, null);
        }
        int localPort = freeLocalPort();
        Path controlSocket = controlSocketPath();
        DatabaseTarget.SshTunnel tunnel = target.sshTunnel();
        List<String> command = new ArrayList<>();
        String password = new SecretResolver().resolve(tunnel.password());
        boolean passwordAuth = password != null && !password.isBlank();
        if (passwordAuth) {
            command.add("sshpass");
            command.add("-e");
        }
        command.add("ssh");
        command.add("-N");
        command.add("-L");
        command.add(localPort + ":" + target.host() + ":" + target.port());
        command.add("-p");
        command.add(String.valueOf(tunnel.bastionPort() <= 0 ? 22 : tunnel.bastionPort()));
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
        command.add("-o");
        command.add("ExitOnForwardFailure=yes");
        command.add("-o");
        command.add("StrictHostKeyChecking=accept-new");
        command.add("-o");
        command.add("ConnectTimeout=8");
        command.add("-o");
        command.add("ConnectionAttempts=1");
        command.add("-o");
        command.add("ControlMaster=yes");
        command.add("-o");
        command.add("ControlPath=" + controlSocket);
        if (tunnel.identityFile() != null && !tunnel.identityFile().isBlank()) {
            command.add("-i");
            command.add(tunnel.identityFile());
        }
        command.add("--");
        command.add(tunnel.bastionUser() + "@" + tunnel.bastionHost());
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        if (passwordAuth) {
            processBuilder.environment().put("SSHPASS", password);
        }
        Process process = processBuilder.start();
        try {
            waitUntilReady(process, controlSocket);
            return new SshTunnel("127.0.0.1", localPort, process, controlSocket);
        } catch (IOException ex) {
            process.destroyForcibly();
            Files.deleteIfExists(controlSocket);
            throw ex;
        }
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    @Override
    public void close() {
        if (process != null) {
            process.destroyForcibly();
        }
        if (controlSocket != null) {
            try {
                Files.deleteIfExists(controlSocket);
            } catch (IOException ignored) {
                // Ephemeral control socket cleanup must not hide the database result.
            }
        }
    }

    private static int freeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static Path controlSocketPath() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return Path.of("/tmp", "workhub-tunnel-" + suffix + ".sock");
    }

    private static void waitUntilReady(Process process, Path controlSocket) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        try {
            while (System.nanoTime() < deadline) {
                if (Files.exists(controlSocket)) {
                    return;
                }
                if (!process.isAlive()) {
                    throw new IOException(classifyFailure(readOutput(process)));
                }
                Thread.sleep(50L);
            }
            throw new IOException("SSH 隧道建立超时");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 SSH 隧道启动被中断", ex);
        }
    }

    private static String readOutput(Process process) {
        try (InputStream input = process.getInputStream()) {
            return new String(input.readNBytes(8 * 1024), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            return "";
        }
    }

    private static String classifyFailure(String output) {
        String normalized = output == null ? "" : output.toLowerCase(Locale.ROOT);
        if (normalized.contains("permission denied") || normalized.contains("authentication failed")) {
            return "堡垒机认证失败";
        }
        if (normalized.contains("connection timed out") || normalized.contains("operation timed out")) {
            return "SSH 隧道建立超时";
        }
        if (normalized.contains("connection refused")) {
            return "堡垒机连接被拒绝";
        }
        if (normalized.contains("could not resolve hostname")) {
            return "堡垒机地址无法解析";
        }
        return "SSH 隧道建立失败";
    }
}
