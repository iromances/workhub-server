package cn.aslight.workhub.service.project;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.project.BusinessLineAccessConfigMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.dao.project.ProjectMapper;
import cn.aslight.workhub.model.project.BusinessLineAccessConfigEntity;
import cn.aslight.workhub.model.project.BusinessLineAccessConfigRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.BusinessLineResponse;
import cn.aslight.workhub.model.project.BusinessLineSaveRequest;
import cn.aslight.workhub.service.intake.GitlabRepositoryService;
import cn.aslight.workhub.service.mcp.McpCryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectServiceBusinessLineAccessConfigTest {

    @Test
    void createBusinessLine_shouldNormalizeAndPersistAccessConfigs() {
        TestContext context = context();
        BusinessLineSaveRequest request = businessLineRequest();
        request.setAccessConfigs(List.of(
                accessConfig("TEST", "operations", "", "https://ops.example.test/admin", null),
                accessConfig("test", "GATEWAY", "统一网关", "https://gateway.example.test", "/asset-api")
        ));

        BusinessLineResponse response = context.service.createBusinessLine(request);

        assertEquals("BL000011", response.businessLineCode());
        assertEquals(List.of("DEV", "TEST", "PROD"), response.supportedEnvironments());
        assertEquals(2, response.accessConfigs().size());
        assertEquals("TEST", response.accessConfigs().get(0).environmentCode());
        assertEquals("OPERATIONS", response.accessConfigs().get(0).endpointType());
        assertEquals("运营端", response.accessConfigs().get(0).endpointName());
        assertEquals("/asset-api", response.accessConfigs().get(1).pathPrefix());
    }

    @Test
    void createBusinessLine_shouldRejectEnvironmentOutsideSupportedList() {
        TestContext context = context();
        BusinessLineSaveRequest request = businessLineRequest();
        request.setAccessConfigs(List.of(
                accessConfig("UAT", "CLIENT", "客户端", "https://client.example.test", null)
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> context.service.createBusinessLine(request)
        );

        assertEquals("访问地址环境 UAT 不在业务线支持环境内", error.getMessage());
    }

    @Test
    void createBusinessLine_shouldRejectEmptyOrDuplicateSupportedEnvironments() {
        TestContext emptyContext = context();
        BusinessLineSaveRequest empty = businessLineRequest();
        empty.setSupportedEnvironments(List.of());

        IllegalArgumentException emptyError = assertThrows(
                IllegalArgumentException.class,
                () -> emptyContext.service.createBusinessLine(empty)
        );
        assertEquals("支持环境不能为空", emptyError.getMessage());

        TestContext duplicateContext = context();
        BusinessLineSaveRequest duplicate = businessLineRequest();
        duplicate.setSupportedEnvironments(List.of("DEV", "dev", "TEST"));

        IllegalArgumentException duplicateError = assertThrows(
                IllegalArgumentException.class,
                () -> duplicateContext.service.createBusinessLine(duplicate)
        );
        assertEquals("支持环境不能重复：DEV", duplicateError.getMessage());
    }

    @Test
    void updateBusinessLine_shouldKeepAccessConfigsWhenFieldIsMissing() {
        TestContext context = context();
        context.currentBusinessLine.set(businessLine(9L, "BL000009", "创新保理"));
        context.savedConfigs.add(entity("BL000009", "PROD", "CLIENT", "客户端", "https://client.example.com", null));
        BusinessLineSaveRequest request = businessLineRequest();
        request.setBusinessLineName("创新保理");
        request.setAccessConfigs(null);

        BusinessLineResponse response = context.service.updateBusinessLine(9L, request);

        assertEquals(1, response.accessConfigs().size());
        verify(context.accessConfigMapper, never()).deleteByBusinessLineCode(any());
    }

    @Test
    void updateBusinessLine_shouldClearAccessConfigsForExplicitEmptyArray() {
        TestContext context = context();
        context.currentBusinessLine.set(businessLine(9L, "BL000009", "创新保理"));
        context.savedConfigs.add(entity("BL000009", "PROD", "CLIENT", "客户端", "https://client.example.com", null));
        BusinessLineSaveRequest request = businessLineRequest();
        request.setBusinessLineName("创新保理");
        request.setAccessConfigs(List.of());

        BusinessLineResponse response = context.service.updateBusinessLine(9L, request);

        assertEquals(List.of(), response.accessConfigs());
        verify(context.accessConfigMapper).deleteByBusinessLineCode("BL000009");
    }

    @Test
    void createBusinessLine_shouldEncryptLoginCredential() {
        TestContext context = context();
        BusinessLineSaveRequest request = businessLineRequest();
        BusinessLineAccessConfigRequest accessConfig =
                accessConfig("TEST", "operations", "", "https://ops.example.test/admin", null);
        accessConfig.setLoginUsername(" ops.admin ");
        accessConfig.setLoginPassword(" s3cret! ");
        request.setAccessConfigs(List.of(accessConfig));

        BusinessLineResponse response = context.service.createBusinessLine(request);

        BusinessLineAccessConfigEntity saved = context.savedConfigs().get(0);
        assertEquals("ops.admin", saved.getLoginUsername());
        assertTrue(saved.getLoginPasswordEncrypted().startsWith("mcp:v1:"));
        assertEquals("s3cret!", context.cryptoService().decrypt(saved.getLoginPasswordEncrypted()));
        assertEquals("ops.admin", response.accessConfigs().get(0).loginUsername());
        assertEquals(Boolean.TRUE, response.accessConfigs().get(0).loginPasswordConfigured());
    }

    @Test
    void updateBusinessLine_shouldKeepExistingPasswordWhenLoginPasswordBlank() {
        TestContext context = context();
        context.currentBusinessLine().set(businessLine(9L, "BL000009", "创新保理"));
        String existingEncrypted = context.cryptoService().encrypt("old-secret");
        BusinessLineAccessConfigEntity existing =
                entity("BL000009", "PROD", "CLIENT", "客户端", "https://client.example.com", null);
        existing.setLoginUsername("client.user");
        existing.setLoginPasswordEncrypted(existingEncrypted);
        context.savedConfigs().add(existing);

        BusinessLineSaveRequest request = businessLineRequest();
        request.setBusinessLineName("创新保理");
        BusinessLineAccessConfigRequest accessConfig =
                accessConfig("PROD", "CLIENT", "客户端", "https://client.example.com", null);
        accessConfig.setLoginUsername("client.user");
        request.setAccessConfigs(List.of(accessConfig));

        BusinessLineResponse response = context.service.updateBusinessLine(9L, request);

        BusinessLineAccessConfigEntity saved = context.savedConfigs().get(0);
        assertEquals("client.user", saved.getLoginUsername());
        assertEquals(existingEncrypted, saved.getLoginPasswordEncrypted());
        assertEquals(Boolean.TRUE, response.accessConfigs().get(0).loginPasswordConfigured());
    }

    @Test
    void createBusinessLine_shouldRejectInvalidUrlAndDuplicateIdentity() {
        TestContext invalidUrlContext = context();
        BusinessLineSaveRequest invalidUrl = businessLineRequest();
        invalidUrl.setAccessConfigs(List.of(accessConfig("test", "CLIENT", "客户端", "ftp://example.test", null)));

        IllegalArgumentException invalidUrlError = assertThrows(
                IllegalArgumentException.class,
                () -> invalidUrlContext.service.createBusinessLine(invalidUrl)
        );
        assertEquals("访问地址必须是合法的 HTTP/HTTPS 地址", invalidUrlError.getMessage());

        TestContext duplicateContext = context();
        BusinessLineSaveRequest duplicate = businessLineRequest();
        duplicate.setAccessConfigs(List.of(
                accessConfig("test", "OTHER", "H5", "https://one.example.test", null),
                accessConfig("TEST", "other", "h5", "https://two.example.test", null)
        ));

        IllegalArgumentException duplicateError = assertThrows(
                IllegalArgumentException.class,
                () -> duplicateContext.service.createBusinessLine(duplicate)
        );
        assertEquals("同一环境下的访问地址类型和名称不能重复", duplicateError.getMessage());
    }

    @Test
    void createBusinessLine_shouldRejectPrefixOnNonGatewayEndpoint() {
        TestContext context = context();
        BusinessLineSaveRequest request = businessLineRequest();
        request.setAccessConfigs(List.of(
                accessConfig("prod", "CLIENT", "客户端", "https://client.example.com", "/client")
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> context.service.createBusinessLine(request)
        );

        assertEquals("只有网关地址可以配置路径前缀", error.getMessage());
    }

    private TestContext context() {
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        BusinessLineAccessConfigMapper accessConfigMapper = mock(BusinessLineAccessConfigMapper.class);
        AtomicReference<BusinessLineEntity> currentBusinessLine = new AtomicReference<>();
        List<BusinessLineAccessConfigEntity> savedConfigs = new ArrayList<>();

        when(businessLineMapper.findMaxBusinessLineCode()).thenReturn("BL000010");
        when(businessLineMapper.findByName(any())).thenReturn(null);
        doAnswer(invocation -> {
            BusinessLineEntity entity = invocation.getArgument(0);
            entity.setId(11L);
            currentBusinessLine.set(entity);
            return 1;
        }).when(businessLineMapper).insert(any());
        doAnswer(invocation -> {
            BusinessLineEntity update = invocation.getArgument(0);
            BusinessLineEntity current = currentBusinessLine.get();
            update.setCreatedAt(current == null ? null : current.getCreatedAt());
            currentBusinessLine.set(update);
            return 1;
        }).when(businessLineMapper).update(any());
        when(businessLineMapper.findById(any())).thenAnswer(invocation -> currentBusinessLine.get());

        when(accessConfigMapper.deleteByBusinessLineCode(any())).thenAnswer(invocation -> {
            savedConfigs.removeIf(item -> invocation.getArgument(0).equals(item.getBusinessLineCode()));
            return 1;
        });
        doAnswer(invocation -> {
            BusinessLineAccessConfigEntity config = invocation.getArgument(0);
            config.setId((long) savedConfigs.size() + 1);
            savedConfigs.add(config);
            return 1;
        }).when(accessConfigMapper).insert(any());
        when(accessConfigMapper.findByBusinessLineCodes(anyList())).thenAnswer(invocation -> {
            List<String> codes = invocation.getArgument(0);
            return savedConfigs.stream().filter(item -> codes.contains(item.getBusinessLineCode())).toList();
        });

        McpCryptoService cryptoService = cryptoService();
        ProjectService service = new ProjectService(
                mock(ProjectMapper.class),
                businessLineMapper,
                accessConfigMapper,
                mock(ProjectInvolvedSystemMapper.class),
                mock(GitlabRepositoryService.class),
                cryptoService
        );
        return new TestContext(service, accessConfigMapper, currentBusinessLine, savedConfigs, cryptoService);
    }

    private McpCryptoService cryptoService() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("workhub-mcp-test-master-key");
        return new McpCryptoService(properties);
    }

    private BusinessLineSaveRequest businessLineRequest() {
        BusinessLineSaveRequest request = new BusinessLineSaveRequest();
        request.setBusinessLineName("新业务线");
        request.setGitlabGroupName("new-group");
        request.setSupportedEnvironments(List.of("dev", "TEST", "Prod"));
        request.setEnabled(true);
        return request;
    }

    private BusinessLineAccessConfigRequest accessConfig(String environmentCode,
                                                         String endpointType,
                                                         String endpointName,
                                                         String endpointUrl,
                                                         String pathPrefix) {
        BusinessLineAccessConfigRequest request = new BusinessLineAccessConfigRequest();
        request.setEnvironmentCode(environmentCode);
        request.setEndpointType(endpointType);
        request.setEndpointName(endpointName);
        request.setEndpointUrl(endpointUrl);
        request.setPathPrefix(pathPrefix);
        return request;
    }

    private BusinessLineEntity businessLine(Long id, String code, String name) {
        BusinessLineEntity entity = new BusinessLineEntity();
        entity.setId(id);
        entity.setBusinessLineCode(code);
        entity.setBusinessLineName(name);
        entity.setEnabled(true);
        return entity;
    }

    private BusinessLineAccessConfigEntity entity(String businessLineCode,
                                                  String environmentCode,
                                                  String endpointType,
                                                  String endpointName,
                                                  String endpointUrl,
                                                  String pathPrefix) {
        BusinessLineAccessConfigEntity entity = new BusinessLineAccessConfigEntity();
        entity.setId(1L);
        entity.setBusinessLineCode(businessLineCode);
        entity.setEnvironmentCode(environmentCode);
        entity.setEndpointType(endpointType);
        entity.setEndpointName(endpointName);
        entity.setEndpointUrl(endpointUrl);
        entity.setPathPrefix(pathPrefix);
        entity.setSortOrder(0);
        entity.setEnabled(true);
        return entity;
    }

    private record TestContext(ProjectService service,
                               BusinessLineAccessConfigMapper accessConfigMapper,
                               AtomicReference<BusinessLineEntity> currentBusinessLine,
                               List<BusinessLineAccessConfigEntity> savedConfigs,
                               McpCryptoService cryptoService) {
    }
}
