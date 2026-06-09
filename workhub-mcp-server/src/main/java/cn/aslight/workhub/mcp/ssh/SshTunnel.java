package cn.aslight.workhub.mcp.ssh;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.security.SecretResolver;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Optional local SSH port forward backed by the local ssh command.
 */
public final class SshTunnel implements AutoCloseable {

    private final String host;
    private final int port;
    private final Process process;

    private SshTunnel(String host, int port, Process process) {
        this.host = host;
        this.port = port;
        this.process = process;
    }

    public static SshTunnel open(DatabaseTarget target) throws IOException {
        if (target.sshTunnel() == null) {
            return new SshTunnel(target.host(), target.port(), null);
        }
        int localPort = freeLocalPort();
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
        } else {
            command.add("-o");
            command.add("BatchMode=yes");
        }
        command.add("-o");
        command.add("ExitOnForwardFailure=yes");
        command.add("-o");
        command.add("StrictHostKeyChecking=accept-new");
        if (tunnel.identityFile() != null && !tunnel.identityFile().isBlank()) {
            command.add("-i");
            command.add(tunnel.identityFile());
        }
        command.add(tunnel.bastionUser() + "@" + tunnel.bastionHost());
        ProcessBuilder processBuilder = new ProcessBuilder(command).redirectErrorStream(true);
        if (passwordAuth) {
            processBuilder.environment().put("SSHPASS", password);
        }
        Process process = processBuilder.start();
        waitUntilAlive(process);
        return new SshTunnel("127.0.0.1", localPort, process);
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
            process.destroy();
        }
    }

    private static int freeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static void waitUntilAlive(Process process) throws IOException {
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("等待 SSH 隧道启动被中断", ex);
        }
        if (!process.isAlive()) {
            try {
                process.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            throw new IOException("SSH 隧道启动失败，退出码：" + process.exitValue());
        }
    }
}
