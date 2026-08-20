package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardSummaryResponse;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorSaveRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorStatusResponse;
import cn.aslight.workhub.model.ops.AccountingRuleResponse;
import cn.aslight.workhub.model.ops.AccountingRunDetailResponse;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class AccountingMonitorService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final Duration MAX_MANUAL_RANGE = Duration.ofDays(31);
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AccountingMonitorMapper monitorMapper;
    private final AccountingRunMapper runMapper;
    private final AccountingRuleCatalog ruleCatalog;
    private final AccountingReconcileExecutor reconcileExecutor;
    private final TaskExecutor taskExecutor;

    public AccountingMonitorService(AccountingMonitorMapper monitorMapper,
                                    AccountingRunMapper runMapper,
                                    AccountingRuleCatalog ruleCatalog,
                                    AccountingReconcileExecutor reconcileExecutor,
                                    @Qualifier("accountingTaskExecutor") TaskExecutor taskExecutor) {
        this.monitorMapper = monitorMapper;
        this.runMapper = runMapper;
        this.ruleCatalog = ruleCatalog;
        this.reconcileExecutor = reconcileExecutor;
        this.taskExecutor = taskExecutor;
    }

    public List<AccountingMonitorConfigEntity> listConfigs(String businessLineCode, boolean enabledOnly) {
        return monitorMapper.findAll(trimToNull(businessLineCode), enabledOnly);
    }

    @Transactional
    public AccountingMonitorConfigEntity createConfig(AccountingMonitorSaveRequest request) {
        validateConfig(request);
        AccountingMonitorConfigEntity duplicate = monitorMapper.findByIdentity(
                request.businessLineCode().trim(),
                request.environmentCode().trim(),
                request.systemName().trim());
        if (duplicate != null) {
            throw new IllegalArgumentException("该业务线、环境和系统的账务监测配置已存在");
        }
        AccountingMonitorConfigEntity entity = configEntity(null, request);
        monitorMapper.insert(entity);
        return monitorMapper.findByIdentity(
                entity.businessLineCode(), entity.environmentCode(), entity.systemName());
    }

    @Transactional
    public AccountingMonitorConfigEntity updateConfig(Long id, AccountingMonitorSaveRequest request) {
        validateConfig(request);
        if (monitorMapper.findById(id) == null) {
            throw new IllegalArgumentException("账务监测配置不存在");
        }
        AccountingMonitorConfigEntity duplicate = monitorMapper.findByIdentity(
                request.businessLineCode().trim(),
                request.environmentCode().trim(),
                request.systemName().trim());
        if (duplicate != null && !id.equals(duplicate.id())) {
            throw new IllegalArgumentException("该业务线、环境和系统的账务监测配置已存在");
        }
        AccountingMonitorConfigEntity entity = configEntity(id, request);
        monitorMapper.update(entity);
        return monitorMapper.findById(id);
    }

    public AccountingDashboardResponse dashboard(String businessLineCode) {
        List<AccountingMonitorStatusResponse> monitors = listConfigs(businessLineCode, false).stream()
                .map(config -> new AccountingMonitorStatusResponse(config, runMapper.findLatestByConfigId(config.id())))
                .toList();
        List<AccountingRunEntity> latestRuns = monitors.stream()
                .map(AccountingMonitorStatusResponse::latestRun)
                .filter(java.util.Objects::nonNull)
                .toList();
        int running = statusCount(latestRuns, "PENDING") + statusCount(latestRuns, "RUNNING");
        int success = statusCount(latestRuns, "SUCCESS");
        int warning = statusCount(latestRuns, "WARNING") + statusCount(latestRuns, "PARTIAL");
        int error = statusCount(latestRuns, "FAILED");
        int anomalies = latestRuns.stream().mapToInt(item -> item.anomalyCount() == null ? 0 : item.anomalyCount()).sum();
        return new AccountingDashboardResponse(
                new AccountingDashboardSummaryResponse(monitors.size(), running, success, warning, error, anomalies),
                monitors
        );
    }

    public List<AccountingRuleResponse> listRules(String ruleProfile) {
        return ruleCatalog.responses(requireValue(ruleProfile, "规则包不能为空"));
    }

    public List<AccountingRunEntity> listRuns(String businessLineCode,
                                              String status,
                                              int page,
                                              int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 200);
        return runMapper.findRuns(trimToNull(businessLineCode), trimToNull(status),
                (normalizedPage - 1) * normalizedSize, normalizedSize);
    }

    public int countRuns(String businessLineCode, String status) {
        return runMapper.countRuns(trimToNull(businessLineCode), trimToNull(status));
    }

    public AccountingRunDetailResponse detail(Long id) {
        AccountingRunEntity run = requireRun(id);
        return new AccountingRunDetailResponse(run, runMapper.findResults(id));
    }

    public AccountingRunEntity submitManual(AccountingManualRunRequest request, String operator) {
        AccountingMonitorConfigEntity config = requireConfig(request.configId());
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new IllegalArgumentException("账务监测配置未启用");
        }
        validateWindow(request.startTime(), request.endTime());
        String ruleCodes = validateRuleCodes(config.ruleProfile(), request.ruleCodes());
        String requestKey = trimToNull(request.requestKey());
        if (requestKey == null) {
            requestKey = UUID.randomUUID().toString();
        }
        String idempotencyKey = "MANUAL:" + config.id() + ":" + requestKey;
        return submit(config, "MANUAL", request.startTime(), request.endTime(),
                ruleCodes, idempotencyKey, operator == null ? "unknown" : operator);
    }

    public AccountingRunEntity retry(Long id, String operator) {
        AccountingRunEntity source = requireRun(id);
        AccountingMonitorConfigEntity config = requireConfig(source.configId());
        String key = "RETRY:" + source.id() + ":" + UUID.randomUUID();
        return submit(config, "RETRY", source.startTime(), source.endTime(),
                source.ruleCodes(), key, operator == null ? "unknown" : operator);
    }

    public void submitPreviousDay() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime start = LocalDateTime.of(today.minusDays(1), LocalTime.MIDNIGHT);
        LocalDateTime end = LocalDateTime.of(today, LocalTime.MIDNIGHT);
        for (AccountingMonitorConfigEntity config : monitorMapper.findAll(null, true)) {
            if (!Boolean.TRUE.equals(config.dailyEnabled())) {
                continue;
            }
            String key = "SCHEDULED:" + config.id() + ":" + start + ":" + end;
            submit(config, "SCHEDULED", start, end, null, key, "SYSTEM");
        }
    }

    private AccountingRunEntity submit(AccountingMonitorConfigEntity config,
                                       String triggerType,
                                       LocalDateTime startTime,
                                       LocalDateTime endTime,
                                       String ruleCodes,
                                       String idempotencyKey,
                                       String operator) {
        AccountingRunEntity existing = runMapper.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        if (trimToNull(config.databaseTargetKey()) == null) {
            throw new IllegalArgumentException("当前业务线尚未配置生产数据库目标");
        }
        String runNo = "AR" + RUN_TIME.format(LocalDateTime.now(ZONE))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        try {
            runMapper.insertRun(runNo, idempotencyKey, config.id(), triggerType,
                    startTime, endTime, ruleCodes, "PENDING", operator);
        } catch (DataIntegrityViolationException ex) {
            AccountingRunEntity concurrent = runMapper.findByIdempotencyKey(idempotencyKey);
            if (concurrent != null) {
                return concurrent;
            }
            throw ex;
        }
        Long runId = runMapper.findIdByRunNo(runNo);
        if (runId == null) {
            throw new IllegalStateException("对账批次创建后未能取得批次ID");
        }
        try {
            taskExecutor.execute(() -> reconcileExecutor.execute(runId));
        } catch (TaskRejectedException ex) {
            runMapper.finishRun(runId, "FAILED", 0, 0, 0, 0, 0, 0,
                    "账务对账执行队列已满，请稍后重试");
        }
        return runMapper.findById(runId);
    }

    private void validateConfig(AccountingMonitorSaveRequest request) {
        AccountingRuleCatalog.validateSchema(request.schemaName());
        if (!ruleCatalog.supportedProfiles().contains(request.ruleProfile())) {
            throw new IllegalArgumentException("不支持的账务规则包：" + request.ruleProfile());
        }
        if ((Boolean.TRUE.equals(request.enabled()) || Boolean.TRUE.equals(request.dailyEnabled()))
                && trimToNull(request.databaseTargetKey()) == null) {
            throw new IllegalArgumentException("启用账务监测前必须配置数据库目标");
        }
        if (Boolean.TRUE.equals(request.dailyEnabled()) && !Boolean.TRUE.equals(request.enabled())) {
            throw new IllegalArgumentException("启用每日任务前必须先启用账务监测配置");
        }
    }

    private void validateWindow(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null || !startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("开始时间必须早于结束时间");
        }
        if (!isHour(startTime) || !isHour(endTime)) {
            throw new IllegalArgumentException("手动对账时间必须精确到整点");
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_MANUAL_RANGE) > 0) {
            throw new IllegalArgumentException("单次手动对账区间不能超过31天");
        }
    }

    private boolean isHour(LocalDateTime value) {
        return value.getMinute() == 0 && value.getSecond() == 0 && value.getNano() == 0;
    }

    private String validateRuleCodes(String profile, List<String> requestedCodes) {
        if (requestedCodes == null || requestedCodes.isEmpty()) {
            return null;
        }
        Set<String> available = ruleCatalog.rulesForProfile(profile).stream()
                .map(AccountingRuleDefinition::ruleCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<String> selected = requestedCodes.stream()
                .map(this::trimToNull)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!available.containsAll(selected)) {
            selected.removeAll(available);
            throw new IllegalArgumentException("包含不属于当前规则包的规则：" + String.join(",", selected));
        }
        return String.join(",", selected);
    }

    private AccountingMonitorConfigEntity configEntity(Long id, AccountingMonitorSaveRequest request) {
        return new AccountingMonitorConfigEntity(
                id,
                request.businessLineCode().trim(),
                null,
                request.environmentCode().trim(),
                request.systemName().trim(),
                request.displayName().trim(),
                trimToNull(request.databaseTargetKey()),
                request.schemaName().trim(),
                request.ruleProfile().trim(),
                request.enabled(),
                request.dailyEnabled(),
                trimToNull(request.remark()),
                null,
                null
        );
    }

    private AccountingMonitorConfigEntity requireConfig(Long id) {
        AccountingMonitorConfigEntity config = monitorMapper.findById(id);
        if (config == null) {
            throw new IllegalArgumentException("账务监测配置不存在");
        }
        return config;
    }

    private AccountingRunEntity requireRun(Long id) {
        AccountingRunEntity run = runMapper.findById(id);
        if (run == null) {
            throw new IllegalArgumentException("对账批次不存在");
        }
        return run;
    }

    private int statusCount(List<AccountingRunEntity> runs, String status) {
        return (int) runs.stream().filter(item -> status.equals(item.status())).count();
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
