package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotNotifier;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * REQ-d227e09d：账务场景核对执行器——按配置的 func_call_sql 编码渲染计数 SQL 经
 * FunctionCallService 只读执行（逐条落 func_call_sql_audit_log），三段式判定
 * （按时执行 / 应发起 vs 实际 / 实际 vs 成功）写 ops_account_reconcile_result，
 * 差异超出容差时按配置推送 WeCom 机器人。
 */
@Service
public class AccountingScenarioCheckExecutor {

    private static final Logger log = LoggerFactory.getLogger(AccountingScenarioCheckExecutor.class);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MESSAGE_LIMIT = 1000;

    private final AccountingMonitorMapper monitorMapper;
    private final AccountingRunMapper runMapper;
    private final FunctionCallService functionCallService;
    private final WecomRobotNotifier wecomRobotNotifier;

    @Autowired
    public AccountingScenarioCheckExecutor(AccountingMonitorMapper monitorMapper,
                                           AccountingRunMapper runMapper,
                                           FunctionCallService functionCallService,
                                           WecomRobotNotifier wecomRobotNotifier) {
        this(monitorMapper, runMapper, functionCallService, wecomRobotNotifier, true);
    }

    AccountingScenarioCheckExecutor(AccountingMonitorMapper monitorMapper,
                                    AccountingRunMapper runMapper,
                                    FunctionCallService functionCallService,
                                    WecomRobotNotifier wecomRobotNotifier, boolean wecomAvailable) {
        this.monitorMapper = monitorMapper;
        this.runMapper = runMapper;
        this.functionCallService = functionCallService;
        this.wecomRobotNotifier = wecomAvailable ? wecomRobotNotifier : null;
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
            failRun(runId, "业务监测配置不存在");
            return;
        }
        try {
            if (AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) {
                List<AccountingResultEntity> results = new AccountingIntegrityCheckExecutor(functionCallService, runMapper)
                        .execute(config, run);
                finishRun(runId, results);
                return;
            }
            // 实际发起笔数是全部维度的基准，取数失败整批失败。
            CountResult actual = count(config, run, config.actualCountSqlCode(), "实际发起笔数");
            List<AccountingResultEntity> results = new ArrayList<>();
            results.add(startedOnTimeResult(config, run, actual));
            results.add(expectedVsActualResult(config, run, actual));
            results.add(successRateResult(config, run, actual));
            for (AccountingResultEntity result : results) {
                runMapper.insertResult(result);
            }
            String status = finishRun(runId, results);
            alertIfNecessary(config, run, results, status);
        } catch (Exception ex) {
            failRun(runId, safeMessage(ex));
        }
    }

    private AccountingResultEntity startedOnTimeResult(AccountingMonitorConfigEntity config,
                                                       AccountingRunEntity run,
                                                       CountResult actual) {
        String scenarioName = AccountingScenarioCatalog.typeName(config.scenarioType());
        if (actual.value() == 0) {
            return result(run.id(), config, AccountingScenarioCatalog.STARTED_ON_TIME, "ERROR", 1,
                    "核对时间已过，当日未发起任何" + scenarioName + "记录", actual.queryDurationMs());
        }
        return result(run.id(), config, AccountingScenarioCatalog.STARTED_ON_TIME, "PASS", 0,
                "当日已发起 " + actual.value() + " 笔" + scenarioName + "记录", actual.queryDurationMs());
    }

    /** 预期来源：expected SQL 或固定值；与实际差异超出容差记 ERROR。 */
    private AccountingResultEntity expectedVsActualResult(AccountingMonitorConfigEntity config,
                                                          AccountingRunEntity run,
                                                          CountResult actual) {
        long started = System.nanoTime();
        long durationMs = actual.queryDurationMs();
        try {
            CountResult expected = config.expectedCountSqlCode() == null
                    ? null
                    : count(config, run, config.expectedCountSqlCode(), "应发起笔数");
            durationMs = expected == null
                    ? Duration.ofNanos(System.nanoTime() - started).toMillis()
                    : expected.queryDurationMs();
            long expectedValue = expected == null ? fixedExpected(config) : expected.value();
            long actualValue = actual.value();
            long difference = Math.abs(expectedValue - actualValue);
            long allowed = allowedDifference(config, expectedValue);
            String message = "应发起 " + expectedValue + " 笔，实际发起 " + actualValue + " 笔，差 "
                    + difference + " 笔（容差 " + percent(config) + "，允许 " + allowed + " 笔）";
            if (difference > allowed) {
                return result(run.id(), config, AccountingScenarioCatalog.EXPECTED_VS_ACTUAL, "ERROR",
                        (int) Math.min(difference, Integer.MAX_VALUE), message, durationMs);
            }
            return result(run.id(), config, AccountingScenarioCatalog.EXPECTED_VS_ACTUAL, "PASS", 0,
                    message, durationMs);
        } catch (Exception ex) {
            return result(run.id(), config, AccountingScenarioCatalog.EXPECTED_VS_ACTUAL, "FAILED", 0,
                    safeMessage(ex), durationMs);
        }
    }

    private AccountingResultEntity successRateResult(AccountingMonitorConfigEntity config,
                                                     AccountingRunEntity run,
                                                     CountResult actual) {
        long started = System.nanoTime();
        if (config.successCountSqlCode() == null) {
            return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "SKIPPED", 0,
                    "未配置成功笔数 SQL，成功率维度未评估",
                    Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
        try {
            CountResult success = count(config, run, config.successCountSqlCode(), "成功笔数");
            long actualValue = actual.value();
            if (actualValue == 0) {
                return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "SKIPPED", 0,
                        "当日未发起" + AccountingScenarioCatalog.typeName(config.scenarioType())
                                + "记录，无成功率可核对",
                        success.queryDurationMs());
            }
            long successValue = success.value();
            long failed = actualValue - successValue;
            if (failed < 0) {
                return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "ERROR",
                        (int) Math.min(-failed, Integer.MAX_VALUE),
                        "成功 " + successValue + " 笔大于发起 " + actualValue + " 笔，计数口径异常",
                        success.queryDurationMs());
            }
            long allowed = allowedDifference(config, actualValue);
            String message = "发起 " + actualValue + " 笔，成功 " + successValue + " 笔，未成功 " + failed
                    + " 笔（容差 " + percent(config) + "，允许 " + allowed + " 笔）";
            if (successValue == 0) {
                return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "ERROR",
                        (int) Math.min(failed, Integer.MAX_VALUE), message + "；成功笔数为 0",
                        success.queryDurationMs());
            }
            if (failed > allowed) {
                return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "WARN",
                        (int) Math.min(failed, Integer.MAX_VALUE), message, success.queryDurationMs());
            }
            return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "PASS", 0,
                    message, success.queryDurationMs());
        } catch (Exception ex) {
            return result(run.id(), config, AccountingScenarioCatalog.SUCCESS_RATE, "FAILED", 0,
                    safeMessage(ex), Duration.ofNanos(System.nanoTime() - started).toMillis());
        }
    }

    /** 渲染并执行计数 SQL：必须返回恰好一行、首列为非负整数。 */
    private CountResult count(AccountingMonitorConfigEntity config, AccountingRunEntity run,
                              String sqlCode, String purpose) {
        long started = System.nanoTime();
        var statement = functionCallService.monitorStatement(run.id(), purpose, sqlCode, FunctionCallService.params(
                "checkDate", run.startTime().toLocalDate().toString(),
                "startDateTime", DATE_TIME.format(run.startTime()),
                "endExclusiveDateTime", DATE_TIME.format(run.endTime())));
        List<Map<String, Object>> rows = functionCallService.executeStatements(
                        config.businessLineCode(), config.environmentCode(),
                        List.of(statement))
                .getFirst();
        if (rows == null || rows.size() != 1 || rows.getFirst() == null) {
            throw new IllegalArgumentException(purpose + " SQL 必须返回一行计数：" + sqlCode);
        }
        Object value = rows.getFirst().values().iterator().next();
        if (value == null) {
            throw new IllegalArgumentException(purpose + " SQL 计数为空：" + sqlCode);
        }
        long countValue;
        try {
            countValue = Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(purpose + " SQL 计数不是整数：" + sqlCode + " -> " + value, ex);
        }
        if (countValue < 0) {
            throw new IllegalArgumentException(purpose + " SQL 计数不能为负数：" + sqlCode);
        }
        return new CountResult(countValue, Duration.ofNanos(System.nanoTime() - started).toMillis());
    }

    private long fixedExpected(AccountingMonitorConfigEntity config) {
        Integer fixed = config.expectedFixedCount();
        if (fixed == null || fixed < 0) {
            throw new IllegalArgumentException(
                    "应发起笔数来源缺失：expected_count_sql_code 与 expected_fixed_count 均未配置");
        }
        return fixed;
    }

    private long allowedDifference(AccountingMonitorConfigEntity config, long base) {
        return tolerance(config).multiply(BigDecimal.valueOf(base))
                .setScale(0, RoundingMode.DOWN).longValue();
    }

    private BigDecimal tolerance(AccountingMonitorConfigEntity config) {
        BigDecimal rate = config.toleranceRate();
        return rate == null ? BigDecimal.ZERO : rate;
    }

    private String percent(AccountingMonitorConfigEntity config) {
        return tolerance(config).multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }

    private String finishRun(Long runId, List<AccountingResultEntity> results) {
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
            errorMessage = "没有维度成功执行";
        } else if (failedRules > 0) {
            status = "PARTIAL";
            errorMessage = failedRules + " 条维度执行失败";
        } else if (errors > 0 || warnings > 0) {
            status = "WARNING";
        } else {
            status = "SUCCESS";
        }
        runMapper.finishRun(runId, status, results.size(), passed, warnings,
                errors + failedRules, skipped, anomalies, errorMessage);
        return status;
    }

    private void alertIfNecessary(AccountingMonitorConfigEntity config, AccountingRunEntity run,
                                  List<AccountingResultEntity> results, String status) {
        if (!Boolean.TRUE.equals(config.alertEnabled()) || wecomRobotNotifier == null) {
            return;
        }
        List<AccountingResultEntity> abnormal = results.stream()
                .filter(item -> "WARN".equals(item.status()) || "ERROR".equals(item.status())
                        || "FAILED".equals(item.status()))
                .toList();
        if (abnormal.isEmpty()) {
            return;
        }
        StringBuilder content = new StringBuilder("""
                **账务场景监测告警**
                > 业务线：%s %s
                > 系统：%s
                > 场景：%s
                > 核对日：%s
                > 批次：%s（%s / %s）
                """.formatted(
                config.businessLineCode(),
                config.displayName(),
                config.systemName(),
                AccountingScenarioCatalog.typeName(config.scenarioType()),
                run.startTime().toLocalDate(),
                run.runNo(),
                status,
                run.triggerType()));
        for (AccountingResultEntity item : abnormal) {
            content.append("> - [").append(item.status()).append("] ")
                    .append(item.ruleName()).append("：").append(item.message()).append('\n');
        }
        try {
            wecomRobotNotifier.sendMarkdownMessage("accounting-scenario-" + run.runNo(), content.toString());
        } catch (Exception ex) {
            log.warn("Accounting scenario alert send failed. runNo={}, message={}", run.runNo(), ex.getMessage());
        }
    }

    private void failRun(Long runId, String message) {
        runMapper.finishRun(runId, "FAILED", 0, 0, 0, 0, 0, 0, truncate(message));
    }

    private AccountingResultEntity result(Long runId,
                                          AccountingMonitorConfigEntity config,
                                          String dimensionCode,
                                          String status,
                                          int anomalyCount,
                                          String message,
                                          long durationMs) {
        return new AccountingResultEntity(
                null,
                runId,
                AccountingScenarioCatalog.ruleCode(config.scenarioType(), dimensionCode),
                AccountingScenarioCatalog.dimensionName(dimensionCode),
                AccountingScenarioCatalog.typeName(config.scenarioType()),
                "SCENARIO",
                AccountingScenarioCatalog.SUCCESS_RATE.equals(dimensionCode) ? "WARN" : "ERROR",
                status,
                anomalyCount,
                BigDecimal.ZERO,
                "[]",
                truncate(message),
                durationMs,
                null
        );
    }

    private int count(List<AccountingResultEntity> results, String status) {
        return (int) results.stream().filter(item -> status.equals(item.status())).count();
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

    private record CountResult(long value, long queryDurationMs) {
    }
}
