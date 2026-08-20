package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.ssh.SshTunnel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class McpServerConnectionTester {

    private final TunnelFactory tunnelFactory;
    private final SshProbe sshProbe;

    @Autowired
    public McpServerConnectionTester(McpBastionConnectionTester sshTester) {
        this(McpServerConnectionTester::openTunnel, sshTester::test);
    }

    McpServerConnectionTester(TunnelFactory tunnelFactory, SshProbe sshProbe) {
        this.tunnelFactory = tunnelFactory;
        this.sshProbe = sshProbe;
    }

    public Result test(Input input) {
        long startedAt = System.nanoTime();
        String mode = input.bastion() == null ? "DIRECT" : "BASTION";
        try (TunnelSession tunnel = tunnelFactory.open(input)) {
            McpBastionConnectionTester.Result sshResult = sshProbe.test(
                    tunnel.host(),
                    tunnel.port(),
                    input.username(),
                    input.password(),
                    input.identityFile()
            );
            if (!sshResult.success()) {
                return new Result(false, mode, "SERVER", elapsedMillis(startedAt),
                        serverMessage(sshResult.message()));
            }
            String message = input.bastion() == null
                    ? "服务器 SSH 连接成功"
                    : "经堡垒机连接服务器成功";
            return new Result(true, mode, null, elapsedMillis(startedAt), message);
        } catch (Exception ex) {
            String stage = input.bastion() == null ? "SERVER" : "BASTION";
            return new Result(false, mode, stage, elapsedMillis(startedAt),
                    "BASTION".equals(stage) ? bastionMessage(ex) : "服务器 SSH 连接失败");
        }
    }

    private static TunnelSession openTunnel(Input input) throws Exception {
        if (input.bastion() == null) {
            return new TunnelSession(input.host(), input.port(), () -> {
            });
        }
        DatabaseTarget.SshTunnel tunnelConfig = new DatabaseTarget.SshTunnel(
                input.bastion().host(),
                input.bastion().port(),
                input.bastion().username(),
                input.bastion().password(),
                input.bastion().identityFile()
        );
        DatabaseTarget target = new DatabaseTarget(
                "server-connection-test", null, List.of(), null, "server-connection-test", null, List.of(),
                input.host(), input.port(), null, input.username(), input.password(), tunnelConfig, List.of()
        );
        SshTunnel tunnel = SshTunnel.open(target);
        return new TunnelSession(tunnel.host(), tunnel.port(), tunnel);
    }

    private static String serverMessage(String message) {
        if (message == null || message.isBlank()) {
            return "服务器 SSH 连接失败";
        }
        return switch (message) {
            case "堡垒机地址无法解析" -> "服务器地址无法解析";
            case "堡垒机网络不可达" -> "服务器网络不可达";
            default -> "服务器 SSH " + message;
        };
    }

    private static String bastionMessage(Exception exception) {
        String messages = allMessages(exception);
        if (messages.contains("认证失败") || messages.contains("permission denied")) {
            return "堡垒机认证失败";
        }
        if (messages.contains("超时") || messages.contains("timed out")) {
            return "SSH 隧道建立超时";
        }
        if (messages.contains("连接被拒绝") || messages.contains("connection refused")) {
            return "堡垒机连接被拒绝";
        }
        if (messages.contains("地址无法解析") || messages.contains("could not resolve")) {
            return "堡垒机地址无法解析";
        }
        if (messages.contains("sshpass")) {
            return "服务环境未安装 sshpass，无法使用堡垒机密码";
        }
        return "SSH 隧道建立失败";
    }

    private static String allMessages(Throwable throwable) {
        StringBuilder result = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null) {
                result.append(' ').append(current.getMessage().toLowerCase(Locale.ROOT));
            }
            current = current.getCause();
        }
        return result.toString();
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    @FunctionalInterface
    interface TunnelFactory {
        TunnelSession open(Input input) throws Exception;
    }

    @FunctionalInterface
    interface SshProbe {
        McpBastionConnectionTester.Result test(String host,
                                               int port,
                                               String username,
                                               String password,
                                               String identityFile);
    }

    record TunnelSession(String host, int port, AutoCloseable resource) implements AutoCloseable {
        @Override
        public void close() throws Exception {
            resource.close();
        }
    }

    public record Input(String host,
                        int port,
                        String username,
                        String password,
                        String identityFile,
                        Bastion bastion) {
    }

    public record Bastion(String host,
                          int port,
                          String username,
                          String password,
                          String identityFile) {
    }

    public record Result(boolean success,
                         String connectionMode,
                         String failureStage,
                         long durationMs,
                         String message) {
    }
}
