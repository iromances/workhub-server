package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderProbeRequest;
import cn.aslight.workhub.model.ai.AiProviderProbeResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiProviderProbeServiceTest {

    private AiProviderConfigService providerConfigService;
    private AiGatewayClient aiGatewayClient;
    private SystemAuditService auditService;
    private AiProviderProbeService service;

    @BeforeEach
    void setUp() {
        providerConfigService = mock(AiProviderConfigService.class);
        aiGatewayClient = mock(AiGatewayClient.class);
        auditService = mock(SystemAuditService.class);
        service = new AiProviderProbeService(providerConfigService, aiGatewayClient, auditService);
    }

    @Test
    void probe_shouldReturnSuccessWithoutExposingProviderOutput() {
        when(providerConfigService.requireExisting(7L)).thenReturn(provider(7L));
        when(aiGatewayClient.probe(7L, "gpt-codex-enterprise"))
                .thenReturn(AiGatewayResult.succeeded("{\"status\":\"OK\"}", "xianxingtong-openai-api", "gpt-codex-enterprise", 12L));

        AiProviderProbeResponse response = service.probe(7L, request(), "admin", "127.0.0.1");

        assertTrue(response.success());
        assertTrue(response.message().contains("通过"));
        assertFalse(response.message().contains("secret"));
        verify(auditService).operation(
                eq("admin"), eq("system:ai-config:update"), eq("PROBE"), eq("AI_PROVIDER_CONFIG"), eq("7"),
                isNull(), eq("供应商探针 providerCode=xianxingtong-openai-api, model=gpt-codex-enterprise"),
                eq("SUCCESS"), isNull(), eq("127.0.0.1")
        );
    }

    @Test
    void probe_shouldRejectResponseWithUnexpectedFields() {
        when(providerConfigService.requireExisting(7L)).thenReturn(provider(7L));
        when(aiGatewayClient.probe(7L, "gpt-codex-enterprise"))
                .thenReturn(AiGatewayResult.failed("供应商探针返回内容不符合预期", "xianxingtong-openai-api", "gpt-codex-enterprise", 12L));

        AiProviderProbeResponse response = service.probe(7L, request(), "admin", "127.0.0.1");

        assertFalse(response.success());
        assertFalse(response.message().contains("secret"));
        verify(auditService).operation(
                eq("admin"), eq("system:ai-config:update"), eq("PROBE"), eq("AI_PROVIDER_CONFIG"), eq("7"),
                isNull(), eq("供应商探针 providerCode=xianxingtong-openai-api, model=gpt-codex-enterprise"),
                eq("FAILURE"), eq("供应商探针返回内容不符合预期"), eq("127.0.0.1")
        );
    }

    @Test
    void probe_shouldReturnFailureAndAuditIt() {
        when(providerConfigService.requireExisting(7L)).thenReturn(provider(7L));
        when(aiGatewayClient.probe(7L, "gpt-codex-enterprise"))
                .thenReturn(AiGatewayResult.failed("Responses API HTTP 500", "xianxingtong-openai-api", "gpt-codex-enterprise", 12L));

        AiProviderProbeResponse response = service.probe(7L, request(), "admin", "127.0.0.1");

        assertFalse(response.success());
        verify(auditService).operation(
                eq("admin"), eq("system:ai-config:update"), eq("PROBE"), eq("AI_PROVIDER_CONFIG"), eq("7"),
                isNull(), eq("供应商探针 providerCode=xianxingtong-openai-api, model=gpt-codex-enterprise"),
                eq("FAILURE"), eq("Responses API HTTP 500"), eq("127.0.0.1")
        );
    }

    private AiProviderProbeRequest request() {
        AiProviderProbeRequest request = new AiProviderProbeRequest();
        request.setModel("gpt-codex-enterprise");
        return request;
    }

    private AiProviderConfigEntity provider(Long id) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setId(id);
        entity.setProviderCode("xianxingtong-openai-api");
        return entity;
    }
}
