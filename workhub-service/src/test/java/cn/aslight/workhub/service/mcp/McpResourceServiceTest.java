package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.config.McpProperties;
import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.mcp.McpAuditPageResponse;
import cn.aslight.workhub.model.mcp.McpBastionEntity;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpResourceResponse;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpResourceServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void auditEntries_shouldReturnNewestFirstPagedResultsAndValidTotal() throws Exception {
        McpResourceService service = new McpResourceService(
                mock(McpResourceMapper.class),
                new McpCryptoService(testProperties()),
                mock(McpBastionService.class),
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        Path auditLog = tempDir.resolve("mcp-audit.jsonl");
        Files.write(auditLog, List.of(
                "{\"timestamp\":\"2026-07-21T10:00:01Z\",\"tool\":\"tool-1\"}",
                "{\"timestamp\":\"2026-07-21T10:00:02Z\",\"tool\":\"tool-2\"}",
                "",
                "{\"timestamp\":\"2026-07-21T10:00:03Z\",\"tool\":\"tool-3\"}",
                "{\"timestamp\":\"2026-07-21T10:00:04Z\",\"tool\":\"tool-4\"}",
                "{\"timestamp\":\"2026-07-21T10:00:05Z\",\"tool\":\"tool-5\"}"
        ));

        McpAuditPageResponse page = service.auditEntries(auditLog, 2, 2);

        assertEquals(5, page.total());
        assertEquals(2, page.items().size());
        assertEquals("tool-3", page.items().get(0).fields().get("tool"));
        assertEquals("tool-2", page.items().get(1).fields().get("tool"));
    }

    @Test
    void createDatabase_shouldEncryptPasswordAndExposeOnlyConfiguredFlag() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        request.setSystemNames(List.of("crm-api", "crm-admin"));

        McpResourceResponse response = service.create(request);

        ArgumentCaptor<McpResourceEntity> captor = ArgumentCaptor.forClass(McpResourceEntity.class);
        verify(mapper).insert(captor.capture());
        assertNotEquals("plain-db-password", captor.getValue().getPasswordEncrypted());
        assertTrue(captor.getValue().getPasswordEncrypted().startsWith("mcp:v1:"));
        assertEquals("[\"crm-api\",\"crm-admin\"]", captor.getValue().getSystemName());
        assertEquals(List.of("crm-api", "crm-admin"), response.systemNames());
        assertTrue(response.passwordConfigured());
    }

    @Test
    void createDatabase_shouldPersistSystemNamesLongerThanLegacyColumnLimit() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                new McpCryptoService(testProperties()),
                mock(McpBastionService.class),
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
        request.setSystemNames(List.of(
                "scf-intf-zhonghua", "scf-merchant-gateway", "scf-front-gateway",
                "scf_h5", "scf_channel_admin", "scf-ops-reporting", "scf-saps",
                "scf-payment", "scf-order", "scf-op-gateway", "scf-intf-common",
                "scf-gateway", "scf_admin"
        ));

        McpResourceResponse response = service.create(request);

        assertTrue(saved.get().getSystemName().length() > 128);
        assertEquals(13, response.systemNames().size());
        assertEquals("scf-order", response.systemNames().get(8));
    }

    @Test
    void createDatabase_shouldGenerateTargetKeyAndNameWhenBlank() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
    void createDatabase_shouldAllowPublicResourceWithoutBusinessLineAndExposeFeatureTags() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                new McpCryptoService(testProperties()),
                mock(McpBastionService.class),
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        when(mapper.findByTargetKey("public-test-db-10-0-0-10-3306")).thenReturn(null);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(9L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(9L)).thenAnswer(invocation -> saved.get());

        McpResourceSaveRequest request = databaseRequest();
        request.setTargetKey(null);
        request.setName(null);
        request.setPublicResource(true);
        request.setBusinessLineCode(null);
        request.setBusinessLineCodes(List.of());
        request.setFeatureTags(List.of(" 禅道 ", "支付", "禅道"));

        McpResourceResponse response = service.create(request);

        assertTrue(response.publicResource());
        assertEquals(List.of("禅道", "支付"), response.featureTags());
        assertEquals(List.of(), response.businessLineCodes());
        assertEquals("", saved.get().getBusinessLineCode());
        assertEquals("[\"禅道\",\"支付\"]", saved.get().getFeatureTagsJson());
        assertEquals("public-test-db-10-0-0-10-3306", response.targetKey());
        assertEquals("公共资源 test 数据库目标 10.0.0.10:3306", response.name());
        verify(mapper, never()).insertBusinessLineBinding(anyLong(), anyString());
    }

    @Test
    void createDatabase_shouldPersistSelectedBastionId() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        AtomicReference<McpResourceEntity> saved = new AtomicReference<>();
        McpBastionEntity bastion = new McpBastionEntity();
        bastion.setId(8L);
        bastion.setName("生产堡垒机");
        bastion.setHost("10.10.0.8");
        bastion.setPort(22);
        bastion.setUsername("workhub");
        bastion.setEnabled(true);
        when(mapper.findByTargetKey("crm-test-mysql")).thenReturn(null);
        when(bastionService.requireEnabled(8L)).thenReturn(bastion);
        when(bastionService.requireExisting(8L)).thenReturn(bastion);
        org.mockito.Mockito.doAnswer(invocation -> {
            McpResourceEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            saved.set(entity);
            return null;
        }).when(mapper).insert(any());
        when(mapper.findById(1L)).thenAnswer(invocation -> saved.get());
        McpResourceSaveRequest request = databaseRequest();
        request.setSshBastionEnabled(true);
        request.setBastionId(8L);

        McpResourceResponse response = service.create(request);

        assertEquals(8L, saved.get().getBastionId());
        assertEquals(8L, response.bastionId());
        assertEquals("生产堡垒机", response.bastionName());
    }

    @Test
    void createServer_shouldGenerateNextAvailableTargetKeyWhenBaseKeyExists() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        request.setAllowedLogPaths(List.of(" /data/logs/assets-saps/ ", "/opt/services/payment/logs"));

        McpResourceResponse response = service.create(request);

        assertEquals("bl000007-prod-server-10-0-1-20-22-2", response.targetKey());
        assertEquals("汇浦 prod 服务器目标 10.0.1.20:22", response.name());
        assertEquals(List.of("/data/logs/assets-saps", "/opt/services/payment/logs"), response.allowedLogPaths());
    }

    @Test
    void createServer_shouldRejectRelativeLogDirectoryWhitelist() {
        McpResourceService service = new McpResourceService(
                mock(McpResourceMapper.class),
                new McpCryptoService(testProperties()),
                mock(McpBastionService.class),
                mock(BusinessLineMapper.class),
                mock(ProjectInvolvedSystemMapper.class),
                mock(SysConfigService.class)
        );
        McpResourceSaveRequest request = serverRequest();
        request.setAllowedLogPaths(List.of("logs/assets-saps"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(request));

        assertEquals("日志目录白名单必须配置绝对路径：logs/assets-saps", ex.getMessage());
    }

    @Test
    void createServer_shouldAllowBlankSystemNamesAsAllSystemsInBusinessLine() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        McpBastionService bastionService = mock(McpBastionService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        entity.setSystemName("[\"crm-api\",\"crm-admin\"]");
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
        assertEquals("crm-api", catalog.databaseTargets().getFirst().get("systemName"));
        assertEquals(List.of("crm-api", "crm-admin"), catalog.databaseTargets().getFirst().get("systemNames"));
    }

    @Test
    void catalog_shouldResolveDatabaseTunnelFromSelectedBastion() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        entity.setUsername("readonly");
        entity.setPasswordEncrypted(cryptoService.encrypt("plain-db-password"));
        entity.setSshBastionEnabled(true);
        entity.setBastionId(8L);
        entity.setAllowedServicesJson("[]");
        entity.setAllowedLogPathsJson("[]");
        entity.setProfilesJson("[{\"key\":\"readonly\",\"maxRows\":100,\"queryTimeoutSeconds\":10,\"maxResultBytes\":65536}]");
        entity.setEnabled(true);
        McpBastionEntity bastion = new McpBastionEntity();
        bastion.setId(8L);
        bastion.setName("生产堡垒机");
        bastion.setHost("10.10.0.8");
        bastion.setPort(22);
        bastion.setUsername("workhub");
        bastion.setPasswordEncrypted(cryptoService.encrypt("plain-bastion-password"));
        bastion.setEnabled(true);
        when(mapper.findAll(isNull(), isNull(), isNull(), isNull(), anyBoolean())).thenReturn(List.of(entity));
        when(mapper.findById(1L)).thenReturn(entity);
        when(bastionService.requireExisting(8L)).thenReturn(bastion);
        when(bastionService.decryptPassword(bastion)).thenReturn("plain-bastion-password");

        McpCatalogResponse catalog = service.catalog();

        @SuppressWarnings("unchecked")
        Map<String, Object> tunnel = (Map<String, Object>) catalog.databaseTargets().getFirst().get("sshTunnel");
        assertEquals("10.10.0.8", tunnel.get("bastionHost"));
        assertEquals("workhub", tunnel.get("bastionUser"));
        assertEquals("plain-bastion-password", tunnel.get("password"));
    }

    @Test
    void catalog_shouldExposeBusinessLineSystemsKnowledgeAndGitlabSummaryWithoutToken() {
        McpResourceMapper mapper = mock(McpResourceMapper.class);
        McpCryptoService cryptoService = new McpCryptoService(testProperties());
        McpBastionService bastionService = mock(McpBastionService.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        SysConfigService sysConfigService = mock(SysConfigService.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
        McpBastionService bastionService = mock(McpBastionService.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        ProjectInvolvedSystemMapper involvedSystemMapper = mock(ProjectInvolvedSystemMapper.class);
        McpResourceService service = new McpResourceService(
                mapper,
                cryptoService,
                bastionService,
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
