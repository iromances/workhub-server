package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.AccountingMonitorMapper;
import cn.aslight.workhub.dao.ops.AccountingRunMapper;
import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardSummaryResponse;
import cn.aslight.workhub.model.ops.AccountingManualRunRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.model.ops.AccountingMonitorSaveRequest;
import cn.aslight.workhub.model.ops.AccountingMonitorStatusResponse;
import cn.aslight.workhub.model.ops.AccountingRunDetailResponse;
import cn.aslight.workhub.model.ops.AccountingRunEntity;
import cn.aslight.workhub.model.ops.AccountingScenarioTypeResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * REQ-d227e09d：账务场景监测——配置按业务场景维度维护，核对按日窗口执行；
 * 计数 SQL 全部经 func_call_sql 登记渲染，不再扫表做数据一致性校验。
 */
@Service
public class AccountingMonitorService {

    private static final ZoneId ZONE = AccountingExecutionEvidenceService.RECORD_ZONE;
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AccountingMonitorMapper monitorMapper;
    private final AccountingRunMapper runMapper;
    private final AccountingScenarioCheckExecutor scenarioCheckExecutor;
    private final FunctionCallService functionCallService;
    private final TaskExecutor taskExecutor;
    private org.springframework.transaction.support.TransactionTemplate rejectionTransaction;
    @org.springframework.beans.factory.annotation.Autowired
    public void setTransactionManager(org.springframework.transaction.PlatformTransactionManager manager) {
        rejectionTransaction = new org.springframework.transaction.support.TransactionTemplate(manager);
        rejectionTransaction.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private cn.aslight.workhub.dao.ops.FuncCallAuditMapper audits;
    private cn.aslight.workhub.dao.agent.AgentMonitorRunMapper links;
    @org.springframework.beans.factory.annotation.Autowired
    public void setEvidenceMappers(cn.aslight.workhub.dao.ops.FuncCallAuditMapper audits,
                                   cn.aslight.workhub.dao.agent.AgentMonitorRunMapper links) {
        this.audits = audits; this.links = links;
    }
    private cn.aslight.workhub.dao.ops.AccountingRuleMapper ruleMapper;
    private AccountingRuleInitializer ruleInitializer;
    /** REQ-7f26a063：规则组存取与种子；未注入时（无库单测）回退静态目录口径。 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRuleStore(cn.aslight.workhub.dao.ops.AccountingRuleMapper ruleMapper,
                             AccountingRuleInitializer ruleInitializer) {
        this.ruleMapper = ruleMapper;
        this.ruleInitializer = ruleInitializer;
    }

    /** REQ-372e4ce1：family 为逗号分隔的规则组/用途过滤；去空白、去重，最多取前 8 个。 */
    public SqlEvidencePage sqlEvidence(Long id, String family, int page, int pageSize) {
        requireRun(id);
        int size = Math.min(50, Math.max(1, pageSize));
        List<String> families = families(family);
        return new SqlEvidencePage(audits.countMonitor(id, families),
                audits.forMonitor(id, families, size, (Math.max(1, page)-1)*size));
    }

    private List<String> families(String family) {
        if (family == null || family.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(family.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .limit(8)
                .toList();
    }

    public record SqlEvidencePage(long total, List<cn.aslight.workhub.model.ops.FuncCallAuditEntity> items) { }

    public List<String> agentRuns(Long id) { requireRun(id); return links.agents(id); }

    /** REQ-7f26a063：规则组按配置读取（含停用与已删除）；无库存取时回退静态目录。 */
    public List<java.util.Map<String, Object>> rules(Long configId) {
        var config = requireConfig(configId);
        if (!AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) return List.of();
        if (ruleMapper == null || ruleInitializer == null) {
            return AccountingIntegrityCatalog.families(config.businessLineCode()).stream().map(f -> java.util.Map.<String,Object>of(
                    "code", f.code(), "name", f.name(), "checks", f.checks(),
                    "sqlCodes", List.of(f.sqlCode("BOUNDS"), f.sqlCode("SUMMARY"), f.sqlCode("SAMPLE")))).toList();
        }
        ruleInitializer.ensureMaterialized(config);
        var checksByFamily = new java.util.LinkedHashMap<Long, List<java.util.Map<String, Object>>>();
        for (var check : ruleMapper.findChecks(configId)) {
            checksByFamily.computeIfAbsent(check.familyId(), key -> new java.util.ArrayList<>())
                    .add(new java.util.LinkedHashMap<>(java.util.Map.of(
                            "id", check.checkId(), "name", check.checkName(), "severity", check.severity(),
                            "note", check.note() == null ? "" : check.note(), "enabled", check.enabled())));
        }
        var result = new java.util.ArrayList<java.util.Map<String, Object>>();
        for (var family : ruleMapper.findFamilies(configId)) {
            var item = new java.util.LinkedHashMap<String, Object>();
            item.put("code", family.familyCode());
            item.put("name", family.familyName());
            item.put("description", family.description());
            item.put("batchSize", family.batchSize());
            item.put("sourceType", family.sourceType());
            item.put("enabled", family.enabled());
            item.put("deleted", family.deleted());
            item.put("sortOrder", family.sortOrder());
            item.put("checks", checksByFamily.getOrDefault(family.id(), List.of()));
            item.put("sqlCodes", List.of(IntegritySqlContract.sqlCode(family.familyCode(), "BOUNDS"),
                    IntegritySqlContract.sqlCode(family.familyCode(), "SUMMARY"),
                    IntegritySqlContract.sqlCode(family.familyCode(), "SAMPLE")));
            result.add(item);
        }
        return result;
    }

    public AccountingMonitorService(AccountingMonitorMapper monitorMapper,
                                    AccountingRunMapper runMapper,
                                    AccountingScenarioCheckExecutor scenarioCheckExecutor,
                                    FunctionCallService functionCallService,
                                    @Qualifier("accountingTaskExecutor") TaskExecutor taskExecutor) {
        this.monitorMapper = monitorMapper;
        this.runMapper = runMapper;
        this.scenarioCheckExecutor = scenarioCheckExecutor;
        this.functionCallService = functionCallService;
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
                normalizeEnvironmentCode(request.environmentCode()),
                request.systemName().trim(),
                request.scenarioType().trim());
        if (duplicate != null) {
            throw new IllegalArgumentException("该业务线、环境、系统和场景的业务监测配置已存在");
        }
        AccountingMonitorConfigEntity entity = configEntity(null, request);
        monitorMapper.insert(entity);
        AccountingMonitorConfigEntity created = monitorMapper.findByIdentity(
                entity.businessLineCode(), entity.environmentCode(),
                entity.systemName(), entity.scenarioType());
        // REQ-7f26a063：新建一致性配置在同一事务内物化内置规则组。
        if (created != null && ruleInitializer != null) {
            ruleInitializer.ensureMaterialized(created);
        }
        return created;
    }

    @Transactional
    public AccountingMonitorConfigEntity updateConfig(Long id, AccountingMonitorSaveRequest request) {
        validateConfig(request);
        if (monitorMapper.findById(id) == null) {
            throw new IllegalArgumentException("业务监测配置不存在");
        }
        AccountingMonitorConfigEntity duplicate = monitorMapper.findByIdentity(
                request.businessLineCode().trim(),
                normalizeEnvironmentCode(request.environmentCode()),
                request.systemName().trim(),
                request.scenarioType().trim());
        if (duplicate != null && !id.equals(duplicate.id())) {
            throw new IllegalArgumentException("该业务线、环境、系统和场景的业务监测配置已存在");
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
                .filter(Objects::nonNull)
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

    public List<AccountingScenarioTypeResponse> scenarioTypes() {
        return AccountingScenarioCatalog.responses();
    }

    public List<AccountingRunEntity> listRuns(String businessLineCode,
                                              String status,
                                              Long configId,
                                              int page,
                                              int pageSize) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(pageSize, 1), 200);
        return runMapper.findRuns(trimToNull(businessLineCode), trimToNull(status), configId,
                (normalizedPage - 1) * normalizedSize, normalizedSize);
    }

    public int countRuns(String businessLineCode, String status, Long configId) {
        return runMapper.countRuns(trimToNull(businessLineCode), trimToNull(status), configId);
    }

    public AccountingRunDetailResponse detail(Long id) {
        AccountingRunEntity run = requireRun(id);
        return new AccountingRunDetailResponse(run, runMapper.findResults(id));
    }

    @Transactional
    public AccountingRunEntity submitManual(AccountingManualRunRequest request, String operator) {
        monitorMapper.lockConfig(request.configId());
        AccountingMonitorConfigEntity config = requireConfig(request.configId());
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new IllegalArgumentException("业务监测配置未启用");
        }
        LocalDate checkDate = request.checkDate();
        if (checkDate == null && !AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) {
            throw new IllegalArgumentException("流程场景必须选择核对日期");
        }
        if (checkDate != null && checkDate.isAfter(LocalDate.now(ZONE))) {
            throw new IllegalArgumentException("核对日期不能晚于今天");
        }
        String requestKey = trimToNull(request.requestKey());
        if (requestKey == null) {
            requestKey = UUID.randomUUID().toString();
        }
        String idempotencyKey = "MANUAL:" + config.id() + ":" + checkDate + ":" + requestKey;
        Long active = runMapper.findActive(config.id(), window(checkDate));
        if (active != null) return runMapper.findById(active);
        return submit(config, "MANUAL", window(checkDate), idempotencyKey,
                operator == null ? "unknown" : operator);
    }

    @Transactional
    public AccountingRunEntity retry(Long id, String operator) {
        AccountingRunEntity source = requireRun(id);
        monitorMapper.lockConfig(source.configId());
        AccountingMonitorConfigEntity config = requireConfig(source.configId());
        Long active = runMapper.findActive(config.id(), source.startTime());
        if (active != null) return runMapper.findById(active);
        String key = "RETRY:" + source.id() + ":" + UUID.randomUUID();
        return submit(config, "RETRY", source.startTime(), key,
                operator == null ? "unknown" : operator);
    }

    /** 场景核对 Job 入口：核对时间已到且当日尚未核对（幂等键防重）的启用配置逐个提交。 */
    @Transactional
    public void submitDueScheduled() {
        LocalDate today = LocalDate.now(ZONE);
        LocalTime now = LocalTime.now(ZONE);
        for (AccountingMonitorConfigEntity config : monitorMapper.findAll(null, true)) {
            if (AccountingIntegrityCatalog.TYPE.equals(config.scenarioType())) {
                continue;
            }
            if (config.checkTime() == null || now.isBefore(config.checkTime())) {
                continue;
            }
            monitorMapper.lockConfig(config.id());
            if (runMapper.findActive(config.id(), window(today)) != null) continue;
            String key = "SCENARIO:" + config.id() + ":" + today;
            submit(config, "SCHEDULED", window(today), key, "SYSTEM");
        }
    }

    private AccountingRunEntity submit(AccountingMonitorConfigEntity config,
                                       String triggerType,
                                       LocalDateTime windowStart,
                                       String idempotencyKey,
                                       String operator) {
        // REQ-7f26a063：一致性配置提交前校验存在生效规则（手动、重试、数字员工三条入口都经过这里）。
        if (AccountingIntegrityCatalog.TYPE.equals(config.scenarioType()) && ruleInitializer != null) {
            ruleInitializer.ensureMaterialized(config);
            if (!ruleInitializer.hasEffectiveRules(config.id())) {
                throw new IllegalArgumentException("该配置未启用任何监测场景规则");
            }
        }
        AccountingRunEntity existing = runMapper.findByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            return existing;
        }
        String runNo = "AS" + RUN_TIME.format(LocalDateTime.now(ZONE))
                + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(Locale.ROOT);
        try {
            runMapper.insertRun(runNo, idempotencyKey, config.id(), triggerType,
                    windowStart, windowStart == null ? null : windowStart.plusDays(1), null, "PENDING", operator);
        } catch (DataIntegrityViolationException ex) {
            AccountingRunEntity concurrent = runMapper.findByIdempotencyKey(idempotencyKey);
            if (concurrent != null) {
                return concurrent;
            }
            throw ex;
        }
        Long runId = runMapper.findIdByRunNo(runNo);
        if (runId == null) {
            throw new IllegalStateException("场景核对批次创建后未能取得批次ID");
        }
        Runnable enqueue = () -> {
            try { taskExecutor.execute(() -> scenarioCheckExecutor.execute(runId)); }
            catch (TaskRejectedException ex) {
                // afterCommit 的原事务已提交；拒绝状态必须在新事务中落库，避免遗留 PENDING。
                rejectionTransaction.executeWithoutResult(status -> runMapper.finishRun(
                        runId, "FAILED", 0, 0, 0, 0, 0, 0, "业务监测执行队列已满，请稍后重试"));
            }
        };
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCommit() { enqueue.run(); }
                    });
        } else enqueue.run();
        return runMapper.findById(runId);
    }

    private void validateConfig(AccountingMonitorSaveRequest request) {
        AccountingScenarioCatalog.requireType(request.scenarioType().trim());
        if (AccountingIntegrityCatalog.TYPE.equals(request.scenarioType().trim())) {
            // REQ-7f26a063：不再限制叉车/智能柜——任意业务线可建一致性配置后登记自定义规则组。
            if (Boolean.TRUE.equals(request.alertEnabled())) {
                throw new IllegalArgumentException("单据及支付一致性当前仅手动核验，通知保持关闭");
            }
            return;
        }
        if (trimToNull(request.actualCountSqlCode()) == null) {
            throw new IllegalArgumentException("实际发起笔数 SQL 编码未配置");
        }
        if (request.expectedCountSqlCode() != null && request.expectedCountSqlCode().isBlank()) {
            throw new IllegalArgumentException("应发起笔数 SQL 编码不能为空白");
        }
        boolean hasExpectedSql = trimToNull(request.expectedCountSqlCode()) != null;
        boolean hasFixedExpected = request.expectedFixedCount() != null;
        if (hasExpectedSql == hasFixedExpected) {
            throw new IllegalArgumentException("应发起笔数必须且只能配置 SQL 编码或固定值之一");
        }
        if (hasFixedExpected && request.expectedFixedCount() < 0) {
            throw new IllegalArgumentException("固定应发起笔数不能为负数");
        }
        BigDecimal tolerance = request.toleranceRate();
        if (tolerance.compareTo(BigDecimal.ZERO) < 0 || tolerance.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("容差比例必须在 0 到 1 之间");
        }
        if (Boolean.TRUE.equals(request.enabled())) {
            requireSqlEnabled(trimToNull(request.actualCountSqlCode()), "实际发起笔数");
            if (hasExpectedSql) {
                requireSqlEnabled(trimToNull(request.expectedCountSqlCode()), "应发起笔数");
            }
            if (trimToNull(request.successCountSqlCode()) != null) {
                requireSqlEnabled(trimToNull(request.successCountSqlCode()), "成功笔数");
            }
        }
    }

    private void requireSqlEnabled(String sqlCode, String purpose) {
        if (sqlCode == null) {
            throw new IllegalArgumentException(purpose + " SQL 编码未配置");
        }
        if (!functionCallService.isCodeEnabled(sqlCode)) {
            throw new IllegalArgumentException(purpose + " SQL 编码未登记或已停用：" + sqlCode);
        }
    }

    private LocalDateTime window(LocalDate checkDate) {
        return checkDate == null ? null : checkDate.atStartOfDay();
    }

    private AccountingMonitorConfigEntity configEntity(Long id, AccountingMonitorSaveRequest request) {
        return new AccountingMonitorConfigEntity(
                id,
                request.businessLineCode().trim(),
                null,
                normalizeEnvironmentCode(request.environmentCode()),
                request.systemName().trim(),
                request.displayName().trim(),
                request.scenarioType().trim(),
                request.expectedTime(),
                request.checkTime(),
                trimToNull(request.expectedCountSqlCode()),
                request.expectedFixedCount(),
                trimToNull(request.actualCountSqlCode()),
                trimToNull(request.successCountSqlCode()),
                request.toleranceRate(),
                request.alertEnabled(),
                request.enabled(),
                trimToNull(request.remark()),
                null,
                null
        );
    }

    private AccountingMonitorConfigEntity requireConfig(Long id) {
        AccountingMonitorConfigEntity config = monitorMapper.findById(id);
        if (config == null) {
            throw new IllegalArgumentException("业务监测配置不存在");
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

    private String normalizeEnvironmentCode(String value) {
        return requireValue(value, "环境不能为空").toUpperCase(Locale.ROOT);
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
