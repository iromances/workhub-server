package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.service.runtime.PrivateFiles;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.service.intake.CodexCliClient;
import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 统一 AI 网关默认实现。
 */
@Service
public class DefaultAiGatewayClient implements AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGatewayClient.class);
    private static final String PROTOCOL_RESPONSES = "OPENAI_RESPONSES";
    private static final String PROTOCOL_OPENROUTER = "OPENROUTER";
    private static final String PROTOCOL_OPENAI_COMPATIBLE = "OPENAI_COMPATIBLE";
    private static final String PROTOCOL_CUSTOM = "CUSTOM";
    private static final String PROBE_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "status": { "type": "string", "enum": ["OK"] }
              },
              "required": ["status"],
              "additionalProperties": false
            }
            """;

    private final AiUseCaseConfigService aiUseCaseConfigService;
    private final AiProviderConfigService aiProviderConfigService;
    private final CodexCliClient codexCliClient;
    private final ZCodeCliClient zcodeCliClient;
    private final OpenAiResponsesClient responsesClient;
    private final OpenAiChatClient chatClient;
    private final AiPromptTemplateRenderer promptTemplateRenderer;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<McpRuntimeService> mcpRuntimeProvider;
    private final McpProperties mcpProperties;
    private final String environmentAccessToken;
    private final String serverPort;
    private final String cliMcpEndpoint;

    public DefaultAiGatewayClient(AiUseCaseConfigService aiUseCaseConfigService,
                                  AiProviderConfigService aiProviderConfigService,
                                  CodexCliClient codexCliClient,
                                  ZCodeCliClient zcodeCliClient,
                                  OpenAiResponsesClient responsesClient,
                                  OpenAiChatClient chatClient,
                                  AiPromptTemplateRenderer promptTemplateRenderer,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<McpRuntimeService> mcpRuntimeProvider,
                                  McpProperties mcpProperties,
                                  @Value("${WORKHUB_MCP_ACCESS_TOKEN:}") String environmentAccessToken,
                                  @Value("${server.port:8080}") String serverPort,
                                  @Value("${workhub.agent.cli-mcp-endpoint:}") String cliMcpEndpoint) {
        this.aiUseCaseConfigService = aiUseCaseConfigService;
        this.aiProviderConfigService = aiProviderConfigService;
        this.codexCliClient = codexCliClient;
        this.zcodeCliClient = zcodeCliClient;
        this.responsesClient = responsesClient;
        this.chatClient = chatClient;
        this.promptTemplateRenderer = promptTemplateRenderer;
        this.objectMapper = objectMapper;
        this.mcpRuntimeProvider = mcpRuntimeProvider;
        this.mcpProperties = mcpProperties;
        this.environmentAccessToken = environmentAccessToken;
        this.serverPort = serverPort;
        this.cliMcpEndpoint = cliMcpEndpoint;
    }

    /** 本地仓库读取场景必须配置 MCP 访问令牌；不静默降级为无工具分析。 */
    private String repositoryMcpUrl() {
        if (!StringUtils.hasText(mcpAccessToken())) throw new IllegalArgumentException("ZCode 通道本地仓库读取需配置 workhub.mcp.access-token");
        return StringUtils.hasText(cliMcpEndpoint) ? cliMcpEndpoint : "http://127.0.0.1:" + serverPort + "/api/mcp/runtime";
    }

    private String mcpAccessToken() {
        return StringUtils.hasText(environmentAccessToken) ? environmentAccessToken : mcpProperties.getAccessToken();
    }

    private List<String> repositoryToolNames() {
        McpRuntimeService runtime = mcpRuntimeProvider.getIfAvailable();
        if (runtime == null) throw new IllegalArgumentException("MCP 运行时未就绪，无法执行 ZCode 本地仓库读取");
        return runtime.toolNames();
    }

    private static String repositoryHint(AiGatewayRequest request) {
        StringBuilder hint = new StringBuilder("\n\n代码读取约束：本场景只能通过 workhub_task MCP 的只读仓库工具查看代码（工具名带 mcp__workhub_task__ 前缀：mcp__workhub_task__list_repository_files / mcp__workhub_task__read_repository_text_file / mcp__workhub_task__search_repository_text）；");
        if (request.workingDirectory() != null) hint.append("仓库根目录：").append(request.workingDirectory()).append("。");
        if (request.allowedDirectories() != null && !request.allowedDirectories().isEmpty()) hint.append("授权目录：").append(String.join("、", request.allowedDirectories())).append("。");
        return hint.toString();
    }

    /** 仓库读取场景的提示词：schema 以终稿格式说明给出，不触发 ZCode CLI 结构化输出模式（该模式阻断工具调用）。 */
    private static String buildZcodePrompt(String prompt, String outputSchema, boolean repository, AiGatewayRequest request) {
        String base = prompt + (repository ? repositoryHint(request) : "");
        if (outputSchema == null) {
            return base;
        }
        if (repository) {
            return base + "\n\n先调查后作答：必须先用上述工具完成代码检索，再基于真实代码结论作答；调查完成前不要输出 JSON。"
                    + "\n最终答复只输出一个符合此 schema 的 JSON 对象（不要代码围栏）：\n" + outputSchema;
        }
        return base + "\n仅输出符合此 schema 的 JSON：\n" + outputSchema;
    }

    @Override
    public boolean isConfigured(String useCaseCode) {
        try {
            AiUseCaseDefinitions.Definition definition = AiUseCaseDefinitions.require(useCaseCode);
            AiUseCaseConfigEntity useCase = aiUseCaseConfigService.requireEnabledByCode(useCaseCode);
            AiProviderConfigEntity provider = aiProviderConfigService.requireExisting(useCase.getProviderConfigId());
            definition.requireChannel(provider.getChannelType());
            if (AiCliType.is(provider, "ZCODE") && definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ) {
                if (!StringUtils.hasText(mcpAccessToken())) return false;
                McpRuntimeService runtime = mcpRuntimeProvider.getIfAvailable();
                if (runtime == null || !runtime.toolNames().contains("list_repository_files")) return false;
            }
            requireText(firstText(useCase.getModel(), provider.getDefaultModel()), "AI 场景模型未配置");
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                return false;
            }
            return !"CLI".equals(normalize(provider.getChannelType())) || AiCliType.is(provider, "ZCODE") || (AiCliType.is(provider, "CODEX") && codexCliClient.isEnabled());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public AiGatewayResult execute(AiGatewayRequest request) {
        return executeInternal(request, false);
    }

    @Override
    public AiGatewayResult executeStructured(AiGatewayRequest request) {
        return executeInternal(request, true);
    }

    @Override
    public AiGatewayResult executeAgentApi(AiAgentApiRequest request) {
        long startedAt = System.nanoTime();
        String providerCode = null;
        String model = null;
        try {
            if (request == null || request.providerConfigId() == null || request.timeoutSeconds() < 1
                    || request.timeoutSeconds() > 14_400) {
                throw new IllegalArgumentException("Agent API 请求缺少有效接入或超时预算");
            }
            AiProviderConfigEntity provider = aiProviderConfigService.getEnabledById(request.providerConfigId());
            providerCode = provider.getProviderCode();
            if (request.expectedProviderVersion() != (provider.getVersion() == null ? 0 : provider.getVersion())
                    || !java.util.Objects.equals(request.expectedApiProtocol(), provider.getApiProtocol())) {
                throw new IllegalStateException("AI 接入配置与 Agent 运行快照不符");
            }
            if (!"API".equals(normalize(provider.getChannelType()))) {
                throw new IllegalArgumentException("此执行器仅处理 API；CLI 必须使用其任务执行适配器");
            }
            model = requireText(request.model(), "Agent 运行快照未记录模型");
            String output = executeApi(new AiApiInvocation("agent.task.step", provider,
                    aiProviderConfigService.resolveApiKey(provider.getId()), model,
                    provider.getDefaultReasoningLevel(), provider.getDefaultSpeedMode(), request.timeoutSeconds(),
                    requireText(request.prompt(), "Agent 任务上下文为空"), List.of(), null));
            return AiGatewayResult.succeeded(output, providerCode, model, elapsedMillis(startedAt));
        } catch (Exception ex) {
            log.warn("Agent API invocation failed. providerCode={}, model={}, errorType={}", providerCode, model, ex.getClass().getSimpleName());
            return AiGatewayResult.failed("Agent API 调用失败: " + summarizeException(ex), providerCode, model, elapsedMillis(startedAt));
        }
    }

    @Override
    public AiGatewayResult probe(Long providerId, String model) {
        long startedAt = System.nanoTime();
        AiProviderConfigEntity provider = null;
        try {
            provider = aiProviderConfigService.requireExisting(providerId);
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                throw new IllegalStateException("AI 接入配置已停用");
            }
            if (!"API".equals(normalize(provider.getChannelType()))) {
                throw new IllegalStateException("连接探针当前只支持 API 通道");
            }
            String output = executeApi(new AiApiInvocation(
                    AiUseCaseDefinitions.PROVIDER_SMOKE,
                    provider,
                    aiProviderConfigService.resolveApiKey(provider.getId()),
                    requireText(model, "探针模型不能为空"),
                    null,
                    null,
                    provider.getCallTimeoutSeconds(),
                    "这是供应商连接探针，不包含业务数据。请严格返回 JSON：{\"status\":\"OK\"}。",
                    List.of(),
                    PROBE_SCHEMA
            ));
            requireExpectedProbeOutput(output);
            return AiGatewayResult.succeeded(output, provider.getProviderCode(), model, elapsedMillis(startedAt));
        } catch (Exception ex) {
            return AiGatewayResult.failed(
                    "AI 供应商探针失败: " + summarizeException(ex),
                    provider == null ? null : provider.getProviderCode(),
                    model,
                    elapsedMillis(startedAt)
            );
        }
    }

    @Override
    public AiGatewayResult testConnection(AiProviderConfigEntity provider, String apiKey) {
        long startedAt = System.nanoTime();
        String model = null;
        try {
            if (provider == null) {
                throw new IllegalArgumentException("AI 接入配置不能为空");
            }
            model = requireText(provider.getDefaultModel(), "通道默认模型不能为空");
            if ("CLI".equals(normalize(provider.getChannelType()))) model = provider.getDefaultModel();
            String output;
            if ("API".equals(normalize(provider.getChannelType()))) {
                output = executeApi(new AiApiInvocation(
                        AiUseCaseDefinitions.PROVIDER_SMOKE,
                        provider,
                        requireText(apiKey, "AI Provider API Key 不能为空"),
                        model,
                        provider.getDefaultReasoningLevel(),
                        provider.getDefaultSpeedMode(),
                        provider.getCallTimeoutSeconds(),
                        "这是 AI 接入配置连通性测试，不包含业务数据。请严格返回 JSON：{\"status\":\"OK\"}。",
                        List.of(),
                        PROBE_SCHEMA
                ));
            } else if ("CLI".equals(normalize(provider.getChannelType()))) {
                output = executeConnectionTestCli(provider, model);
            } else {
                throw new IllegalStateException("连接测试只支持 API 或 CLI 通道");
            }
            requireExpectedProbeOutput(output);
            return AiGatewayResult.succeeded(output, provider.getProviderCode(), model, elapsedMillis(startedAt));
        } catch (Exception ex) {
            return AiGatewayResult.failed(
                    "AI 接入配置连通测试失败: " + summarizeException(ex),
                    provider == null ? null : provider.getProviderCode(),
                    model,
                    elapsedMillis(startedAt)
            );
        }
    }

    private String executeConnectionTestCli(AiProviderConfigEntity provider, String model) throws Exception {
        if (AiCliType.is(provider, "ZCODE")) {
            AiCliType.validate(provider);
            return zcodeCliClient.execute(provider, model, provider.getDefaultReasoningLevel(),
                    "这是不含业务数据的连接测试。严格只返回 JSON：{\"status\":\"OK\"}。", provider.getCallTimeoutSeconds(), List.of(), null, null, null, List.of());
        }
        if (!AiCliType.is(provider, "CODEX")) throw new IllegalArgumentException("所选 CLI 没有对应适配器");
        if (!codexCliClient.isEnabled()) {
            throw new IllegalStateException("Codex CLI 未启用");
        }
        Path workingDirectory = resolveConnectionTestWorkingDirectory(provider.getCliWorkingDirectory());
        CodexCliClient.CodexCliResult result = codexCliClient.execute(
                new CodexCliClient.CodexCliRequest(
                        AiUseCaseDefinitions.PROVIDER_SMOKE,
                        workingDirectory,
                        List.of(),
                        List.of(),
                        PROBE_SCHEMA,
                        "这是 AI 接入配置连通性测试，不包含业务数据。请严格返回 JSON：{\"status\":\"OK\"}。"
                ),
                new CodexCliClient.CodexCliExecutionOptions(
                        provider.getCliCommand(),
                        model,
                        normalizeCliReasoning(provider.getDefaultReasoningLevel()),
                        provider.getCallTimeoutSeconds(),
                        true,
                        false
                )
        );
        if (!result.succeeded()) {
            throw new IllegalStateException(result.failureSummary());
        }
        return result.outputJson();
    }

    private Path resolveConnectionTestWorkingDirectory(String configuredDirectory) {
        Path workingDirectory = configuredDirectory == null || configuredDirectory.isBlank()
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(configuredDirectory.trim());
        Path normalized = workingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized) || !Files.isReadable(normalized)) {
            throw new IllegalArgumentException("CLI 工作目录不存在或不可访问");
        }
        return normalized;
    }

    private void requireExpectedProbeOutput(String output) throws Exception {
        JsonNode root = objectMapper.readTree(output);
        if (!root.isObject() || root.size() != 1 || !"OK".equals(root.path("status").asText())) {
            throw new IllegalStateException("供应商探针返回内容不符合预期");
        }
    }

    private AiGatewayResult executeInternal(AiGatewayRequest request, boolean structured) {
        long startedAt = System.nanoTime();
        String providerCode = null;
        String model = null;
        try {
            if (request == null) {
                throw new IllegalArgumentException("AI 网关请求不能为空");
            }
            AiUseCaseDefinitions.Definition definition = AiUseCaseDefinitions.require(
                    requireText(request.useCaseCode(), "AI 场景编码不能为空")
            );
            AiUseCaseConfigEntity useCase = aiUseCaseConfigService.requireEnabledByCode(definition.code());
            Long providerId = request.providerConfigIdOverride() == null
                    ? useCase.getProviderConfigId() : request.providerConfigIdOverride();
            AiProviderConfigEntity provider = aiProviderConfigService.requireExisting(providerId);
            providerCode = provider.getProviderCode();
            model = requireText(firstText(request.modelOverride(), firstText(useCase.getModel(), provider.getDefaultModel())),
                    "AI 场景模型未配置: " + definition.code());
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                throw new IllegalStateException("AI 接入配置已停用: " + providerCode);
            }
            definition.requireChannel(provider.getChannelType());
            String prompt = promptTemplateRenderer.render(useCase.getPromptTemplate(), request.variables(), definition);
            String outputSchema = structured && !Boolean.FALSE.equals(useCase.getJsonSchemaEnabled())
                    ? resolveSchema(request.outputSchema(), firstText(request.schemaClasspathOverride(), useCase.getSchemaClasspath()))
                    : null;
            if (definition.code().startsWith("intake.") && definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ) {
                prompt += "\n面向用户的说明采用 ELI5：用简单中文解释需求要解决什么、为什么需要这些改动、影响是什么以及如何验收；术语首次出现时解释。保留全部任务、验收标准和证据，不用删减内容凑长度，不编造未核实的结论。";
            }
            String output;
            if ("CLI".equals(normalize(provider.getChannelType()))) {
                try (var files = cn.aslight.workhub.service.attachment.AttachmentTemporaryFileSet.materialize(request.images())) {
                    var local = files.contexts().isEmpty() ? request : request.withLocalImages(files.contexts());
                    output = executeCli(local, definition, provider, useCase, prompt, outputSchema);
                }
            } else if ("API".equals(normalize(provider.getChannelType()))) {
                output = executeApi(new AiApiInvocation(
                        definition.code(),
                        provider,
                        aiProviderConfigService.resolveApiKey(provider.getId()),
                        model,
                        firstText(request.reasoningLevelOverride(), firstText(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel())),
                        firstText(request.speedModeOverride(), firstText(useCase.getSpeedMode(), provider.getDefaultSpeedMode())),
                        positive(request.timeoutSecondsOverride(), positive(useCase.getTimeoutSeconds(), provider.getCallTimeoutSeconds())),
                        prompt,
                        request.imagePaths(),
                        outputSchema, request.images()
                ));
            } else {
                throw new IllegalStateException("不支持的 AI 调用通道: " + provider.getChannelType());
            }
            log.info("AI gateway execution completed. useCase={}, providerCode={}, channel={}, protocol={}, model={}, durationMs={}, outputLength={}",
                    definition.code(), providerCode, provider.getChannelType(), provider.getApiProtocol(), model,
                    elapsedMillis(startedAt), output == null ? 0 : output.length());
            return AiGatewayResult.succeeded(output, providerCode, model, elapsedMillis(startedAt));
        } catch (Exception ex) {
            log.warn("AI gateway execution failed. useCase={}, providerCode={}, model={}, durationMs={}, errorType={}, cause={}",
                    request == null ? null : request.useCaseCode(), providerCode, model, elapsedMillis(startedAt),
                    ex.getClass().getSimpleName(),
                    ex.getCause() == null ? ex.getMessage() : ex.getCause().getClass().getSimpleName() + ": " + ex.getCause().getMessage());
            log.debug("AI gateway execution exception", ex);
            return AiGatewayResult.failed(
                    "AI 网关执行失败: " + summarizeException(ex),
                    providerCode,
                    model,
                    elapsedMillis(startedAt), ex
            );
        }
    }

    private String executeCli(AiGatewayRequest request,
                              AiUseCaseDefinitions.Definition definition,
                              AiProviderConfigEntity provider,
                              AiUseCaseConfigEntity useCase,
                              String prompt,
                              String outputSchema) throws Exception {
        if (AiCliType.is(provider, "ZCODE")) {
            String speed = firstCliValue(request.speedModeOverride(), firstCliValue(useCase.getSpeedMode(), provider.getDefaultSpeedMode()));
            if (!"STANDARD".equalsIgnoreCase(speed)) throw new IllegalArgumentException("ZCode CLI 速度使用 STANDARD");
            boolean repository = definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ;
            // ZCode 结构化输出模式会阻断 MCP 工具调用（实测带 schema 会话模型无法真正调用工具，只能产出
            // 占位/空结果）。仓库读取场景不把 schema 传进 CLI 结构化输出，让模型自由完成工具调查；终稿仍
            // 强制为 JSON 对象，模型交文本时按 schema 做一次无工具格式校正。
            if (repository && request.resultRecovery() != null) {
                String model = firstCliValue(request.modelOverride(), firstCliValue(useCase.getModel(), provider.getDefaultModel()));
                String reasoning = firstCliValue(request.reasoningLevelOverride(), firstCliValue(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel()));
                String fingerprint = AiAnalysisFingerprint.create(request, provider.getId(), model, reasoning, prompt, outputSchema);
                var recovery = request.resultRecovery().open(fingerprint);
                String output = zcodeCliClient.executeRecoverableAnalysis(provider, model, reasoning,
                        buildZcodePrompt(prompt, outputSchema, true, request),
                        positive(request.timeoutSecondsOverride(), positive(useCase.getTimeoutSeconds(), provider.getCallTimeoutSeconds())),
                        request.imagePaths(), outputSchema, repositoryMcpUrl(), mcpAccessToken(), repositoryToolNames(), recovery);
                request.resultRecovery().complete().run();
                return output;
            }
            if (repository) {
                return zcodeCliClient.executeStructuredWithoutCliSchema(provider,
                        firstCliValue(request.modelOverride(), firstCliValue(useCase.getModel(), provider.getDefaultModel())),
                        firstCliValue(request.reasoningLevelOverride(), firstCliValue(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel())),
                        buildZcodePrompt(prompt, outputSchema, true, request),
                        positive(request.timeoutSecondsOverride(), positive(useCase.getTimeoutSeconds(), provider.getCallTimeoutSeconds())),
                        request.imagePaths(), outputSchema,
                        repositoryMcpUrl(), mcpAccessToken(), repositoryToolNames());
            }
            return zcodeCliClient.execute(provider,
                    firstCliValue(request.modelOverride(), firstCliValue(useCase.getModel(), provider.getDefaultModel())),
                    firstCliValue(request.reasoningLevelOverride(), firstCliValue(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel())),
                    buildZcodePrompt(prompt, outputSchema, false, request),
                    positive(request.timeoutSecondsOverride(), positive(useCase.getTimeoutSeconds(), provider.getCallTimeoutSeconds())),
                    request.imagePaths(), outputSchema,
                    null, null, List.of());
        }
        if (!AiCliType.is(provider, "CODEX")) throw new IllegalArgumentException("所选 CLI 没有对应适配器");
        if (!codexCliClient.isEnabled()) {
            throw new IllegalStateException("Codex CLI 未启用");
        }
        Path temporaryDirectory = null;
        try {
            boolean localRepository = definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ;
            Path workingDirectory = request.workingDirectory();
            List<String> allowedDirectories = request.allowedDirectories();
            if (!localRepository) {
                temporaryDirectory = PrivateFiles.tempDirectory("workhub-ai-gateway-");
                workingDirectory = temporaryDirectory;
                allowedDirectories = List.of();
            }
            if (workingDirectory == null) {
                throw new IllegalArgumentException("CLI 场景工作目录不能为空");
            }
            CodexCliClient.CodexCliResult result = codexCliClient.execute(
                    new CodexCliClient.CodexCliRequest(
                            definition.code(),
                            workingDirectory,
                            allowedDirectories,
                            request.imagePaths(),
                            outputSchema == null ? "{}" : outputSchema,
                            prompt
                    ),
                    new CodexCliClient.CodexCliExecutionOptions(
                            provider.getCliCommand(),
                            firstCliValue(request.modelOverride(), firstCliValue(useCase.getModel(), provider.getDefaultModel())),
                            normalizeCliReasoning(firstCliValue(request.reasoningLevelOverride(), firstCliValue(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel()))),
                            positive(request.timeoutSecondsOverride(), positive(useCase.getTimeoutSeconds(), provider.getCallTimeoutSeconds())),
                            true,
                            localRepository
                    )
            );
            if (!result.succeeded()) {
                throw new IllegalStateException(result.failureSummary());
            }
            return result.outputJson();
        } finally {
            deleteDirectoryIfExists(temporaryDirectory);
        }
    }

    private String executeApi(AiApiInvocation invocation) throws Exception {
        String protocol = normalize(invocation.provider().getApiProtocol());
        if (PROTOCOL_RESPONSES.equals(protocol)) {
            return responsesClient.execute(invocation);
        }
        if (PROTOCOL_OPENROUTER.equals(protocol) || PROTOCOL_OPENAI_COMPATIBLE.equals(protocol) || PROTOCOL_CUSTOM.equals(protocol)) {
            return chatClient.execute(invocation);
        }
        throw new IllegalStateException("不支持的 AI API 协议: " + invocation.provider().getApiProtocol());
    }

    private String resolveSchema(String inlineSchema, String schemaClasspath) throws Exception {
        if (inlineSchema != null && !inlineSchema.isBlank()) {
            objectMapper.readTree(inlineSchema);
            return inlineSchema;
        }
        if (schemaClasspath == null || schemaClasspath.isBlank()) {
            return null;
        }
        if (schemaClasspath.startsWith("/") || schemaClasspath.contains("..")
                || !schemaClasspath.toLowerCase(Locale.ROOT).endsWith(".json")) {
            throw new IllegalArgumentException("AI 场景 Schema 必须是安全的 classpath JSON 相对路径");
        }
        String normalized = schemaClasspath;
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(normalized)) {
            if (input == null) {
                throw new IllegalStateException("AI 场景 Schema 资源不存在: " + normalized);
            }
            String schema = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            objectMapper.readTree(schema);
            return schema;
        }
    }

    private String firstCliValue(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private String normalizeCliReasoning(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > 32) throw new IllegalArgumentException("CLI 推理强度不能超过32个字符");
        // 仅兼容旧字典的大写值，动态目录返回的编码原样传递。
        return switch (value) {
            case "NONE", "LOW", "MEDIUM", "HIGH", "XHIGH", "MAX", "ULTRA" -> value.toLowerCase(Locale.ROOT);
            default -> value;
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) return first.trim();
        return second == null || second.isBlank() ? null : second.trim();
    }

    private Integer positive(Integer first, Integer second) {
        if (first != null && first > 0) {
            return first;
        }
        return second != null && second > 0 ? second : 600;
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String summarizeException(Exception ex) {
        String message = ex.getMessage();
        // 协议失败等场景根因在 cause 链上（如 "ZCode 协议执行失败" 的真实原因是任务执行失败），只看首层会丢失可行动信息。
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root != ex && root.getMessage() != null && !root.getMessage().isBlank() && !root.getMessage().equals(message)) {
            message = (message == null || message.isBlank() ? ex.getClass().getSimpleName() : message) + "；原因: " + root.getMessage();
        }
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240);
    }

    private void deleteDirectoryIfExists(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (Exception ex) {
                    log.debug("Failed to delete AI gateway temp path: {}", item, ex);
                }
            });
        } catch (Exception ex) {
            log.debug("Failed to clean AI gateway temp directory: {}", path, ex);
        }
    }
}
