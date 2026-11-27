package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** 显式指定已有 CLI 才运行；真正的 ZCode 进程，模型与企业工具全部为本机夹具。 */
@EnabledIfSystemProperty(named = "workhub.test.zcode.command", matches = ".+")
class ZCodeCliLocalProtocolTest {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final ZCodeCliClient client = new ZCodeCliClient(json);
    private HttpServer server;
    private ExecutorService executor;
    private AiProviderConfigEntity provider;
    private final List<tools.jackson.databind.JsonNode> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger mcpCalls = new AtomicInteger();
    private final AtomicReference<Throwable> handlerFailure = new AtomicReference<>();
    private final List<String> events = new CopyOnWriteArrayList<>();
    private volatile boolean toolsEnabled;
    private volatile boolean attack;
    private volatile boolean hang;
    private volatile boolean formatCorrection;
    private volatile boolean nativeAgent;
    private volatile boolean nativeContinuation;
    private volatile boolean recoveryOnly;

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 10);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                var request = json.readTree(exchange.getRequestBody().readNBytes(1_000_001)); requests.add(request);
                assertEquals("glm-4.6", request.path("model").asText());
                var actualNames = new TreeSet<String>(); request.path("tools").forEach(tool -> actualNames.add(tool.path("function").path("name").asText()));
                boolean formatting = recoveryOnly || formatCorrection && requests.size() >= 3;
                if (nativeAgent) {
                    assertTrue(actualNames.containsAll(Set.of("Bash", "Write", "mcp__workhub_task__monitor_read")), actualNames::toString);
                } else {
                    assertEquals(toolsEnabled && !formatting ? Set.of("mcp__workhub_task__monitor_read") : Set.of(), actualNames);
                }
                if (hang) { try { Thread.sleep(10000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); } return; }
                Object delta; String reason;
                if (nativeAgent && requests.size() <= 3) {
                    String name = switch (requests.size()) { case 1 -> "Write"; case 2 -> "Bash"; default -> "mcp__workhub_task__monitor_read"; };
                    var arguments = switch (requests.size()) {
                        case 1 -> Map.of("file_path", directory.resolve("native-write.txt").toString(), "content", "native-cli-ok");
                        case 2 -> Map.of("command", "cat '" + directory.resolve("native-write.txt") + "'", "description", "Read fixture file");
                        default -> Map.of();
                    };
                    if (requests.size() == 3) assertTrue(request.path("messages").toString().contains("native-cli-ok"));
                    delta = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "call-native-" + requests.size(), "type", "function",
                            "function", Map.of("name", name, "arguments", json.writeValueAsString(arguments)))));
                    reason = "tool_calls";
                } else if ((toolsEnabled || attack) && requests.size() == 1) {
                    String name = attack ? "Bash" : "mcp__workhub_task__monitor_read";
                    String arguments = attack ? json.writeValueAsString(Map.of("command", "touch " + directory.resolve("forbidden-write"))) : "{}";
                    delta = Map.of("role", "assistant", "tool_calls", List.of(Map.of("index", 0, "id", "call-fixture", "type", "function", "function", Map.of("name", name, "arguments", arguments))));
                    reason = "tool_calls";
                } else {
                    if (toolsEnabled) assertTrue(request.path("messages").toString().contains("observed-from-workhub-fixture"));
                    delta = Map.of("role", "assistant", "content", formatCorrection && requests.size() == 2
                            ? "已读取 observed-from-workhub-fixture，任务完成。"
                            : "{\"type\":\"RESULT\",\"summary\":\"本地完整工具任务通过\"}"); reason = "stop";
                }
                if (nativeContinuation && requests.size() == 3) {
                    delta = Map.of("role", "assistant", "content", "{\"type\":\"RESULT\",\"summary\":\"原生续接");
                    reason = "length";
                } else if (nativeContinuation && requests.size() == 4) {
                    delta = Map.of("role", "assistant", "content", "保留全部事实\"}"); reason = "stop";
                } else if (recoveryOnly) {
                    delta = Map.of("role", "assistant", "content", json.writeValueAsString(Map.of("type", "RESULT", "summary", "原文事实".repeat(30000))));
                    reason = "stop";
                }
                if (!request.path("stream").asBoolean()) {
                    assertTrue(formatting || nativeAgent, "受限会话只有无工具格式校正使用普通文本生成请求");
                    byte[] body = json.writeValueAsBytes(Map.of("id", "fixture-format", "object", "chat.completion",
                            "model", "glm-4.6", "created", 1, "choices", List.of(Map.of("index", 0, "message", delta, "finish_reason", reason))));
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); return;
                }
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream"); exchange.sendResponseHeaders(200, 0);
                for (var chunk : List.of(Map.of("index", 0, "delta", delta), Map.of("index", 0, "delta", Map.of(), "finish_reason", reason))) {
                    String event = json.writeValueAsString(Map.of("id", "fixture", "object", "chat.completion.chunk", "model", "glm-4.6", "created", 1, "choices", List.of(chunk)));
                    exchange.getResponseBody().write(("data: " + event + "\n\n").getBytes(StandardCharsets.UTF_8));
                }
                exchange.getResponseBody().write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            } catch (Throwable ex) { handlerFailure.compareAndSet(null, ex); }
        });
        server.createContext("/mcp", exchange -> {
            try (exchange) {
                events.add("MCP " + exchange.getRequestMethod());
                assertEquals("Bearer task-fixture-token", exchange.getRequestHeaders().getFirst("Authorization"));
                if (!exchange.getRequestMethod().equals("POST")) { exchange.sendResponseHeaders(405, -1); return; }
                var request = json.readTree(exchange.getRequestBody().readNBytes(100000));
                events.add("MCP " + request.path("method").asText());
                if (!request.has("id")) { exchange.sendResponseHeaders(202, -1); return; }
                Object result = switch (request.path("method").asText()) {
                    case "initialize" -> Map.of("protocolVersion", "2025-11-25", "capabilities", Map.of("tools", Map.of()), "serverInfo", Map.of("name", "fixture", "version", "1"));
                    case "tools/list" -> Map.of("tools", List.of(tool("monitor_read"), tool("not_authorized")));
                    case "tools/call" -> {
                        assertEquals("monitor_read", request.path("params").path("name").asText()); mcpCalls.incrementAndGet();
                        yield Map.of("content", List.of(Map.of("type", "text", "text", "observed-from-workhub-fixture")));
                    }
                    case "ping" -> Map.of();
                    default -> null;
                };
                byte[] body = json.writeValueAsBytes(result == null
                        ? Map.of("jsonrpc", "2.0", "id", request.get("id"), "error", Map.of("code", -32601, "message", "Method not found"))
                        : Map.of("jsonrpc", "2.0", "id", request.get("id"), "result", result));
                exchange.getResponseHeaders().set("Content-Type", "application/json"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
            } catch (Throwable ex) { handlerFailure.compareAndSet(null, ex); }
        });
        server.start();
        provider = new AiProviderConfigEntity(); provider.setChannelType("CLI"); provider.setCliType("ZCODE");
        provider.setCliCommand(System.getProperty("workhub.test.zcode.command")); provider.setCliRuntimeCommand(System.getProperty("workhub.test.zcode.node"));
        provider.setCliBuiltinConfigPath(System.getProperty("workhub.test.zcode.builtin")); provider.setDefaultModel("fixture/glm-4.6"); provider.setDefaultReasoningLevel("disabled"); provider.setDefaultSpeedMode("STANDARD");
        Path personal = directory.resolve("provider.json");
        Files.writeString(personal, json.writeValueAsString(Map.of("schemaVersion", 1, "config", Map.of(
                "providerConfigRules", Map.of("providerRules", List.of(Map.of("providerId", "fixture", "providerName", "Local Fixture", "config", Map.of(
                        "group", "standard-personal", "access", Map.of("type", "api-key", "apiKey", "local-test-not-a-secret"),
                        "api", Map.of("type", "openai-chat-completions", "baseUrl", base() + "/v1"), "personalModelIds", List.of("glm-4.6"))))),
                "modelConfigRules", Map.of("providerModelRules", List.of(), "manualProviderModelRules", List.of()),
                "defaultModelSelection", AiCliType.selection("fixture/glm-4.6", "disabled")))));
        provider.setCliPersonalConfigPath(personal.toString());
    }
    private String base() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private Object tool(String name) { return Map.of("name", name, "description", "local fixture", "inputSchema", Map.of("type", "object", "properties", Map.of()), "annotations", Map.of("readOnlyHint", true)); }
    @AfterEach void cleanup() { if (server != null) server.stop(0); if (executor != null) executor.shutdownNow(); }

    @Test void actualZcodeUsesOnlyTaskMcpAndObservesItsResultBeforeCompleting() throws Exception {
        assertActualMcpTask();
    }
    @Test void actualAgentWritesFileRunsShellAndCallsMcp() throws Exception {
        nativeAgent = true; assertActualMcpTask();
        assertEquals("native-cli-ok", Files.readString(directory.resolve("native-write.txt")));
        assertTrue(events.stream().anyMatch(value -> value.contains("Bash")));
        assertTrue(events.stream().anyMatch(value -> value.contains("Write")));
    }
    @Test void actualZcodeCorrectsFormatWithNoToolsAndDoesNotReplayMcp() throws Exception {
        formatCorrection = true; assertActualMcpTask();
        assertTrue(events.stream().anyMatch(value -> value.contains("原生会话整理")));
        assertTrue(requests.get(2).path("stream").asBoolean(), "整理也必须走原生流式任务调度");
        assertTrue(requests.get(2).path("max_completion_tokens").asInt() > 8192, "输出额度应由 ZCode 原生模型能力决定");
    }
    @Test void actualZcodeContinuesLengthThroughNativeRuntime() throws Exception {
        nativeContinuation = true; formatCorrection = true; assertActualMcpTask();
        assertEquals("原生续接保留全部事实", json.readTree(Files.readString(directory.resolve("run/result.json"))).path("summary").asText());
    }
    @Test void actualRecoveryOnlyFormatsSavedOriginalWithoutMcpAndPreservesLargeResult() throws Exception {
        recoveryOnly = true; assertActualMcpTask();
        assertEquals("原文事实".repeat(30000), json.readTree(Files.readString(directory.resolve("run/result.json"))).path("summary").asText());
        assertTrue(requests.getFirst().path("stream").asBoolean());
    }
    private void assertActualMcpTask() throws Exception {
        toolsEnabled = !recoveryOnly; client.preflight(provider);
        Path run = Files.createDirectory(directory.resolve("run")), output = run.resolve("result.json");
        var launch = nativeAgent
                ? client.prepareAgent(provider, provider.getDefaultModel(), "disabled", run, output, "写入并读取本地样本，调用 WorkHub 工具后完成任务。", base() + "/mcp", "task-fixture-token", "{\"type\":\"object\"}")
                : client.prepare(provider, provider.getDefaultModel(), "disabled", run, output, "使用唯一工具读取本地样本后完成任务。", List.of("mcp__workhub_task__monitor_read"), base() + "/mcp", "task-fixture-token", formatCorrection ? "{\"type\":\"object\"}" : null);
        if (recoveryOnly) {
            ((ZCodeTaskConversation) launch.conversation()).analysisRecovery(new AiResultRecovery.Session("已保存的原文事实", ignored -> { }));
        }
        if (nativeAgent) {
            // 实测不加载操作者的真实账户、插件或 MCP；生产入口本身保留原生 HOME。
            Path home = Files.createDirectory(run.resolve("fixture-home"));
            launch.builder().environment().put("HOME", home.toString());
            launch.builder().environment().put("ZCODE_DATA_BASE_DIR", home.toString());
        }
        Process process = launch.builder().redirectError(run.resolve("stderr.log").toFile()).start(); var failure = new AtomicReference<Throwable>();
        var descendants = new LinkedHashMap<Long, ProcessHandle>();
        try {
            launch.conversation().start(process.getOutputStream());
            Thread reader = Thread.ofVirtual().start(() -> {
                try { ZCodeCliClient.readProtocol(process.getInputStream(), new CliTaskConversation() {
                    @Override public boolean unlimitedResultMessages() { return launch.conversation().unlimitedResultMessages(); }
                    @Override public void start(java.io.OutputStream input) throws java.io.IOException { launch.conversation().start(input); }
                    @Override public String accept(tools.jackson.databind.JsonNode event) throws java.io.IOException {
                        if (event.has("error")) System.out.println("ZCODE_LOCAL_PROTOCOL_ERROR " + event.path("error"));
                        return launch.conversation().accept(event);
                    }
                }, events::add); }
                catch (Throwable ex) { failure.set(ex); }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (process.isAlive() && failure.get() == null && handlerFailure.get() == null && System.nanoTime() < deadline) {
                process.descendants().forEach(child -> descendants.put(child.pid(), child)); process.waitFor(100, TimeUnit.MILLISECONDS);
            }
            if (failure.get() != null) throw new AssertionError("Protocol failed", failure.get());
            if (handlerFailure.get() != null) throw new AssertionError("Fixture failed: " + events, handlerFailure.get());
            assertFalse(process.isAlive(), () -> "CLI did not finish: " + events); reader.join(1000); assertFalse(reader.isAlive()); assertEquals(0, process.exitValue());
            assertNull(handlerFailure.get()); assertEquals(recoveryOnly ? 0 : 1, mcpCalls.get());
            if (nativeAgent) {
                assertTrue(requests.size() >= 4);
                assertEquals(3, events.stream().filter(value -> value.startsWith("ZCode 正在调用 ")).count());
            } else assertEquals(recoveryOnly ? 1 : nativeContinuation ? 4 : formatCorrection ? 3 : 2, requests.size());
            assertEquals("RESULT", json.readTree(Files.readString(output)).path("type").asText());
        } finally { assertTrue(ZCodeCliClient.stopTree(process, descendants)); }
    }
    @Test void actualZcodeModelOnlyTaskHasNoHostTools() throws Exception {
        String result = client.execute(provider, provider.getDefaultModel(), "disabled", "返回本地测试结果。", 20, List.of(), null, null, null, List.of());
        assertEquals("RESULT", json.readTree(result).path("type").asText()); assertNull(handlerFailure.get()); assertEquals(1, requests.size());
    }
    @Test void forgedBashCallCannotWriteToHost() throws Exception {
        attack = true;
        try { client.execute(provider, provider.getDefaultModel(), "disabled", "本地工具限制测试。", 15, List.of(), null, null, null, List.of()); }
        catch (IllegalStateException | IllegalArgumentException expected) { /* 拒绝是允许的失败结果。 */ }
        assertFalse(Files.exists(directory.resolve("forbidden-write"))); assertEquals(0, mcpCalls.get()); assertNull(handlerFailure.get()); assertFalse(requests.isEmpty());
    }
    @Test void timedOutActualZcodeIsStoppedAndDoesNotProduceSuccess() {
        hang = true;
        assertThrows(IllegalStateException.class, () -> client.execute(provider, provider.getDefaultModel(), "disabled", "超时测试。", 2, List.of(), null, null, null, List.of()));
    }
}
