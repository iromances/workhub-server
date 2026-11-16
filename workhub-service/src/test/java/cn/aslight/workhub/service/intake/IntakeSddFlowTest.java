package cn.aslight.workhub.service.intake;

import cn.aslight.workhub.dao.intake.DevelopmentAnalysisMapper;
import cn.aslight.workhub.dao.intake.IntakeHistoryMapper;
import cn.aslight.workhub.dao.intake.IntakeMapper;
import cn.aslight.workhub.dao.intake.IntakeSubmitTestReviewMapper;
import cn.aslight.workhub.dao.intake.IntakeStructuredFieldMapper;
import cn.aslight.workhub.dao.intake.IntakeTodoMapper;
import cn.aslight.workhub.dao.intake.IntakeWorkItemRelationMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.intake.IntakeRecordEntity;
import cn.aslight.workhub.model.intake.IntakeSubmitTestConfirmRequest;
import cn.aslight.workhub.model.intake.IntakeSubmitTestReviewEntity;
import cn.aslight.workhub.model.intake.IntakeUploadRequest;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.ai.AiGatewayClient;
import cn.aslight.workhub.service.attachment.AttachmentService;
import cn.aslight.workhub.service.system.SysConfigService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
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
    void readSddProcessSpec_shouldFailWithoutVaultAndReadConfiguredFile() throws Exception {
        SysConfigService sysConfigService = mock(SysConfigService.class);
        ProjectKnowledgeBaseService service = new ProjectKnowledgeBaseService(
                sysConfigService, null, new ObjectMapper(), mock(PlatformTransactionManager.class));

        when(sysConfigService.findPlainValue("knowledge.project", "vaultPath")).thenReturn(null);
        IllegalStateException missing = assertThrows(IllegalStateException.class, service::readSddProcessSpec);
        assertTrue(missing.getMessage().contains("vaultPath"));

        Path vault = Files.createDirectories(tempDir.resolve("vault"));
        Path spec = Files.createDirectories(vault.resolve("schema/研发/SDD")).resolve("流程.md");
        Files.writeString(spec, "# SDD 流程\n规范内容");
        when(sysConfigService.findPlainValue("knowledge.project", "vaultPath")).thenReturn(vault.toString());

        assertEquals("# SDD 流程\n规范内容", service.readSddProcessSpec());

        when(sysConfigService.findPlainValue("knowledge.project", "sddProcessPath")).thenReturn("../outside.md");
        assertThrows(IllegalStateException.class, service::readSddProcessSpec);
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

    @Test
    void submitTestConfirm_shouldRequireCompletedReviewAndForceFlag() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
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
        intake.setDemandStatus("开发中");
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

    @Test
    void submitTestConfirm_shouldAllowSkippingAiReviewAndJenkins() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        AgentJenkinsClient jenkinsClient = mock(AgentJenkinsClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
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
        intake.setDemandStatus("开发中");
        intake.setRequirementType("研发需求");
        intake.setDevelopmentBranchName("feature/skip-flow");
        when(intakeMapper.findById(7L)).thenReturn(intake);
        when(reviewMapper.findByIntakeId(7L)).thenReturn(null);
        when(zentaoClient.createTestTask(any(), any(), any(), any(), any()))
                .thenReturn(new ZentaoClient.ZentaoTestTask("88", "http://zentao/testtask-view-88.html"));

        IntakeSubmitTestConfirmRequest request = new IntakeSubmitTestConfirmRequest();
        request.setSkipAiReview(true);
        request.setTriggerJenkins(false);
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
    void submitTestConfirm_shouldAllowSkipWhileReviewRunning() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewMapper reviewMapper = mock(IntakeSubmitTestReviewMapper.class);
        ZentaoClient zentaoClient = mock(ZentaoClient.class);
        IntakeService intakeService = mock(IntakeService.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                reviewMapper,
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
        IllegalArgumentException missingOwner = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(10L, request, "admin"));
        assertTrue(missingOwner.getMessage().contains("请选择测试负责人"));
        verify(zentaoClient, never()).createTestTask(any(), any(), any(), any(), any());
        verify(intakeService, never()).advanceStage(any(), any(), any());
    }

    @Test
    void submitTestConfirm_shouldRequireEnvironmentWhenTriggeringJenkins() {
        IntakeMapper intakeMapper = mock(IntakeMapper.class);
        IntakeSubmitTestReviewService service = new IntakeSubmitTestReviewService(
                intakeMapper,
                mock(IntakeSubmitTestReviewMapper.class),
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

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirmAndSubmit(9L, request, "admin"));
        assertTrue(error.getMessage().contains("请选择测试环境"));
    }
}
