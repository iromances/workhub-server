package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.intake.IntakeDetailResponse;
import cn.aslight.workhub.model.intake.IntakeStructuredData;
import cn.aslight.workhub.model.intake.IntakeSummaryResponse;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpRuntimeServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    void handle_shouldListIntakeToolsInHttpRuntime() throws Exception {
        McpRuntimeService runtimeService = new McpRuntimeService(emptyResourceService(), new FakeIntakeService(), new FakeGitlabRepositoryService());

        JsonNode response = runtimeService.handle(objectMapper.readTree("""
                {"jsonrpc":"2.0","id":3,"method":"tools/list","params":{}}
                """));

        String text = response.at("/result/tools").toString();
        assertTrue(text.contains("get_intake_detail"));
        assertTrue(text.contains("search_intakes"));
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

        assertEquals(-32603, response.at("/error/code").asInt());
        assertTrue(response.at("/error/message").asText().contains("审批编号或需求名称至少填写一个"));
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
        return Map.of(
                "key", key,
                "businessLineCode", businessLine,
                "businessLineCodes", List.of(businessLine),
                "environmentCode", "prod",
                "name", businessLine + "-生产库",
                "host", "127.0.0.1",
                "port", 3306,
                "username", "readonly",
                "password", "secret",
                "profiles", List.of(Map.of(
                        "key", "readonly",
                        "maxRows", 100,
                        "queryTimeoutSeconds", 10,
                        "maxResultBytes", 65536
                ))
        );
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
