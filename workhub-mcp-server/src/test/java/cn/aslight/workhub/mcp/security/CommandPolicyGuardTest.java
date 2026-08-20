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
            List.of("/data/logs/workhub"),
            List.of(new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 100, 10))
    );
    private final McpResourceCatalog.ServerTarget.ServerProfile profile =
            new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 100, 10);

    @Test
    void buildCommand_shouldAllowLogFilesBelowWhitelistedDirectory() {
        List<String> command = guard.buildCommand(target, profile, "tail_log_file", Map.of(
                "logPath", "/data/logs/workhub/workhub-api/app.log",
                "lines", "200"
        ));

        assertEquals(List.of("tail", "-n", "100", "/data/logs/workhub/workhub-api/app.log"), command);
    }

    @Test
    void buildCommand_shouldAllowLogFilesBelowAnyWhitelistedDirectory() {
        McpResourceCatalog.ServerTarget multiDirectoryTarget = targetWithWhitelists(
                List.of("workhub-server"),
                List.of("/data/logs/workhub-api", "/opt/services/payment/logs")
        );

        List<String> command = guard.buildCommand(multiDirectoryTarget, profile, "tail_log_file", Map.of(
                "logPath", "/opt/services/payment/logs/payment-error.2026-08-06.log"
        ));

        assertEquals(List.of("tail", "-n", "100", "/opt/services/payment/logs/payment-error.2026-08-06.log"), command);
    }

    @Test
    void buildCommand_shouldRejectLogPathOutsideWhitelist() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "/etc/passwd"
                )));

        assertEquals("日志路径不在目录白名单中：/etc/passwd", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectSiblingDirectoryWithSamePrefix() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "/data/logs/workhub-backup/app.log"
                )));

        assertEquals("日志路径不在目录白名单中：/data/logs/workhub-backup/app.log", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectPathTraversalOutsideWhitelistedDirectory() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "/data/logs/workhub/../../secrets.txt"
                )));

        assertEquals("日志路径不在目录白名单中：/data/secrets.txt", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectRelativeLogPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "logs/app.log"
                )));

        assertEquals("日志路径必须是绝对路径：logs/app.log", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectShellMetacharactersInLogPath() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "tail_log_file", Map.of(
                        "logPath", "/data/logs/workhub/$(id).log"
                )));

        assertEquals("日志路径包含不允许的字符：/data/logs/workhub/$(id).log", ex.getMessage());
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
        McpResourceCatalog.ServerTarget unrestrictedTarget = targetWithWhitelists(List.of(), List.of("/data/logs/workhub"));

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
    void buildCommand_shouldSearchPlainLogWithFixedStringAndCappedMatches() {
        List<String> command = guard.buildCommand(target, profile, "search_log_file", Map.of(
                "logPath", "/data/logs/workhub/workhub-api/app.log",
                "keyword", "EQDC26081601300000011",
                "maxMatches", "200"
        ));

        assertEquals(List.of(
                "grep", "-F", "-n", "-m", "100", "--",
                "EQDC26081601300000011", "/data/logs/workhub/workhub-api/app.log"
        ), command);
    }

    @Test
    void buildCommand_shouldSearchZipLogWithBoundedMatches() {
        List<String> command = guard.buildCommand(target, profile, "search_log_file", Map.of(
                "logPath", "/data/logs/workhub/workhub-api/info-20260816.0.zip",
                "keyword", "EQDC26081601300000011",
                "maxMatches", "20"
        ));

        assertEquals(List.of(
                "zipgrep", "-n", "-m20",
                "EQDC26081601300000011", "/data/logs/workhub/workhub-api/info-20260816.0.zip"
        ), command);
    }

    @Test
    void buildCommand_shouldRejectLeadingDashInZipSearchKeyword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "search_log_file", Map.of(
                        "logPath", "/data/logs/workhub/workhub-api/info-20260816.0.zip",
                        "keyword", "-EQDC26081601300000011"
                )));

        assertEquals("ZIP 日志检索关键词不能以 - 开头", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectRegexMetacharactersInZipSearchKeyword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "search_log_file", Map.of(
                        "logPath", "/data/logs/workhub/workhub-api/info-20260816.0.zip",
                        "keyword", "EQDC.*"
                )));

        assertEquals("ZIP 日志检索关键词不能包含正则元字符", ex.getMessage());
    }

    @Test
    void buildCommand_shouldRejectControlCharactersInSearchKeyword() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "search_log_file", Map.of(
                        "logPath", "/data/logs/workhub/workhub-api/app.log",
                        "keyword", "statement\ncat /etc/passwd"
                )));

        assertEquals("检索关键词不能包含控制字符", ex.getMessage());
    }

    @Test
    void buildCommand_shouldApplyLogPathWhitelistToSearch() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> guard.buildCommand(target, profile, "search_log_file", Map.of(
                        "logPath", "/etc/passwd",
                        "keyword", "root"
                )));

        assertEquals("日志路径不在目录白名单中：/etc/passwd", ex.getMessage());
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
