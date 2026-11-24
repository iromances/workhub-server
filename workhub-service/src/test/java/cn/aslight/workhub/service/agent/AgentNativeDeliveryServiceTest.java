package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.intake.*;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.intake.*;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.service.system.SysConfigService;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentNativeDeliveryServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final IntakeAiDevelopmentMapper developments = mock(IntakeAiDevelopmentMapper.class);
    private final IntakeMapper intakes = mock(IntakeMapper.class);
    private final DevelopmentAnalysisMapper analyses = mock(DevelopmentAnalysisMapper.class);
    private final BusinessLineMapper lines = mock(BusinessLineMapper.class);
    private final SysConfigService configs = mock(SysConfigService.class);
    private final AgentGitLabClient gitlab = mock(AgentGitLabClient.class);
    private final AgentMapper agents = mock(AgentMapper.class);
    private final AgentAccessService access = mock(AgentAccessService.class);
    private final String sha = "a".repeat(40);
    private final String branch = "feature/intake";
    private final AgentGitLabClient.Target remote = new AgentGitLabClient.Target(URI.create("https://gitlab.example"), 1, "group/server", "fixture");
    private final List<AgentEventEntity> events = new ArrayList<>();
    private final AgentToolContext context = new AgentToolContext("run", "task", "agent", "owner", "lease",
            new AgentConfiguration("Code", "OPS", "开发", 1L, "fixture", List.of(), 100, 3600, 2),
            new AgentTaskSpecification("开发", "需求", "交付", List.of("CHANGE_SET"), List.of(new AgentScope("BL1", "TEST"))));
    private final AgentRunEntity run = mock(AgentRunEntity.class);
    private final AgentNativeDeliveryService service = new AgentNativeDeliveryService(developments, intakes, analyses,
            lines, configs, gitlab, agents, access, json);
    private IntakeRecordEntity intake;
    private DevelopmentAnalysisEntity analysis;

    @BeforeEach void setup() {
        var development = new IntakeAiDevelopmentEntity();
        development.setIntakeId(1L); development.setAgentTaskId("task"); development.setAgentId("agent"); development.setDevelopmentBranch(branch);
        when(developments.findByAgentTaskId("task")).thenReturn(development);
        intake = new IntakeRecordEntity(); intake.setId(1L); intake.setBusinessLineCode("BL1"); intake.setDevelopmentBranchName(branch);
        when(intakes.findById(1L)).thenReturn(intake);
        analysis = new DevelopmentAnalysisEntity();
        analysis.setDraftJson("{\"workItems\":[{\"systemTags\":[\"server\"]}]}");
        when(analyses.findLatestByIntakeId(1L)).thenReturn(analysis);
        var line = new BusinessLineEntity(); line.setEnabled(true); line.setGitlabGroupName("group");
        when(lines.findByCode("BL1")).thenReturn(line);
        when(configs.requirePlainValue("gitlab.global", "webApiUrl")).thenReturn("https://gitlab.example");
        when(configs.requirePlainValue("gitlab.global", "accessToken")).thenReturn("fixture");
        when(access.actor("owner")).thenReturn(new AgentAccessService.Actor("owner", Set.of("agent:code:use")));
        when(gitlab.resolve(any(), eq("group/server"), eq("fixture"))).thenReturn(remote);
        when(gitlab.branch(remote, branch)).thenReturn(new AgentGitLabClient.Branch(branch, sha, false, false));
        when(run.id()).thenReturn("run"); when(run.taskId()).thenReturn("task"); when(run.agentId()).thenReturn("agent");
        when(run.requestedBy()).thenReturn("owner"); when(run.ownerToken()).thenReturn("lease");
        when(run.configurationJson()).thenReturn(json.writeValueAsString(context.configuration()));
        when(run.providerBindingJson()).thenReturn("{\"channelType\":\"CLI\"}");
        when(run.specificationJson()).thenReturn(json.writeValueAsString(context.specification()));
        when(agents.listEvents("run")).thenAnswer(call -> List.copyOf(events));
        when(agents.findEventByKey(eq("run"), anyString())).thenAnswer(call -> events.stream()
                .filter(event -> event.eventKey().equals(call.getArgument(1))).findFirst().orElse(null));
    }

    @Test void verifiesRemoteAndAcceptsOnlyMatchingPlatformReceipt() {
        var declarations = declarations("group/server", branch, sha);
        var receipt = service.verify(context, declarations);
        assertEquals("SATISFIED", receipt.status());
        verify(gitlab).branch(remote, branch);
        result("cli:1", declarations, "RESULT");
        assertEquals("MISSING", service.check(run).status());
        event("cli:1:delivery", "NATIVE_CODE_DELIVERY", receipt);
        assertEquals("SATISFIED", service.check(run).status());
        assertEquals("EVENT", service.check(run).evidence().getFirst().kind());
    }

    @Test void refusesWrongBranchForeignProjectMissingOrDuplicateRepositoriesBeforeNetwork() {
        for (JsonNode declaration : List.of(declarations("group/server", "main", sha), declarations("foreign/server", branch, sha),
                json.readTree("[]"), json.readTree("[{},{}]"))) {
            assertEquals("MISSING", service.verify(context, declaration).status());
        }
        verifyNoInteractions(gitlab);
    }

    @Test void doesNotAcceptMissingOrMismatchedRemoteCommit() {
        when(gitlab.branch(remote, branch)).thenReturn(null);
        assertEquals("MISSING", service.verify(context, declarations("group/server", branch, sha)).status());
        when(gitlab.branch(remote, branch)).thenReturn(new AgentGitLabClient.Branch(branch, "b".repeat(40), false, false));
        assertEquals("MISSING", service.verify(context, declarations("group/server", branch, sha)).status());
    }

    @Test void requiresEverySystemFromTaskBreakdown() {
        analysis.setDraftJson("{\"workItems\":[{\"systemTags\":[\"server\",\"web\"]}]}");
        assertEquals("MISSING", service.verify(context, declarations("group/server", branch, sha)).status());
        verifyNoInteractions(gitlab);
    }

    @Test void oldAttemptAndChangedRequirementsDoNotSatisfyCurrentAttempt() {
        var declaration = declarations("group/server", branch, sha);
        var receipt = service.verify(context, declaration);
        event("cli:1:delivery", "NATIVE_CODE_DELIVERY", receipt);
        result("cli:2", declaration, "RESULT");
        assertEquals("MISSING", service.check(run).status());
        event("cli:2:delivery", "NATIVE_CODE_DELIVERY", receipt);
        analysis.setDraftJson("{\"summary\":\"changed\",\"workItems\":[{\"systemTags\":[\"server\"]}]}");
        assertEquals("BLOCKED", service.check(run).status());
    }

    @Test void finalClaimsCannotOverrideReceiptAndNeedsInputCannotComplete() {
        var declaration = declarations("group/server", branch, sha);
        event("cli:1:delivery", "NATIVE_CODE_DELIVERY", service.verify(context, declaration));
        result("cli:1", declarations("group/server", branch, "b".repeat(40)), "RESULT");
        assertEquals("BLOCKED", service.check(run).status());
        result("cli:2", declaration, "NEEDS_INPUT");
        assertEquals("MISSING", service.check(run).status());
    }

    @Test void queryFailuresAndAuthorizationChangesNeverProduceSuccess() {
        when(gitlab.branch(remote, branch)).thenThrow(new IllegalStateException("fixture secret"));
        var failure = service.verify(context, declarations("group/server", branch, sha));
        assertEquals("MISSING", failure.status()); assertFalse(failure.message().contains("fixture secret"));
        doThrow(new org.springframework.security.access.AccessDeniedException("revoked")).when(access).requireRunAuthorization(context);
        assertEquals("MISSING", service.verify(context, declarations("group/server", branch, sha)).status());
    }

    @Test void ordinaryTasksRetainLegacyAcceptance() {
        when(developments.findByAgentTaskId("task")).thenReturn(null);
        assertNull(service.check(run));
    }

    @Test void apiTasksKeepDomainManifestAcceptance() {
        when(run.providerBindingJson()).thenReturn("{\"channelType\":\"API\"}");
        assertNull(service.check(run));
        verifyNoInteractions(gitlab, developments);
    }

    private JsonNode declarations(String project, String targetBranch, String commit) {
        return json.valueToTree(List.of(Map.of("projectPath", project, "branch", targetBranch, "commit", commit)));
    }
    private void result(String attempt, JsonNode declaration, String type) {
        event(attempt + ":result", "CLI_RESULT", Map.of("exitCode", 0, "processTreeStopped", true,
                "result", Map.of("type", type, "summary", "交付", "deliveries", declaration)));
    }
    private void event(String key, String kind, Object payload) {
        events.add(new AgentEventEntity(UUID.randomUUID().toString(), "run", key, events.size() + 1L, kind,
                json.writeValueAsString(payload), "owner", LocalDateTime.now()));
    }
}
