package cn.aslight.workhub.service.ops;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountingRuleCatalogTest {

    private final AccountingRuleCatalog catalog = new AccountingRuleCatalog();

    @Test
    void shouldExposeSystemSpecificRulePackages() {
        assertTrue(catalog.rulesForProfile("ASSETS_SAPS").stream()
                .anyMatch(rule -> "ASSETS_STATEMENT_COMPARE_ERROR".equals(rule.ruleCode())));
        assertTrue(catalog.rulesForProfile("AMP_SAPS").stream()
                .anyMatch(rule -> "AMP_REFACTORING_REQUIRED_FIELDS".equals(rule.ruleCode())));
        assertTrue(catalog.rulesForProfile("LOGISTICS_SAPS").stream()
                .anyMatch(rule -> "LOGISTICS_ORDER_TRADE_MISMATCH".equals(rule.ruleCode())));
    }

    @Test
    void shouldRecognizeWholeDayWindow() {
        assertTrue(AccountingRuleCatalog.isWholeDayWindow(
                LocalDateTime.of(2026, 7, 22, 0, 0),
                LocalDateTime.of(2026, 7, 23, 0, 0)));
        assertFalse(AccountingRuleCatalog.isWholeDayWindow(
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0)));
    }

    @Test
    void shouldBuildHalfOpenHourlySqlAndRejectUnsafeSchema() {
        AccountingRuleDefinition rule = catalog.rulesForProfile("COMMON_SAPS").stream()
                .filter(item -> "TX_INVALID_AMOUNT".equals(item.ruleCode()))
                .findFirst()
                .orElseThrow();
        AccountingRuleSql sql = rule.buildSql(
                "jiatai_amp_saps",
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0));
        assertTrue(sql.summarySql().contains("create_time >= '2026-07-22 10:00:00'"));
        assertTrue(sql.summarySql().contains("create_time < '2026-07-22 11:00:00'"));
        assertFalse(sql.summarySql().contains("BETWEEN"));
        assertThrows(IllegalArgumentException.class, () -> rule.buildSql(
                "jiatai_amp_saps;DROP TABLE statement",
                LocalDateTime.of(2026, 7, 22, 10, 0),
                LocalDateTime.of(2026, 7, 22, 11, 0)));
    }
}
