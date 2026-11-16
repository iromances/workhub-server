package cn.aslight.workhub.mcp.security;

import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ServerFilePolicyTest {
    @TempDir
    Path temporary;
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpResourceCatalog.ServerTarget.ServerProfile profile =
            new McpResourceCatalog.ServerTarget.ServerProfile("diagnostic", 20, 5);

    @Test
    void deniesMissingRootsTraversalSiblingAndInvalidLimits() throws Exception {
        String root = temporary.toRealPath().toString();
        assertThrows(IllegalArgumentException.class, () -> ServerFilePolicy.buildCommand(target(List.of()), profile,
                "read_server_file", Map.of("path", root + "/start.sh")));
        for (String path : List.of("relative.sh", root + "/../outside.sh", root + "-backup/start.sh", root + "/bad\nfile")) {
            assertThrows(IllegalArgumentException.class, () -> command("read_server_file", Map.of("path", path)));
        }
        assertThrows(IllegalArgumentException.class, () -> ServerFilePolicy.normalizeRoot("/"));
        assertThrows(IllegalArgumentException.class, () -> command("read_server_file", Map.of("path", root, "lines", "0")));
        assertThrows(IllegalArgumentException.class, () -> command("list_server_directory", Map.of("path", root, "maxDepth", "invalid")));
        var capped = command("list_server_directory", Map.of("path", root, "maxDepth", "100", "maxEntries", "100000"));
        JsonNode options = mapper.readTree(capped.getLast());
        assertEquals(3, options.path("maxDepth").asInt());
        assertEquals(20, options.path("maxEntries").asInt());
    }

    @Test
    void mntIsTheDefaultButOtherDirectoriesNeedExplicitAllowlistAuthorization() {
        var target = target(null);
        assertEquals(List.of("/mnt"), target.allowedLogPaths());
        assertDoesNotThrow(() -> ServerFilePolicy.buildCommand(target, profile, "read_server_file", Map.of("path", "/mnt/etc-server/start.sh")));
        assertThrows(IllegalArgumentException.class, () -> ServerFilePolicy.buildCommand(target, profile,
                "read_server_file", Map.of("path", "/mnt-backup/start.sh")));
        assertThrows(IllegalArgumentException.class, () -> ServerFilePolicy.buildCommand(target, profile,
                "read_server_file", Map.of("path", "/data/start.sh")));
        assertDoesNotThrow(() -> ServerFilePolicy.buildCommand(target(List.of("/mnt", "/data")), profile,
                "read_server_file", Map.of("path", "/data/start.sh")));
    }

    @Test
    void readsScriptAsDataWithoutExecutingAndSupportsQuotedNames() throws Exception {
        Path marker = temporary.resolve("executed");
        Path script = temporary.resolve("start ' quoted $(touch executed).sh");
        String source = "touch " + marker + "\njava -jar app.jar --spring.profiles.active=sit\n";
        Files.writeString(script, source);
        JsonNode response = run("read_server_file", Map.of("path", script.toRealPath().toString()));
        assertEquals(source, response.path("content").asText());
        assertFalse(Files.exists(marker));
        assertFalse(response.path("truncated").asBoolean());
    }

    @Test
    void listsMetadataWithBoundedDepthAndEntriesWithoutFollowingLinks() throws Exception {
        Path root = temporary.toRealPath();
        Files.writeString(root.resolve("app.sh"), "profile=sit\n");
        Path nested = Files.createDirectories(root.resolve("child/grandchild"));
        Files.writeString(nested.resolve("hidden.sh"), "profile=prod\n");
        Files.createSymbolicLink(root.resolve("outside"), root.getParent());
        JsonNode response = run("list_server_directory", Map.of("path", root.toString(), "maxDepth", "1"));
        assertEquals(3, response.path("entries").size());
        assertTrue(response.path("truncated").asBoolean());
        assertFalse(response.toString().contains("hidden.sh"));
        assertTrue(response.path("entries").findValuesAsText("type").contains("symlink"));
        assertEquals(1, run("list_server_directory", Map.of("path", root.toString(), "maxEntries", "1")).path("entries").size());
    }

    @Test
    void rejectsLeafAndIntermediateLinksEvenIfDestinationIsInsideRoot() throws Exception {
        Path root = temporary.toRealPath();
        Path inside = Files.createDirectory(root.resolve("inside"));
        Files.writeString(inside.resolve("start.sh"), "sit");
        Files.createSymbolicLink(root.resolve("linked-directory"), inside);
        Files.createSymbolicLink(root.resolve("linked.sh"), inside.resolve("start.sh"));
        for (String suffix : List.of("/linked.sh", "/linked-directory/start.sh")) {
            assertTrue(run("read_server_file", Map.of("path", root + suffix)).has("error"));
        }
        Files.createSymbolicLink(root.resolve("outside"), root.getParent());
        assertTrue(run("list_server_directory", Map.of("path", root.resolve("outside").toString())).has("error"));
    }

    @Test
    void rejectsBinaryInvalidUtf8DirectoriesFifosAndCredentials() throws Exception {
        Path root = temporary.toRealPath();
        Files.write(root.resolve("binary"), new byte[]{0, 1, 2});
        Files.write(root.resolve("bad-utf8"), new byte[]{(byte) 0xff});
        Files.writeString(root.resolve("private.pem"), "private-key-material");
        Files.createDirectory(root.resolve(".ssh"));
        Path fifo = root.resolve("fifo");
        Process createFifo = new ProcessBuilder("python3", "-I", "-S", "-c", "import os,sys; os.mkfifo(sys.argv[1])", fifo.toString()).start();
        assertTrue(createFifo.waitFor(5, TimeUnit.SECONDS));
        assertEquals(0, createFifo.exitValue());
        for (String suffix : List.of("/binary", "/bad-utf8", "/private.pem", "/fifo", "/.ssh")) {
            assertTrue(run("read_server_file", Map.of("path", root + suffix)).has("error"), suffix);
        }
    }

    @Test
    void capsBytesLinesAndHandlesPartialMultibyteCharacter() throws Exception {
        Path file = temporary.resolve("large.sh");
        Files.writeString(file, "sit\n中文\nthird\n");
        JsonNode bytes = run("read_server_file", Map.of("path", file.toRealPath().toString(), "maxBytes", "5"));
        assertEquals("sit\n", bytes.path("content").asText());
        assertTrue(bytes.path("truncated").asBoolean());
        JsonNode lines = run("read_server_file", Map.of("path", file.toRealPath().toString(), "lines", "1"));
        assertEquals("sit\n", lines.path("content").asText());
        assertTrue(lines.path("truncated").asBoolean());
    }

    private List<String> command(String operation, Map<String, String> parameters) throws Exception {
        return ServerFilePolicy.buildCommand(target(List.of(temporary.toRealPath().toString())), profile, operation, parameters);
    }

    private JsonNode run(String operation, Map<String, String> parameters) throws Exception {
        Process process = new ProcessBuilder(command(operation, parameters)).redirectErrorStream(true).start();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "固定读取程序应有界完成");
        return mapper.readTree(process.getInputStream().readAllBytes());
    }

    private McpResourceCatalog.ServerTarget target(List<String> roots) {
        return new McpResourceCatalog.ServerTarget("test", false, List.of(), "BL000006", List.of("BL000006"),
                "TEST", "物流测试", null, List.of(), "127.0.0.1", 22, "test", null, null, null,
                List.of(), roots, List.of(profile));
    }
}
