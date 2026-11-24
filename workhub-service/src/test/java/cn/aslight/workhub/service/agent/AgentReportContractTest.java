package cn.aslight.workhub.service.agent;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.*;
import tools.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class AgentReportContractTest {
    static ObjectNode fixture() throws Exception {
        try (var in = AgentReportContractTest.class.getResourceAsStream("/agent/report-v2.json")) {
            return (ObjectNode) new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
    @Test void threeReportTypesAndLongBodyValidateWithoutTruncation() throws Exception {
        for (String type : new String[]{"INSPECTION", "CODE_REVIEW", "GENERAL"}) {
            var result = fixture(); ((ObjectNode) result.path("report")).put("reportType", type).put("conclusion", "完整中文".repeat(30000));
            AgentReportContract.validate(result);
            assertEquals(120000, AgentReportContract.summary(result).length());
        }
    }
    @Test void uniqueFencedJsonWithIntroductionIsParsedWithoutRewriting() throws Exception {
        var result = fixture();
        assertEquals(result, AgentReportContract.extract("巡检完成。\n```json\n" + result + "\n```\n"));
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.extract("```json\n"+result+"\n```\n```json\n"+result+"\n```"));
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.extract("```json\n"+result.toString().substring(0,80)));
    }
    @Test void unknownVersionExtraFieldMissingFieldAndInvalidNestedTypeAreRejected() throws Exception {
        var result = fixture(); result.put("schemaVersion",3); assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(result));
        result.put("schemaVersion",2).put("injected","x"); assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(result));
        result.remove("injected"); ((ObjectNode)result.path("report")).remove("scope"); assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(result));
        var nested=fixture(); ((ObjectNode)nested.path("report").path("findings").get(0)).put("severity",7);
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(nested));
    }
    @Test void referencesDuplicatesAndFalseNormalAreRejected() throws Exception {
        var result=fixture(); ((ObjectNode)result.path("report").path("evidence").get(0)).put("targetKey","other-report");
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(result));
        var duplicate=fixture(); ((tools.jackson.databind.node.ArrayNode)duplicate.path("report").path("checks")).add(duplicate.path("report").path("checks").get(0));
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(duplicate));
        var normal=fixture(); ((ObjectNode)normal.path("report")).put("assessment","NORMAL");
        assertThrows(IllegalArgumentException.class, () -> AgentReportContract.validate(normal));
    }
    @Test void generalReviewFindingsDoNotBecomeMonitoringIssues() throws Exception {
        var result=fixture(); assertEquals(1,AgentReportContract.problems(result).size());
        ((ObjectNode)result.path("report")).put("reportType","CODE_REVIEW");
        assertTrue(AgentReportContract.problems(result).isMissingNode());
        ((ObjectNode)result.path("report")).put("reportType","INSPECTION");
        ((ObjectNode)result.path("report").path("findings").get(0)).put("verificationStatus","UNVERIFIED");
        assertEquals(0,AgentReportContract.problems(result).size());
    }
}
