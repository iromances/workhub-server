package cn.aslight.workhub.service.ai;

import cn.aslight.workhub.config.AiProperties;
import cn.aslight.workhub.dao.ai.AiProviderConfigMapper;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.model.ai.AiProviderConfigRequest;
import cn.aslight.workhub.model.ai.AiProviderConfigResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AiProviderConfigServiceTest {

    private FakeAiProviderConfigMapper mapper;
    private AiCredentialCryptoService cryptoService;
    private AiProviderConfigService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeAiProviderConfigMapper();
        AiProperties properties = new AiProperties();
        properties.setMasterKey("test-ai-provider-master-key");
        cryptoService = new AiCredentialCryptoService(properties);
        service = new AiProviderConfigService(mapper, cryptoService);
    }

    @Test
    void create_shouldEncryptApiKeyAtRestAndReturnMaskedValue() {
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setApiKey("provider-key-123456");

        AiProviderConfigResponse response = service.create(request);

        String storedApiKey = mapper.stored.getApiKey();
        assertTrue(storedApiKey.startsWith("ai:v1:"));
        assertNotEquals("provider-key-123456", storedApiKey);
        assertEquals("provider-key-123456", cryptoService.decrypt(storedApiKey));
        assertEquals("**********3456", response.apiKey());
    }

    @Test
    void create_shouldAllowExplicitHttpsPortAndTrailingSlash() {
        AiProviderConfigResponse response = service.create(
                xianxingtongRequest("https://aiserver.thchengtay.com:443/v1/")
        );

        assertEquals("https://aiserver.thchengtay.com:443/v1/", response.apiBaseUrl());
    }

    @Test
    void listAndResolveApiKey_shouldMaskResponseAndReadLegacyPlainText() {
        AiProviderConfigEntity legacy = providerEntity(7L, "legacy-provider-key-1234");
        mapper.stored = legacy;

        List<AiProviderConfigResponse> responses = service.list();

        assertEquals("**********1234", responses.getFirst().apiKey());
        assertEquals("legacy-provider-key-1234", service.resolveApiKey(7L));
    }

    @Test
    void update_shouldKeepExistingSecretForBlankOrOriginalMask() {
        String originalCipherText = cryptoService.encrypt("existing-provider-key-5678");
        mapper.stored = providerEntity(8L, originalCipherText);
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setApiKey(" ");

        AiProviderConfigResponse blankUpdateResponse = service.update(8L, request);

        assertEquals(originalCipherText, mapper.stored.getApiKey());
        assertEquals("**********5678", blankUpdateResponse.apiKey());

        request.setApiKey(blankUpdateResponse.apiKey());
        service.update(8L, request);

        assertEquals(originalCipherText, mapper.stored.getApiKey());
        assertEquals("existing-provider-key-5678", service.resolveApiKey(8L));
    }

    @Test
    void update_shouldMigrateLegacyPlainTextWhenSecretIsKept() {
        mapper.stored = providerEntity(9L, "legacy-provider-key-9012");
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setApiKey("**********9012");

        service.update(9L, request);

        assertTrue(mapper.stored.getApiKey().startsWith("ai:v1:"));
        assertEquals("legacy-provider-key-9012", cryptoService.decrypt(mapper.stored.getApiKey()));
    }

    @Test
    void update_shouldRejectProviderCodeMutation() {
        mapper.stored = providerEntity(10L, cryptoService.encrypt("provider-key-123456"));
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setProviderCode("renamed-provider-code");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.update(10L, request)
        );

        assertTrue(error.getMessage().contains("创建后不可修改"));
        assertEquals("xianxingtong-openai-responses-api", mapper.stored.getProviderCode());
    }

    @Test
    void prepareConnectionTest_shouldRestoreExistingMaskedKeyWithoutSaving() {
        mapper.stored = providerEntity(11L, cryptoService.encrypt("existing-provider-key-3456"));
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setId(11L);
        request.setApiKey("**********3456");

        AiProviderConfigService.ConnectionTestContext context = service.prepareConnectionTest(request);

        assertEquals("existing-provider-key-3456", context.apiKey());
        assertEquals("https://aiserver.thchengtay.com/v1", context.provider().getApiBaseUrl());
        assertEquals(0, mapper.insertCount);
        assertEquals(0, mapper.updateCount);
    }

    @Test
    void prepareConnectionTest_shouldUsePlainKeyForUnsavedConfig() {
        AiProviderConfigRequest request = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        request.setApiKey("new-provider-key-7890");

        AiProviderConfigService.ConnectionTestContext context = service.prepareConnectionTest(request);

        assertEquals("new-provider-key-7890", context.apiKey());
        assertEquals(0, mapper.insertCount);
        assertEquals(0, mapper.updateCount);
    }

    @Test
    void prepareConnectionTest_shouldRejectMaskWithoutIdAndChannelMismatch() {
        AiProviderConfigRequest unsaved = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        unsaved.setApiKey("******");
        assertThrows(IllegalArgumentException.class, () -> service.prepareConnectionTest(unsaved));

        mapper.stored = providerEntity(12L, cryptoService.encrypt("existing-provider-key-3456"));
        AiProviderConfigRequest mismatched = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        mismatched.setId(12L);
        mismatched.setChannelType("CLI");
        assertThrows(IllegalArgumentException.class, () -> service.prepareConnectionTest(mismatched));
        assertEquals(0, mapper.insertCount);
        assertEquals(0, mapper.updateCount);
    }

    @Test
    void create_shouldAuditMetadataWithoutApiKey() {
        SystemAuditService auditService = mock(SystemAuditService.class);
        AiProviderConfigService auditedService = new AiProviderConfigService(mapper, cryptoService, auditService);

        auditedService.create(
                xianxingtongRequest("https://aiserver.thchengtay.com/v1"),
                "admin",
                "127.0.0.1"
        );

        verify(auditService).operation(
                eq("admin"),
                eq("system:ai-config:create"),
                eq("CREATE"),
                eq("AI_PROVIDER_CONFIG"),
                eq("1"),
                eq(null),
                argThat(snapshot -> snapshot.contains("providerCode=xianxingtong-openai-responses-api")
                        && !snapshot.contains("provider-key-123456")),
                eq("SUCCESS"),
                eq(null),
                eq("127.0.0.1")
        );
    }

    @Test
    void create_shouldRejectUnsafeXianxingtongResponsesUrls() {
        List<String> unsafeUrls = List.of(
                "http://aiserver.thchengtay.com/v1",
                "https://aiserver.thchengtay.com.evil.example/v1",
                "https://user@aiserver.thchengtay.com/v1",
                "https://aiserver.thchengtay.com:8443/v1",
                "https://aiserver.thchengtay.com/",
                "https://aiserver.thchengtay.com/v1/responses",
                "https://aiserver.thchengtay.com/v1?debug=true",
                "https://aiserver.thchengtay.com/v1#fragment"
        );

        for (String unsafeUrl : unsafeUrls) {
            AiProviderConfigRequest request = xianxingtongRequest(unsafeUrl);
            assertThrows(IllegalArgumentException.class, () -> service.create(request), unsafeUrl);
        }
        assertEquals(0, mapper.insertCount);
    }

    private AiProviderConfigRequest xianxingtongRequest(String baseUrl) {
        AiProviderConfigRequest request = new AiProviderConfigRequest();
        request.setProviderCode("xianxingtong-openai-responses-api");
        request.setProviderName("先行通 OpenAI Responses API通道");
        request.setChannelType("API");
        request.setVendor("XIANXINGTONG");
        request.setModelProvider("OPENAI");
        request.setDefaultModel("gpt-5.6-sol");
        request.setDefaultReasoningLevel("HIGH");
        request.setDefaultSpeedMode("STANDARD");
        request.setCallTimeoutSeconds(600);
        request.setApiProtocol("OPENAI_RESPONSES");
        request.setApiBaseUrl(baseUrl);
        request.setApiKey("provider-key-123456");
        request.setEnabled(true);
        return request;
    }

    private AiProviderConfigEntity providerEntity(Long id, String apiKey) {
        AiProviderConfigEntity entity = new AiProviderConfigEntity();
        entity.setId(id);
        entity.setProviderCode("xianxingtong-openai-responses-api");
        entity.setProviderName("先行通 OpenAI Responses API通道");
        entity.setChannelType("API");
        entity.setVendor("XIANXINGTONG");
        entity.setModelProvider("OPENAI");
        entity.setDefaultModel("gpt-5.6-sol");
        entity.setDefaultReasoningLevel("HIGH");
        entity.setDefaultSpeedMode("STANDARD");
        entity.setCallTimeoutSeconds(600);
        entity.setVersion(0);
        entity.setApiProtocol("OPENAI_RESPONSES");
        entity.setApiBaseUrl("https://aiserver.thchengtay.com/v1");
        entity.setApiKey(apiKey);
        entity.setEnabled(true);
        return entity;
    }

    @Test
    void create_shouldRequireDefaultsOnlyWhenEnabled() {
        AiProviderConfigRequest enabled = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        enabled.setDefaultModel(null);
        assertThrows(IllegalArgumentException.class, () -> service.create(enabled));

        AiProviderConfigRequest disabled = xianxingtongRequest("https://aiserver.thchengtay.com/v1");
        disabled.setEnabled(false);
        disabled.setDefaultModel(null);
        disabled.setDefaultReasoningLevel(null);
        disabled.setDefaultSpeedMode(null);
        disabled.setApiBaseUrl(null);
        disabled.setApiKey(null);
        AiProviderConfigResponse response = service.create(disabled);
        assertEquals(false, response.enabled());
    }

    private static class FakeAiProviderConfigMapper implements AiProviderConfigMapper {

        private AiProviderConfigEntity stored;
        private int insertCount;
        private int updateCount;

        @Override
        public List<AiProviderConfigEntity> findAll() {
            return stored == null ? List.of() : List.of(stored);
        }

        @Override
        public AiProviderConfigEntity findById(Long id) {
            return stored != null && stored.getId().equals(id) ? stored : null;
        }

        @Override
        public AiProviderConfigEntity findByCode(String providerCode) {
            return stored != null && stored.getProviderCode().equals(providerCode) ? stored : null;
        }

        @Override
        public int insert(AiProviderConfigEntity entity) {
            if (entity.getId() == null) {
                entity.setId(1L);
            }
            stored = entity;
            insertCount += 1;
            return 1;
        }

        @Override
        public int update(AiProviderConfigEntity entity) {
            stored = entity;
            updateCount += 1;
            return 1;
        }

        @Override
        public int countUseCases(Long providerConfigId) {
            return 0;
        }
    }
}
