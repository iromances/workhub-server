package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentScope;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 Git smart HTTP、临时远端裸仓库；不访问真实 GitLab，也不运行项目脚本。 */
class AgentGitPublishTest {
    @TempDir Path temporary;
    private HttpServer server;
    private ExecutorService workers;
    private Path source, remote, privateDirectory, gitBackend;
    private AgentGitLabClient.Target target;
    private String base, candidate;
    private final AgentCodeGit git = new AgentCodeGit();
    private final AtomicInteger authorized = new AtomicInteger();
    private volatile boolean skipCi;
    private final String branch = "codex/agent-11111111-2222-3333-4444-555555555555-r1";

    @BeforeEach void setup() throws Exception {
        temporary = temporary.toRealPath(); source = Files.createDirectory(temporary.resolve("source"));
        command(source, "init", "-b", "development"); Files.writeString(source.resolve("Main.txt"), "old\n"); command(source, "add", "."); command(source, "commit", "-m", "baseline");
        base = command(source, "rev-parse", "HEAD").trim(); Files.writeString(source.resolve("Main.txt"), "user dirty content\n");
        Path group = Files.createDirectories(temporary.resolve("remotes/group")); remote = group.resolve("repository.git");
        command(temporary, "clone", "--bare", source.toString(), remote.toString()); command(remote, "config", "http.receivepack", "true"); command(remote, "config", "receive.advertisePushOptions", "true");
        gitBackend = Path.of(command(temporary, "--exec-path").trim()).resolve("git-http-backend"); assertTrue(Files.isExecutable(gitBackend), "使用已安装 Git 的 HTTP backend");
        var repository = new AgentCodePolicy.Repository("fixture", "Fixture", new AgentScope("BL000012", "TEST"), source.toString(), "development", List.of("."), List.of("."), true);
        var policy = new AgentCodePolicy.Snapshot(new AgentCodePolicy.Policy(temporary.resolve("private-workspaces").toString(), List.of(repository)), "fixture");
        privateDirectory = git.directory(policy, UUID.randomUUID().toString()); git.prepare(privateDirectory, source, "development", base);
        candidate = git.revise(privateDirectory, base, base, "Main.txt", "new\n", "100644", "candidate", 1793736000);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); workers = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(workers);
        server.createContext("/", exchange -> { try { serve(exchange); } catch (Exception ex) { exchange.close(); } }); server.start();
        target = new AgentGitLabClient.Target(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), 1, "group/repository", "fixture-only-token");
    }
    @AfterEach void close() { if (server != null) server.stop(0); if (workers != null) workers.shutdownNow(); }

    @Test void pushesExactCommitAsNewBranchWithoutTouchingSourceOrSavingCredentials() throws Exception {
        git.publish(privateDirectory, target, branch, candidate);
        assertEquals(candidate, command(remote, "rev-parse", "refs/heads/" + branch).trim());
        assertEquals("new\n", command(remote, "show", branch + ":Main.txt")); assertTrue(authorized.get() >= 2); assertTrue(skipCi);
        assertEquals(base, command(source, "rev-parse", "HEAD").trim()); assertEquals("user dirty content\n", Files.readString(source.resolve("Main.txt")));
        assertFalse(Files.readString(privateDirectory.resolve("objects.git/config")).contains("fixture-only-token"));
    }

    @Test void retryIsIdempotentButExistingDifferentBranchCannotBeOverwritten() throws Exception {
        git.publish(privateDirectory, target, branch, candidate); git.publish(privateDirectory, target, branch, candidate);
        assertEquals(candidate, command(remote, "rev-parse", "refs/heads/" + branch).trim());
        String next = git.revise(privateDirectory, candidate, base, "Main.txt", "newer\n", "100644", "next", 1793736001);
        assertThrows(IllegalArgumentException.class, () -> git.publish(privateDirectory, target, branch, next));
        assertEquals(candidate, command(remote, "rev-parse", "refs/heads/" + branch).trim());
        assertThrows(IllegalArgumentException.class, () -> git.publish(privateDirectory, target, "development", candidate));
        assertEquals(base, command(remote, "rev-parse", "refs/heads/development").trim());
    }

    private void serve(HttpExchange exchange) throws Exception {
        String expected = "Basic " + Base64.getEncoder().encodeToString("oauth2:fixture-only-token".getBytes(StandardCharsets.UTF_8));
        if (!expected.equals(exchange.getRequestHeaders().getFirst("Authorization"))) { exchange.sendResponseHeaders(401, -1); exchange.close(); return; }
        authorized.incrementAndGet(); byte[] input = exchange.getRequestBody().readAllBytes(); skipCi |= new String(input, StandardCharsets.ISO_8859_1).contains("ci.skip");
        var builder = new ProcessBuilder(gitBackend.toString()).directory(temporary.toFile()).redirectError(temporary.resolve("http-backend-error.log").toFile());
        builder.environment().clear(); builder.environment().putAll(Map.of("PATH", "/usr/bin:/bin", "GIT_PROJECT_ROOT", temporary.resolve("remotes").toString(), "GIT_HTTP_EXPORT_ALL", "1",
                "REQUEST_METHOD", exchange.getRequestMethod(), "PATH_INFO", exchange.getRequestURI().getPath(), "QUERY_STRING", Objects.toString(exchange.getRequestURI().getRawQuery(), ""),
                "CONTENT_TYPE", Objects.toString(exchange.getRequestHeaders().getFirst("Content-Type"), ""), "CONTENT_LENGTH", String.valueOf(input.length), "REMOTE_USER", "fixture"));
        Process process = builder.start(); try (var stream = process.getOutputStream()) { stream.write(input); }
        byte[] output = process.getInputStream().readAllBytes(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue());
        String raw = new String(output, StandardCharsets.ISO_8859_1); int delimiter = raw.indexOf("\r\n\r\n"); assertTrue(delimiter >= 0);
        int status = 200;
        for (String header : raw.substring(0, delimiter).split("\r\n")) {
            int colon = header.indexOf(':'); if (colon < 0) continue;
            if (header.startsWith("Status:")) status = Integer.parseInt(header.substring(colon + 1).trim().split(" ")[0]);
            else exchange.getResponseHeaders().add(header.substring(0, colon), header.substring(colon + 1).trim());
        }
        byte[] payload = Arrays.copyOfRange(output, delimiter + 4, output.length); exchange.sendResponseHeaders(status, payload.length);
        try (var stream = exchange.getResponseBody()) { stream.write(payload); } finally { exchange.close(); }
    }
    private String command(Path directory, String... arguments) throws Exception {
        var args = new ArrayList<>(List.of("git", "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false", "-c", "user.name=Fixture", "-c", "user.email=fixture@example.invalid")); args.addAll(List.of(arguments));
        var builder = new ProcessBuilder(args).directory(directory.toFile()); builder.environment().put("GIT_CONFIG_GLOBAL", "/dev/null"); builder.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        var process = builder.start(); String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8); String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(10, TimeUnit.SECONDS)); assertEquals(0, process.exitValue(), error); return result;
    }
}
