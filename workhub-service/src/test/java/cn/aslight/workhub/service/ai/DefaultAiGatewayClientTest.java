package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.service.intake.CodexCliClient;
import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.service.mcp.McpRuntimeService;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiGatewayClientTest {

    @Test
    void zcodeConnectionTestUsesNativeClientWithExactModelAndReasoning() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        when(context.zcodeCliClient.execute(eq(provider), eq("GLM/glm-4.6"), eq("disabled"), any(), eq(30), any(), any(), any(), any(), any()))
                .thenReturn("{\"status\":\"OK\"}");
        assertTrue(context.gateway.testConnection(provider, null).succeeded());
        verify(context.zcodeCliClient).execute(eq(provider), eq("GLM/glm-4.6"), eq("disabled"), any(), eq(30), any(), any(), any(), any(), any());
        verifyNoInteractions(context.codexCliClient, context.chatClient, context.responsesClient);
    }

    @Test
    void zcodeTextTaskPreservesSelectionsAndSchemaWithoutCodexFallback() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.STRUCTURED_EXTRACT, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.zcodeCliClient.execute(eq(provider), eq("GLM/glm-4.6"), eq("disabled"), any(), eq(30), any(), any(), any(), any(), any()))
                .thenReturn("{\"status\":\"OK\"}");
        var request = AiGatewayRequest.withVariables(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "整理需求"),
                Path.of("/tmp"), List.of(), List.of(), "{\"type\":\"object\"}");
        assertTrue(context.gateway.isConfigured(useCase.getUseCaseCode()));
        assertTrue(context.gateway.executeStructured(request).succeeded());
        verify(context.zcodeCliClient).execute(eq(provider), eq("GLM/glm-4.6"), eq("disabled"),
                argThat(prompt -> prompt.contains("整理需求") && prompt.contains("\"type\":\"object\"")), eq(30), any(), any(), any(), any(), any());
        verifyNoInteractions(context.codexCliClient, context.chatClient, context.responsesClient);
    }

    @Test
    void zcodeRejectsUnsupportedLegacySpeedBeforeStarting() {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.STRUCTURED_EXTRACT, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        var request = new AiGatewayRequest(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"), Path.of("/tmp"), List.of(),
                List.of(), "{\"type\":\"object\"}",
                null, null, null, "FAST", null, null);
        assertFalse(context.gateway.executeStructured(request).succeeded());
        verifyNoInteractions(context.zcodeCliClient, context.codexCliClient, context.chatClient, context.responsesClient);
    }

    @Test
    void zcodeRepositorySceneRequiresMcpTokenBeforeStarting() {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        var request = new AiGatewayRequest(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"), Path.of("/tmp"), List.of(),
                List.of(), "{\"type\":\"object\"}",
                null, null, null, null, null, null);
        assertFalse(context.gateway.executeStructured(request).succeeded());
        verifyNoInteractions(context.zcodeCliClient, context.codexCliClient, context.chatClient, context.responsesClient);
    }

    @Test
    void zcodeRepositoryScenePassesRepositoryContextToNativeClient() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.mcpRuntime.toolNames()).thenReturn(List.of("list_repository_files", "read_repository_text_file"));
        when(context.mcpRuntimeProvider.getIfAvailable()).thenReturn(context.mcpRuntime);
        context.mcpProperties.setAccessToken("test-token");
        when(context.zcodeCliClient.executeStructuredWithoutCliSchema(eq(provider), any(), any(), any(), eq(30), any(), any(), any(), any(), any()))
                .thenReturn("{}");
        var request = new AiGatewayRequest(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"), Path.of("/tmp"), List.of(),
                List.of(), "{\"type\":\"object\"}",
                null, null, null, null, null, null);
        assertTrue(context.gateway.executeStructured(request).succeeded());
        verify(context.zcodeCliClient).executeStructuredWithoutCliSchema(eq(provider), any(), any(),
                argThat(prompt -> prompt.contains("代码读取约束") && prompt.contains("/tmp")),
                eq(30), any(),
                argThat(schema -> schema != null && schema.contains("object")),
                eq("http://127.0.0.1:8080/api/mcp/runtime"), eq("test-token"),
                eq(List.of("list_repository_files", "read_repository_text_file")));
        verifyNoInteractions(context.codexCliClient, context.chatClient, context.responsesClient);
    }

    @Test
    void zcodeRepositorySceneDoesNotPassCliSchemaToKeepToolsUsable() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.mcpRuntime.toolNames()).thenReturn(List.of("list_repository_files", "read_repository_text_file"));
        when(context.mcpRuntimeProvider.getIfAvailable()).thenReturn(context.mcpRuntime);
        context.mcpProperties.setAccessToken("test-token");
        when(context.zcodeCliClient.executeStructuredWithoutCliSchema(any(), any(), any(),
                argThat(prompt -> prompt.contains("先调查后作答") && prompt.contains("\"type\":\"object\"")),
                anyInt(), any(), argThat(schema -> schema != null), any(), any(), any()))
                .thenReturn("{\"summary\":\"s\",\"workItems\":[]}");
        var request = AiGatewayRequest.withVariables(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"),
                Path.of("/tmp"), List.of(), List.of(), "{\"type\":\"object\"}");
        assertTrue(context.gateway.executeStructured(request).succeeded());
        verify(context.zcodeCliClient).executeStructuredWithoutCliSchema(any(), any(), any(), any(), anyInt(), any(),
                argThat(schema -> schema != null), any(), any(), any());
        verify(context.zcodeCliClient, never()).execute(any(), any(), any(), any(), anyInt(), any(), argThat(schema -> schema == null), any(), any(), any());
    }

    @Test
    void isConfigured_allowsZcodeRepositorySceneWhenMcpBridgeReady() {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.CLARIFICATION_ANALYZE, 7L);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.mcpRuntime.toolNames()).thenReturn(List.of("list_repository_files", "read_repository_text_file"));
        when(context.mcpRuntimeProvider.getIfAvailable()).thenReturn(context.mcpRuntime);
        context.mcpProperties.setAccessToken("test-token");

        assertTrue(context.gateway.isConfigured(useCase.getUseCaseCode()));
    }

    @Test
    void isConfigured_rejectsZcodeRepositorySceneWithoutMcpBridge() {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.CLARIFICATION_ANALYZE, 7L);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);

        assertFalse(context.gateway.isConfigured(useCase.getUseCaseCode()));

        when(context.mcpRuntimeProvider.getIfAvailable()).thenReturn(context.mcpRuntime);
        context.mcpProperties.setAccessToken("test-token");
        when(context.mcpRuntime.toolNames()).thenReturn(List.of());
        assertFalse(context.gateway.isConfigured(useCase.getUseCaseCode()));

        when(context.mcpRuntime.toolNames()).thenReturn(List.of("search_business_logs"));
        assertFalse(context.gateway.isConfigured(useCase.getUseCaseCode()));
    }

    @Test
    void zcodePassesImageAttachmentsThroughToNativeClient() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.STRUCTURED_EXTRACT, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.zcodeCliClient.execute(eq(provider), any(), any(), any(), eq(30), any(), any(), any(), any(), any())).thenReturn("{}");
        var request = new AiGatewayRequest(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"), Path.of("/tmp"), List.of(),
                List.of("/tmp/image.png"), "{\"type\":\"object\"}",
                null, null, null, null, null, null);
        assertTrue(context.gateway.executeStructured(request).succeeded());
        verify(context.zcodeCliClient).execute(eq(provider), any(), any(), any(), eq(30),
                argThat(images -> images != null && images.equals(List.of("/tmp/image.png"))), any(), any(), any(), any());
        verifyNoInteractions(context.codexCliClient, context.chatClient, context.responsesClient);
    }

    private AiProviderConfigEntity zcodeProvider() {
        var provider = provider(7L, "CLI", null);
        provider.setCliType("ZCODE"); provider.setCliCommand("zcode");
        provider.setDefaultModel("GLM/glm-4.6"); provider.setDefaultReasoningLevel("disabled"); provider.setDefaultSpeedMode("STANDARD");
        return provider;
    }

    @Test void analysisRecoveryUsesSeparateClientPathAndMarksFormattingCompletedOnlyOnSuccess() throws Exception {
        var context = new TestContext(); var provider = zcodeProvider();
        var useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.mcpRuntime.toolNames()).thenReturn(List.of("list_repository_files", "read_repository_text_file"));
        when(context.mcpRuntimeProvider.getIfAvailable()).thenReturn(context.mcpRuntime);
        context.mcpProperties.setAccessToken("test-token");
        var saved = new java.util.HashMap<String, String>();
        var completed = new java.util.concurrent.atomic.AtomicInteger();
        var recovery = new AiResultRecovery(key -> "已调查的原始结论", saved::put, completed::incrementAndGet);
        when(context.zcodeCliClient.executeRecoverableAnalysis(any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(call -> {
                    assertTrue(((String) call.getArgument(3)).contains("ELI5"));
                    AiResultRecovery.Session checkpoint = call.getArgument(10);
                    assertEquals("已调查的原始结论", checkpoint.original());
                    checkpoint.save().accept("完整原文");
                    return "{}";
                });
        var request = AiGatewayRequest.withVariables(useCase.getUseCaseCode(), variables(useCase.getUseCaseCode(), "测试"),
                Path.of("/tmp"), List.of(), List.of(), "{\"type\":\"object\"}").withResultRecovery(recovery);
        assertTrue(context.gateway.executeStructured(request).succeeded());
        assertEquals(1, saved.size()); assertEquals(1, completed.get());
        doThrow(new IllegalStateException("格式未完成")).when(context.zcodeCliClient).executeRecoverableAnalysis(any(), any(), any(), any(), anyInt(), any(), any(), any(), any(), any(), any());
        assertFalse(context.gateway.executeStructured(request).succeeded());
        assertEquals(1, completed.get());
    }

    @Test
    void cliPassesDynamicCodesUnchangedAndOnlyNormalizesLegacyUppercase() {
        for (String effort : List.of("minimal", "ultra", "futureEffort", "HIGH", "ULTRA")) {
            TestContext context = new TestContext();
            AiProviderConfigEntity provider = provider(null, "CLI", null);
            provider.setCliCommand("codex");
            provider.setCliWorkingDirectory("/tmp");
            provider.setDefaultModel("CaseModel");
            provider.setDefaultReasoningLevel(effort);
            when(context.codexCliClient.isEnabled()).thenReturn(true);
            when(context.codexCliClient.execute(any(), any()))
                    .thenReturn(CodexCliClient.CodexCliResult.succeeded("{\"status\":\"OK\"}"));
            assertTrue(context.gateway.testConnection(provider, null).succeeded());
            String expected = switch (effort) { case "HIGH" -> "high"; case "ULTRA" -> "ultra"; default -> effort; };
            verify(context.codexCliClient).execute(any(), argThat(options -> expected.equals(options.reasoningEffort())));
        }
    }

    @Test
    void executeStructured_shouldResolveUseCaseRenderPromptAndRouteResponses() throws Exception {
        TestContext context = new TestContext();
        AiUseCaseConfigEntity useCase = useCase(AiUseCaseDefinitions.STRUCTURED_EXTRACT, 7L);
        AiProviderConfigEntity provider = provider(7L, "API", "OPENAI_RESPONSES");
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.providerService.resolveApiKey(7L)).thenReturn("secret");
        when(context.responsesClient.execute(any(AiApiInvocation.class))).thenAnswer(invocation -> {
            AiApiInvocation call = invocation.getArgument(0);
            assertTrue(call.prompt().contains("业务提示词"));
            assertTrue(call.outputSchema().contains("additionalProperties"));
            return "{\"status\":\"OK\"}";
        });

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.withVariables(
                useCase.getUseCaseCode(),
                variables(useCase.getUseCaseCode(), "业务提示词"),
                Path.of("/tmp"),
                List.of(),
                List.of(),
                "{\"type\":\"object\",\"additionalProperties\":false}"
        ));

        assertTrue(result.succeeded());
        verify(context.responsesClient).execute(any(AiApiInvocation.class));
        verify(context.chatClient, never()).execute(any(AiApiInvocation.class));
    }

    @Test
    void executeStructured_shouldRejectApiForLocalRepositoryUseCase() throws Exception {
        TestContext context = new TestContext();
        AiUseCaseConfigEntity useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        AiProviderConfigEntity provider = provider(7L, "API", "OPENAI_RESPONSES");
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.withVariables(
                useCase.getUseCaseCode(),
                variables(useCase.getUseCaseCode(), "分析仓库"),
                Path.of("/tmp/repo"),
                List.of("/tmp/repo/service"),
                List.of(),
                "{\"type\":\"object\"}"
        ));

        assertFalse(result.succeeded());
        assertTrue(result.failureSummary().contains("不允许使用 API 通道"));
        verify(context.responsesClient, never()).execute(any(AiApiInvocation.class));
    }

    @Test
    void executeStructured_shouldAllowLocalRepositoryOnlyForCliDefinition() {
        TestContext context = new TestContext();
        AiUseCaseConfigEntity useCase = useCase(AiUseCaseDefinitions.DEVELOPMENT_ANALYZE, 7L);
        AiProviderConfigEntity provider = provider(7L, "CLI", null);
        provider.setCliCommand("codex");
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(7L)).thenReturn(provider);
        when(context.codexCliClient.isEnabled()).thenReturn(true);
        when(context.codexCliClient.execute(any(CodexCliClient.CodexCliRequest.class),
                any(CodexCliClient.CodexCliExecutionOptions.class)))
                .thenReturn(CodexCliClient.CodexCliResult.succeeded("{\"status\":\"OK\"}"));

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.withVariables(
                useCase.getUseCaseCode(),
                variables(useCase.getUseCaseCode(), "分析仓库"),
                Path.of("/tmp/repo"),
                List.of("/tmp/repo/service"),
                List.of(),
                "{\"type\":\"object\"}"
        ));

        assertTrue(result.succeeded());
        verify(context.codexCliClient).execute(
                argThat(request -> request.addDirs().equals(List.of("/tmp/repo/service"))),
                argThat(CodexCliClient.CodexCliExecutionOptions::allowLocalTools)
        );
    }

    @Test
    void executeStructured_shouldApplyRequestOverridesAndRouteCustomToChat() throws Exception {
        TestContext context = new TestContext();
        AiUseCaseConfigEntity useCase = useCase(AiUseCaseDefinitions.STRUCTURED_EXTRACT, 7L);
        useCase.setModel(null); useCase.setReasoningLevel(null); useCase.setSpeedMode(null); useCase.setTimeoutSeconds(null);
        AiProviderConfigEntity provider = provider(9L, "API", "CUSTOM");
        provider.setDefaultModel("provider-model"); provider.setDefaultReasoningLevel("LOW");
        provider.setDefaultSpeedMode("STANDARD"); provider.setCallTimeoutSeconds(120);
        when(context.useCaseService.requireEnabledByCode(useCase.getUseCaseCode())).thenReturn(useCase);
        when(context.providerService.requireExisting(9L)).thenReturn(provider);
        when(context.providerService.resolveApiKey(9L)).thenReturn("secret");
        when(context.chatClient.execute(any())).thenAnswer(invocation -> {
            AiApiInvocation call = invocation.getArgument(0);
            assertTrue("override-model".equals(call.model()));
            assertTrue("HIGH".equals(call.reasoningLevel()));
            assertTrue("FAST".equals(call.speedMode()));
            assertTrue(Integer.valueOf(45).equals(call.timeoutSeconds()));
            return "{}";
        });
        AiGatewayRequest request = new AiGatewayRequest(useCase.getUseCaseCode(),
                variables(useCase.getUseCaseCode(), "业务提示词"), Path.of("/tmp"), List.of(), List.of(),
                "{\"type\":\"object\"}", 9L, "override-model", "HIGH", "FAST", 45, null);
        assertTrue(context.gateway.executeStructured(request).succeeded());
        verify(context.chatClient).execute(any());
    }

    @Test
    void testConnection_shouldRouteCurrentApiConfigWithPlainKey() throws Exception {
        TestContext context = new TestContext();
        AiProviderConfigEntity provider = provider(null, "API", "OPENAI_RESPONSES");
        provider.setDefaultModel("gpt-test");
        provider.setDefaultReasoningLevel("HIGH");
        provider.setDefaultSpeedMode("STANDARD");
        when(context.responsesClient.execute(any())).thenAnswer(invocation -> {
            AiApiInvocation call = invocation.getArgument(0);
            assertEquals("plain-key", call.apiKey());
            assertEquals("gpt-test", call.model());
            assertEquals("HIGH", call.reasoningLevel());
            return "{\"status\":\"OK\"}";
        });

        AiGatewayResult result = context.gateway.testConnection(provider, "plain-key");

        assertTrue(result.succeeded());
        verify(context.responsesClient).execute(any());
        verify(context.chatClient, never()).execute(any());
    }

    @Test
    void testConnection_shouldRouteCliInRestrictedMode() {
        TestContext context = new TestContext();
        AiProviderConfigEntity provider = provider(null, "CLI", null);
        provider.setCliCommand("codex");
        provider.setCliWorkingDirectory("/tmp");
        provider.setDefaultModel("gpt-test");
        provider.setDefaultReasoningLevel("HIGH");
        when(context.codexCliClient.isEnabled()).thenReturn(true);
        when(context.codexCliClient.execute(any(), any()))
                .thenReturn(CodexCliClient.CodexCliResult.succeeded("{\"status\":\"OK\"}"));

        AiGatewayResult result = context.gateway.testConnection(provider, null);

        assertTrue(result.succeeded());
        verify(context.codexCliClient).execute(
                argThat(request -> request.workingDirectory().equals(Path.of("/tmp"))
                        && request.addDirs().isEmpty()
                        && request.outputSchema().contains("status")),
                argThat(options -> options.disablePlugins() && !options.allowLocalTools()
                        && "codex".equals(options.command()))
        );
    }

    @Test
    void testConnection_shouldRejectUnexpectedStructuredOutput() throws Exception {
        TestContext context = new TestContext();
        AiProviderConfigEntity provider = provider(null, "API", "CUSTOM");
        provider.setDefaultModel("gpt-test");
        when(context.chatClient.execute(any())).thenReturn("{\"status\":\"OK\",\"extra\":true}");

        AiGatewayResult result = context.gateway.testConnection(provider, "plain-key");

        assertFalse(result.succeeded());
        assertTrue(result.failureSummary().contains("返回内容不符合预期"));
    }

    @Test
    void agentApiUsesSelectedProviderForEveryApiProtocolWithoutCliFallback() throws Exception {
        for (String protocol : List.of("OPENAI_RESPONSES", "OPENROUTER", "OPENAI_COMPATIBLE", "CUSTOM")) {
            TestContext context = new TestContext();
            var provider = provider(9L, "API", protocol);
            when(context.providerService.getEnabledById(9L)).thenReturn(provider);
            when(context.providerService.resolveApiKey(9L)).thenReturn("provider-key");
            when(context.responsesClient.execute(any())).thenReturn("{\"kind\":\"RESULT\"}");
            when(context.chatClient.execute(any())).thenReturn("{\"kind\":\"RESULT\"}");
            var result = context.gateway.executeAgentApi(new AiAgentApiRequest(9L, "chosen-model", "task-context", 20, 0, protocol));
            assertTrue(result.succeeded());
            assertEquals("chosen-model", result.model());
            if (protocol.equals("OPENAI_RESPONSES")) {
                verify(context.responsesClient).execute(argThat(call -> call.model().equals("chosen-model") && call.outputSchema() == null));
            } else {
                verify(context.chatClient).execute(argThat(call -> call.model().equals("chosen-model") && call.outputSchema() == null));
            }
            verify(context.codexCliClient, never()).execute(any(), any());
            verify(context.useCaseService, never()).requireEnabledByCode(any());
        }
    }

    @Test
    void agentApiCannotSilentlyRouteCliProviderThroughLegacyCodexClient() {
        TestContext context = new TestContext();
        when(context.providerService.getEnabledById(9L)).thenReturn(provider(9L, "CLI", null));
        var result = context.gateway.executeAgentApi(new AiAgentApiRequest(9L, "chosen-model", "task-context", 20, 0, null));
        assertFalse(result.succeeded());
        verify(context.codexCliClient, never()).execute(any(), any());
    }

    private AiUseCaseConfigEntity useCase(String code, Long providerId) {
        AiUseCaseConfigEntity entity = new AiUseCaseConfigEntity();
        entity.setUseCaseCode(code);
        entity.setUseCaseName(code);
        entity.setProviderConfigId(providerId);
        entity.setModel("gpt-test");
        entity.setReasoningLevel("HIGH");
        entity.setSpeedMode("STANDARD");
        entity.setTimeoutSeconds(30);
        entity.setJsonSchemaEnabled(true);
        StringBuilder template = new StringBuilder("网关模板：");
        for (String variable : AiUseCaseDefinitions.require(code).allowedPromptVariables()) {
            template.append("${").append(variable).append('}');
        }
        entity.setPromptTemplate(template.toString());
        entity.setEnabled(true);
        return entity;
    }

    private java.util.Map<String, Object> variables(String code, String value) {
        java.util.Map<String, Object> vars = new java.util.LinkedHashMap<>();
        for (String name : AiUseCaseDefinitions.require(code).allowedPromptVariables()) {
            vars.put(name, value);
        }
        return vars;
    }

    private AiProviderConfigEntity provider(Long id, String channelType, String protocol) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setId(id);
        entity.setProviderCode("test-provider");
        entity.setProviderName("Test Provider");
        entity.setChannelType(channelType);
        entity.setApiProtocol(protocol);
        entity.setApiBaseUrl("https://example.com/v1");
        entity.setConnectTimeoutSeconds(1);
        entity.setReadTimeoutSeconds(30);
        entity.setCallTimeoutSeconds(30);
        entity.setEnabled(true);
        return entity;
    }

    private static class TestContext {
        private final AiUseCaseConfigService useCaseService = mock(AiUseCaseConfigService.class);
        private final AiProviderConfigService providerService = mock(AiProviderConfigService.class);
        private final CodexCliClient codexCliClient = mock(CodexCliClient.class);
        private final ZCodeCliClient zcodeCliClient = mock(ZCodeCliClient.class);
        private final OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        private final OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
            private final McpRuntimeService mcpRuntime = mock(McpRuntimeService.class);
            @SuppressWarnings("unchecked")
            private final ObjectProvider<McpRuntimeService> mcpRuntimeProvider = mock(ObjectProvider.class);
            private final McpProperties mcpProperties = new McpProperties();
            private final DefaultAiGatewayClient gateway = new DefaultAiGatewayClient(
                useCaseService,
                providerService,
                codexCliClient,
                zcodeCliClient,
                responsesClient,
                chatClient,
                new AiPromptTemplateRenderer(new ObjectMapper()),
                new ObjectMapper(),
                mcpRuntimeProvider,
                mcpProperties,
                "",
                "8080",
                "");
    }
}
