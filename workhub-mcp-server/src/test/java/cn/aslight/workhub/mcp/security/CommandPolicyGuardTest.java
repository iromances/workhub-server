package cn.aslight.workhub.mcp.security;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CommandPolicyGuardTest {

    private final CommandPolicyGuard guard = new CommandPolicyGuard();
    private final McpResourceCatalog.ServerTarget target = new McpResourceCatalog.ServerTarget(
            "workhub-server-test",
            "workhub",
            List.of("workhub"),
            "test",
            "WorkHub test server",
            "workhub-api",
            List.of("workhub-api"),
            "127.0.0.1",
            22,
            "workhub",
            null,
            null,
            null,
            List.of("workhub-server"),
            List.of("/data/logs/workhub/app.log"),
            List.of(new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 100, 10))
    );
    private final McpResourceCatalog.ServerTarget.ServerProfile profile =
            new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 100, 10);

    @Test
    void buildCommand_shouldAllowOnlyWhitelistedLogPath() {
        List<String> command = guard.buildCommand(target, profile, "tail_log_file", Map.of(
                "logPath", "/data/logs/workhub/app.log",
                "lines", "200"
        ));

        assertEquals(List.of("tail", "-n", "100", "/data/logs/workhub/app.log"), command);
    }

    @Test
    void buildCommand_shouldRejectLogPathOutsideWhitelist() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "/etc/passwd"
                )));

        assertEquals("日志路径不在白名单中：/etc/passwd", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectServiceOutsideWhitelist() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "service_logs", Map.of(
                        "service", "sshd"
                )));

        assertEquals("服务不在白名单中：sshd", ex.getMessage());
    }

    @Test
    void buildCommand_shouldAllowAnyServiceWhenWhitelistIsBlank() {
        McpResourceCatalog.ServerTarget unrestrictedTarget = targetWithWhitelists(List.of(), List.of("/data/logs/workhub/app.log"));

        List<String> command = guard.buildCommand(unrestrictedTarget, profile, "service_logs", Map.of(
                "service", "sshd",
                "lines", "20"
        ));

        assertEquals(List.of("journalctl", "-u", "sshd", "-n", "20", "--no-pager"), command);
    }

    @Test
    void buildCommand_shouldAllowAnyLogPathWhenWhitelistIsBlank() {
        McpResourceCatalog.ServerTarget unrestrictedTarget = targetWithWhitelists(List.of("workhub-server"), List.of());

        List<String> command = guard.buildCommand(unrestrictedTarget, profile, "tail_log_file", Map.of(
                "logPath", "/var/log/messages",
                "lines", "20"
        ));

        assertEquals(List.of("tail", "-n", "20", "/var/log/messages"), command);
    }

    @Test
    void buildCommand_shouldRejectUnsupportedCommand() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "disk_usage", Map.of()));

        assertEquals("不支持的诊断命令：disk_usage", ex.getMessage());
    }

    private McpResourceCatalog.ServerTarget targetWithWhitelists(List<String> allowedServices,
                                                                 List<String> allowedLogPaths) {
        return new McpResourceCatalog.ServerTarget(
                "workhub-server-test",
                "workhub",
                List.of("workhub"),
                "test",
                "WorkHub test server",
                "workhub-api",
                List.of("workhub-api"),
                "127.0.0.1",
                22,
                "workhub",
                null,
                null,
                null,
                allowedServices,
                allowedLogPaths,
                List.of(new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 100, 10))
        );
    }
}
