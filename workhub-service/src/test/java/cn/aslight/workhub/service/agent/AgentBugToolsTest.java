package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.agent.AgentMonitorReadMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.system.SysUserEntity;
import cn.aslight.workhub.service.auth.PermissionContextService;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentBugToolsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentMonitorReadMapper reads = mock(AgentMonitorReadMapper.class);
    private final AgentMapper agents = mock(AgentMapper.class);
    private final PermissionContextService permissions = mock(PermissionContextService.class);
    private final BusinessLineMapper lines = mock(BusinessLineMapper.class);
    private final AgentScope scope = new AgentScope("BL000012", "TEST");
    private final List<String> codes = List.of("workhub.bugs.list", "workhub.bugs.detail");
    private final AgentConfiguration configuration = config(List.of(scope));
    private final AgentToolContext context = new AgentToolContext("run", "task", "agent", "owner", configuration,
            new AgentTaskSpecification("分析登记缺陷", "", "据实际证据定位", List.of("REPORT"), List.of(scope), codes));
    private final AgentBugTools tools = new AgentBugTools(reads, new AgentAccessService(permissions, lines, agents, json), new AgentToolData(json), json);

    @BeforeEach void setup() {
        var user = new SysUserEntity(); user.setId(1L); user.setUserName("owner");
        when(permissions.requireActiveUser("owner")).thenReturn(user);
        when(permissions.permissionCodes(1L)).thenReturn(List.of("agent:manage", "agent:task:assign", "work-item:view"));
        when(agents.findAgent("agent")).thenReturn(definition(configuration));
        var line = new BusinessLineEntity(); line.setBusinessLineCode("BL000012"); line.setEnabled(true); line.setSupportedEnvironments("TEST,PROD");
        when(lines.findByCode("BL000012")).thenReturn(line);
    }

    @Test void listIsBoundToActualLineAndDoesNotInferEnvironmentFromTaskScope() {
        var page = List.of(record(3, null), record(2, null), record(1, null));
        when(reads.bugs("BL000012", null, 0, 3)).thenReturn(page);
        var result = (JsonNode) tools.agentBugListTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\",\"pageSize\":2}"));
        assertEquals("UNRESOLVED", result.path("environmentState").asText());
        assertFalse(result.has("environmentCode")); assertEquals(2, result.at("/data/items").size());
        assertEquals(2, result.at("/data/nextBeforeWorkItemId").asLong()); assertTrue(result.at("/data/hasMore").asBoolean());
        assertFalse(result.has("observationId"));
    }

    @Test void detailKeepsSourceIdentityAndMasksTextBeforeReturningIt() {
        var raw = record(7, "Authorization: Bearer private-token-value");
        when(reads.bug("BL000012", 7)).thenReturn(raw);
        var result = (JsonNode) tools.agentBugDetailTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\",\"workItemId\":7}"));
        assertEquals(7, result.at("/data/id").asLong()); assertEquals("BUG-7", result.at("/data/no").asText());
        assertFalse(result.toString().contains("private-token-value")); assertEquals("UNRESOLVED", result.path("environmentState").asText());
        assertEquals(cn.aslight.workhub.service.autonomy.AutonomyEvidence.digest(json.writeValueAsString(raw)), result.path("workItemVersion").asText());
        assertThrows(IllegalArgumentException.class, () -> tools.agentBugDetailTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\",\"workItemId\":999}")));
    }

    @Test void foreignLineAndRevokedCurrentScopeAreRejectedBeforeSourceRead() {
        assertThrows(AccessDeniedException.class, () -> tools.agentBugDetailTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000001\",\"workItemId\":7}")));
        when(agents.findAgent("agent")).thenReturn(definition(config(List.of(new AgentScope("BL000012", "PROD")))));
        assertThrows(AccessDeniedException.class, () -> tools.agentBugListTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\"}")));
        verifyNoInteractions(reads);
    }

    @Test void sourcePermissionAndToolArgumentRulesRemainRequired() {
        assertThrows(IllegalArgumentException.class, () -> tools.agentBugListTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\",\"environmentCode\":\"TEST\"}")));
        assertThrows(IllegalArgumentException.class, () -> tools.agentBugListTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\",\"beforeWorkItemId\":-1}")));
        when(permissions.permissionCodes(1L)).thenReturn(List.of("agent:manage", "agent:task:assign"));
        assertThrows(AccessDeniedException.class, () -> tools.agentBugListTool().execute(context, json.readTree("{\"businessLineCode\":\"BL000012\"}")));
        verifyNoInteractions(reads);
    }

    private AgentConfiguration config(List<AgentScope> scopes) { return new AgentConfiguration("缺陷巡检", "读取线索", 1L, "model", scopes, codes, 20, 60, 1); }
    private AgentDefinitionEntity definition(AgentConfiguration config) { return new AgentDefinitionEntity("agent", config.name(), json.writeValueAsString(config), true, "owner", 0, null, null); }
    private AgentBugRecord record(long id, String description) {
        return new AgentBugRecord(id, "BUG-" + id, 1L, "WorkHub", "BL000012", "缺陷", "处理中", "P1", "MANUAL", "fixture",
                description, "据实际证据验证", LocalDateTime.now(), LocalDateTime.now());
    }
}
