package cn.aslight.workhub.service.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-7f26a063 §3.5：一致性模板保存契约——既有 87 个内置模板全部满足，违规样例逐项报错。 */
class IntegritySqlContractTest {

    @Test
    void allBuiltinTemplatesSatisfyTheContract() {
        var seeds = AccountingIntegrityCatalog.seeds();
        assertEquals(87, seeds.size());
        for (var seed : seeds) {
            IntegritySqlContract.validate(seed.sqlCode(), seed.sqlTemplate());
        }
    }

    @Test
    void rejectsWrongSuffix() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_TOTAL", "SELECT 1"));
        assertTrue(ex.getMessage().contains("必须以 _BOUNDS/_SUMMARY/_SAMPLE 结尾"));
    }

    @Test
    void rejectsAnyParameterInBounds() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_BOUNDS", "SELECT MIN(id), MAX(id) FROM t WHERE id > :startId"));
        assertTrue(ex.getMessage().contains("边界模板不允许引用参数"));
    }

    @Test
    void rejectsSummaryMissingBatchParamsOrCheckedCountOrUnknownParam() {
        var missingStart = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_SUMMARY",
                        "SELECT COUNT(*) AS checked_count FROM t WHERE id BETWEEN 1 AND :endId"));
        assertTrue(missingStart.getMessage().contains("缺少必需参数 :startId"));
        var missingCount = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_SUMMARY",
                        "SELECT COUNT(*) FROM t WHERE id BETWEEN :startId AND :endId"));
        assertTrue(missingCount.getMessage().contains("checked_count"));
        var unknown = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_SUMMARY",
                        "SELECT COUNT(*) AS checked_count FROM t WHERE id BETWEEN :startId AND :endId AND c = :ruleCode"));
        assertTrue(unknown.getMessage().contains("未传入的参数"));
    }

    @Test
    void rejectsSampleMissingRequiredParams() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> IntegritySqlContract.validate("AI_X_SAMPLE",
                        "SELECT id FROM t WHERE id BETWEEN :startId AND :endId AND c = :ruleCode"));
        assertTrue(ex.getMessage().contains("缺少必需参数 :sampleLimit"));
    }
}
