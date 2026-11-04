package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentReleaseToolsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentReleasePlanService service = mock(AgentReleasePlanService.class);
    private final AgentReleaseTools tools = new AgentReleaseTools(service, mock(AgentChangeService.class), mock(AgentAccessService.class), json);
    private final AgentToolContext context = new AgentToolContext("run", "task", "agent", "owner", "token", null, null);

    @Test void apiAndCliToolContractRequiresAndReturnsProposedStandards() {
        var tool = tools.agentReleasePrepareTool(); var args = args();
        var plan = json.treeToValue(args.path("verificationPlan"), AgentReleaseVerificationPlan.class);
        when(service.prepare(any(), eq("owner"))).thenReturn(Map.of("snapshot", new AgentReleasePlanSnapshot(2, null, List.of(), List.of(), List.of(), plan)));
        var result = json.valueToTree(tool.execute(context, args));
        var captured = ArgumentCaptor.forClass(AgentReleasePlanRequest.class);
        verify(service).prepare(captured.capture(), eq("owner"));
        assertEquals(plan, captured.getValue().verificationPlan());
        assertEquals(json.valueToTree(plan), result.path("verificationPlan"));
        assertTrue(tool.inputSchema().path("required").toString().contains("verificationPlan"));
        assertFalse(tool.readOnly());
        assertFalse(result.path("businessVerified").asBoolean());
    }

    @Test void unknownNestedFieldsMissingStandardsAndCoercedNumbersAreRejectedBeforePreparation() {
        var invalid = new ArrayList<ObjectNode>();
        var missing = args(); missing.remove("verificationPlan"); invalid.add(missing);
        var unknown = args(); ((ObjectNode) unknown.path("verificationPlan")).put("approved", true); invalid.add(unknown);
        var text = args(); ((ObjectNode) text.at("/verificationPlan/checks/0")).put("observationSeconds", "3600"); invalid.add(text);
        var fraction = args(); ((ObjectNode) fraction.at("/verificationPlan/checks/0")).put("minimumSamples", 1.5); invalid.add(fraction);
        var scope = args(); ((ObjectNode) scope.at("/verificationPlan/checks/0/scope")).put("allEnvironments", true); invalid.add(scope);
        var zero = args(); ((ObjectNode) zero.at("/verificationPlan/checks/0")).put("sampleIntervalSeconds", 0); invalid.add(zero);
        for (var value : invalid) assertThrows(AgentTool.Rejected.class, () -> tools.agentReleasePrepareTool().execute(context, value));
        verifyNoInteractions(service);
    }

    private ObjectNode args() {
        var check = new AgentReleaseVerificationPlan.Check("health", new AgentScope("BL000012", "TEST"), "monitor.accounting.detail",
                "原问题的账务规则", "原规则通过且证据完整", 3600, 60, 300, 2, "完整运行凭据和新鲜规则结果");
        return (ObjectNode) json.valueToTree(new AgentReleasePlanRequest("key", "change", "TEST_DEPLOY", List.of("job"), List.of(),
                new AgentReleaseVerificationPlan(List.of(check), "历史问题单独处理", "失败回到修复")));
    }
}
