package cn.aslight.workhub.mcp.server;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.security.CommandPolicyGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ServerDiagnosticServiceTest {

    @TempDir
    Path temporary;

    @Test
    void fullFileFlowMasksContentAndAuditsOnlyMetadataWithoutUsingNetwork() throws Exception {
        Path root = temporary.toRealPath();
        Path source = root.resolve("start.sh");
        Files.writeString(source, "PASSWORD=TEST_FILE_SECRET\njava -Dspring.profiles.active=sit -jar app.jar\n");
        Path auditPath = root.resolve("audit.jsonl");
        ServerDiagnosticService service = localService(root, auditPath, false);
        Map<String, Object> result = service.readServerFile("local-test", "diagnostic", source.toString(), "20", "1000");
        assertTrue(result.get("content").toString().contains("-Dspring.profiles.active=sit"));
        assertFalse(result.get("content").toString().contains("TEST_FILE_SECRET"));
        String audit = Files.readString(auditPath);
        assertFalse(audit.contains("TEST_FILE_SECRET"));
        assertFalse(audit.contains("Fixed read-only helper"));
        assertTrue(audit.contains(source.toString()));
        assertTrue(audit.contains("SUCCEEDED"));
        assertThrows(IllegalArgumentException.class,
                () -> service.readServerFile("local-test", "diagnostic", root.getParent() + "/outside", null, null));
        assertTrue(Files.readString(auditPath).contains("FAILED"));
    }

    @Test
    void sameAllowlistProtectsLogTailSearchAndFileReadIncludingSymlinks() throws Exception {
        Path root = temporary.toRealPath();
        Path source = root.resolve("app.log");
        Files.writeString(source, "first\nneedle PASSWORD=TAIL_SECRET\nlast\n");
        ServerDiagnosticService service = localService(root, root.resolve("audit.jsonl"), false);
        Map<String, Object> tail = service.readServiceLogs("local-test", "diagnostic", null, source.toString(), "1");
        assertEquals(0, tail.get("exitCode"));
        assertEquals("last\n", tail.get("output"));
        Map<String, Object> search = service.searchServiceLogs("local-test", "diagnostic", source.toString(), "needle", "2");
        assertEquals(0, search.get("exitCode"));
        assertTrue(search.get("output").toString().contains("needle"));
        assertFalse(search.get("output").toString().contains("TAIL_SECRET"));
        assertEquals(1, service.searchServiceLogs("local-test", "diagnostic", source.toString(), "absent", "2").get("exitCode"));
        Files.createSymbolicLink(root.resolve("linked.log"), source);
        assertThrows(IllegalArgumentException.class, () -> service.readServiceLogs("local-test", "diagnostic", null,
                root.resolve("linked.log").toString(), "1"));
        assertThrows(IllegalArgumentException.class, () -> service.searchServiceLogs("local-test", "diagnostic",
                root.getParent() + "/outside.log", "needle", "2"));
        assertThrows(IllegalArgumentException.class, () -> service.readServerFile("local-test", "diagnostic",
                root.getParent() + "/outside.log", "1", "100"));
    }

    @Test
    void searchesZipLogsThroughVerifiedDescriptorWithoutExtractingToDisk() throws Exception {
        Path root = temporary.toRealPath();
        Path archive = root.resolve("archive.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new java.util.zip.ZipEntry("archive.log"));
            output.write("needle archive-value\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        ServerDiagnosticService service = localService(root, root.resolve("audit.jsonl"), false);
        Map<String, Object> result = service.searchServiceLogs("local-test", "diagnostic", archive.toString(), "needle", "1");
        assertEquals(0, result.get("exitCode"));
        assertTrue(result.get("output").toString().contains("archive-value"));
        assertFalse(Files.exists(root.resolve("archive.log")));
        Path credentialArchive = root.resolve("credentials.zip");
        try (var output = new java.util.zip.ZipOutputStream(Files.newOutputStream(credentialArchive))) {
            output.putNextEntry(new java.util.zip.ZipEntry(".ssh/id_rsa"));
            output.write("needle protected-content\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        assertThrows(IllegalArgumentException.class, () -> service.searchServiceLogs("local-test", "diagnostic",
                credentialArchive.toString(), "needle", "1"));
    }

    @Test
    void fileReadTimeoutStopsLocalProcessAndRecordsFailure() throws Exception {
        Path root = temporary.toRealPath();
        Path audit = root.resolve("audit.jsonl");
        ServerDiagnosticService service = localService(root, audit, true);
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () -> {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.readServerFile("local-test", "diagnostic", root + "/start.sh", null, null));
            assertTrue(error.getMessage().contains("超时"));
        });
        assertTrue(Files.readString(audit).contains("FAILED"));
    }

    private ServerDiagnosticService localService(Path root, Path auditPath, boolean simulateTimeout) {
        var profile = new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 20, 1);
        var target = new McpResourceCatalog.ServerTarget("local-test", false, List.of(), "BL000006", List.of("BL000006"),
                "TEST", "local-test", null, List.of(), "127.0.0.1", 22, "test", null, null, null,
                List.of(), List.of(root.toString()), List.of(profile));
        var catalog = new McpResourceCatalog(List.of(), List.of(), List.of(), List.of(target), Map.of(), Map.of());
        return new ServerDiagnosticService(catalog, new CommandPolicyGuard(), new McpAuditLogger(new ObjectMapper(), auditPath)) {
            @Override
            ProcessBuilder sshProcess(McpResourceCatalog.ServerTarget ignored, List<String> command, String host, int port) {
                return (simulateTimeout
                        ? new ProcessBuilder("python3", "-I", "-S", "-c", "import time; time.sleep(30)")
                        : new ProcessBuilder("sh", "-c", ServerDiagnosticService.formatCommand(command))).redirectErrorStream(true);
            }
        };
    }

    @Test
    void quotesRemoteArgumentsForSshShellAndKeepsFixedProgramOutOfAudit() {
        ServerDiagnosticService service = new ServerDiagnosticService(null, new CommandPolicyGuard(), null);
        McpResourceCatalog.ServerTarget target = new McpResourceCatalog.ServerTarget("test", "BL000006", List.of(),
                "TEST", "test", null, List.of(), "127.0.0.1", 22, "test", null, null, null,
                List.of(), List.of(), List.of());
        List<String> command = List.of("python3", "-I", "-S", "-c", "# Fixed read-only helper\nprint('ok')", "{\"path\":\"/safe/$(id).sh\"}");
        List<String> ssh = service.sshProcess(target, command, "127.0.0.1", 22).command();
        assertEquals(ServerDiagnosticService.formatCommand(command), ssh.getLast());
        assertTrue(ssh.getLast().contains("'{\"path\":\"/safe/$(id).sh\"}'"));
        List<String> audit = ServerDiagnosticService.sanitizeCommandForAudit("read_server_file", command);
        assertEquals("[FIXED_FILE_READER]", audit.get(4));
        assertFalse(audit.toString().contains("print('ok')"));
    }

    @Test
    void masksStructuredFileResponseAndNeverEchoesMalformedPayloadInErrors() throws Exception {
        ServerDiagnosticService service = new ServerDiagnosticService(null, new CommandPolicyGuard(), null);
        Map<String, Object> result = service.fileResult("{\"content\":\"PASSWORD=super-secret\\nprofile=sit\",\"truncated\":false}", 0);
        assertFalse(result.get("content").toString().contains("super-secret"));
        assertTrue(result.get("content").toString().contains("profile=sit"));
        assertEquals(true, result.get("dataMasked"));
        assertFalse(assertThrows(IllegalArgumentException.class,
                () -> service.fileResult("private-content-not-json", 0)).getMessage().contains("private-content"));
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.fileResult("python3: command not found", 127)).getMessage().contains("python3"));
        assertThrows(IllegalArgumentException.class, () -> service.fileResult("{\"error\":\"禁止读取软链接\"}", 1));
    }

    @Test
    void drainsOutputWithoutKeepingUnboundedContent() throws Exception {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[100000]);
        assertThrows(IOException.class, () -> ServerDiagnosticService.readBoundedOutput(input, 100));
        assertEquals(0, input.available());
        assertArrayEquals(new byte[]{1, 2}, ServerDiagnosticService.readBoundedOutput(new ByteArrayInputStream(new byte[]{1, 2}), 2));
    }

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
