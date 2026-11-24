package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingResultEntity;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 主键高水位固定后顺序分批；每条 SQL 经 FunctionCall 审计。中途失败绝不判为通过。 */
public final class AccountingIntegrityCheckExecutor {
    private static final int SAMPLE_LIMIT = 20;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final FunctionCallService functionCallService;
    private final AccountingRunMapper runMapper;
    private final ObjectMapper json = new ObjectMapper();

    public AccountingIntegrityCheckExecutor(FunctionCallService functionCallService, AccountingRunMapper runMapper) {
        this.functionCallService = functionCallService;
        this.runMapper = runMapper;
    }

    public List<AccountingResultEntity> execute(AccountingMonitorConfigEntity config, AccountingRunEntity run) {
        var families = AccountingIntegrityCatalog.families(config.businessLineCode());
        if (families.isEmpty()) {
            throw new IllegalArgumentException("该业务线尚未登记账务一致性规则");
        }
        List<AccountingResultEntity> results = new ArrayList<>();
        for (var family : families) {
            List<AccountingResultEntity> familyResults = executeFamily(config, run, family);
            for (var result : familyResults) {
                runMapper.insertResult(result);
                results.add(result);
            }
        }
        return results;
    }

    List<AccountingResultEntity> executeFamily(AccountingMonitorConfigEntity config, AccountingRunEntity run,
                                                       AccountingIntegrityCatalog.Family family) {
        long started = System.nanoTime();
        Map<String, Aggregate> totals = new LinkedHashMap<>();
        family.checks().forEach(check -> totals.put(check.id(), new Aggregate()));
        long checked = 0;
        long high = 0;
        long completedThrough = 0;
        String failure = null;
        try {
            Map<String, Object> bounds = single(query(config, run, family, "BOUNDS", Map.of()));
            long low = integer(bounds, "min_id");
            high = integer(bounds, "max_id");
            if (low < 0 || high < low || family.batchSize() < 1 || family.batchSize() > 20000) {
                throw new IllegalArgumentException("主键边界或批量大小无效");
            }
            for (long from = low; from <= high && high > 0;) {
                long to = from + Math.min(high - from, family.batchSize() - 1L);
                var params = FunctionCallService.params("startId", from, "endId", to,
                        "startDateTime", dateLiteral(run.startTime()),
                        "endExclusiveDateTime", dateLiteral(run.endTime()));
                if (bounds.keySet().stream().anyMatch(key -> key.equalsIgnoreCase("legacy_max_id"))) {
                    params.put("legacyMaxId", String.valueOf(integer(bounds, "legacy_max_id")));
                }
                var row = single(query(config, run, family, "SUMMARY", params));
                checked = Math.addExact(checked, integer(row, "checked_count"));
                for (var check : family.checks()) {
                    Aggregate total = totals.get(check.id());
                    long count = integer(row, check.id() + "_n");
                    total.count = Math.addExact(total.count, count);
                    total.difference = total.difference.add(decimal(row, check.id() + "_diff"));
                    if (count > 0 && total.samples.size() < SAMPLE_LIMIT) {
                        params.put("ruleCode", "'" + check.id() + "'");
                        params.put("sampleLimit", String.valueOf(SAMPLE_LIMIT - total.samples.size()));
                        var samples = query(config, run, family, "SAMPLE", params);
                        if (samples == null || samples.isEmpty()) {
                            throw new IllegalArgumentException("异常计数非零但样本为空，请重新核验并发变更");
                        }
                        total.samples.addAll(samples.stream().limit(SAMPLE_LIMIT - total.samples.size()).toList());
                    }
                }
                completedThrough = to;
                if (to == high) {
                    break;
                }
                from = to + 1;
            }
        } catch (Exception ex) {
            failure = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        List<AccountingResultEntity> results = new ArrayList<>();
        for (var check : family.checks()) {
            Aggregate total = totals.get(check.id());
            String status = failure != null ? "FAILED" : checked == 0 ? "SKIPPED"
                    : total.count == 0 ? "PASS" : check.severity();
            String message = "检查 " + checked + " 条来源记录；高水位 " + high + "，已完成至 " + completedThrough
                    + "；" + check.note() + (checked == 0 ? "；无适用样本，未验证" : "")
                    + (failure == null ? "" : "；未完成：" + failure);
            String samples;
            try {
                samples = json.writeValueAsString(total.samples);
            } catch (Exception ex) {
                throw new IllegalStateException("异常样本序列化失败", ex);
            }
            results.add(new AccountingResultEntity(null, run.id(), family.code() + "_" + check.id(),
                    family.name() + "·" + check.name(), "单据支付一致性", "SCENARIO", check.severity(), status,
                    Math.toIntExact(total.count), total.difference, samples,
                    message.substring(0, Math.min(1000, message.length())),
                    Duration.ofNanos(System.nanoTime() - started).toMillis(), null));
        }
        return results;
    }

    private List<Map<String, Object>> query(AccountingMonitorConfigEntity config, AccountingRunEntity run,
                                            AccountingIntegrityCatalog.Family family, String suffix,
                                            Map<String, String> params) {
        String code = family.sqlCode(suffix);
        return functionCallService.executeStatements(config.businessLineCode(), config.environmentCode(),
                List.of(functionCallService.monitorStatement(run.id(), family.code(), code, params))).getFirst();
    }

    private static Map<String, Object> single(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() != 1 || rows.getFirst() == null) {
            throw new IllegalArgumentException("汇总 SQL 必须恰好返回一行");
        }
        return rows.getFirst();
    }

    static BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(key))
                .map(Map.Entry::getValue).filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("汇总列缺失或为空：" + key));
        BigDecimal number = new BigDecimal(value.toString());
        if (number.signum() < 0) {
            throw new IllegalArgumentException("汇总计数/绝对差额不能为负：" + key);
        }
        return number;
    }

    static long integer(Map<String, Object> row, String key) {
        return decimal(row, key).longValueExact();
    }

    private static String dateLiteral(LocalDateTime value) {
        return value == null ? "NULL" : "'" + DATE_TIME.format(value) + "'";
    }

    private static final class Aggregate {
        private long count;
        private BigDecimal difference = BigDecimal.ZERO;
        private final List<Map<String, Object>> samples = new ArrayList<>();
    }
}
