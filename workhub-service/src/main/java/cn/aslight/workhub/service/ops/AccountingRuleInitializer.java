package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingRuleMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorCheckEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorFamilyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * REQ-7f26a063 §3.2：内置目录按配置只补缺物化（库内定义优先，页面维护的启停与级别不覆盖），
 * 并提供执行口径的“生效规则”读取（启用且未删除的规则组 × 启用的检查项）。
 */
@Service
public class AccountingRuleInitializer {

    private static final Logger log = LoggerFactory.getLogger(AccountingRuleInitializer.class);

    private final AccountingRuleMapper rules;
    private final Set<Long> materialized = ConcurrentHashMap.newKeySet();

    public AccountingRuleInitializer(AccountingRuleMapper rules) {
        this.rules = rules;
    }

    /** 物化某配置的内置规则；进程内每个配置只执行一次，撞唯一键视为并发已插入。 */
    public synchronized void ensureMaterialized(AccountingMonitorConfigEntity config) {
        if (config == null || !AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())
                || materialized.contains(config.id())) {
            return;
        }
        int familiesSeeded = 0;
        int checksSeeded = 0;
        int order = 0;
        for (AccountingIntegrityCatalog.Family seed : AccountingIntegrityCatalog.families(config.businessLineCode())) {
            order++;
            AccountingMonitorFamilyEntity existing = rules.findFamily(config.id(), seed.code());
            if (existing == null) {
                try {
                    rules.insertFamily(new AccountingMonitorFamilyEntity(null, config.id(), seed.code(), seed.name(),
                            seed.description(), seed.batchSize(), "BUILTIN", true, false, order, "system", "system",
                            null, null));
                    familiesSeeded++;
                } catch (DataIntegrityViolationException ex) {
                    // 并发物化撞 (config_id, family_code) 唯一键，按已存在处理
                }
                existing = rules.findFamily(config.id(), seed.code());
                if (existing == null) {
                    continue;
                }
            }
            if (!"BUILTIN".equals(existing.sourceType())) {
                continue;
            }
            Set<String> present = rules.findChecksByFamily(existing.id()).stream()
                    .map(AccountingMonitorCheckEntity::checkId)
                    .collect(Collectors.toUnmodifiableSet());
            int checkOrder = 0;
            for (AccountingIntegrityCatalog.Check check : seed.checks()) {
                checkOrder++;
                if (present.contains(check.id())) {
                    continue;
                }
                rules.insertCheck(new AccountingMonitorCheckEntity(null, existing.id(), check.id(), check.name(),
                        check.severity(), check.note(), true, checkOrder, "system", "system", null, null));
                checksSeeded++;
            }
        }
        if (familiesSeeded > 0 || checksSeeded > 0) {
            log.info("Materialized accounting rules for config {}: {} families, {} checks",
                    config.id(), familiesSeeded, checksSeeded);
        }
        materialized.add(config.id());
    }

    /** 生效规则：启用且未删除的规则组（按 sort_order, id），各取启用的检查项；无启用检查项的规则组剔除。 */
    public List<AccountingIntegrityCatalog.Family> effectiveFamilies(Long configId) {
        Map<Long, List<AccountingIntegrityCatalog.Check>> enabledChecks = new LinkedHashMap<>();
        for (AccountingMonitorCheckEntity check : rules.findChecks(configId)) {
            if (!Boolean.TRUE.equals(check.enabled())) {
                continue;
            }
            enabledChecks.computeIfAbsent(check.familyId(), key -> new ArrayList<>())
                    .add(new AccountingIntegrityCatalog.Check(check.checkId(), check.checkName(), check.severity(),
                            check.note()));
        }
        List<AccountingIntegrityCatalog.Family> effective = new ArrayList<>();
        for (AccountingMonitorFamilyEntity family : rules.findFamilies(configId)) {
            if (Boolean.TRUE.equals(family.deleted()) || !Boolean.TRUE.equals(family.enabled())) {
                continue;
            }
            List<AccountingIntegrityCatalog.Check> checks = enabledChecks.getOrDefault(family.id(), List.of());
            if (checks.isEmpty()) {
                continue;
            }
            // SQL 模板按编码执行时经 FunctionCallService 直查，目录中的 SQL 文本仅作种子，这里可为空。
            effective.add(new AccountingIntegrityCatalog.Family(family.familyCode(), null, family.familyName(),
                    family.description(), family.batchSize(), null, null, null, List.copyOf(checks)));
        }
        return effective;
    }

    public boolean hasEffectiveRules(Long configId) {
        return !effectiveFamilies(configId).isEmpty();
    }

    /** 数字员工指南统计（§3.7）：生效规则数、启用规则组/总数、自定义数。 */
    public RuleStats stats(Long configId) {
        List<AccountingMonitorFamilyEntity> families = rules.findFamilies(configId).stream()
                .filter(family -> !Boolean.TRUE.equals(family.deleted()))
                .toList();
        int enabledFamilies = (int) families.stream().filter(family -> Boolean.TRUE.equals(family.enabled())).count();
        int customFamilies = (int) families.stream().filter(family -> "CUSTOM".equals(family.sourceType())).count();
        int effectiveChecks = effectiveFamilies(configId).stream()
                .mapToInt(family -> family.checks().size())
                .sum();
        return new RuleStats(effectiveChecks, enabledFamilies, families.size(), customFamilies);
    }

    public record RuleStats(int effectiveChecks, int enabledFamilies, int totalFamilies, int customFamilies) {
    }
}
