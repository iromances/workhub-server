package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleAction;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemAlertEventClassifierTest {

    @Test
    void shouldClassifyDiscardIdleConnectionAsSlowSql() {
        ElkSystemAlertLog event = event("discard long time none received connection. jdbcUrl: jdbc:mysql://db/example");

        assertEquals(SystemAlertRuleAction.SLOW_SQL, SystemAlertEventClassifier.classify(event,
                List.of(rule(10L, "SLOW_SQL", "ALL_TEXT", "ANY", 100,
                        "discard long time none received connection", "lastPacketReceivedIdleMillis"))));
    }

    @Test
    void shouldClassifyLastPacketIdleFieldCaseInsensitively() {
        ElkSystemAlertLog event = event("LASTPACKETRECEIVEDIDLEMILLIS : 119987");

        assertEquals(SystemAlertRuleAction.SLOW_SQL, SystemAlertEventClassifier.classify(event,
                List.of(rule(10L, "SLOW_SQL", "ALL_TEXT", "ANY", 100,
                        "discard long time none received connection", "lastPacketReceivedIdleMillis"))));
    }

    @Test
    void shouldKeepOrdinaryErrorInSystemErrorCategory() {
        ElkSystemAlertLog event = event("支付回调处理失败");

        assertEquals(SystemAlertRuleAction.SYSTEM_ERROR,
                SystemAlertEventClassifier.classify(event, List.of()));
    }

    @Test
    void shouldRequireEveryKeywordForAllMode() {
        SystemAlertRuleEntity rule = rule(1L, "IGNORE", "MESSAGE", "ALL", 10,
                "[900000]", "账单已结算");

        assertEquals(SystemAlertRuleAction.IGNORE,
                SystemAlertEventClassifier.classify(event("[900000]A001账单已结算"), List.of(rule)));
        assertEquals(SystemAlertRuleAction.SYSTEM_ERROR,
                SystemAlertEventClassifier.classify(event("A001账单已结算"), List.of(rule)));
    }

    @Test
    void messageScopeShouldNotReadStackTrace() {
        ElkSystemAlertLog event = new ElkSystemAlertLog("source-1", "asset-payment", "ERROR", "PaymentService",
                "普通错误", "IllegalStateException", "target-keyword", "trace-1", "req-1", Instant.EPOCH);

        assertEquals(SystemAlertRuleAction.SYSTEM_ERROR, SystemAlertEventClassifier.classify(event,
                List.of(rule(1L, "IGNORE", "MESSAGE", "ANY", 10, "target-keyword"))));
        assertEquals(SystemAlertRuleAction.IGNORE, SystemAlertEventClassifier.classify(event,
                List.of(rule(2L, "IGNORE", "ALL_TEXT", "ANY", 10, "target-keyword"))));
    }

    @Test
    void shouldUseFirstMatchingRuleByPriority() {
        SystemAlertRuleEntity slow = rule(2L, "SLOW_SQL", "ALL_TEXT", "ANY", 100, "共同关键词");
        SystemAlertRuleEntity ignore = rule(1L, "IGNORE", "MESSAGE", "ANY", 10, "共同关键词");

        assertEquals(SystemAlertRuleAction.IGNORE,
                SystemAlertEventClassifier.classify(event("共同关键词"), List.of(slow, ignore)));
    }

    private ElkSystemAlertLog event(String message) {
        return new ElkSystemAlertLog("source-1", "asset-payment", "ERROR", "PaymentService",
                message, "IllegalStateException", "stack", "trace-1", "req-1", Instant.EPOCH);
    }

    private SystemAlertRuleEntity rule(Long id, String action, String scope, String mode,
                                       int priority, String... keywords) {
        SystemAlertRuleEntity rule = new SystemAlertRuleEntity();
        rule.setId(id);
        rule.setAction(action);
        rule.setMatchScope(scope);
        rule.setMatchMode(mode);
        rule.setPriority(priority);
        rule.setEnabled(true);
        rule.setKeywords(List.of(keywords));
        return rule;
    }
}
