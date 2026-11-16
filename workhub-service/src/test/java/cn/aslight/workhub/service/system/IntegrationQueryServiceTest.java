package cn.aslight.workhub.service.system;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.service.agent.AgentJenkinsClient;
import cn.aslight.workhub.service.agent.AgentJenkinsPolicy;
import cn.aslight.workhub.service.intake.ZentaoClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationQueryServiceTest {

    private static final AgentJenkinsPolicy.Instance INSTANCE =
            new AgentJenkinsPolicy.Instance("i1", "主实例", "http://jenkins.example", "deploy",
                    "jenkins.credential.i1", "apiToken", true);
    private static final AgentJenkinsPolicy.Policy POLICY = new AgentJenkinsPolicy.Policy(List.of(INSTANCE), List.of());
    private static final AgentJenkinsClient.Target TARGET = new AgentJenkinsClient.Target(
            URI.create("http://jenkins.example/"), List.of("folder", "job"), "deploy", "tok");

    @TempDir
    Path tempDir;

    private final ZentaoClient zentaoClient = mock(ZentaoClient.class);
    private final AgentJenkinsPolicy jenkinsPolicy = mock(AgentJenkinsPolicy.class);
    private final AgentJenkinsClient jenkinsClient = mock(AgentJenkinsClient.class);
    private final SysConfigService sysConfigService = mock(SysConfigService.class);
    private McpAuditLogger auditLogger;
    private IntegrationQueryService service;

    @BeforeEach
    void setUp() {
        auditLogger = new McpAuditLogger(new com.fasterxml.jackson.databind.ObjectMapper(), tempDir.resolve("audit.jsonl"));
        service = new IntegrationQueryService(zentaoClient, jenkinsPolicy, jenkinsClient, sysConfigService, auditLogger);
        when(jenkinsPolicy.current()).thenReturn(POLICY);
        when(sysConfigService.requireSecretValue("jenkins.credential.i1", "apiToken")).thenReturn("tok");
        when(sysConfigService.requireSecretValue("jenkins.credential.i2", "apiToken")).thenReturn("tok");
    }

    @Test
    void searchZentaoBugsClampsLimitAndAuditsSuccess() {
        when(zentaoClient.searchBugs(59L, null, "登录", 50)).thenReturn(List.of(bugNode()));

        Map<String, Object> result = service.searchZentaoBugs(59L, null, "登录", 500);

        assertEquals(1, result.get("count"));
        assertEquals(50, result.get("limit"));
        assertEquals(Boolean.FALSE, result.get("truncated"));
        verify(zentaoClient).searchBugs(59L, null, "登录", 50);
        assertTrue(auditContent().contains("search_zentao_bugs"));
        assertTrue(auditContent().contains("SUCCESS"));
    }

    @Test
    void searchZentaoBugsRejectsNonPositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.searchZentaoBugs(null, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> service.searchZentaoBugs(null, null, null, -3));
    }

    @Test
    void getZentaoBugTruncatesOversizedFieldsAndAddsViewUrl() {
        when(zentaoClient.viewUrl("bug", 101)).thenReturn("http://zentao.example/bug-view-101.html");
        when(zentaoClient.getBug(101L)).thenReturn(bugNode());

        Map<String, Object> result = service.getZentaoBug("101");

        assertEquals("http://zentao.example/bug-view-101.html", result.get("viewUrl"));
        @SuppressWarnings("unchecked")
        Map<String, Object> bug = (Map<String, Object>) result.get("bug");
        String steps = (String) bug.get("steps");
        assertTrue(steps.endsWith("…[字段截断]"));
        assertTrue(auditContent().contains("\"tool\":\"get_zentao_bug\""));
    }

    @Test
    void getZentaoBugRejectsInvalidId() {
        assertThrows(IllegalArgumentException.class, () -> service.getZentaoBug("0"));
        assertThrows(IllegalArgumentException.class, () -> service.getZentaoBug("abc"));
        assertThrows(IllegalArgumentException.class, () -> service.getZentaoBug(null));
    }

    @Test
    void listJenkinsJobsUsesFirstEnabledInstanceAtRoot() {
        when(jenkinsClient.listJobs(any(), any(), anyInt())).thenReturn(jsonNode("{\"jobs\":[{\"name\":\"job\"}]}"));

        Map<String, Object> result = service.listJenkinsJobs(null, null, 500);

        assertEquals("i1", result.get("instanceId"));
        assertEquals(200, result.get("limit"));
        verify(jenkinsClient).listJobs(any(), eq(List.of()), eq(200));
    }

    @Test
    void listJenkinsJobsResolvesExplicitInstance() {
        var disabled = new AgentJenkinsPolicy.Instance("i2", "备用", "http://jenkins2.example", "deploy",
                "jenkins.credential.i2", "apiToken", true);
        when(jenkinsPolicy.current()).thenReturn(new AgentJenkinsPolicy.Policy(List.of(INSTANCE, disabled), List.of()));
        when(jenkinsClient.listJobs(any(), any(), anyInt())).thenReturn(jsonNode("{\"jobs\":[]}"));

        Map<String, Object> result = service.listJenkinsJobs("i2", "group/sub", 20);

        assertEquals("i2", result.get("instanceId"));
        verify(jenkinsClient).listJobs(any(), eq(List.of("group", "sub")), eq(20));
    }

    @Test
    void listJenkinsJobsRejectsUnknownInstance() {
        assertThrows(IllegalArgumentException.class, () -> service.listJenkinsJobs("missing", null, null));
        assertTrue(auditContent().contains("ERROR"));
    }

    @Test
    void getJenkinsJobMergesParameterNamesFromProbe() {
        when(jenkinsClient.jobDetail(any())).thenReturn(jsonNode("{\"fullName\":\"folder/job\",\"buildable\":true}"));
        when(jenkinsClient.probe(any())).thenReturn(new AgentJenkinsClient.Probe("folder/job", true, List.of("ENV")));

        Map<String, Object> result = service.getJenkinsJob("i1", "folder/job");

        @SuppressWarnings("unchecked")
        Map<String, Object> job = (Map<String, Object>) result.get("job");
        assertEquals(List.of("ENV"), job.get("parameterNames"));
        assertEquals(Boolean.TRUE, job.get("buildable"));
    }

    @Test
    void getJenkinsJobRejectsBlankJobPath() {
        assertThrows(IllegalArgumentException.class, () -> service.getJenkinsJob("i1", "  "));
        assertThrows(IllegalArgumentException.class, () -> service.getJenkinsBuild("i1", "..", "1"));
    }

    @Test
    void getJenkinsBuildReturnsBuildNode() {
        when(jenkinsClient.buildDetail(any(), eq(12L))).thenReturn(jsonNode("{\"number\":12,\"result\":\"SUCCESS\"}"));

        Map<String, Object> result = service.getJenkinsBuild("i1", "folder/job", "12");

        @SuppressWarnings("unchecked")
        Map<String, Object> build = (Map<String, Object>) result.get("build");
        assertEquals("SUCCESS", build.get("result"));
    }

    @Test
    void getJenkinsBuildTrimsLogTailToLines() {
        when(jenkinsClient.buildLogTail(any(), eq(12L), anyInt()))
                .thenReturn(new AgentJenkinsClient.LogTail("l1\nl2\nl3", true));

        Map<String, Object> result = service.getJenkinsBuildLog("i1", "folder/job", "12", 2, null);

        assertEquals(Boolean.TRUE, result.get("truncated"));
        assertEquals(Boolean.TRUE, result.get("headTruncated"));
        assertEquals("l2\nl3", result.get("content"));
        verify(jenkinsClient).buildLogTail(any(), eq(12L), eq(IntegrationQueryService.DEFAULT_LOG_BYTES));
    }

    @Test
    void getJenkinsBuildLogRejectsNonPositiveLines() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getJenkinsBuildLog("i1", "folder/job", "12", 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.getJenkinsBuildLog("i1", "folder/job", "12", null, -1));
        assertThrows(IllegalArgumentException.class,
                () -> service.getJenkinsBuildLog("i1", "folder/job", "0", null, null));
    }

    private String auditContent() throws RuntimeException {
        try {
            Path file = tempDir.resolve("audit.jsonl");
            return Files.exists(file) ? Files.readString(file) : "";
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private tools.jackson.databind.JsonNode jsonNode(String json) {
        return new tools.jackson.databind.ObjectMapper().readTree(json);
    }

    private tools.jackson.databind.JsonNode bugNode() {
        return jsonNode("{\"id\":101,\"title\":\"登录失败\",\"product\":59,\"steps\":\"" + "x".repeat(5000) + "\"}");
    }
}
