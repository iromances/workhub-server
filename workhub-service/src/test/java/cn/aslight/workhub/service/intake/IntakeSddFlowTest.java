package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeSubmitTestReviewMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSubmitTestConfirmRequest;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewEntity;
import cn.aslight.workhub.model.intake.IntakeTestDeployment;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SDD 研发规范、审核员与提测审核链路的定向测试。
 */
class IntakeSddFlowTest {

    @TempDir
    Path tempDir;

    @Test
    void createUploaded_shouldPersistDevelopmentStandardWithSddDefault() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        AttachmentService attachmentService = mock(AttachmentService.class);
        IntakeEnrichmentService enrichmentService = mock(IntakeEnrichmentService.class);
        IntakeService service = new IntakeService(
                intakeMapper,
                mock(IntakeHistoryMapper.class),
                mock(IntakeWorkItemRelationMapper.class),
                mock(IntakeTodoMapper.class),
                mock(IntakeStructuredFieldMapper.class),
                null,
                attachmentService,
                new IntakeStructuredDataExtractor(),
                new IntakeStructuredFieldNormalizer(),
                enrichmentService,
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper(),
                null,
                null
        );
        when(intakeMapper.findById(any())).thenReturn(null);
        IntakeUploadRequest request = new IntakeUploadRequest();
        request.setSenderName("张三");
        request.setDevelopmentOwnerUserName("shihao");
        request.setRawContent("SDD 规范需求内容");
        request.setDevelopmentStandard(null);

        assertThrows(Exception.class, () -> service.createUploaded(request, List.of(), List.of()));
        ArgumentCaptor<IntakeRecordEntity> captor = ArgumentCaptor.forClass(IntakeRecordEntity.class);
        verify(intakeMapper).insert(captor.capture());
        assertEquals("SDD", captor.getValue().getDevelopmentStandard());
    }

    @Test
    void createUploaded_shouldRejectUnknownDevelopmentStandard() {
        IntakeService service = new IntakeService(
                mock(IntakeMapper.class),
                mock(IntakeHistoryMapper.class),
                mock(IntakeWorkItemRelationMapper.class),
                mock(IntakeTodoMapper.class),
                mock(IntakeStructuredFieldMapper.class),
                null,
                mock(AttachmentService.class),
                new IntakeStructuredDataExtractor(),
                new IntakeStructuredFieldNormalizer(),
                mock(IntakeEnrichmentService.class),
                mock(CodexCliSqlDraftGenerator.class),
                null,
                new ObjectMapper(),
                null,
                null
        );
        IntakeUploadRequest request = new IntakeUploadRequest();
        request.setSenderName("张三");
        request.setDevelopmentOwnerUserName("shihao");
        request.setRawContent("内容");
        request.setDevelopmentStandard("UNKNOWN");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.createUploaded(request, List.of(), List.of()));
        assertTrue(error.getMessage().contains("研发规范"));
    }

    @Test
    void readSddProcessSpec_shouldPreferRepositoryDocsThenVaultConfig() throws Exception {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(
                sysConfigService, null, new ObjectMapper(), mock(PlatformTransactionManager.class));

        Path repository = Files.createDirectories(tempDir.resolve("repo"));
        Files.createDirectories(repository.resolve("workhub-service"));
        Files.createDirectories(repository.resolve("workhub-bootstrap"));
        Files.writeString(repository.resolve("pom.xml"), "<project/>");
        when(sysConfigService.findPlainValue("knowledge.project", "repositoryPath")).thenReturn(repository.toString());

        when(sysConfigService.findPlainValue("knowledge.project", "vaultPath")).thenReturn(null);
        IllegalStateException missing = assertThrows(IllegalStateException.class, service::readSddProcessSpec);
        assertTrue(missing.getMessage().contains("vaultPath"));

        Path vault = Files.createDirectories(tempDir.resolve("vault"));
        Path spec = Files.createDirectories(vault.resolve("schema/研发/SDD")).resolve("流程.md");
        Files.writeString(spec, "# SDD 流程\n知识库规范内容");
        when(sysConfigService.findPlainValue("knowledge.project", "vaultPath")).thenReturn(vault.toString());

        assertEquals("# SDD 流程\n知识库规范内容", service.readSddProcessSpec());

        when(sysConfigService.findPlainValue("knowledge.project", "sddProcessPath")).thenReturn("../outside.md");
        assertThrows(IllegalStateException.class, service::readSddProcessSpec);

        Path repositorySpec = Files.createDirectories(repository.resolve("docs").resolve("研发规范/SDD")).resolve("流程.md");
        Files.writeString(repositorySpec, "# SDD 流程\n仓库规范内容");
        when(sysConfigService.findPlainValue("knowledge.project", "sddProcessPath")).thenReturn(null);
        assertEquals("# SDD 流程\n仓库规范内容", service.readSddProcessSpec());
    }

    @Test
    void zentaoClient_shouldFailFastWhenNotConfigured() {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        ZentaoClient client = new ZentaoClient(sysConfigService);
        when(sysConfigService.findPlainValue("zentao.base", "baseUrl")).thenReturn(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null));
        assertTrue(error.getMessage().contains("zentao.base.baseUrl"));

        when(sysConfigService.findPlainValue("zentao.base", "baseUrl")).thenReturn("http://10.10.116.28/zentao");
        when(sysConfigService.findPlainValue("zentao.base", "account")).thenReturn("shihao");
        when(sysConfigService.findPlainValue("zentao.base", "password")).thenReturn(null);
        IllegalArgumentException missingPassword = assertThrows(IllegalArgumentException.class,
                () -> client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null));
        assertTrue(missingPassword.getMessage().contains("zentao.base.password"));

        when(sysConfigService.findPlainValue("zentao.base", "password")).thenReturn("secret");
        when(sysConfigService.findPlainValue("zentao.base", "defaultMapping")).thenReturn(null);
        when(sysConfigService.findPlainValue("zentao.mapping", "BL000001")).thenReturn(null);
        IllegalArgumentException missingMapping = assertThrows(IllegalArgumentException.class,
                () -> client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null));
        assertTrue(missingMapping.getMessage().contains("zentao.mapping"));

        when(sysConfigService.findPlainValue("zentao.base", "defaultMapping")).thenReturn("59:385:896");
        IllegalArgumentException missingBranch = assertThrows(IllegalArgumentException.class,
                () -> client.createTestTask("测试单", "shihao", "BL000001", " ", null));
        assertTrue(missingBranch.getMessage().contains("研发分支"));

        when(sysConfigService.findPlainValue("zentao.mapping", "BL000001")).thenReturn("bad-format");
        IllegalArgumentException badMapping = assertThrows(IllegalArgumentException.class,
                () -> client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null));
        assertTrue(badMapping.getMessage().contains("产品ID:项目ID:执行ID"));
    }

    @Test
    void zentaoClient_shouldAutoCreateBuildWhenMissing() throws Exception {
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/projects/385/builds",
                new StubReply(200, "{\"total\":1,\"builds\":[{\"id\":301,\"name\":\"feature/other\"}]}"));
        replies.put("POST /zentao/api.php/v1/projects/385/builds",
                new StubReply(201, "{\"id\":\"555\",\"name\":\"feature/x_001\"}"));
        replies.put("POST /zentao/api.php/v2/testtasks", new StubReply(201, "{\"id\":888}"));
        List<StubRequest> requests = new ArrayList<>();
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            ZentaoClient.ZentaoTestTask task = client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null);

            assertEquals("888", task.taskNo());
            assertTrue(task.url().endsWith("/zentao/testtask-view-888.html"));
            assertEquals(4, requests.size());
            assertEquals("GET", requests.get(1).method());
            assertEquals("POST", requests.get(2).method());
            tools.jackson.databind.JsonNode buildPayload = new ObjectMapper().readTree(requests.get(2).body());
            assertEquals(896, buildPayload.path("execution").asLong());
            assertEquals(59, buildPayload.path("product").asLong());
            assertEquals("feature/x_001", buildPayload.path("name").asText());
            assertEquals("shihao", buildPayload.path("builder").asText());
            assertEquals(LocalDate.now().toString(), buildPayload.path("date").asText());
            tools.jackson.databind.JsonNode taskPayload = new ObjectMapper().readTree(requests.get(3).body());
            assertEquals("555", taskPayload.path("build").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void zentaoClient_shouldReuseExistingBuildWithoutCreating() throws Exception {
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/projects/385/builds",
                new StubReply(200, "{\"total\":1,\"builds\":[{\"id\":\"301\",\"name\":\"feature/x_001\"}]}"));
        replies.put("POST /zentao/api.php/v2/testtasks", new StubReply(201, "{\"id\":889}"));
        List<StubRequest> requests = new ArrayList<>();
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            ZentaoClient.ZentaoTestTask task = client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null);

            assertEquals("889", task.taskNo());
            assertEquals(3, requests.size());
            assertTrue(requests.stream().noneMatch(request -> "POST".equals(request.method())
                    && request.path().endsWith("/projects/385/builds")));
            tools.jackson.databind.JsonNode taskPayload = new ObjectMapper().readTree(requests.get(2).body());
            assertEquals("301", taskPayload.path("build").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void zentaoClient_shouldFailClearlyWhenBuildCreationRejected() throws Exception {
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/projects/385/builds", new StubReply(200, "{\"total\":0,\"builds\":[]}"));
        replies.put("POST /zentao/api.php/v1/projects/385/builds",
                new StubReply(400, "{\"message\":\"『版本名称』不能为空\"}"));
        List<StubRequest> requests = new ArrayList<>();
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> client.createTestTask("测试单", "shihao", "BL000001", "feature/x_001", null));
            assertTrue(error.getMessage().contains("禅道自动创建版本"));
            assertTrue(error.getMessage().contains("feature/x_001"));
            assertTrue(requests.stream().noneMatch(request -> request.path().endsWith("/api.php/v2/testtasks")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void zentaoClient_shouldResolveMappingFromLinkedStory() throws Exception {
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/stories/786",
                new StubReply(200, "{\"id\":\"786\",\"title\":\"国胜项目-调整回购规则\",\"product\":\"74\"}"));
        replies.put("GET /zentao/api.php/v1/products/74/projects",
                new StubReply(200, "{\"total\":1,\"projects\":[{\"id\":379,\"name\":\"嘉泰-租赁保理系统\",\"type\":\"project\"},{\"id\":380,\"name\":\"某迭代\",\"type\":\"sprint\"}]}"));
        replies.put("GET /zentao/api.php/v1/projects/379/executions",
                new StubReply(200, "{\"total\":2,\"limit\":100,\"page\":1,\"executions\":["
                        + "{\"id\":922,\"name\":\"国胜项目-系统迭代需求1104\"},"
                        + "{\"id\":923,\"name\":\"国胜项目-调整回购规则\"}]}"));
        replies.put("GET /zentao/api.php/v1/projects/379/builds",
                new StubReply(200, "{\"total\":0,\"builds\":[]}"));
        replies.put("POST /zentao/api.php/v1/projects/379/builds",
                new StubReply(201, "{\"id\":\"433\",\"name\":\"feature/repurchase_rule_202611030001\"}"));
        replies.put("POST /zentao/api.php/v2/testtasks", new StubReply(201, "{\"id\":890}"));
        List<StubRequest> requests = new ArrayList<>();
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            ZentaoClient.ZentaoTestTask task = client.createTestTask("测试单：国胜项目-调整回购规则", "shihao",
                    "BL000009", "feature/repurchase_rule_202611030001",
                    "http://10.10.116.28/zentao/story-view-786.html");

            assertEquals("890", task.taskNo());
            // 未回退 defaultMapping 的项目 385
            assertTrue(requests.stream().noneMatch(request -> request.path().contains("/projects/385")));
            tools.jackson.databind.JsonNode buildPayload = new ObjectMapper().readTree(
                    requests.stream().filter(request -> "POST".equals(request.method())
                            && request.path().endsWith("/projects/379/builds")).findFirst().orElseThrow().body());
            assertEquals(923, buildPayload.path("execution").asLong());
            assertEquals(74, buildPayload.path("product").asLong());
            tools.jackson.databind.JsonNode taskPayload = new ObjectMapper().readTree(
                    requests.stream().filter(request -> request.path().endsWith("/api.php/v2/testtasks"))
                            .findFirst().orElseThrow().body());
            assertEquals(74, taskPayload.path("productID").asLong());
            assertEquals("433", taskPayload.path("build").asText());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void zentaoClient_shouldRejectBadOrUnresolvableStoryUrl() throws Exception {
        List<StubRequest> requests = new ArrayList<>();
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/stories/999", new StubReply(404, "{\"message\":\"Not Found\"}"));
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            IllegalArgumentException notStory = assertThrows(IllegalArgumentException.class,
                    () -> client.createTestTask("测试单", "shihao", "BL000009", "feature/x_001",
                            "http://10.10.116.28/zentao/task-view-123.html"));
            assertTrue(notStory.getMessage().contains("未识别出需求或迭代ID"));

            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                    () -> client.createTestTask("测试单", "shihao", "BL000009", "feature/x_001",
                            "http://10.10.116.28/zentao/story-view-999.html"));
            assertTrue(missing.getMessage().contains("不存在"));

            IllegalArgumentException missingExecution = assertThrows(IllegalArgumentException.class,
                    () -> client.createTestTask("测试单", "shihao", "BL000009", "feature/x_001",
                            "http://10.10.116.28/zentao/execution-task-999.html"));
            assertTrue(missingExecution.getMessage().contains("迭代（ID 999）不存在"));

            // 需求存在但产品下无同名迭代：明确报错，不回退业务线映射
            replies.put("GET /zentao/api.php/v1/stories/786",
                    new StubReply(200, "{\"id\":\"786\",\"title\":\"国胜项目-调整回购规则\",\"product\":\"74\"}"));
            replies.put("GET /zentao/api.php/v1/products/74/projects",
                    new StubReply(200, "{\"total\":1,\"projects\":[{\"id\":379,\"name\":\"嘉泰-租赁保理系统\",\"type\":\"project\"}]}"));
            replies.put("GET /zentao/api.php/v1/projects/379/executions",
                    new StubReply(200, "{\"total\":1,\"limit\":100,\"page\":1,"
                            + "\"executions\":[{\"id\":922,\"name\":\"国胜项目-系统迭代需求1104\"}]}"));
            IllegalArgumentException noExecution = assertThrows(IllegalArgumentException.class,
                    () -> client.createTestTask("测试单", "shihao", "BL000009", "feature/x_001",
                            "http://10.10.116.28/zentao/story-view-786.html"));
            assertTrue(noExecution.getMessage().contains("没有同名迭代"));
            assertTrue(requests.stream().noneMatch(request -> request.path().contains("/projects/385")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void zentaoClient_shouldResolveMappingFromExecutionUrl() throws Exception {
        Map<String, StubReply> replies = new HashMap<>();
        replies.put("POST /zentao/api.php/v1/tokens", new StubReply(200, "{\"token\":\"t1\"}"));
        replies.put("GET /zentao/api.php/v1/executions/923",
                new StubReply(200, "{\"id\":\"923\",\"name\":\"国胜项目-调整回购规则\",\"project\":\"379\","
                        + "\"products\":[{\"id\":\"74\",\"name\":\"中恒卡尔恒昌\"}]}"));
        replies.put("GET /zentao/api.php/v1/projects/379/builds",
                new StubReply(200, "{\"total\":0,\"builds\":[]}"));
        replies.put("POST /zentao/api.php/v1/projects/379/builds",
                new StubReply(201, "{\"id\":\"434\",\"name\":\"feature/repurchase_rule_202611030001\"}"));
        replies.put("POST /zentao/api.php/v2/testtasks", new StubReply(201, "{\"id\":891}"));
        List<StubRequest> requests = new ArrayList<>();
        HttpServer server = startZentaoStub(replies, requests);
        try {
            ZentaoClient client = new ZentaoClient(zentaoStubConfig(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/zentao"));

            ZentaoClient.ZentaoTestTask task = client.createTestTask("测试单：国胜项目-调整回购规则", "shihao",
                    "BL000009", "feature/repurchase_rule_202611030001",
                    "http://10.10.116.28/zentao/execution-task-923.html");

            assertEquals("891", task.taskNo());
            // 迭代地址一步定位，未按需求名再去查产品项目与迭代列表
            assertTrue(requests.stream().noneMatch(request -> request.path().contains("/stories/")
                    || request.path().contains("/executions?") || request.path().endsWith("/projects")));
            assertTrue(requests.stream().noneMatch(request -> request.path().contains("/projects/385")));
            tools.jackson.databind.JsonNode buildPayload = new ObjectMapper().readTree(
                    requests.stream().filter(request -> "POST".equals(request.method())
                            && request.path().endsWith("/projects/379/builds")).findFirst().orElseThrow().body());
            assertEquals(923, buildPayload.path("execution").asLong());
            assertEquals(74, buildPayload.path("product").asLong());
        } finally {
            server.stop(0);
        }
    }

    private SysConfigService zentaoStubConfig(String baseUrl) {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        when(sysConfigService.findPlainValue("zentao.base", "baseUrl")).thenReturn(baseUrl);
        when(sysConfigService.findPlainValue("zentao.base", "account")).thenReturn("shihao");
        when(sysConfigService.findPlainValue("zentao.base", "password")).thenReturn("secret");
        when(sysConfigService.findPlainValue("zentao.base", "defaultMapping")).thenReturn("59:385:896");
        return sysConfigService;
    }

    private HttpServer startZentaoStub(Map<String, StubReply> replies, List<StubRequest> requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new StubRequest(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
            StubReply reply = replies.getOrDefault(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath(),
                    new StubReply(404, "{}"));
            try (exchange) {
                byte[] response = reply.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(reply.status(), response.length);
                exchange.getResponseBody().write(response);
            } catch (java.io.IOException ignored) {
                // 客户端断开不影响用例断言
            }
        });
        server.start();
        return server;
    }

    private record StubReply(int status, String body) {
    }

    private record StubRequest(String method, String path, String body) {
    }

    @ParameterizedTest
    @ValueSource(strings = {"开发中", "开发完成"})
    void submitTestConfirm_shouldRequireCompletedReviewAndForceFlag(String status) {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                mock(ZentaoClient.class),
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(5L);
        intake.setDemandStatus(status);
        intake.setRequirementType("研发需求");
        when(intakeMapper.findById(5L)).thenReturn(intake);
        when(reviewMapper.findByIntakeId(5L)).thenReturn(null);

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setEnvironmentCode("TEST");
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(5L, request, "admin"));
        assertTrue(missing.getMessage().contains("审核尚未完成"));

        IntakeRecordEntity notDeveloping = new IntakeRecordEntity();
        notDeveloping.setId(6L);
        notDeveloping.setDemandStatus("待设计");
        notDeveloping.setRequirementType("研发需求");
        when(intakeMapper.findById(6L)).thenReturn(notDeveloping);
        IllegalArgumentException wrongStage = assertThrows(IllegalArgumentException.class,
                () -> service.startReview(6L, "admin"));
        assertTrue(wrongStage.getMessage().contains("开发中"));
        verify(reviewMapper, never()).insert(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"开发中", "开发完成"})
    void submitTestConfirm_shouldAllowSkippingAiReviewAndJenkins(String status) {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        AgentJenkinsClient jenkinsClient = mock(AgentJenkinsClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                zentaoClient,
                jenkinsClient,
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                intakeService
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(7L);
        intake.setDemandStatus(status);
        intake.setRequirementType("研发需求");
        intake.setDevelopmentBranchName("feature/skip-flow");
        when(intakeMapper.findById(7L)).thenReturn(intake);
        when(reviewMapper.findByIntakeId(7L)).thenReturn(null);
        when(zentaoClient.createTestTask(any(), any(), any(), any(), any()))
                .thenReturn(new ZentaoClient.ZentaoTestTask("88", "http://zentao/testtask-view-88.html"));

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setEnvironmentCode("TEST");
        request.setTestOwnerUserName(" tester ");
        service.confirmAndSubmit(7L, request, "admin");

        ArgumentCaptor<IntakeSubmitTestReviewEntity> captor = ArgumentCaptor.forClass(IntakeSubmitTestReviewEntity.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals("SKIPPED", captor.getValue().getReviewStatus());
        assertEquals("88", captor.getValue().getZentaoTestTaskNo());
        verify(zentaoClient).createTestTask(any(), eq("tester"), any(), any(), any());
        verify(jenkinsClient, never()).submit(any(), any());
        verify(intakeService).advanceStage(eq(7L), any(), eq("admin"));
    }

    @Test
    void submitTestConfirm_shouldAllowSkippingZentaoTestTask() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                zentaoClient,
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                intakeService
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(13L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setDevelopmentBranchName("feature/skip-zentao");
        when(intakeMapper.findById(13L)).thenReturn(intake);
        // 回滚后重提：已有审核记录残留上次禅道回执，不提单时应被清空
        IntakeSubmitTestReviewEntity existing = new IntakeSubmitTestReviewEntity();
        existing.setIntakeId(13L);
        existing.setReviewStatus("PASSED");
        existing.setZentaoTestTaskNo("88");
        existing.setZentaoTestTaskUrl("http://zentao/testtask-view-88.html");
        when(reviewMapper.findByIntakeId(13L)).thenReturn(existing);

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setEnvironmentCode("TEST");
        request.setCreateZentaoTestTask(false);
        request.setTestOwnerUserName("tester");
        service.confirmAndSubmit(13L, request, "admin");

        verify(zentaoClient, never()).createTestTask(any(), any(), any(), any(), any());
        ArgumentCaptor<IntakeSubmitTestReviewEntity> captor = ArgumentCaptor.forClass(IntakeSubmitTestReviewEntity.class);
        verify(reviewMapper).updateByIntakeId(captor.capture());
        assertNull(captor.getValue().getZentaoTestTaskNo());
        assertNull(captor.getValue().getZentaoTestTaskUrl());
        verify(intakeService).advanceStage(eq(13L), any(), eq("admin"));
    }

    @Test
    void submitTestConfirm_shouldStillRequireTestOwnerWhenSkippingZentaoTestTask() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                zentaoClient,
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(14L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        when(intakeMapper.findById(14L)).thenReturn(intake);
        when(reviewMapper.findByIntakeId(14L)).thenReturn(null);

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setEnvironmentCode("TEST");
        request.setCreateZentaoTestTask(false);
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(14L, request, "admin"));
        assertTrue(missing.getMessage().contains("测试负责人"));
        verify(zentaoClient, never()).createTestTask(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"开发中", "开发完成"})
    void submitTestReview_shouldEnqueueForBothDevelopmentStages(String status) {
        var intakeMapper = mock(IntakeMapper.class);
        var reviews = mock(IntakeSubmitTestReviewMapper.class);
        var executor = mock(org.springframework.core.task.TaskExecutor.class);
        var service = new IntakeSubmitTestReviewService(intakeMapper, reviews, mock(BusinessLineMapper.class), mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class), mock(ZentaoClient.class), mock(AgentJenkinsClient.class),
                mock(SysConfigService.class), new ObjectMapper(), executor, mock(IntakeService.class));
        var intake = new IntakeRecordEntity();
        intake.setId(11L); intake.setRequirementType("研发需求"); intake.setDemandStatus(status);
        when(intakeMapper.findById(11L)).thenReturn(intake);

        service.startReview(11L, "admin");

        var saved = ArgumentCaptor.forClass(IntakeSubmitTestReviewEntity.class);
        verify(reviews).insert(saved.capture());
        assertEquals("PENDING", saved.getValue().getReviewStatus());
        verify(executor).execute(any());
        assertEquals(status, intake.getDemandStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"已收录", "待设计", "测试中", "待验收", "已完成", "已暂停"})
    void submitTest_shouldRejectOtherStagesBeforeAnySideEffect(String status) {
        var intakeMapper = mock(IntakeMapper.class);
        var reviews = mock(IntakeSubmitTestReviewMapper.class);
        var executor = mock(org.springframework.core.task.TaskExecutor.class);
        var zentao = mock(ZentaoClient.class);
        var jenkins = mock(AgentJenkinsClient.class);
        var stages = mock(IntakeService.class);
        var service = new IntakeSubmitTestReviewService(intakeMapper, reviews, mock(BusinessLineMapper.class), mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class), zentao, jenkins, mock(SysConfigService.class), new ObjectMapper(), executor, stages);
        var intake = new IntakeRecordEntity();
        intake.setId(12L); intake.setRequirementType("研发需求"); intake.setDemandStatus(status);
        when(intakeMapper.findById(12L)).thenReturn(intake);
        var request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true); request.setTriggerJenkins(false); request.setTestOwnerUserName("tester");

        assertThrows(IllegalArgumentException.class, () -> service.startReview(12L, "admin"));
        assertThrows(IllegalArgumentException.class, () -> service.confirmAndSubmit(12L, request, "admin"));
        org.mockito.Mockito.verifyNoInteractions(reviews, executor, zentao, jenkins, stages);
    }

    @Test
    void submitTestConfirm_shouldAllowSkipWhileReviewRunning() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                zentaoClient,
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                intakeService
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(8L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setDevelopmentBranchName("feature/running-review");
        when(intakeMapper.findById(8L)).thenReturn(intake);
        IntakeSubmitTestReviewEntity running = new IntakeSubmitTestReviewEntity();
        running.setIntakeId(8L);
        running.setReviewStatus("RUNNING");
        when(reviewMapper.findByIntakeId(8L)).thenReturn(running);
        when(zentaoClient.createTestTask(any(), any(), any(), any(), any()))
                .thenReturn(new ZentaoClient.ZentaoTestTask("89", "http://zentao/testtask-view-89.html"));

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setEnvironmentCode("TEST");
        request.setTestOwnerUserName("tester");
        service.confirmAndSubmit(8L, request, "admin");

        verify(reviewMapper, never()).insert(any());
        ArgumentCaptor<IntakeSubmitTestReviewEntity> captor = ArgumentCaptor.forClass(IntakeSubmitTestReviewEntity.class);
        verify(reviewMapper).updateByIntakeId(captor.capture());
        assertEquals("RUNNING", captor.getValue().getReviewStatus());
        assertEquals("89", captor.getValue().getZentaoTestTaskNo());
        verify(intakeService).advanceStage(eq(8L), any(), eq("admin"));
    }

    @Test
    void submitTestConfirm_shouldRequireTestOwner() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                zentaoClient,
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                intakeService
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(10L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setDevelopmentBranchName("feature/owner-required");
        when(intakeMapper.findById(10L)).thenReturn(intake);

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setEnvironmentCode("TEST");
        IllegalArgumentException missingOwner = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(10L, request, "admin"));
        assertTrue(missingOwner.getMessage().contains("请选择测试负责人"));
        verify(zentaoClient, never()).createTestTask(any(), any(), any(), any(), any());
        verify(intakeService, never()).advanceStage(any(), any(), any());
    }

    @Test
    void submitTestConfirm_shouldRequireEnvironmentEvenWithoutJenkins() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                mock(IntakeSubmitTestReviewMapper.class),
                mock(BusinessLineMapper.class),
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                mock(ZentaoClient.class),
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(9L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        when(intakeMapper.findById(9L)).thenReturn(intake);
        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setTestOwnerUserName("tester");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(9L, request, "admin"));
        assertTrue(error.getMessage().contains("请选择测试环境"));
    }

    @Test
    void submitTestConfirm_shouldRejectProductionAndUnsupportedEnvironment() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                mock(IntakeSubmitTestReviewMapper.class),
                businessLineMapper,
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                mock(ZentaoClient.class),
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(15L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setBusinessLineCode("BL000011");
        when(intakeMapper.findById(15L)).thenReturn(intake);
        BusinessLineEntity line = new BusinessLineEntity();
        line.setBusinessLineCode("BL000011");
        line.setSupportedEnvironments("TEST,PROD");
        when(businessLineMapper.findByCode("BL000011")).thenReturn(line);

        IntakeSubmitTestConfirmRequest prodRequest = new IntakeSubmitTestConfirmRequest();
        prodRequest.setSkipAiReview(true);
        prodRequest.setTriggerJenkins(false);
        prodRequest.setEnvironmentCode("PROD");
        prodRequest.setTestOwnerUserName("tester");
        IllegalArgumentException prodError = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(15L, prodRequest, "admin"));
        assertTrue(prodError.getMessage().contains("提测不允许选择生产环境"));

        IntakeSubmitTestConfirmRequest unsupportedRequest = new IntakeSubmitTestConfirmRequest();
        unsupportedRequest.setSkipAiReview(true);
        unsupportedRequest.setTriggerJenkins(false);
        unsupportedRequest.setEnvironmentCode("SIT");
        unsupportedRequest.setTestOwnerUserName("tester");
        IllegalArgumentException unsupportedError = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(15L, unsupportedRequest, "admin"));
        assertTrue(unsupportedError.getMessage().contains("测试环境须为业务线支持的环境"));
    }

    @Test
    void submitTestConfirm_shouldRequireEnvironmentMatchingDeploymentBinding() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        IntakeTestDeploymentService deployments = mock(IntakeTestDeploymentService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                mock(IntakeSubmitTestReviewMapper.class),
                businessLineMapper,
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                mock(ZentaoClient.class),
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        service.setTestDeployments(deployments);
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(16L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setBusinessLineCode("BL000011");
        when(intakeMapper.findById(16L)).thenReturn(intake);
        BusinessLineEntity line = new BusinessLineEntity();
        line.setBusinessLineCode("BL000011");
        line.setSupportedEnvironments("TEST,SIT,PROD");
        when(businessLineMapper.findByCode("BL000011")).thenReturn(line);
        // 所选发布绑定为 TEST 环境，与确认时选择的 SIT 不一致
        when(deployments.validateRequest(eq(16L), any())).thenReturn(new IntakeTestDeployment.Binding(
                "test", "BL000011", "scs-saps", "scs/scs-saps", "jenkins", "http://jenkins/",
                "test-scs-saps", "TEST", "test-scs-server", "10.10.116.43", "master", true));

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setEnvironmentCode("SIT");
        request.setTestOwnerUserName("tester");
        request.setTestDeployment(new IntakeTestDeployment.Request("test", List.of(16L), null));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(16L, request, "admin"));
        assertTrue(error.getMessage().contains("测试环境与所选测试发布目标不符"));
        verify(deployments, never()).submit(any(), any(), any());
    }

    @Test
    void submitTestConfirm_shouldRecordEnvironmentWithoutJenkins() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        BusinessLineMapper businessLineMapper = mock(BusinessLineMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
                businessLineMapper,
                mock(DevelopmentAnalysisMapper.class),
                mock(AiGatewayClient.class),
                mock(ZentaoClient.class),
                mock(AgentJenkinsClient.class),
                mock(SysConfigService.class),
                new ObjectMapper(),
                Runnable::run,
                mock(IntakeService.class)
        );
        IntakeRecordEntity intake = new IntakeRecordEntity();
        intake.setId(17L);
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setBusinessLineCode("BL000011");
        when(intakeMapper.findById(17L)).thenReturn(intake);
        BusinessLineEntity line = new BusinessLineEntity();
        line.setBusinessLineCode("BL000011");
        line.setSupportedEnvironments("TEST,SIT,PROD");
        when(businessLineMapper.findByCode("BL000011")).thenReturn(line);
        when(reviewMapper.findByIntakeId(17L)).thenReturn(null);

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
        request.setCreateZentaoTestTask(false);
        request.setEnvironmentCode(" sit ");
        request.setTestOwnerUserName("tester");
        service.confirmAndSubmit(17L, request, "admin");

        ArgumentCaptor<IntakeSubmitTestReviewEntity> captor = ArgumentCaptor.forClass(IntakeSubmitTestReviewEntity.class);
        verify(reviewMapper).insert(captor.capture());
        assertEquals("SIT", captor.getValue().getJenkinsEnvironmentCode());
        assertNull(captor.getValue().getJenkinsBuildStatus());
    }
}
