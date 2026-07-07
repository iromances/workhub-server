package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpResourceResponse;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpResourceServiceTest {

    @Test
    void createDatabase_shouldEncryptPasswordAndExposeOnlyConfiguredFlag() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        when(mapper.findByTargetKey("crm-test-mysql")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(1L)).thenAnswer(invocation -> saved.get());

        McpResourceSaveRequest request = databaseRequest();

        McpResourceResponse response = service.create(request);

        ArgumentCaptor<McpResourceEntity> captor = ArgumentCaptor.forClass(McpResourceEntity.class);
        verify(mapper).insert(captor.capture());
        assertNotEquals("plain-db-password", captor.getValue().getPasswordEncrypted());
        assertTrue(captor.getValue().getPasswordEncrypted().startsWith("mcp:v1:"));
        assertTrue(response.passwordConfigured());
    }

    @Test
    void createDatabase_shouldGenerateTargetKeyAndNameWhenBlank() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                businessLineMapper,
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        when(mapper.findByTargetKey("bl000001-test-db-10-0-0-10-3306")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(1L)).thenAnswer(invocation -> saved.get());
        when(businessLineMapper.findByCode("BL000001")).thenReturn(businessLine("BL000001", "保费分期"));

        McpResourceSaveRequest request = databaseRequest();
        request.setTargetKey(null);
        request.setName(null);
        request.setBusinessLineCode("BL000001");
        request.setBusinessLineCodes(List.of("BL000001"));

        McpResourceResponse response = service.create(request);

        assertEquals("bl000001-test-db-10-0-0-10-3306", response.targetKey());
        assertEquals("保费分期 test 数据库目标 10.0.0.10:3306", response.name());
    }

    @Test
    void createServer_shouldGenerateNextAvailableTargetKeyWhenBaseKeyExists() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                businessLineMapper,
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        when(mapper.findByTargetKey("bl000007-prod-server-10-0-1-20-22")).thenReturn(new McpResourceEntity());
        when(mapper.findByTargetKey("bl000007-prod-server-10-0-1-20-22-2")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(2L)).thenAnswer(invocation -> saved.get());
        when(businessLineMapper.findByCode("BL000007")).thenReturn(businessLine("BL000007", "汇浦"));

        McpResourceSaveRequest request = serverRequest();
        request.setTargetKey(null);
        request.setName(null);

        McpResourceResponse response = service.create(request);

        assertEquals("bl000007-prod-server-10-0-1-20-22-2", response.targetKey());
        assertEquals("汇浦 prod 服务器目标 10.0.1.20:22", response.name());
    }

    @Test
    void createServer_shouldAllowBlankSystemNamesAsAllSystemsInBusinessLine() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                businessLineMapper,
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        when(mapper.findByTargetKey("bl000007-prod-server-10-0-1-20-22")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(3L)).thenAnswer(invocation -> saved.get());
        when(businessLineMapper.findByCode("BL000007")).thenReturn(businessLine("BL000007", "汇浦"));

        McpResourceSaveRequest request = serverRequest();
        request.setTargetKey(null);
        request.setName(null);
        request.setSystemNames(List.of());
        request.setSystemName(null);

        McpResourceResponse response = service.create(request);

        assertEquals("bl000007-prod-server-10-0-1-20-22", response.targetKey());
        assertNull(response.systemName());
        assertEquals(List.of(), response.systemNames());
    }

    @Test
    void catalog_shouldDecryptStoredPasswordOnlyForMcpRuntime() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        McpResourceEntity entity = new McpResourceEntity();
        entity.setId(1L);
        entity.setResourceType("DATABASE");
        entity.setTargetKey("crm-test-mysql");
        entity.setBusinessLineCode("crm");
        entity.setEnvironmentCode("test");
        entity.setName("CRM 测试库");
        entity.setHost("10.0.0.10");
        entity.setPort(3306);
        entity.setDatabaseSchema("crm");
        entity.setUsername("readonly");
        entity.setPasswordEncrypted(cryptoService.encrypt("plain-db-password"));
        entity.setAllowedServicesJson("[]");
        entity.setAllowedLogPathsJson("[]");
        entity.setProfilesJson("[{\"key\":\"readonly\",\"maxRows\":100,\"queryTimeoutSeconds\":10,\"maxResultBytes\":65536}]");
        entity.setEnabled(true);
        when(mapper.findAll(isNull(), isNull(), isNull(), isNull(), anyBoolean())).thenReturn(List.of(entity));
        when(mapper.findById(1L)).thenReturn(entity);

        McpCatalogResponse catalog = service.catalog();

        assertEquals("plain-db-password", catalog.databaseTargets().getFirst().get("password"));
    }

    @Test
    void catalog_shouldExposeBusinessLineSystemsKnowledgeAndGitlabSummaryWithoutToken() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        SysConfigService sysConfigService = mock(SysConfigService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                businessLineMapper,
                involvedSystemMapper,
                sysConfigService
        );
        McpResourceEntity entity = new McpResourceEntity();
        entity.setId(1L);
        entity.setResourceType("DATABASE");
        entity.setTargetKey("jiatai-hp-db-prod");
        entity.setBusinessLineCode("汇浦");
        entity.setEnvironmentCode("prod");
        entity.setName("汇浦生产库");
        entity.setHost("10.0.0.10");
        entity.setPort(3306);
        entity.setUsername("readonly");
        entity.setPasswordEncrypted(cryptoService.encrypt("plain-db-password"));
        entity.setAllowedServicesJson("[]");
        entity.setAllowedLogPathsJson("[]");
        entity.setProfilesJson("[{\"key\":\"readonly\",\"maxRows\":100,\"queryTimeoutSeconds\":10,\"maxResultBytes\":65536}]");
        entity.setEnabled(true);
        when(mapper.findAll(isNull(), isNull(), isNull(), isNull(), anyBoolean())).thenReturn(List.of(entity));
        when(mapper.findById(1L)).thenReturn(entity);
        BusinessLineEntity businessLine = new BusinessLineEntity();
        businessLine.setBusinessLineName("汇浦");
        businessLine.setGitlabGroupName("ca-assets");
        businessLine.setEnabled(true);
        when(businessLineMapper.findAll(null)).thenReturn(List.of(businessLine));
        when(involvedSystemMapper.findAll(isNull(), isNull(), anyBoolean(), isNull())).thenReturn(List.of(
                involvedSystem("BUSINESS_LINE", "汇浦", "assets-saps"),
                involvedSystem("MIDDLE_PLATFORM", "", "authing")
        ));
        when(sysConfigService.findPlainValue("knowledge.project", "vaultPath"))
                .thenReturn("/mnt/workhub/project-knowledge");
        when(sysConfigService.findPlainValue("gitlab.global", "webApiUrl"))
                .thenReturn("http://gitlab.example.com");
        when(sysConfigService.findPlainValue("gitlab.global", "sshHost"))
                .thenReturn("git@gitlab.example.com");
        when(sysConfigService.findPlainValue("gitlab.global", "accessToken"))
                .thenReturn("plain-gitlab-token");

        McpCatalogResponse catalog = service.catalog();

        Map<String, Object> businessLineSummary = catalog.businessLines().getFirst();
        assertEquals("ca-assets", businessLineSummary.get("gitlabGroupName"));
        assertEquals(List.of("assets-saps"), businessLineSummary.get("involvedSystems"));
        assertEquals(List.of("authing"), businessLineSummary.get("globalSystems"));
        assertEquals("/mnt/workhub/project-knowledge", catalog.knowledge().get("projectVaultPath"));
        assertEquals("http://gitlab.example.com", catalog.gitlab().get("webApiUrl"));
        assertEquals(true, catalog.gitlab().get("accessTokenConfigured"));
        assertTrue(!catalog.gitlab().containsValue("plain-gitlab-token"));
    }

    @Test
    void catalog_shouldUseBusinessLineCodeAndMatchSystemBindingsByCodeOrName() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpResourceSchemaInitializer schemaInitializer = mock(McpResourceSchemaInitializer.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                schemaInitializer,
                businessLineMapper,
                involvedSystemMapper,
                mock(SysConfigService.class)
        );
        when(mapper.findAll(isNull(), isNull(), isNull(), isNull(), anyBoolean())).thenReturn(List.of());
        BusinessLineEntity businessLine = businessLine("BL000007", "汇浦");
        businessLine.setGitlabGroupName("ca-assets");
        when(businessLineMapper.findAll(null)).thenReturn(List.of(businessLine));
        ProjectInvolvedSystemEntity codeBoundSystem = involvedSystem("BUSINESS_LINE", null, "assets-saps");
        codeBoundSystem.setBusinessLineCode("BL000007");
        ProjectInvolvedSystemEntity nameBoundSystem = involvedSystem("BUSINESS_LINE", "汇浦", "assets-admin");
        when(involvedSystemMapper.findAll(isNull(), isNull(), anyBoolean(), isNull())).thenReturn(List.of(
                codeBoundSystem,
                nameBoundSystem
        ));

        McpCatalogResponse catalog = service.catalog();

        Map<String, Object> businessLineSummary = catalog.businessLines().getFirst();
        assertEquals("BL000007", businessLineSummary.get("code"));
        assertEquals("汇浦", businessLineSummary.get("name"));
        assertEquals(List.of("assets-saps", "assets-admin"), businessLineSummary.get("involvedSystems"));
    }

    private McpResourceSaveRequest databaseRequest() {
        McpResourceSaveRequest request = new McpResourceSaveRequest();
        request.setResourceType("DATABASE");
        request.setTargetKey("crm-test-mysql");
        request.setBusinessLineCode("crm");
        request.setEnvironmentCode("test");
        request.setName("CRM 测试库");
        request.setHost("10.0.0.10");
        request.setPort(3306);
        request.setUsername("readonly");
        request.setPassword("plain-db-password");
        return request;
    }

    private McpResourceSaveRequest serverRequest() {
        McpResourceSaveRequest request = new McpResourceSaveRequest();
        request.setResourceType("SERVER");
        request.setBusinessLineCode("BL000007");
        request.setBusinessLineCodes(List.of("BL000007"));
        request.setEnvironmentCode("prod");
        request.setSystemNames(List.of("assets-saps"));
        request.setHost("10.0.1.20");
        request.setPort(22);
        request.setUsername("ops");
        request.setSshPassword("plain-ssh-password");
        return request;
    }

    private BusinessLineEntity businessLine(String code, String name) {
        BusinessLineEntity entity = new BusinessLineEntity();
        entity.setBusinessLineCode(code);
        entity.setBusinessLineName(name);
        entity.setEnabled(true);
        return entity;
    }

    private McpProperties testProperties() {
        McpProperties properties = new McpProperties();
        properties.setMasterKey("test-mcp-master-key");
        return properties;
    }

    private ProjectInvolvedSystemEntity involvedSystem(String scope, String businessLine, String systemName) {
        ProjectInvolvedSystemEntity entity = new ProjectInvolvedSystemEntity();
        entity.setSystemScope(scope);
        entity.setBusinessLine(businessLine);
        entity.setSystemName(systemName);
        entity.setEnabled(true);
        return entity;
    }
}
