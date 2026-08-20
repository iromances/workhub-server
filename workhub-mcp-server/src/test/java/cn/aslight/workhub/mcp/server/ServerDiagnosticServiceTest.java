package cn.aslight.workhub.mcp.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerDiagnosticServiceTest {

    @Test
    void formatCommand_shouldKeepExecutableCommandReadableAndShellSafe() {
        String command = ServerDiagnosticService.formatCommand(List.of(
                "ssh",
                "-p",
                "22",
                "ops@127.0.0.1",
                "--",
                "journalctl",
                "-u",
                "workhub api",
                "--no-pager"
        ));

        assertEquals("ssh -p 22 ops@127.0.0.1 -- journalctl -u 'workhub api' --no-pager", command);
    }

    @Test
    void sanitizeCommandForAudit_shouldRedactSearchKeyword() {
        List<String> sanitized = ServerDiagnosticService.sanitizeCommandForAudit(
                "search_log_file",
                List.of(
                        "ssh", "ops@127.0.0.1", "--", "grep", "-F", "-n", "-m", "20", "--",
                        "EQDC26081601300000011", "/data/logs/workhub/app.log"
                )
        );

        assertEquals(List.of(
                "ssh", "ops@127.0.0.1", "--", "grep", "-F", "-n", "-m", "20", "--",
                "[REDACTED]", "/data/logs/workhub/app.log"
        ), sanitized);
    }
}
