package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.dao.ai.AiUseCaseConfigMapper;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigEntity;
import cn.aslight.workhub.model.ai.AiUseCaseConfigRequest;
import cn.aslight.workhub.model.ai.AiUseCaseConfigResponse;
import cn.aslight.workhub.model.ai.AiUseCaseConfigRow;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        when(mapper.findDetailById(11L)).thenReturn(row(11L, "intake.structured.extract"));

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
        when(mapper.findDetailById(12L)).thenReturn(row(12L, "intake.structured.extract"));
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
        when(mapper.findDetailById(13L)).thenReturn(row(13L, "intake.structured.extract"));
        AiUseCaseConfigRequest request = request("intake.structured.extract"); request.setEnabled(false);
        service.create(request);
        verify(mapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void create_shouldAllowZcodeProviderForLocalRepositoryUseCase() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setId(7L); provider.setEnabled(true); provider.setChannelType("CLI"); provider.setCliType("ZCODE");
        provider.setCallTimeoutSeconds(1800); // ZCode 超时取通道默认
        when(providerService.requireExisting(7L)).thenReturn(provider);

        AiUseCaseConfigRequest zcodeRequest = request("intake.clarification.analyze");
        zcodeRequest.setModel("supplier/model"); // ZCode 模型须为 Provider/Model 标识
        zcodeRequest.setReasoningLevel("max"); // ZCode 推理档位必填
        zcodeRequest.setSpeedMode("STANDARD"); // ZCode 速度仅支持标准档
        when(mapper.insert(org.mockito.ArgumentMatchers.any(AiUseCaseConfigEntity.class))).thenAnswer(inv -> {
            inv.getArgument(0, AiUseCaseConfigEntity.class).setId(99L);
            return 1;
        });
        when(mapper.findDetailById(99L)).thenReturn(row(99L, "intake.clarification.analyze"));
        assertDoesNotThrow(() -> service.create(zcodeRequest));
        verify(mapper).insert(org.mockito.ArgumentMatchers.any(AiUseCaseConfigEntity.class));
    }

    @Test
    void create_shouldAllowCodexProviderForLocalRepositoryUseCase() {
        AiProviderConfigEntity provider = new AiProviderConfigEntity();
        provider.setId(7L); provider.setEnabled(true); provider.setChannelType("CLI"); provider.setCliType("CODEX");
        provider.setDefaultModel("gpt-5-codex"); provider.setDefaultReasoningLevel("HIGH");
        provider.setDefaultSpeedMode("STANDARD"); provider.setCallTimeoutSeconds(900);
        when(providerService.requireExisting(7L)).thenReturn(provider);
        when(mapper.insert(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            invocation.<AiUseCaseConfigEntity>getArgument(0).setId(14L); return 1;
        });
        when(mapper.findDetailById(14L)).thenReturn(row(14L, "intake.clarification.analyze"));

        service.create(request("intake.clarification.analyze"));

        verify(mapper).insert(org.mockito.ArgumentMatchers.any(AiUseCaseConfigEntity.class));
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

    @Test
    void detail_shouldFillCapabilityFieldsFromDefinitions() {
        when(mapper.findDetailById(3L)).thenReturn(row(3L, "intake.clarification.analyze"));

        AiUseCaseConfigResponse response = service.detail(3L);

        assertEquals("LOCAL_REPOSITORY_READ", response.capability());
        assertEquals(List.of("CLI"), response.allowedChannelTypes());
        assertEquals(List.of("CODEX", "ZCODE"), response.allowedCliTypes());
    }

    @Test
    void delete_shouldSoftDeleteWithVersionCheck() {
        AiUseCaseConfigEntity existing = new AiUseCaseConfigEntity();
        existing.setId(5L); existing.setUseCaseCode("intake.clarification.analyze"); existing.setVersion(2);
        when(mapper.findEntityById(5L)).thenReturn(existing);
        when(mapper.softDelete(5L, 2, "admin")).thenReturn(1);

        service.deleteForManage(5L, 2, "admin", "127.0.0.1");

        verify(mapper).softDelete(5L, 2, "admin");
    }

    @Test
    void delete_shouldRejectVersionConflictWithoutSoftDelete() {
        AiUseCaseConfigEntity existing = new AiUseCaseConfigEntity();
        existing.setId(5L); existing.setUseCaseCode("intake.clarification.analyze"); existing.setVersion(2);
        when(mapper.findEntityById(5L)).thenReturn(existing);

        assertThrows(IllegalStateException.class, () -> service.deleteForManage(5L, 3, "admin", "127.0.0.1"));
        verify(mapper, never()).softDelete(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void delete_shouldFailForMissingRow() {
        when(mapper.findEntityById(404L)).thenReturn(null);
        assertThrows(IllegalArgumentException.class, () -> service.deleteForManage(404L, 1, "admin", "127.0.0.1"));
    }

    @Test
    void detail_shouldFillCapabilityFieldsForRegisteredSddAnalyze() {
        when(mapper.findDetailById(11L)).thenReturn(row(11L, "intake.development.sdd-analyze"));

        AiUseCaseConfigResponse response = service.detail(11L);

        assertEquals("LOCAL_REPOSITORY_READ", response.capability());
        assertEquals(List.of("CLI"), response.allowedChannelTypes());
        assertEquals(List.of("CODEX", "ZCODE"), response.allowedCliTypes());
    }

    @Test
    void detail_shouldReturnNullCapabilityForUnregisteredCode() {
        when(mapper.findDetailById(11L)).thenReturn(row(11L, "intake.development.not-registered"));

        AiUseCaseConfigResponse response = service.detail(11L);

        assertNull(response.capability());
        assertNull(response.allowedChannelTypes());
        assertNull(response.allowedCliTypes());
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

    private AiUseCaseConfigRow row(Long id, String code) {
        return new AiUseCaseConfigRow(
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
                "supplier-model",
                null,
                null,
                3600,
                "USE_CASE",
                "USE_CASE",
                "USE_CASE",
                "USE_CASE",
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
