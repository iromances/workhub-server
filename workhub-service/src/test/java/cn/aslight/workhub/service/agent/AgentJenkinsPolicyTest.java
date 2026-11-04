package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证固定目标、密钥引用、版本失效和只读工具边界；不访问真实 Jenkins。 */
class AgentJenkinsPolicyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> raw = new AtomicReference<>();
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private SysConfigService configs;
    private AgentJenkinsPolicy service;
    private AgentJenkinsClient client;
    private AgentJenkinsTools tools;
    private AgentToolContext context;

    @BeforeEach void setup() {
        configs = mock(SysConfigService.class);
        when(configs.findPlainValue("agent.jenkins", "policy")).thenAnswer(invocation -> raw.get());
        when(configs.requireSecretValue("jenkins.main", "apiToken")).thenReturn("dummy-test-token");
        service = new AgentJenkinsPolicy(configs, json);
        save(binding(Map.of("server", "SERVER_SHA", "web", "WEB_SHA"), Map.of("MODE", "deploy")));
        client = mock(AgentJenkinsClient.class);
        var access = mock(AgentAccessService.class);
        doAnswer(invocation -> {
            AgentToolContext value = invocation.getArgument(0);
            if (!value.specification().scopes().contains(invocation.getArgument(1))) throw new AccessDeniedException("scope");
            return null;
        }).when(access).requireScope(any(), any());
        tools = new AgentJenkinsTools(service, client, access, json);
        var config = new AgentConfiguration("release", "prepare", 1L, "model", List.of(scope), List.of(), 30, 120, 1);
        context = new AgentToolContext("run", "task", "agent", "owner", "lease", config,
                new AgentTaskSpecification("inspect release", "", "report", List.of("REPORT"), List.of(scope), List.of()));
    }

    @Test void fixedParametersIncludeExactManifestEnvironmentAndEveryRepository() {
        var snapshot = service.binding("test-deploy");
        var params = service.parameters(snapshot, "req-one", "f".repeat(64), Map.of("server", "a".repeat(40), "web", "b".repeat(40)));
        assertEquals("a".repeat(40), params.get("SERVER_SHA")); assertEquals("b".repeat(40), params.get("WEB_SHA"));
        assertEquals("TEST", params.get("ENVIRONMENT")); assertEquals("req-one", params.get("REQUEST_ID"));
        assertEquals("f".repeat(64), params.get("MANIFEST_HASH"));
        assertThrows(IllegalArgumentException.class, () -> service.parameters(snapshot, "req-one", "f".repeat(64), Map.of("server", "a".repeat(40))));
        assertThrows(IllegalArgumentException.class, () -> service.parameters(snapshot, "req-one", "f".repeat(64), Map.of("server", "main", "web", "b".repeat(40))));
        verifyNoInteractions(client);
    }

    @Test void equivalentParameterOrderKeepsHashButBindingChangeInvalidatesSnapshot() {
        var before = service.binding("test-deploy");
        var reordered = new LinkedHashMap<String, String>(); reordered.put("web", "WEB_SHA"); reordered.put("server", "SERVER_SHA");
        save(binding(reordered, Map.of("MODE", "deploy")));
        assertEquals(before.hash(), service.binding("test-deploy").hash());
        save(binding(reordered, Map.of("MODE", "another-approved-mode")));
        assertThrows(IllegalStateException.class, () -> service.requireCurrent(before));
        assertThrows(IllegalStateException.class, () -> service.target(before));
    }

    @Test void credentialsAreOnlyResolvedFromEncryptedReferenceWhenNeeded() {
        var snapshot = service.binding("test-deploy");
        verify(configs, never()).requireSecretValue(anyString(), anyString());
        var target = service.target(snapshot);
        assertEquals("dummy-test-token", target.apiToken());
        verify(configs).requireSecretValue("jenkins.main", "apiToken");
        assertFalse(json.writeValueAsString(snapshot).contains("dummy-test-token"));
        assertFalse(json.writeValueAsString(target).contains("dummy-test-token"));
    }

    @Test void copiedHashCannotAuthorizeDifferentSnapshotFields() {
        var original = service.binding("test-deploy"); var instance = original.instance();
        var substituted = new AgentJenkinsPolicy.Instance(instance.id(), instance.name(), "https://another.invalid/", instance.userName(), instance.credentialGroup(), instance.tokenKey(), true);
        var forged = new AgentJenkinsPolicy.BindingSnapshot(substituted, original.binding(), original.hash());
        assertThrows(IllegalStateException.class, () -> service.target(forged));
        verify(configs, never()).requireSecretValue(anyString(), anyString());
    }

    @Test void rejectsParameterOverrideAndPlainSensitiveFixedValues() {
        save(binding(Map.of("server", "SERVER_SHA"), Map.of("REQUEST_ID", "override")));
        assertThrows(IllegalArgumentException.class, () -> service.current());
        save(binding(Map.of("server", "SERVER_SHA"), Map.of("apiToken", "must-not-send-secret")));
        assertThrows(IllegalArgumentException.class, () -> service.current());
    }

    @Test void preflightReportsMissingParametersAndNeverApprovesOrTriggers() {
        var tool = tools.agentJenkinsPreflightTool();
        when(client.probe(any())).thenReturn(new AgentJenkinsClient.Probe("team/app", true, List.of("REQUEST_ID")));
        var rejected = json.valueToTree(tool.execute(context, json.readTree("{\"bindingId\":\"test-deploy\"}")));
        assertEquals("NOT_READY", rejected.path("state").asText()); assertTrue(rejected.path("missingParameters").size() > 0);
        var parameters = service.requiredParameters(service.binding("test-deploy").binding());
        when(client.probe(any())).thenReturn(new AgentJenkinsClient.Probe("team/app", true, parameters));
        var passed = json.valueToTree(tool.execute(context, json.readTree("{\"bindingId\":\"test-deploy\"}")));
        assertEquals("PREFLIGHT_PASSED", passed.path("state").asText());
        assertFalse(passed.path("approved").asBoolean()); assertFalse(passed.path("triggered").asBoolean());
        assertTrue(tool.readOnly()); verify(client, never()).submit(any(), any());
    }

    @Test void crossScopePreflightFailsBeforeCredentialResolutionOrNetwork() {
        var other = new AgentToolContext("run", "task", "agent", "owner", "lease", context.configuration(),
                new AgentTaskSpecification("test", "", "report", List.of("REPORT"), List.of(new AgentScope("BL000012", "PROD")), List.of()));
        assertThrows(AccessDeniedException.class, () -> tools.agentJenkinsPreflightTool().execute(other, json.readTree("{\"bindingId\":\"test-deploy\"}")));
        verify(configs, never()).requireSecretValue(anyString(), anyString()); verifyNoInteractions(client);
    }

    @Test void targetListExposesOnlyScopedMetadataAndMissingConfigIsVisible() {
        var listed = json.valueToTree(tools.agentJenkinsTargetsTool().execute(context, json.createObjectNode()));
        assertEquals("CONFIGURED_NOT_PROBED", listed.path("targets").get(0).path("state").asText());
        assertFalse(listed.toString().contains("jenkins.invalid")); assertFalse(listed.toString().contains("apiToken"));
        verifyNoInteractions(client);
        raw.set(null); assertThrows(AgentCodePolicy.Unavailable.class, () -> tools.agentJenkinsTargetsTool().execute(context, json.createObjectNode()));
    }

    private AgentJenkinsPolicy.JobBinding binding(Map<String, String> commits, Map<String, String> fixed) {
        return new AgentJenkinsPolicy.JobBinding("test-deploy", "Test deploy", "main", scope, "example", "TEST_DEPLOY",
                List.of("team", "app"), commits, "REQUEST_ID", "MANIFEST_HASH", "ENVIRONMENT", fixed, "reports/workhub-release.json", true);
    }
    private void save(AgentJenkinsPolicy.JobBinding binding) {
        raw.set(json.writeValueAsString(new AgentJenkinsPolicy.Policy(List.of(new AgentJenkinsPolicy.Instance("main", "Main", "https://jenkins.invalid/jenkins/", "workhub", "jenkins.main", "apiToken", true)), List.of(binding))));
    }
}
