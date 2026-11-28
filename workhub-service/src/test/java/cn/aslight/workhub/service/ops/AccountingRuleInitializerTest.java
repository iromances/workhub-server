package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingRuleMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorCheckEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorFamilyEntity;
import org.apache.ibatis.session.Configuration;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** REQ-7f26a063：真实迁移建表 + Mapper 落库，验证种子只补缺与生效规则口径（H2 MODE=MySQL）。 */
class AccountingRuleInitializerTest {

    private Connection connection;
    private AccountingRuleMapper rules;
    private AccountingRuleInitializer initializer;
    private final AccountingMonitorConfigEntity config = new AccountingMonitorConfigEntity(
            15L, "BL000005", "智能柜", "PROD", "ol-saps", "一致性", "ACCOUNTING_INTEGRITY",
            null, LocalTime.of(8, 30), null, null, null, null, java.math.BigDecimal.ZERO, false, true, null, null, null);

    @BeforeEach
    void setup() throws Exception {
        var db = new JdbcDataSource();
        db.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL");
        connection = db.getConnection();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(db);
        jdbc.execute(Files.readString(Path.of(
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261127_120000__accounting_rule_management.sql"))
                .split(";\\s*\\n")[0]);
        jdbc.execute(Files.readString(Path.of(
                "../workhub-bootstrap/src/main/resources/db/schema/mysql/V20261127_120000__accounting_rule_management.sql"))
                .split(";\\s*\\n")[1]);
        var configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AccountingRuleMapper.class);
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(db);
        factory.setConfiguration(configuration);
        rules = new SqlSessionTemplate(factory.getObject()).getMapper(AccountingRuleMapper.class);
        initializer = new AccountingRuleInitializer(rules);
    }

    @AfterEach
    void close() throws Exception {
        if (connection != null) connection.close();
    }

    @Test
    void materializesCatalogOnceWithoutOverwritingMaintenance() {
        initializer.ensureMaterialized(config);
        List<AccountingMonitorFamilyEntity> families = rules.findFamilies(15L);
        assertEquals(16, families.size());
        assertEquals(67, rules.findChecks(15L).size());
        assertEquals(List.copyOf(AccountingIntegrityCatalog.families("BL000005")).stream()
                .map(AccountingIntegrityCatalog.Family::code).toList(),
                families.stream().map(AccountingMonitorFamilyEntity::familyCode).toList());
        assertTrue(families.stream().allMatch(family -> "BUILTIN".equals(family.sourceType())
                && Boolean.TRUE.equals(family.enabled()) && !Boolean.TRUE.equals(family.deleted())));

        // 页面维护：停用一个规则组、停用一个检查项、改一个级别——再次物化不得覆盖
        rules.updateFamilyEnabled(families.get(0).id(), false, "tester");
        var checks = rules.findChecksByFamily(families.get(0).id());
        rules.updateCheckState(checks.get(0).id(), false, "WARN", "tester");
        initializer.ensureMaterialized(config);
        List<AccountingMonitorFamilyEntity> again = rules.findFamilies(15L);
        assertEquals(16, again.size());
        assertEquals(67, rules.findChecks(15L).size());
        assertFalse(again.get(0).enabled());
        var reloaded = rules.findChecksByFamily(families.get(0).id()).get(0);
        assertFalse(reloaded.enabled());
        assertEquals("WARN", reloaded.severity());
    }

    @Test
    void materializeAddsOnlyMissingChecksForExistingFamily() {
        var seed = AccountingIntegrityCatalog.families("BL000005").getFirst();
        rules.insertFamily(new AccountingMonitorFamilyEntity(null, 15L, seed.code(), seed.name(), null,
                seed.batchSize(), "BUILTIN", true, false, 1, "system", "system", null, null));
        var inserted = rules.findFamily(15L, seed.code());
        rules.insertCheck(new AccountingMonitorCheckEntity(null, inserted.id(), seed.checks().getFirst().id(),
                seed.checks().getFirst().name(), seed.checks().getFirst().severity(), null, false, 1,
                "system", "system", null, null));

        initializer.ensureMaterialized(config);

        var checks = rules.findChecksByFamily(inserted.id());
        assertEquals(seed.checks().size(), checks.size());
        var first = checks.stream()
                .filter(check -> check.checkId().equals(seed.checks().getFirst().id())).findFirst().orElseThrow();
        assertFalse(first.enabled(), "已停用的检查项不能被种子改回启用");
    }

    @Test
    void effectiveFamiliesFollowEnablementAndSkipEmptyOnes() {
        initializer.ensureMaterialized(config);
        var families = rules.findFamilies(15L);
        rules.updateFamilyEnabled(families.get(0).id(), false, "tester");
        var second = families.get(1);
        for (var check : rules.findChecksByFamily(second.id())) {
            rules.updateCheckState(check.id(), false, check.severity(), "tester");
        }

        List<AccountingIntegrityCatalog.Family> effective = initializer.effectiveFamilies(15L);

        assertEquals(14, effective.size());
        assertTrue(effective.stream().noneMatch(family -> family.code().equals(families.get(0).familyCode())));
        assertTrue(effective.stream().noneMatch(family -> family.code().equals(second.familyCode())));
        var seed = AccountingIntegrityCatalog.families("BL000005").get(2);
        var restored = effective.stream().filter(family -> family.code().equals(seed.code())).findFirst().orElseThrow();
        assertEquals(seed.checks().size(), restored.checks().size());
        assertEquals(seed.batchSize(), restored.batchSize());
        assertTrue(initializer.hasEffectiveRules(15L));

        for (var family : rules.findFamilies(15L)) {
            rules.updateFamilyEnabled(family.id(), false, "tester");
        }
        assertFalse(initializer.hasEffectiveRules(15L));
    }

    @Test
    void statsCountEffectiveRulesAndCustomFamilies() {
        initializer.ensureMaterialized(config);
        var stats = initializer.stats(15L);
        assertEquals(67, stats.effectiveChecks());
        assertEquals(16, stats.enabledFamilies());
        assertEquals(16, stats.totalFamilies());
        assertEquals(0, stats.customFamilies());

        rules.insertFamily(new AccountingMonitorFamilyEntity(null, 15L, "demo_custom", "演示", null, 100,
                "CUSTOM", true, false, 99, "tester", "tester", null, null));
        rules.updateFamilyEnabled(rules.findFamilies(15L).get(0).id(), false, "tester");
        var updated = initializer.stats(15L);
        // 17 个未删除规则组：停用一个内置组后，15 个内置 + 1 个自定义均启用
        assertEquals(16, updated.enabledFamilies());
        assertEquals(17, updated.totalFamilies());
        assertEquals(1, updated.customFamilies());
    }
}
