package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class AccountingReconcileExecutor {

    private static final int MESSAGE_LIMIT = 1000;
    private final AccountingMonitorMapper monitorMapper;
    private final AccountingRunMapper runMapper;
    private final AccountingRuleCatalog ruleCatalog;
    private final AccountingDatabaseQueryClient queryClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public AccountingReconcileExecutor(AccountingMonitorMapper monitorMapper,
                                       AccountingRunMapper runMapper,
                                       AccountingRuleCatalog ruleCatalog,
                                       AccountingDatabaseQueryClient queryClient) {
        this(monitorMapper, runMapper, ruleCatalog, queryClient, new ObjectMapper());
    }

    AccountingReconcileExecutor(AccountingMonitorMapper monitorMapper,
                                AccountingRunMapper runMapper,
                                AccountingRuleCatalog ruleCatalog,
                                AccountingDatabaseQueryClient queryClient,
                                ObjectMapper objectMapper) {
        this.monitorMapper = monitorMapper;
        this.runMapper = runMapper;
        this.ruleCatalog = ruleCatalog;
        this.queryClient = queryClient;
        this.objectMapper = objectMapper;
    }

    public void execute(Long runId) {
        if (runMapper.markRunning(runId) == 0) {
            return;
        }
        AccountingRunEntity run = runMapper.findById(runId);
        if (run == null) {
            return;
        }
        AccountingMonitorConfigEntity config = monitorMapper.findById(run.configId());
        if (config == null) {
            failRun(runId, "账务监测配置不存在");
            return;
        }

        try {
            List<AccountingRuleDefinition> rules = selectedRules(config, run.ruleCodes());
            Set<String> existingTables = loadExistingTables(config, rules);
            List<AccountingResultEntity> results = new ArrayList<>();
            for (AccountingRuleDefinition rule : rules) {
                AccountingResultEntity result = executeRule(config, run, rule, existingTables);
                runMapper.insertResult(result);
                results.add(result);
            }
            finishRun(runId, results);
        } catch (Exception ex) {
            failRun(runId, safeMessage(ex));
        }
    }

    private AccountingResultEntity executeRule(AccountingMonitorConfigEntity config,
                                               AccountingRunEntity run,
                                               AccountingRuleDefinition rule,
                                               Set<String> existingTables) {
        long started = System.nanoTime();
        if (AccountingRuleCatalog.DAILY.equals(rule.granularity())
                && !AccountingRuleCatalog.isWholeDayWindow(run.startTime(), run.endTime())) {
            return result(run.id(), rule, "SKIPPED", 0, BigDecimal.ZERO, "[]",
                    "当前为非整日区间，日级账单/对账单规则未执行", elapsedMillis(started));
        }
        Set<String> missing = new LinkedHashSet<>(rule.requiredTables());
        missing.removeAll(existingTables);
        if (!missing.isEmpty()) {
            return result(run.id(), rule, "SKIPPED", 0, BigDecimal.ZERO, "[]",
                    "生产 schema 缺少规则所需白名单表：" + String.join(",", missing), elapsedMillis(started));
        }

        try {
            AccountingRuleSql sql = rule.buildSql(config.schemaName(), run.startTime(), run.endTime());
            List<Map<String, Object>> rows = queryClient.runRows(config, sql.summarySql());
            Map<String, Object> summary = rows.isEmpty() ? Map.of() : rows.getFirst();
            int anomalyCount = intValue(value(summary, "anomaly_count"));
            BigDecimal differenceAmount = decimalValue(value(summary, "difference_amount"));
            String sampleJson = "[]";
            if (anomalyCount > 0) {
                sampleJson = writeSamples(queryClient.runRows(config, sql.sampleSql()));
            }
            String status = anomalyCount == 0
                    ? "PASS"
                    : ("WARN".equals(rule.severity()) ? "WARN" : "ERROR");
            String message = anomalyCount == 0
                    ? "未发现异常"
                    : "发现 " + anomalyCount + " 条异常，差异金额 " + differenceAmount.toPlainString();
            return result(run.id(), rule, status, anomalyCount, differenceAmount,
                    sampleJson, message, elapsedMillis(started));
        } catch (Exception ex) {
            return result(run.id(), rule, "FAILED", 0, BigDecimal.ZERO, "[]",
                    safeMessage(ex), elapsedMillis(started));
        }
    }

    private Set<String> loadExistingTables(AccountingMonitorConfigEntity config,
                                           List<AccountingRuleDefinition> rules) {
        String schema = AccountingRuleCatalog.validateSchema(config.schemaName());
        Set<String> requiredTables = rules.stream()
                .flatMap(rule -> rule.requiredTables().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requiredTables.isEmpty()) {
            return Set.of();
        }
        String tableNames = requiredTables.stream()
                .map(name -> "'" + name + "'")
                .collect(java.util.stream.Collectors.joining(","));
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = '%s'
                  AND table_type = 'BASE TABLE'
                  AND table_name IN (%s)
                ORDER BY table_name
                LIMIT 100
                """.formatted(schema, tableNames);
        Set<String> tables = new LinkedHashSet<>();
        for (Map<String, Object> row : queryClient.runRows(config, sql)) {
            Object value = value(row, "table_name");
            if (value != null) {
                tables.add(String.valueOf(value));
            }
        }
        return tables;
    }

    private List<AccountingRuleDefinition> selectedRules(AccountingMonitorConfigEntity config,
                                                         String selectedRuleCodes) {
        List<AccountingRuleDefinition> available = ruleCatalog.rulesForProfile(config.ruleProfile());
        Set<String> selected = csvSet(selectedRuleCodes);
        if (selected.isEmpty()) {
            selected.addAll(monitorMapper.findEnabledRuleCodes(config.id()));
            if (selected.isEmpty() && monitorMapper.countRuleConfigs(config.id()) > 0) {
                return List.of();
            }
        }
        if (selected.isEmpty()) {
            return available;
        }
        return available.stream().filter(rule -> selected.contains(rule.ruleCode())).toList();
    }

    private void finishRun(Long runId, List<AccountingResultEntity> results) {
        int passed = count(results, "PASS");
        int warnings = count(results, "WARN");
        int errors = count(results, "ERROR");
        int failedRules = count(results, "FAILED");
        int skipped = count(results, "SKIPPED");
        int anomalies = results.stream().mapToInt(item -> item.anomalyCount() == null ? 0 : item.anomalyCount()).sum();
        String status;
        String errorMessage = null;
        if (results.isEmpty() || failedRules == results.size()) {
            status = "FAILED";
            errorMessage = "没有规则成功执行";
        } else if (skipped == results.size()) {
            status = "PARTIAL";
            errorMessage = "当前时间区间内没有适用规则";
        } else if (failedRules > 0) {
            status = "PARTIAL";
            errorMessage = failedRules + " 条规则执行失败";
        } else if (errors > 0 || warnings > 0) {
            status = "WARNING";
        } else {
            status = "SUCCESS";
        }
        runMapper.finishRun(runId, status, results.size(), passed, warnings,
                errors + failedRules, skipped, anomalies, errorMessage);
    }

    private void failRun(Long runId, String message) {
        runMapper.finishRun(runId, "FAILED", 0, 0, 0, 0, 0, 0, truncate(message));
    }

    private AccountingResultEntity result(Long runId,
                                          AccountingRuleDefinition rule,
                                          String status,
                                          int anomalyCount,
                                          BigDecimal differenceAmount,
                                          String sampleJson,
                                          String message,
                                          long durationMs) {
        return new AccountingResultEntity(
                null,
                runId,
                rule.ruleCode(),
                rule.ruleName(),
                rule.category(),
                rule.granularity(),
                rule.severity(),
                status,
                anomalyCount,
                differenceAmount,
                sampleJson,
                truncate(message),
                durationMs,
                null
        );
    }

    private int count(List<AccountingResultEntity> results, String status) {
        return (int) results.stream().filter(item -> status.equals(item.status())).count();
    }

    private Set<String> csvSet(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String item : value.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim());
            }
        }
        return result;
    }

    private Object value(Map<String, Object> row, String key) {
        Object exact = row.get(key);
        if (exact != null) {
            return exact;
        }
        return row.entrySet().stream()
                .filter(entry -> key.equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
    }

    private int intValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return new BigDecimal(String.valueOf(value)).intValue();
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private String writeSamples(List<Map<String, Object>> samples) {
        try {
            return objectMapper.writeValueAsString(samples);
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return truncate(message);
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MESSAGE_LIMIT) {
            return value;
        }
        return value.substring(0, MESSAGE_LIMIT);
    }
}
