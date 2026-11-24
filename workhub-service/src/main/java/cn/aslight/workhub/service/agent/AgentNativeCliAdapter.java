package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.service.ai.AiPromptTemplateRenderer;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import cn.aslight.workhub.service.ai.AiUseCaseConfigService;
import cn.aslight.workhub.service.ai.AiUseCaseDefinitions;
import cn.aslight.workhub.service.ai.ZCodeCliClient;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** CLI 接收完整任务并原生规划，经平台标准 MCP 使用 WorkHub 能力（AI 按职责描述自行选择）。平台只记录事实和生命周期。 */
@Component
public class AgentNativeCliAdapter implements AgentExecutionAdapter {
    private static final String RESULT_SCHEMA = AgentReportContract.SCHEMA;
    private final AgentRunStore store;
    private final AgentToolData data;
    private final AiProviderConfigService providers;
    private final AiUseCaseConfigService aiUseCaseConfigService;
    private final AiPromptTemplateRenderer promptRenderer;
    private final AgentPlatformGuide platformGuide;
    private final List<AgentCliProtocol> protocols;
    private final ObjectMapper json;
    private final McpProperties mcpProperties;
    private final String environmentAccessToken;
    private final String serverPort;
    private final String cliMcpEndpoint;
    private final AgentNativeDeliveryService nativeDelivery;

    public AgentNativeCliAdapter(AgentRunStore store, AgentToolData data,
                                  AiProviderConfigService providers, AiUseCaseConfigService aiUseCaseConfigService,
                                  AiPromptTemplateRenderer promptRenderer, AgentPlatformGuide platformGuide,
                                  List<AgentCliProtocol> protocols, ObjectMapper json,
                                  McpProperties mcpProperties,
                                  @Value("${WORKHUB_MCP_ACCESS_TOKEN:}") String environmentAccessToken,
                                  @Value("${server.port:8080}") String serverPort,
                                  @Value("${workhub.agent.cli-mcp-endpoint:}") String cliMcpEndpoint,
                                  AgentNativeDeliveryService nativeDelivery) {
        this.store = store;
        this.data = data;
        this.providers = providers;
        this.aiUseCaseConfigService = aiUseCaseConfigService;
        this.promptRenderer = promptRenderer;
        this.platformGuide = platformGuide;
        this.protocols = List.copyOf(protocols);
        this.json = json;
        this.mcpProperties = mcpProperties;
        this.environmentAccessToken = environmentAccessToken;
        this.serverPort = serverPort;
        this.cliMcpEndpoint = cliMcpEndpoint;
        this.nativeDelivery = nativeDelivery;
    }

    private String mcpAccessToken() {
        return StringUtils.hasText(environmentAccessToken) ? environmentAccessToken : mcpProperties.getAccessToken();
    }

    private String mcpUrl() {
        if (!StringUtils.hasText(mcpAccessToken())) return null;
        return StringUtils.hasText(cliMcpEndpoint) ? cliMcpEndpoint
                : "http://127.0.0.1:" + serverPort + "/api/mcp/runtime";
    }

    @Override public String channelType() { return "CLI"; }

    @Override
    public void execute(String runId, String owner) {
        store.requireActive(runId, owner);
        if (store.finishSavedNative(runId, owner)) return;
        var provider = store.requireProvider(store.get(runId));
        if (!"CLI".equals(provider.getChannelType())) throw new IllegalArgumentException("原生 CLI 适配器不能执行 API 接入");
        var matches = protocols.stream().filter(protocol -> protocol.supports(provider)).toList();
        if (matches.size() != 1) {
            store.finish(runId, owner, "WAITING_RESOURCE", null, "所选 CLI 尚无唯一匹配的原生协议适配器；未转给其他 AI 或套用其他 CLI 参数");
            return;
        }
        AgentCliProtocol protocol = matches.getFirst();
        try { protocol.preflight(provider); }
        catch (IOException | IllegalArgumentException ex) {
            store.finish(runId, owner, "WAITING_RESOURCE", null, ex.getMessage());
            return;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AgentRunStore.StopRequested("CLI 预检已中断");
        }
        while (true) {
            store.requireActive(runId, owner);
            if (store.finishSavedNative(runId, owner)) return;
            String attempt = "cli:" + UUID.randomUUID();
            Path directory = null;
            Process process = null;
            var descendants = new LinkedHashMap<Long, ProcessHandle>();
            boolean intentSaved = false;
            boolean processConfirmedStopped = false;
            boolean replanRequested = false;
            try {
                directory = Files.createTempDirectory("workhub-agent-");
                Path schema = directory.resolve("result-schema.json");
                Path output = directory.resolve("result.json");
                Files.writeString(schema, RESULT_SCHEMA, StandardCharsets.UTF_8);
                var context = store.context(store.get(runId));
                var promptEvents = store.events(runId);
                String recoveredResult = store.recoverableNativeResult(runId);
                String prompt;
                try {
                    prompt = recoveredResult == null ? prompt(context, promptEvents) : "仅恢复已保存结果";
                } catch (RuntimeException ex) {
                    String reason = ex.getMessage() == null || ex.getMessage().isBlank() ? ex.getClass().getSimpleName() : ex.getMessage();
                    store.finish(runId, owner, "FAILED", null, "AI 任务配置不可用 " + AiUseCaseDefinitions.AGENT_CLI_STEP + "：" + reason);
                    return;
                }
                if (prompt.length() > 180_000) {
                    store.finish(runId, owner, "WAITING_INPUT", null, "CLI 任务上下文超过上限，请拆分任务；已有证据保留");
                    return;
                }
                String accessToken = mcpAccessToken();
                String mcpUrl = mcpUrl();
                var launch = protocol.prepare(provider, context.configuration().model(), directory, schema, output, mcpUrl,
                        StringUtils.hasText(accessToken) ? accessToken : null, prompt);
                launch.conversation().resultRecovery(recoveredResult, original ->
                        store.record(runId, owner, attempt + ":raw", "CLI_RAW_RESULT", Map.of("text", original, "inputThroughOrdinal", promptEvents.isEmpty() ? 0L : promptEvents.getLast().ordinal())));
                store.beginNative(runId, owner, attempt, InetAddress.getLocalHost().getHostName(), promptEvents.isEmpty() ? 0L : promptEvents.getLast().ordinal());
                intentSaved = true;
                process = launch.builder().start();
                String startedAt = process.info().startInstant().map(Instant::toString).orElseThrow(() -> new IllegalStateException("无法取得 CLI 进程启动身份"));
                store.nativeStarted(runId, owner, attempt, process.pid(), startedAt);
                var readerFailure = new AtomicReference<Exception>();
                var outputSequence = new AtomicInteger();
                Process observed = process;
                Thread writer = Thread.ofVirtual().start(() -> {
                    try { launch.conversation().start(observed.getOutputStream()); }
                    catch (Exception ex) { readerFailure.compareAndSet(null, ex); }
                });
                Thread reader = Thread.ofVirtual().start(() -> {
                    try { ZCodeCliClient.readProtocol(observed.getInputStream(), launch.conversation(),
                            message -> progress(runId, owner, attempt, message, outputSequence)); }
                    catch (Exception ex) { readerFailure.set(ex); }
                });
                long remaining = Math.max(1, context.configuration().maxRunSeconds() * 1000L - store.get(runId).elapsedMillis());
                if (provider.getCallTimeoutSeconds() != null) remaining = Math.min(remaining, provider.getCallTimeoutSeconds() * 1000L);
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(remaining);
                while (process.isAlive()) {
                    process.descendants().forEach(child -> descendants.put(child.pid(), child));
                    store.requireActive(runId, owner);
                    if (store.hasNewInputSince(runId, attempt + ":request")) throw new AgentRunStore.InputChanged("已收到新指令，停止旧 CLI 并保留工具回执");
                    if (readerFailure.get() != null) throw new IllegalStateException("CLI 协议执行失败，运行已停止并保留已有轨迹：" + readerFailure.get().getMessage(), readerFailure.get());
                    if (System.nanoTime() >= deadline) throw new AgentRunStore.BudgetExceeded("CLI 运行时间预算已耗尽");
                    process.waitFor(200, TimeUnit.MILLISECONDS);
                }
                processConfirmedStopped = stopTree(process, descendants);
                reader.join(1000); writer.join(1000);
                if (!processConfirmedStopped || reader.isAlive() || writer.isAlive()) throw new IllegalStateException("CLI 的停止结果尚未确认");
                if (readerFailure.get() != null) throw new IllegalStateException("CLI 未返回有效协议结果：" + readerFailure.get().getMessage(), readerFailure.get());
                if (process.exitValue() != 0) throw new IllegalStateException("CLI 执行失败，退出码 " + process.exitValue());
                if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) throw new IllegalStateException("CLI 未返回最终产物");
                JsonNode result;
                try {
                    String original = Files.readString(output);
                    if (store.events(runId).stream().noneMatch(e -> (attempt + ":raw").equals(e.eventKey())))
                        store.record(runId, owner, attempt + ":file-raw", "CLI_RAW_RESULT", Map.of("text", original, "inputThroughOrdinal", promptEvents.isEmpty() ? 0L : promptEvents.getLast().ordinal()));
                    result = AgentReportContract.extract(original);
                    AgentReportContract.validate(result);
                }
                catch (tools.jackson.core.JacksonException ex) { throw new IllegalArgumentException("CLI 最终结果不是有效 JSON；未确认任务完成，请查看执行记录", ex); }
                if (!result.isObject() || result.size() > 4 || !Set.of("RESULT", "NEEDS_INPUT").contains(result.path("type").asText())
                        || AgentReportContract.summary(result).isBlank()) {
                    throw new IllegalArgumentException("CLI 最终结果格式无效");
                }
                if ("RESULT".equals(result.path("type").asText()) && result.has("deliveries")) {
                    store.record(runId, owner, attempt + ":delivery", "NATIVE_CODE_DELIVERY", nativeDelivery.verify(context, result.path("deliveries")));
                }
                store.endNative(runId, owner, attempt, Map.of("exitCode", 0, "result", result, "processTreeStopped", true));
                intentSaved = false;
                if (store.finishSavedNative(runId, owner)) return;
            } catch (AgentRunStore.InputChanged ex) {
                replanRequested = true;
                // finally 确认旧进程停止；WorkHub MCP 调用都在 CLI 进程树内，进程树停止即无在途企业调用。
                continue;
            } catch (IOException ex) {
                throw new IllegalStateException("CLI 运行资源或输入输出不可用", ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AgentRunStore.StopRequested("CLI 执行已中断");
            } finally {
                boolean interrupted = Thread.interrupted();
                if (process != null && !processConfirmedStopped) processConfirmedStopped = stopTree(process, descendants);
                if (intentSaved && (process == null || processConfirmedStopped)) {
                    try { store.endNative(runId, owner, attempt, Map.of("processTreeStopped", true, "started", process != null, "resultAvailable", false, "replanRequested", replanRequested)); }
                    catch (RuntimeException ignored) { /* 失租约时保留原句柄，由对账处理，不能改写新所有者状态。 */ }
                }
                if (process == null || processConfirmedStopped) deletePrivateDirectory(directory);
                if (interrupted) Thread.currentThread().interrupt();
            }
        }
    }

    private String prompt(AgentToolContext context, List<cn.aslight.workhub.model.agent.AgentEventEntity> events) {
        var config = aiUseCaseConfigService.requireEnabledByCode(AiUseCaseDefinitions.AGENT_CLI_STEP);
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("agentInstructions", json.writeValueAsString(data.sanitize(context.configuration().instructions())));
        variables.put("taskSpecification", json.writeValueAsString(data.sanitize(context.specification())));
        variables.put("dataWindow", json.writeValueAsString(data.sanitize(store.dataWindow(context))));
        variables.put("platformGuide", platformGuide.guide(context));
        variables.put("trajectory", json.writeValueAsString(events.stream()
                .filter(event -> !Set.of("CLI_RAW_RESULT", "CLI_FORMAT_RECOVERY").contains(event.kind()))
                .map(event -> Map.of("kind", event.kind(), "payload", json.readTree(event.payloadJson()))).toList()));
        return promptRenderer.render(config.getPromptTemplate(), variables, AiUseCaseDefinitions.require(AiUseCaseDefinitions.AGENT_CLI_STEP))
                + "\n" + AgentReportContract.PRESENTATION_GUIDE
                + "\n如任务要求交付需求研发分支，最终 JSON 必须带 deliveries 数组，逐个列出全部已确认系统的 GitLab projectPath、branch、完整40位 commit；"
                + "平台会独立回读远端核验，不接受仅在 summary 中声称已推送。自测结果与尚未执行的联调分别如实写入 report.checks 和 report.limitations。";
    }

    private void progress(String runId, String owner, String attempt, String message, AtomicInteger sequence) {
        if (sequence.get() >= 100) return;
        if (store.events(runId).stream().filter(event -> "CLI_PROGRESS".equals(event.kind())).count() >= 100) return;
        store.record(runId, owner, attempt + ":progress:" + sequence.incrementAndGet(), "CLI_PROGRESS", Map.of("message", message));
    }

    static boolean stopTree(Process process, Map<Long, ProcessHandle> descendants) {
        process.descendants().forEach(child -> descendants.put(child.pid(), child));
        descendants.values().forEach(child -> { if (child.isAlive()) child.destroy(); });
        if (process.isAlive()) process.destroy();
        long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        do {
            descendants.values().forEach(child -> { if (child.isAlive()) child.destroyForcibly(); });
            if (process.isAlive()) process.destroyForcibly();
            if (!process.isAlive() && descendants.values().stream().noneMatch(ProcessHandle::isAlive)) return true;
            try { Thread.sleep(20); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); return false; }
        } while (System.nanoTime() < until);
        return !process.isAlive() && descendants.values().stream().noneMatch(ProcessHandle::isAlive);
    }

    private static void deletePrivateDirectory(Path directory) {
        if (directory == null) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (IOException ignored) { } });
        } catch (IOException ignored) { }
    }
}
