package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderConfigRequest;
import cn.aslight.workhub.model.ai.AiProviderConnectionTestResponse;
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

class AiProviderConnectionTestServiceTest {

    private AiProviderConfigService providerConfigService;
    private AiGatewayClient aiGatewayClient;
    private SystemAuditService auditService;
    private AiProviderConnectionTestService service;

    @BeforeEach
    void setUp() {
        providerConfigService = mock(AiProviderConfigService.class);
        aiGatewayClient = mock(AiGatewayClient.class);
        auditService = mock(SystemAuditService.class);
        service = new AiProviderConnectionTestService(providerConfigService, aiGatewayClient, auditService);
    }

    @Test
    void test_shouldReturnSuccessAndAuditWithoutSecret() {
        AiProviderConfigRequest request = request();
        AiProviderConfigEntity provider = provider();
        when(providerConfigService.prepareConnectionTest(request))
                .thenReturn(new AiProviderConfigService.ConnectionTestContext(provider, "secret-value"));
        when(aiGatewayClient.testConnection(provider, "secret-value"))
                .thenReturn(AiGatewayResult.succeeded("{\"status\":\"OK\"}", "openai-api", "gpt-test", 23L));

        AiProviderConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertTrue(response.success());
        assertTrue(response.message().contains("连接成功"));
        assertFalse(response.message().contains("secret-value"));
        verify(auditService).operation(
                eq("admin"), eq("system:ai-config:create"), eq("TEST_CONNECTION"),
                eq("AI_PROVIDER_CONFIG"), eq("TEMP"), isNull(),
                eq("连通测试 channel=API, protocol=OPENAI_RESPONSES, model=gpt-test"),
                eq("SUCCESS"), isNull(), eq("127.0.0.1")
        );
    }

    @Test
    void test_shouldClassifyAuthenticationFailureWithoutRawBody() {
        AiProviderConfigRequest request = request();
        request.setId(7L);
        AiProviderConfigEntity provider = provider();
        provider.setId(7L);
        when(providerConfigService.prepareConnectionTest(request))
                .thenReturn(new AiProviderConfigService.ConnectionTestContext(provider, "secret-value"));
        when(aiGatewayClient.testConnection(provider, "secret-value"))
                .thenReturn(AiGatewayResult.failed(
                        "Responses API HTTP 401: upstream raw secret-value body",
                        "openai-api", "gpt-test", 31L));

        AiProviderConnectionTestResponse response = service.test(request, "admin", "127.0.0.1");

        assertFalse(response.success());
        assertTrue(response.message().contains("认证未通过"));
        assertFalse(response.message().contains("secret-value"));
        verify(auditService).operation(
                eq("admin"), eq("system:ai-config:update"), eq("TEST_CONNECTION"),
                eq("AI_PROVIDER_CONFIG"), eq("7"), isNull(),
                eq("连通测试 channel=API, protocol=OPENAI_RESPONSES, model=gpt-test"),
                eq("FAILURE"), eq("连接失败：认证未通过，请检查 API Key 或访问权限"), eq("127.0.0.1")
        );
    }

    private AiProviderConfigRequest request() {
        AiProviderConfigRequest request = new AiProviderConfigRequest();
        request.setProviderCode("openai-api");
        request.setProviderName("OpenAI API通道");
        request.setChannelType("API");
        request.setModelProvider("OPENAI");
        request.setDefaultModel("gpt-test");
        request.setApiProtocol("OPENAI_RESPONSES");
        return request;
    }

    private AiProviderConfigEntity provider() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setProviderCode("openai-api");
        provider.setChannelType("API");
        provider.setApiProtocol("OPENAI_RESPONSES");
        provider.setDefaultModel("gpt-test");
        return provider;
    }
}
