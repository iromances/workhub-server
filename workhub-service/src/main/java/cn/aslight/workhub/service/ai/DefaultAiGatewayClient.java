package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.service.intake.CodexCliClient;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 统一 AI 网关默认实现。
 */
@Service
public class DefaultAiGatewayClient implements AiGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiGatewayClient.class);
    private static final Pattern TEMPLATE_VARIABLE_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_.-]+)}");
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
    private final OpenAiResponsesClient responsesClient;
    private final OpenAiChatClient chatClient;
    private final ObjectMapper objectMapper;

    public DefaultAiGatewayClient(AiUseCaseConfigService aiUseCaseConfigService,
                                  AiProviderConfigService aiProviderConfigService,
                                  CodexCliClient codexCliClient,
                                  OpenAiResponsesClient responsesClient,
                                  OpenAiChatClient chatClient,
                                  ObjectMapper objectMapper) {
        this.aiUseCaseConfigService = aiUseCaseConfigService;
        this.aiProviderConfigService = aiProviderConfigService;
        this.codexCliClient = codexCliClient;
        this.responsesClient = responsesClient;
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured(String useCaseCode) {
        try {
            AiUseCaseDefinitions.Definition definition = AiUseCaseDefinitions.require(useCaseCode);
            AiUseCaseConfigEntity useCase = aiUseCaseConfigService.requireEnabledByCode(useCaseCode);
            AiProviderConfigEntity provider = aiProviderConfigService.requireExisting(useCase.getProviderConfigId());
            definition.requireChannel(provider.getChannelType());
            requireText(firstText(useCase.getModel(), provider.getDefaultModel()), "AI 场景模型未配置");
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                return false;
            }
            return !"CLI".equals(normalize(provider.getChannelType())) || codexCliClient.isEnabled();
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
            String prompt = renderPrompt(useCase.getPromptTemplate(), request.variables(), definition);
            String outputSchema = structured && !Boolean.FALSE.equals(useCase.getJsonSchemaEnabled())
                    ? resolveSchema(request.outputSchema(), firstText(request.schemaClasspathOverride(), useCase.getSchemaClasspath()))
                    : null;
            String output;
            if ("CLI".equals(normalize(provider.getChannelType()))) {
                output = executeCli(request, definition, provider, useCase, prompt, outputSchema);
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
                        outputSchema
                ));
            } else {
                throw new IllegalStateException("不支持的 AI 调用通道: " + provider.getChannelType());
            }
            log.info("AI gateway execution completed. useCase={}, providerCode={}, channel={}, protocol={}, model={}, durationMs={}, outputLength={}",
                    definition.code(), providerCode, provider.getChannelType(), provider.getApiProtocol(), model,
                    elapsedMillis(startedAt), output == null ? 0 : output.length());
            return AiGatewayResult.succeeded(output, providerCode, model, elapsedMillis(startedAt));
        } catch (Exception ex) {
            log.warn("AI gateway execution failed. useCase={}, providerCode={}, model={}, durationMs={}, errorType={}",
                    request == null ? null : request.useCaseCode(), providerCode, model, elapsedMillis(startedAt),
                    ex.getClass().getSimpleName());
            log.debug("AI gateway execution exception", ex);
            return AiGatewayResult.failed(
                    "AI 网关执行失败: " + summarizeException(ex),
                    providerCode,
                    model,
                    elapsedMillis(startedAt)
            );
        }
    }

    private String executeCli(AiGatewayRequest request,
                              AiUseCaseDefinitions.Definition definition,
                              AiProviderConfigEntity provider,
                              AiUseCaseConfigEntity useCase,
                              String prompt,
                              String outputSchema) throws Exception {
        if (!codexCliClient.isEnabled()) {
            throw new IllegalStateException("Codex CLI 未启用");
        }
        Path temporaryDirectory = null;
        try {
            boolean localRepository = definition.capability() == AiUseCaseDefinitions.Capability.LOCAL_REPOSITORY_READ;
            Path workingDirectory = request.workingDirectory();
            List<String> allowedDirectories = request.allowedDirectories();
            if (!localRepository) {
                temporaryDirectory = Files.createTempDirectory("workhub-ai-gateway-");
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
                            firstText(request.modelOverride(), firstText(useCase.getModel(), provider.getDefaultModel())),
                            normalizeCliReasoning(firstText(request.reasoningLevelOverride(), firstText(useCase.getReasoningLevel(), provider.getDefaultReasoningLevel()))),
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

    private String renderPrompt(String template,
                                Map<String, Object> variables,
                                AiUseCaseDefinitions.Definition definition) throws Exception {
        String normalizedTemplate = requireText(template, "AI 场景提示词模板为空: " + definition.code());
        Matcher matcher = TEMPLATE_VARIABLE_PATTERN.matcher(normalizedTemplate);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String variableName = matcher.group(1);
            if (!definition.allowedPromptVariables().contains(variableName)) {
                throw new IllegalArgumentException("AI 场景提示词包含未授权变量: " + variableName);
            }
            Object value = variables.get(variableName);
            if (value == null) {
                throw new IllegalArgumentException("AI 场景提示词变量缺失: " + variableName);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(stringify(value)));
        }
        matcher.appendTail(rendered);
        if (TEMPLATE_VARIABLE_PATTERN.matcher(rendered).find()) {
            throw new IllegalArgumentException("AI 场景提示词存在未渲染变量");
        }
        return rendered.toString();
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

    private String stringify(Object value) throws Exception {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        return objectMapper.writeValueAsString(value);
    }

    private String normalizeCliReasoning(String value) {
        return AiHttpPayloadSupport.normalizeReasoning(value);
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
