package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.service.intake.CodexCliClient;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultAiGatewayClientTest {

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

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.structured(
                useCase.getUseCaseCode(),
                "业务提示词",
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

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.structured(
                useCase.getUseCaseCode(),
                "分析仓库",
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

        AiGatewayResult result = context.gateway.executeStructured(AiGatewayRequest.structured(
                useCase.getUseCaseCode(),
                "分析仓库",
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
                java.util.Map.of("prompt", "业务提示词"), Path.of("/tmp"), List.of(), List.of(),
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
        entity.setPromptTemplate("网关模板：${prompt}");
        entity.setEnabled(true);
        return entity;
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
        private final OpenAiResponsesClient responsesClient = mock(OpenAiResponsesClient.class);
        private final OpenAiChatClient chatClient = mock(OpenAiChatClient.class);
        private final DefaultAiGatewayClient gateway = new DefaultAiGatewayClient(
                useCaseService,
                providerService,
                codexCliClient,
                responsesClient,
                chatClient,
                new ObjectMapper()
        );
    }
}
