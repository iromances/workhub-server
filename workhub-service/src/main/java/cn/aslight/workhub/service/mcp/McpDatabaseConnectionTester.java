package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.mcp.config.McpResourceCatalog.DatabaseTarget;
import cn.aslight.workhub.mcp.ssh.SshTunnel;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Component
public class McpDatabaseConnectionTester {

    private static final int TIMEOUT_MILLIS = 8_000;
    private static final int TIMEOUT_SECONDS = 8;

    private final ProbeExecutor probeExecutor;

    public McpDatabaseConnectionTester() {
        this(McpDatabaseConnectionTester::executeActual);
    }

    McpDatabaseConnectionTester(ProbeExecutor probeExecutor) {
        this.probeExecutor = probeExecutor;
    }

    public Result test(Input input) {
        long startedAt = System.nanoTime();
        try {
            probeExecutor.execute(input);
            String message = input.bastion() == null
                    ? "直连数据库成功"
                    : "经堡垒机连接数据库成功";
            return new Result(true, input.bastion() == null ? "DIRECT" : "BASTION", null,
                    elapsedMillis(startedAt), message);
        } catch (ProbeException ex) {
            return new Result(false, input.bastion() == null ? "DIRECT" : "BASTION", ex.stage(),
                    elapsedMillis(startedAt), safeMessage(ex));
        } catch (Exception ex) {
            return new Result(false, input.bastion() == null ? "DIRECT" : "BASTION", "DATABASE",
                    elapsedMillis(startedAt), classifyDatabaseFailure(ex));
        }
    }

    private static void executeActual(Input input) throws Exception {
        DatabaseTarget.SshTunnel tunnelConfig = input.bastion() == null ? null : new DatabaseTarget.SshTunnel(
                input.bastion().host(),
                input.bastion().port(),
                input.bastion().username(),
                input.bastion().password(),
                input.bastion().identityFile()
        );
        DatabaseTarget target = new DatabaseTarget(
                "connection-test", null, List.of(), null, "connection-test", null, List.of(),
                input.host(), input.port(), input.schema(), input.username(), input.password(),
                tunnelConfig, List.of()
        );

        SshTunnel tunnel;
        try {
            tunnel = SshTunnel.open(target);
        } catch (Exception ex) {
            throw new ProbeException("BASTION", classifyBastionFailure(ex), ex);
        }
        try (tunnel;
             Connection connection = DriverManager.getConnection(
                     jdbcUrl(tunnel.host(), tunnel.port(), input.schema()),
                     input.username(),
                     input.password());
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                if (!resultSet.next() || resultSet.getInt(1) != 1) {
                    throw new ProbeException("DATABASE", "数据库未返回预期探测结果", null);
                }
            }
        } catch (ProbeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProbeException("DATABASE", classifyDatabaseFailure(ex), ex);
        }
    }

    static String jdbcUrl(String host, int port, String schema) {
        String path = schema == null || schema.isBlank() ? "/" : "/" + schema.trim();
        return "jdbc:mysql://" + host + ":" + port + path
                + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
                + "&allowPublicKeyRetrieval=true&useSSL=false"
                + "&connectTimeout=" + TIMEOUT_MILLIS + "&socketTimeout=" + TIMEOUT_MILLIS;
    }

    private static String safeMessage(ProbeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? ("BASTION".equals(exception.stage()) ? "SSH 隧道建立失败" : "数据库连接失败")
                : exception.getMessage();
    }

    private static String classifyBastionFailure(Exception exception) {
        String message = allMessages(exception);
        if (message.contains("permission denied") || message.contains("authentication failed")) {
            return "堡垒机认证失败";
        }
        if (message.contains("timed out") || message.contains("连接超时")) {
            return "SSH 隧道建立超时";
        }
        if (message.contains("sshpass")) {
            return "服务环境未安装 sshpass，无法使用堡垒机密码";
        }
        return "SSH 隧道建立失败";
    }

    private static String classifyDatabaseFailure(Exception exception) {
        String message = allMessages(exception);
        if (message.contains("access denied") || message.contains("authentication")) {
            return "数据库认证失败";
        }
        if (message.contains("unknown database")) {
            return "数据库 Schema 不存在";
        }
        if (message.contains("timed out") || message.contains("communications link failure")) {
            return "数据库连接超时或网络不可达";
        }
        if (message.contains("connection refused")) {
            return "数据库连接被拒绝";
        }
        return "数据库连接失败";
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

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt));
    }

    @FunctionalInterface
    interface ProbeExecutor {
        void execute(Input input) throws Exception;
    }

    static final class ProbeException extends Exception {
        private final String stage;

        ProbeException(String stage, String message, Throwable cause) {
            super(message, cause);
            this.stage = stage;
        }

        String stage() {
            return stage;
        }
    }

    public record Input(String host,
                        int port,
                        String schema,
                        String username,
                        String password,
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
