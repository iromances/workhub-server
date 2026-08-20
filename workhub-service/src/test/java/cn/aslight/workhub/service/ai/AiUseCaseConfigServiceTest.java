package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.dao.ai.AiUseCaseConfigMapper;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigRequest;
import cn.aslight.workhub.model.ai.AiUseCaseConfigResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiUseCaseConfigServiceTest {

    private AiUseCaseConfigMapper mapper;
    private AiProviderConfigService providerService;
    private SystemAuditService auditService;
    private AiUseCaseConfigService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AiUseCaseConfigMapper.class);
        providerService = mock(AiProviderConfigService.class);
        auditService = mock(SystemAuditService.class);
        service = new AiUseCaseConfigService(mapper, providerService, auditService);
    }

    @Test
    void create_shouldAuditMetadataWithoutPromptContent() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setId(7L);
        provider.setEnabled(true);
        provider.setChannelType("API");
        provider.setDefaultModel("provider-model");
        provider.setDefaultReasoningLevel("HIGH");
        provider.setDefaultSpeedMode("STANDARD");
        provider.setCallTimeoutSeconds(600);
        when(providerService.requireExisting(7L)).thenReturn(provider);
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AiUseCaseConfigEntity.class)))
                .thenAnswer(invocation -> {
                    invocation.<AiUseCaseConfigEntity>getArgument(0).setId(11L);
                    return 1;
                });
        when(mapper.findDetailById(11L)).thenReturn(response(11L, "intake.structured.extract"));

        service.create(request("intake.structured.extract"), "admin", "127.0.0.1");

        verify(auditService).operation(
                eq("admin"),
                eq("system:ai-config:create"),
                eq("CREATE"),
                eq("AI_USE_CASE_CONFIG"),
                eq("11"),
                eq(null),
                argThat(snapshot -> snapshot.contains("useCaseCode=intake.structured.extract")
                        && !snapshot.contains("SENSITIVE_PROMPT")),
                eq("SUCCESS"),
                eq(null),
                eq("127.0.0.1")
        );
    }

    @Test
    void create_shouldPersistNullOverridesAndInheritProviderDefaults() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setId(7L); provider.setEnabled(true); provider.setChannelType("API");
        provider.setDefaultModel("provider-model"); provider.setDefaultReasoningLevel("HIGH");
        provider.setDefaultSpeedMode("STANDARD"); provider.setCallTimeoutSeconds(600);
        when(providerService.requireExisting(7L)).thenReturn(provider);
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            AiUseCaseConfigEntity saved = invocation.getArgument(0);
            assertNull(saved.getModel()); assertNull(saved.getReasoningLevel());
            assertNull(saved.getSpeedMode()); assertNull(saved.getTimeoutSeconds());
            saved.setId(12L); return 1;
        });
        when(mapper.findDetailById(12L)).thenReturn(response(12L, "intake.structured.extract"));
        AiUseCaseConfigRequest request = request("intake.structured.extract");
        request.setModel(null); request.setReasoningLevel(null); request.setSpeedMode(null); request.setTimeoutSeconds(null);
        service.create(request);
        verify(mapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_shouldAllowStoppedUseCaseToBindStoppedProvider() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setId(7L); provider.setEnabled(false); provider.setChannelType("API");
        when(providerService.requireExisting(7L)).thenReturn(provider);
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            invocation.<AiUseCaseConfigEntity>getArgument(0).setId(13L); return 1;
        });
        when(mapper.findDetailById(13L)).thenReturn(response(13L, "intake.structured.extract"));
        AiUseCaseConfigRequest request = request("intake.structured.extract"); request.setEnabled(false);
        service.create(request);
        verify(mapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void update_shouldRejectUseCaseCodeMutationBeforePersisting() {
        AiUseCaseConfigEntity existing = new AiUseCaseConfigEntity();
        existing.setId(11L);
        existing.setUseCaseCode("intake.structured.extract");
        when(mapper.findEntityById(11L)).thenReturn(existing);

        assertThrows(IllegalArgumentException.class,
                () -> service.update(11L, request("intake.development.analyze"), "admin", "127.0.0.1"));

        verify(mapper, never()).update(org.mockito.ArgumentMatchers.any(AiUseCaseConfigEntity.class));
        verify(auditService, never()).operation(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private AiUseCaseConfigRequest request(String code) {
        AiUseCaseConfigRequest request = new AiUseCaseConfigRequest();
        request.setUseCaseCode(code);
        request.setUseCaseName("结构化提取");
        request.setDomain("INTAKE");
        request.setProviderConfigId(7L);
        request.setModel("supplier-model");
        request.setPromptTemplate("SENSITIVE_PROMPT");
        request.setEnabled(true);
        return request;
    }

    private AiUseCaseConfigResponse response(Long id, String code) {
        return new AiUseCaseConfigResponse(
                id,
                code,
                "结构化提取",
                "INTAKE",
                null,
                7L,
                "xianxingtong-openai-responses-api",
                "先行通 Responses",
                "API",
                "XIANXINGTONG",
                "OPENAI",
                "supplier-model",
                null,
                null,
                3600,
                true,
                null,
                "SENSITIVE_PROMPT",
                null,
                1,
                "checksum",
                true,
                null
        );
    }
}
