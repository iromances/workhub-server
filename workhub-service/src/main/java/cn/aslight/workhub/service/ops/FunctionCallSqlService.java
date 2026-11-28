package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.FuncCallAuditMapper;
import cn.aslight.workhub.dao.ops.FuncCallSqlMapper;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallAuditItem;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallAuditPage;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallExecuteRequest;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallExecuteResponse;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallSqlItem;
import cn.aslight.workhub.model.ops.FuncCallModels.FuncCallSqlSaveRequest;
import cn.aslight.workhub.model.ops.FuncCallAuditEntity;
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** REQ-9f3c2a84：Function Call 页面服务——SQL 登记 CRUD 与试运行。 */
@Service
public class FunctionCallSqlService {

    private static final Pattern SQL_CODE_PATTERN = Pattern.compile("^[A-Z0-9_]{2,64}$");
    private static final Pattern SCENARIO_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,64}$");
    private static final Pattern PARAM_PATTERN = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");
    private static final int TEMPLATE_MAX = 60_000;
    private static final int TRY_RUN_MAX_ROWS = 50;
    private static final int AUDIT_RETENTION_DAYS = 365;
    private static final int AUDIT_PAGE_MAX = 200;

    private final FuncCallSqlMapper funcCallSqlMapper;
    private final FuncCallAuditMapper funcCallAuditMapper;
    private final FuncCallSchemaInitializer schemaInitializer;
    private final FunctionCallService functionCallService;

    public FunctionCallSqlService(FuncCallSqlMapper funcCallSqlMapper,
                                  FuncCallAuditMapper funcCallAuditMapper,
                                  FuncCallSchemaInitializer schemaInitializer,
                                  FunctionCallService functionCallService) {
        this.funcCallSqlMapper = funcCallSqlMapper;
        this.funcCallAuditMapper = funcCallAuditMapper;
        this.schemaInitializer = schemaInitializer;
        this.functionCallService = functionCallService;
    }

    public List<FuncCallSqlItem> list(String keyword, Boolean enabled) {
        schemaInitializer.ensureInitialized();
        return funcCallSqlMapper.findAll(trimToNull(keyword), Boolean.TRUE.equals(enabled))
                .stream()
                .map(FunctionCallSqlService::toItem)
                .toList();
    }

    @Transactional
    public FuncCallSqlItem create(FuncCallSqlSaveRequest request) {
        schemaInitializer.ensureInitialized();
        FuncCallSqlEntity entity = toEntity(new FuncCallSqlEntity(), request);
        if (funcCallSqlMapper.findByCode(entity.getSqlCode()) != null) {
            throw new IllegalArgumentException("SQL 编码已存在：" + entity.getSqlCode());
        }
        funcCallSqlMapper.insert(entity);
        return toItem(funcCallSqlMapper.findByCode(entity.getSqlCode()));
    }

    @Transactional
    public FuncCallSqlItem update(Long id, FuncCallSqlSaveRequest request) {
        schemaInitializer.ensureInitialized();
        FuncCallSqlEntity existing = requireExisting(id);
        FuncCallSqlEntity entity = toEntity(existing, request);
        if (funcCallSqlMapper.countOtherByCode(entity.getSqlCode(), id) > 0) {
            throw new IllegalArgumentException("SQL 编码已存在：" + entity.getSqlCode());
        }
        entity.setId(id);
        funcCallSqlMapper.update(entity);
        return toItem(requireExisting(id));
    }

    @Transactional
    public FuncCallSqlItem updateEnabled(Long id, Boolean enabled) {
        schemaInitializer.ensureInitialized();
        if (enabled == null) {
            throw new IllegalArgumentException("enabled 不能为空");
        }
        requireExisting(id);
        funcCallSqlMapper.updateEnabled(id, enabled);
        return toItem(requireExisting(id));
    }

    /** 试运行：传 SQL 编码或场景编码（场景取第一个分支渲染），返回限行结果与渲染后 SQL。 */
    public FuncCallExecuteResponse tryRun(FuncCallExecuteRequest request) {
        if (request == null || isBlank(request.codeOrScenario()) || isBlank(request.businessLineCode())
                || isBlank(request.environmentCode())) {
            throw new IllegalArgumentException("编码、业务线、环境均不能为空");
        }
        Map<String, String> params = request.params() == null ? Map.of() : request.params();
        String code = request.codeOrScenario().trim();
        String sqlCode;
        String sqlRendered;
        List<FuncCallSqlEntity> matches = funcCallSqlMapper.findByCodeOrScenario(code);
        List<FuncCallSqlEntity> enabled = matches.stream().filter(e -> !Boolean.FALSE.equals(e.getEnabled())).toList();
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("编码未登记或已停用：" + code);
        }
        if (enabled.size() == 1) {
            sqlCode = enabled.getFirst().getSqlCode();
            sqlRendered = functionCallService.render(sqlCode, params);
        } else {
            // 场景多分支：试运行只跑第一分支（完整分支组合由调用方代码负责）
            sqlCode = enabled.getFirst().getSqlCode();
            sqlRendered = functionCallService.render(sqlCode, params);
        }
        List<Map<String, Object>> rows = functionCallService.run(
                sqlCode, params, request.businessLineCode().trim(), request.environmentCode().trim());
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet());
        List<Map<String, Object>> limited = rows.size() > TRY_RUN_MAX_ROWS ? rows.subList(0, TRY_RUN_MAX_ROWS) : rows;
        return new FuncCallExecuteResponse(sqlCode, columns, limited, sqlRendered);
    }

    /** REQ-b4d7e6f2：执行审计分页查询（编码模糊/来源/状态/时间窗）。 */
    public FuncCallAuditPage audits(String sqlCode, String source, String status,
                                     LocalDate startDate, LocalDate endDate, int page, int pageSize) {
        schemaInitializer.ensureInitialized();
        String normalizedSource = trimToNull(source) == null ? null : trimToNull(source).toUpperCase();
        if (normalizedSource != null && !"SYSTEM".equals(normalizedSource) && !"MANUAL".equals(normalizedSource)) {
            throw new IllegalArgumentException("执行来源只支持 SYSTEM 或 MANUAL");
        }
        String normalizedStatus = trimToNull(status) == null ? null : trimToNull(status).toUpperCase();
        if (normalizedStatus != null && !"SUCCESS".equals(normalizedStatus) && !"ERROR".equals(normalizedStatus)) {
            throw new IllegalArgumentException("执行状态只支持 SUCCESS 或 ERROR");
        }
        LocalDateTime from = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime to = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), AUDIT_PAGE_MAX);
        long total = funcCallAuditMapper.count(trimToNull(sqlCode), normalizedSource, normalizedStatus, from, to);
        List<FuncCallAuditItem> items = funcCallAuditMapper
                .findPage(trimToNull(sqlCode), normalizedSource, normalizedStatus, from, to,
                        normalizedPageSize, (normalizedPage - 1) * normalizedPageSize)
                .stream()
                .map(FunctionCallSqlService::toAuditItem)
                .toList();
        return new FuncCallAuditPage(total, normalizedPage, normalizedPageSize, items);
    }

    /** 清理超过保留期（365 天）的执行审计；供 OPS_FUNC_CALL_AUDIT_CLEANUP 任务调用。 */
    public int cleanupExpiredAudits() {
        schemaInitializer.ensureInitialized();
        return funcCallAuditMapper.deleteBefore(LocalDateTime.now().minusDays(AUDIT_RETENTION_DAYS));
    }

    private static FuncCallAuditItem toAuditItem(FuncCallAuditEntity entity) {
        return new FuncCallAuditItem(entity.getId(), entity.getSqlCode(), entity.getScenarioCode(),
                entity.getSource(), entity.getOperator(), entity.getBusinessLineCode(), entity.getEnvironmentCode(),
                entity.getRenderedSql(), entity.getStatus(), entity.getRowCount(), entity.getDurationMs(),
                entity.getErrorMessage(),
                entity.getStartedAt() == null ? null : entity.getStartedAt().toString(),
                entity.getFinishedAt() == null ? null : entity.getFinishedAt().toString());
    }

    private FuncCallSqlEntity requireExisting(Long id) {
        schemaInitializer.ensureInitialized();
        FuncCallSqlEntity existing = funcCallSqlMapper.findById(id);
        if (existing == null) {
            throw new IllegalArgumentException("SQL 登记不存在：" + id);
        }
        return existing;
    }

    private FuncCallSqlEntity toEntity(FuncCallSqlEntity entity, FuncCallSqlSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        String sqlCode = normalizeSqlCode(request.sqlCode());
        String scenarioCode = normalizeScenarioCode(request.scenarioCode());
        String name = requireText(request.name(), "名称不能为空", 128);
        String sqlTemplate = requireText(request.sqlTemplate(), "SQL 模板不能为空", TEMPLATE_MAX);
        validateTemplate(sqlTemplate);
        if (!sqlTemplate.strip().toUpperCase().startsWith("SELECT") && !sqlTemplate.strip().toUpperCase().startsWith("WITH")
                && !sqlTemplate.strip().toUpperCase().startsWith("SHOW")
                && !sqlTemplate.strip().toUpperCase().startsWith("DESC")) {
            throw new IllegalArgumentException("模板必须以 SELECT/WITH/SHOW/DESC 开头（只读网关约束）");
        }
        entity.setSqlCode(sqlCode);
        entity.setScenarioCode(scenarioCode);
        entity.setBranchOrder(request.branchOrder() == null ? 1 : Math.max(request.branchOrder(), 1));
        entity.setName(name);
        entity.setDescription(optionalText(request.description(), 512));
        entity.setSqlTemplate(sqlTemplate);
        entity.setParamDesc(optionalText(request.paramDesc(), 1024));
        entity.setSourceModule(optionalText(request.sourceModule(), 64));
        entity.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        entity.setRemark(optionalText(request.remark(), 255));
        // REQ-7f26a063：一致性模板保存时执行三段契约校验（Function Call 页面保存同样生效）。
        if ("OPS_ACCOUNTING_INTEGRITY".equals(entity.getSourceModule())) {
            cn.aslight.workhub.service.ops.IntegritySqlContract.validate(entity.getSqlCode(), entity.getSqlTemplate());
        }
        return entity;
    }

    /** 模板语法校验：占位符必须是 :name 形式且无非法残留（%s、%d、${} 等一律拒绝；允许无参模板）。 */
    private void validateTemplate(String template) {
        if (template.contains("%s") || template.contains("%d") || template.contains("${")) {
            throw new IllegalArgumentException("模板只支持 :name 命名参数，不支持 %s/%d/${} 占位");
        }
    }

    private String normalizeSqlCode(String value) {
        String code = trimToNull(value);
        if (code == null || !SQL_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("SQL 编码必须为 2-64 位大写字母/数字/下划线");
        }
        return code;
    }

    private String normalizeScenarioCode(String value) {
        String code = trimToNull(value);
        if (code == null) {
            return null;
        }
        if (!SCENARIO_CODE_PATTERN.matcher(code).matches()) {
            throw new IllegalArgumentException("场景编码必须为 2-64 位字母/数字/中划线/下划线");
        }
        return code;
    }

    private static FuncCallSqlItem toItem(FuncCallSqlEntity entity) {
        return new FuncCallSqlItem(entity.getId(), entity.getSqlCode(), entity.getScenarioCode(), entity.getBranchOrder(),
                entity.getName(), entity.getDescription(), entity.getSqlTemplate(), entity.getParamDesc(),
                entity.getSourceModule(), Boolean.TRUE.equals(entity.getEnabled()), entity.getRemark());
    }

    private String requireText(String value, String message, int max) {
        String text = trimToNull(value);
        if (text == null) {
            throw new IllegalArgumentException(message);
        }
        if (text.length() > max) {
            throw new IllegalArgumentException("内容超长（最多 " + max + " 字符）");
        }
        return text;
    }

    private String optionalText(String value, int max) {
        String text = trimToNull(value);
        if (text != null && text.length() > max) {
            throw new IllegalArgumentException("内容超长（最多 " + max + " 字符）");
        }
        return text;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
