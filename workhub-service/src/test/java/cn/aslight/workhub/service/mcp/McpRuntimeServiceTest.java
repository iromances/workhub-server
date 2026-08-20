package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.dao.project.BusinessLineAccessConfigMapper;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
import cn.aslight.workhub.model.project.BusinessLineAccessConfigEntity;
import cn.aslight.workhub.service.intake.GitlabRepositoryService;
import cn.aslight.workhub.service.intake.IntakeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void handleHttp_shouldNegotiateCurrentProtocolAndReturnInstructions() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        McpRuntimeService.HttpResponse httpResponse = runtimeService.handleHttp("""
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-11-25","capabilities":{},"clientInfo":{"name":"codex","version":"0.144.2"}}}
                """, null);
        JsonNode response = objectMapper.readTree(httpResponse.body());

        assertEquals(200, httpResponse.status());
        assertEquals("2025-11-25", response.at("/result/protocolVersion").asText());
        assertTrue(response.at("/result/instructions").asText().contains("数据库只允许只读 SQL"));
    }

    @Test
    void handleHttp_shouldAcceptInitializedNotificationWithoutResponseBody() {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        McpRuntimeService.HttpResponse response = runtimeService.handleHttp("""
                {"jsonrpc":"2.0","method":"notifications/initialized","params":{}}
                """, "2025-11-25");

        assertEquals(202, response.status());
        assertNull(response.body());
    }

    @Test
    void handleHttp_shouldReturnJsonRpcErrorsForMalformedJsonAndUnsupportedProtocol() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        McpRuntimeService.HttpResponse malformed = runtimeService.handleHttp("{", null);
        McpRuntimeService.HttpResponse unsupported = runtimeService.handleHttp("""
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """, "2024-11-05");
        McpRuntimeService.HttpResponse codexPreview = runtimeService.handleHttp("""
                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                """, "2026-07-28");

        assertEquals(400, malformed.status());
        assertEquals(-32700, objectMapper.readTree(malformed.body()).at("/error/code").asInt());
        assertEquals(400, unsupported.status());
        assertEquals(-32602, objectMapper.readTree(unsupported.body()).at("/error/code").asInt());
        assertEquals(200, codexPreview.status());
    }

    @Test
    void handle_shouldReturnToolExecutionErrorsAsToolResults() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"unknown_tool","arguments":{}}}
                """));

        assertTrue(response.at("/result/isError").asBoolean());
        assertTrue(response.at("/result/content/0/text").asText().contains("未知 MCP 工具"));
    }

    @Test
    void handle_shouldListTargetsFromCurrentWorkhubCatalog() throws Exception {
        McpResourceService resourceService = resourceService(new McpCatalogResponse(
                List.of(Map.of(
                        "code", "汇浦",
                        "name", "汇浦",
                        "gitlabGroupName", "ca-assets",
                        "involvedSystems", List.of("assets-saps"),
                        "globalSystems", List.of("authing"),
                        "enabled", true
                )),
                List.of(Map.of("code", "prod", "name", "prod", "production", true, "enabled", true)),
                List.of(databaseTarget("jiatai-hp-db-prod", "汇浦")),
                List.of(),
                Map.of("projectVaultPath", "/mnt/workhub/project-knowledge"),
                Map.of(
                        "webApiUrl", "http://gitlab.example.com",
                        "sshHost", "git@gitlab.example.com",
                        "accessTokenConfigured", true
                )
        ));
        McpRuntimeService runtimeService = new McpRuntimeService(resourceService, new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_mcp_targets","arguments":{}}}
                """));

        assertEquals("2.0", response.path("jsonrpc").asText());
        assertEquals(1, response.path("id").asInt());
        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("jiatai-hp-db-prod"));
        assertTrue(text.contains("汇浦"));
        assertTrue(text.contains("ca-assets"));
        assertTrue(text.contains("projectVaultPath"));
        assertTrue(text.contains("accessTokenConfigured"));
    }

    @Test
    void handle_shouldReturnBusinessLineContextWithoutGitlabToken() throws Exception {
        McpResourceService resourceService = resourceService(new McpCatalogResponse(
                List.of(Map.of(
                        "code", "汇浦",
                        "name", "汇浦",
                        "gitlabGroupName", "ca-assets",
                        "involvedSystems", List.of("assets-saps", "assets-lease-admin"),
                        "globalSystems", List.of("authing"),
                        "enabled", true
                )),
                List.of(Map.of("code", "prod", "name", "prod", "production", true, "enabled", true)),
                List.of(databaseTarget("jiatai-hp-db-prod", "汇浦")),
                List.of(),
                Map.of("projectVaultPath", "/mnt/workhub/project-knowledge"),
                Map.of(
                        "webApiUrl", "http://gitlab.example.com",
                        "sshHost", "git@gitlab.example.com",
                        "accessTokenConfigured", true
                )
        ));
        McpRuntimeService runtimeService = new McpRuntimeService(resourceService, new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_business_line_context","arguments":{"businessLine":"汇浦"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("ca-assets"));
        assertTrue(text.contains("assets-saps"));
        assertTrue(text.contains("jiatai-hp-db-prod"));
        assertTrue(text.contains("data/git-cache/ca-assets"));
        assertTrue(text.contains("projectVaultPath"));
        assertTrue(text.contains("accessTokenConfigured"));
        assertTrue(!text.contains("plain-gitlab-token"));
    }

    @Test
    void handle_shouldExposePublicResourceAndFeatureTagsInEveryBusinessLineContext() throws Exception {
        McpResourceService resourceService = resourceService(new McpCatalogResponse(
                List.of(Map.of(
                        "code", "BL000001",
                        "name", "保费分期",
                        "gitlabGroupName", "scf",
                        "involvedSystems", List.of("scf-order"),
                        "globalSystems", List.of(),
                        "enabled", true
                )),
                List.of(Map.of("code", "test", "name", "测试环境", "production", false, "enabled", true)),
                List.of(publicDatabaseTarget("public-test-db-zentao", "禅道")),
                List.of(),
                Map.of(),
                Map.of()
        ));
        McpRuntimeService runtimeService = new McpRuntimeService(
                resourceService,
                new FakeIntakeService(),
                new FakeGitlabRepositoryService()
        );

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":20,"method":"tools/call","params":{"name":"get_business_line_context","arguments":{"businessLine":"BL000001"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("public-test-db-zentao"));
        assertTrue(text.contains("\"publicResource\" : true"));
        assertTrue(text.contains("\"featureTags\" : [ \"禅道\" ]"));
    }

    @Test
    void handle_shouldListIntakeToolsInHttpRuntime() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}
                """));

        String text = response.at("/result/tools").toString();
        assertTrue(text.contains("get_intake_detail"));
        assertTrue(text.contains("search_intakes"));
        assertTrue(text.contains("get_requirement_test_context"));
        assertTrue(text.contains("get_business_line_test_context"));
        assertTrue(text.contains("sync_gitlab_group_repositories"));
    }

    @Test
    void handle_shouldReadIntakeDetailByIdWithoutRecordingViewHistory() throws Exception {
        FakeIntakeService intakeService = new FakeIntakeService();
        intakeService.detailResponse = new IntakeDetailResponse(
                88L,
                "UPLOAD",
                "需求管理",
                null,
                "张三",
                "李四",
                LocalDateTime.parse("2026-06-01T10:00:00"),
                "待澄清",
                "需求原始内容",
                structuredData(),
                List.of("workhub-server"),
                List.of(),
                List.of(),
                "已确认",
                "SUCCEEDED",
                null,
                LocalDateTime.parse("2026-06-01T10:05:00"),
                null,
                List.of(),
                List.of(),
                null,
                LocalDateTime.parse("2026-06-01T10:00:00"),
                LocalDateTime.parse("2026-06-01T10:05:00")
        );
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), intakeService, new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":4,"method":"tools/call","params":{"name":"get_intake_detail","arguments":{"intakeId":"88"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("\"id\" : 88"));
        assertTrue(text.contains("SP-20260601"));
        assertTrue(text.contains("新增 MCP 需求读取"));
        assertEquals(88L, intakeService.detailIntakeId);
        assertEquals(false, intakeService.detailRecordView);
    }

    @Test
    void handle_shouldSearchIntakesByApprovalCodeAndRequirementName() throws Exception {
        FakeIntakeService intakeService = new FakeIntakeService();
        intakeService.summaryResponses = List.of(summary(88L));
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), intakeService, new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":5,"method":"tools/call","params":{"name":"search_intakes","arguments":{"approvalCode":"SP-20260601","requirementName":"MCP","limit":"5"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("\"id\" : 88"));
        assertTrue(text.contains("SP-20260601"));
        assertTrue(text.contains("MCP 需求读取"));
        assertEquals("MCP", intakeService.searchRequirementName);
        assertEquals("SP-20260601", intakeService.searchApprovalCode);
    }

    @Test
    void handle_shouldRejectIntakeSearchWithoutCriteria() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":6,"method":"tools/call","params":{"name":"search_intakes","arguments":{}}}
                """));

        assertTrue(response.at("/result/isError").asBoolean());
        assertTrue(response.at("/result/content/0/text").asText()
                .contains("审批编号、需求名称或摘要关键词至少填写一个"));
    }

    @Test
    void handle_shouldResolveRequirementTestContextFromRequirementAndCatalog() throws Exception {
        McpResourceService resourceService = resourceService(new McpCatalogResponse(
                List.of(Map.of(
                        "code", "BL000007",
                        "name", "汇浦",
                        "gitlabGroupName", "ca-assets",
                        "involvedSystems", List.of("assets-saps"),
                        "globalSystems", List.of("authing"),
                        "enabled", true
                )),
                List.of(Map.of("code", "test", "name", "测试环境", "production", false, "enabled", true)),
                List.of(
                        databaseTarget("jiatai-hp-db-test", "BL000007", "test", "assets-saps"),
                        databaseTarget("unrelated-db-test", "BL000007", "test", "assets-admin")
                ),
                List.of(serverTarget("jiatai-hp-server-test", "BL000007", "test", "assets-saps")),
                Map.of("projectVaultPath", "/mnt/workhub/project-knowledge"),
                Map.of(
                        "webApiUrl", "http://gitlab.example.com",
                        "sshHost", "git@gitlab.example.com",
                        "accessTokenConfigured", true
                )
        ));
        FakeIntakeService intakeService = new FakeIntakeService();
        intakeService.summaryResponses = List.of(summary(88L));
        intakeService.detailResponse = new IntakeDetailResponse(
                88L,
                "UPLOAD",
                "需求管理",
                null,
                "张三",
                "李四",
                LocalDateTime.parse("2026-06-01T10:00:00"),
                "测试中",
                "需求原始内容",
                structuredData(),
                List.of("assets-saps"),
                List.of(),
                List.of(),
                "已确认",
                "SUCCEEDED",
                null,
                LocalDateTime.parse("2026-06-01T10:05:00"),
                null,
                List.of(),
                List.of(),
                null,
                LocalDateTime.parse("2026-06-01T10:00:00"),
                LocalDateTime.parse("2026-06-01T10:05:00")
        );
        BusinessLineAccessConfigMapper accessConfigMapper = new FakeBusinessLineAccessConfigMapper(List.of(
                accessConfig("BL000007", "test", "OPERATIONS", "运营端",
                        "https://ops.example.test", null, true)
        ));
        McpRuntimeService runtimeService = new McpRuntimeService(
                resourceService,
                intakeService,
                new FakeGitlabRepositoryService(),
                accessConfigMapper
        );

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":8,"method":"tools/call","params":{"name":"get_requirement_test_context","arguments":{"approvalCode":"SP-20260601","environmentCode":"test"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertTrue(text.contains("\"resolutionStatus\" : \"RESOLVED\""));
        assertTrue(text.contains("\"developmentBranchName\" : \"feature/demo\""));
        assertTrue(text.contains("\"code\" : \"BL000007\""));
        assertTrue(text.contains("\"jiatai-hp-db-test\""));
        assertTrue(!text.contains("unrelated-db-test"));
        assertTrue(text.contains("\"jiatai-hp-server-test\""));
        assertTrue(text.contains("\"profiles\" : [ \"readonly\" ]"));
        assertTrue(text.contains("\"profiles\" : [ \"diagnostic\" ]"));
        assertTrue(text.contains("\"codeCacheRoot\" : \"data/git-cache/ca-assets\""));
        assertTrue(text.contains("\"accessEndpoints\""));
        assertTrue(text.contains("\"effectiveUrl\" : \"https://ops.example.test\""));
        assertTrue(!text.contains("plain-gitlab-token"));
        assertEquals(88L, intakeService.detailIntakeId);
        assertEquals(false, intakeService.detailRecordView);
    }

    @Test
    void handle_shouldReturnEnabledAccessEndpointsForRequestedEnvironment() throws Exception {
        McpResourceService resourceService = resourceService(new McpCatalogResponse(
                List.of(Map.of(
                        "code", "BL000009",
                        "name", "创新保理",
                        "gitlabGroupName", "ca-assets",
                        "involvedSystems", List.of("assets-saps"),
                        "globalSystems", List.of(),
                        "enabled", true
                )),
                List.of(
                        Map.of("code", "test", "name", "测试环境", "production", false, "enabled", true),
                        Map.of("code", "prod", "name", "生产环境", "production", true, "enabled", true)
                ),
                List.of(),
                List.of(),
                Map.of(),
                Map.of()
        ));
        BusinessLineAccessConfigMapper accessConfigMapper = new FakeBusinessLineAccessConfigMapper(List.of(
                accessConfig("BL000009", "test", "OPERATIONS", "运营端", "https://ops.example.test", null, true),
                accessConfig("BL000009", "test", "GATEWAY", "统一网关", "https://gateway.example.test/", "/asset-api", true),
                accessConfig("BL000009", "prod", "GATEWAY", "生产网关", "https://gateway.example.com", "/asset-api", true),
                accessConfig("BL000009", "test", "CLIENT", "停用客户端", "https://disabled.example.test", null, false)
        ));
        McpRuntimeService runtimeService = new McpRuntimeService(
                resourceService,
                new FakeIntakeService(),
                new FakeGitlabRepositoryService(),
                accessConfigMapper
        );

        JsonNode testResponse = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":9,"method":"tools/call","params":{"name":"get_business_line_test_context","arguments":{"businessLine":"BL000009","environmentCode":"test"}}}
                """));
        JsonNode testContext = objectMapper.readTree(testResponse.at("/result/content/0/text").asText())
                .path("testContext");

        assertEquals(2, testContext.path("accessEndpoints").size());
        assertEquals("https://ops.example.test",
                testContext.at("/accessEndpoints/0/effectiveUrl").asText());
        assertEquals("/asset-api", testContext.at("/accessEndpoints/1/pathPrefix").asText());
        assertEquals("https://gateway.example.test/asset-api",
                testContext.at("/accessEndpoints/1/effectiveUrl").asText());
        assertTrue(testContext.path("accessEndpoints").toString().contains("OPERATIONS"));
        assertTrue(!testContext.path("accessEndpoints").toString().contains("生产网关"));
        assertTrue(!testContext.path("accessEndpoints").toString().contains("停用客户端"));

        JsonNode generalResponse = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":10,"method":"tools/call","params":{"name":"get_business_line_context","arguments":{"businessLine":"BL000009"}}}
                """));
        JsonNode generalContext = objectMapper.readTree(generalResponse.at("/result/content/0/text").asText());
        assertEquals(3, generalContext.path("accessEndpoints").size());
        assertTrue(generalContext.path("accessEndpoints").toString().contains("生产网关"));
    }

    @Test
    void handle_shouldSyncGitlabGroupRepositoriesByBusinessLineWithoutReturningToken() throws Exception {
        FakeGitlabRepositoryService gitlabRepositoryService = new FakeGitlabRepositoryService();
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), gitlabRepositoryService);

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"sync_gitlab_group_repositories","arguments":{"businessLine":"汇浦"}}}
                """));

        String text = response.at("/result/content/0/text").asText();
        assertEquals("汇浦", gitlabRepositoryService.syncedBusinessLine);
        assertTrue(text.contains("\"gitlabGroupName\" : \"ca-assets\""));
        assertTrue(text.contains("\"repositoryCount\" : 1"));
        assertTrue(text.contains("/tmp/workhub-git-cache/ca-assets/assets-saps"));
        assertTrue(!text.contains("plain-gitlab-token"));
    }

    private Map<String, Object> databaseTarget(String key, String businessLine) {
        return databaseTarget(key, businessLine, "prod");
    }

    private Map<String, Object> databaseTarget(String key, String businessLine, String environmentCode) {
        return databaseTarget(key, businessLine, environmentCode, null);
    }

    private Map<String, Object> databaseTarget(String key,
                                               String businessLine,
                                               String environmentCode,
                                               String systemName) {
        Map<String, Object> target = new java.util.LinkedHashMap<>();
        target.put("key", key);
        target.put("businessLineCode", businessLine);
        target.put("businessLineCodes", List.of(businessLine));
        target.put("environmentCode", environmentCode);
        target.put("name", businessLine + "-生产库");
        if (systemName != null) {
            target.put("systemName", systemName);
            target.put("systemNames", List.of(systemName));
        }
        target.put("host", "127.0.0.1");
        target.put("port", 3306);
        target.put("username", "readonly");
        target.put("password", "secret");
        target.put("profiles", List.of(Map.of(
                        "key", "readonly",
                        "maxRows", 100,
                        "queryTimeoutSeconds", 10,
                        "maxResultBytes", 65536
                )));
        return target;
    }

    private Map<String, Object> publicDatabaseTarget(String key, String featureTag) {
        Map<String, Object> target = databaseTarget(key, "", "test", null);
        target.put("publicResource", true);
        target.put("featureTags", List.of(featureTag));
        target.put("businessLineCode", "");
        target.put("businessLineCodes", List.of());
        target.put("name", featureTag + "公共数据库");
        return target;
    }

    private Map<String, Object> serverTarget(String key, String businessLine, String environmentCode, String systemName) {
        Map<String, Object> target = new java.util.LinkedHashMap<>();
        target.put("key", key);
        target.put("businessLineCode", businessLine);
        target.put("businessLineCodes", List.of(businessLine));
        target.put("environmentCode", environmentCode);
        target.put("name", businessLine + "-测试服务");
        target.put("systemName", systemName);
        target.put("systemNames", List.of(systemName));
        target.put("host", "127.0.0.1");
        target.put("port", 22);
        target.put("username", "ops");
        target.put("password", "secret");
        target.put("allowedServices", List.of(systemName));
        target.put("allowedLogPaths", List.of("/data/logs/" + systemName + "/app.log"));
        target.put("profiles", List.of(Map.of(
                "key", "diagnostic",
                "maxOutputLines", 500,
                "timeoutSeconds", 10
        )));
        return target;
    }

    private BusinessLineAccessConfigEntity accessConfig(String businessLineCode,
                                                        String environmentCode,
                                                        String endpointType,
                                                        String endpointName,
                                                        String endpointUrl,
                                                        String pathPrefix,
                                                        boolean enabled) {
        BusinessLineAccessConfigEntity entity = new BusinessLineAccessConfigEntity();
        entity.setBusinessLineCode(businessLineCode);
        entity.setEnvironmentCode(environmentCode);
        entity.setEndpointType(endpointType);
        entity.setEndpointName(endpointName);
        entity.setEndpointUrl(endpointUrl);
        entity.setPathPrefix(pathPrefix);
        entity.setEnabled(enabled);
        return entity;
    }

    private McpResourceService emptyResourceService() {
        return resourceService(new McpCatalogResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                Map.of()
        ));
    }

    private McpResourceService resourceService(McpCatalogResponse catalog) {
        return new FakeMcpResourceService(catalog);
    }

    private IntakeSummaryResponse summary(Long id) {
        return new IntakeSummaryResponse(
                id,
                "UPLOAD",
                "需求管理",
                "张三",
                "李四",
                LocalDateTime.parse("2026-06-01T10:00:00"),
                "待澄清",
                "张三",
                "SP-20260601",
                "2026-06-01",
                "研发需求",
                "高",
                "feature/mcp",
                null,
                "摘要",
                "研发部",
                "MCP 需求读取",
                "让 MCP 支持需求内容读取",
                "汇浦",
                "BL000007",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "WorkHub",
                List.of("workhub-server"),
                "SUCCEEDED",
                "已确认",
                null,
                0L
        );
    }

    private IntakeStructuredData structuredData() {
        return new IntakeStructuredData(
                "需求审批",
                "审批标题",
                "张三",
                "李四",
                "SP-20260601",
                "2026-06-01",
                "研发需求",
                "feature/demo",
                null,
                "摘要",
                "新增 MCP 需求读取",
                "让 MCP 读取需求详情",
                "研发部",
                "汇浦",
                "BL000007",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "WorkHub",
                List.of(),
                List.of(),
                null
        );
    }

    private static class FakeMcpResourceService extends McpResourceService {
        private final McpCatalogResponse catalog;

        FakeMcpResourceService(McpCatalogResponse catalog) {
            super(null, null, null, null, null, null);
            this.catalog = catalog;
        }

        @Override
        public McpCatalogResponse catalog() {
            return catalog;
        }
    }

    private static class FakeBusinessLineAccessConfigMapper implements BusinessLineAccessConfigMapper {
        private final List<BusinessLineAccessConfigEntity> configs;

        private FakeBusinessLineAccessConfigMapper(List<BusinessLineAccessConfigEntity> configs) {
            this.configs = configs;
        }

        @Override
        public List<BusinessLineAccessConfigEntity> findByBusinessLineCodes(List<String> businessLineCodes) {
            return configs.stream()
                    .filter(config -> businessLineCodes.contains(config.getBusinessLineCode()))
                    .toList();
        }

        @Override
        public int deleteByBusinessLineCode(String businessLineCode) {
            throw new UnsupportedOperationException("测试只读 Mapper 不支持删除");
        }

        @Override
        public int insert(BusinessLineAccessConfigEntity entity) {
            throw new UnsupportedOperationException("测试只读 Mapper 不支持新增");
        }
    }

    private class FakeIntakeService extends IntakeService {
        private IntakeDetailResponse detailResponse;
        private List<IntakeSummaryResponse> summaryResponses = List.of();
        private Long detailIntakeId;
        private boolean detailRecordView;
        private String searchRequirementName;
        private String searchApprovalCode;

        FakeIntakeService() {
            super(null, null, null, null, null, null, null, null, null, null);
        }

        @Override
        public IntakeDetailResponse detail(Long id, String operatorUserName, boolean recordView) {
            this.detailIntakeId = id;
            this.detailRecordView = recordView;
            return detailResponse;
        }

        @Override
        public List<IntakeSummaryResponse> list(String status,
                                                String requirementName,
                                                String approvalCode,
                                                String proposerName,
                                                String businessLine,
                                                String requirementType,
                                                String demandStatus,
                                                String releasedStartDate,
                                                String releasedEndDate) {
            this.searchRequirementName = requirementName;
            this.searchApprovalCode = approvalCode;
            return summaryResponses;
        }
    }

    private static class FakeGitlabRepositoryService extends GitlabRepositoryService {
        private String syncedBusinessLine;

        FakeGitlabRepositoryService() {
            super(null, null, null);
        }

        @Override
        public GitlabRepositoryBundle resolveAndFetchGroup(String businessLine) {
            this.syncedBusinessLine = businessLine;
            return new GitlabRepositoryBundle(
                    "ca-assets",
                    Path.of("/tmp/workhub-git-cache/ca-assets"),
                    List.of(new GitlabRepository(
                            "https://gitlab.example.com/ca-assets/assets-saps.git",
                            Path.of("/tmp/workhub-git-cache/ca-assets/assets-saps")
                    ))
            );
        }
    }
}
