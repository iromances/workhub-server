package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.FuncCallAuditMapper;
import cn.aslight.workhub.dao.ops.FuncCallSqlMapper;
import cn.aslight.workhub.model.ops.FuncCallAuditEntity;
import cn.aslight.workhub.model.ops.FuncCallSqlEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * REQ-9f3c2a84：通用业务库查询函数（Function Call）。调用方传 SQL 编码或场景编码，
 * 服务从 func_call_sql 取启用的模板，按 :name 命名参数渲染后经 MCP 只读网关执行。
 *
 * REQ-b4d7e6f2：业务库执行的统一收口——渲染、执行、审计一体；每次执行的每条语句
 * 落 func_call_sql_audit_log（编码/来源/操作人/目标库/渲染 SQL/状态/行数/耗时）。
 *
 * 参数值的安全责任边界：值必须由调用方先经白名单校验/转义（如 quoteDatabaseName、
 * sqlLiteral、escapeSqlLikeLiteral 等）；本服务只做字面量替换，最终 SQL 仍过 SqlPolicyGuard
 * （仅 SELECT/WITH/SHOW/DESC/DESCRIBE/EXPLAIN、单条、强制 LIMIT）。
 */
@Service
public class FunctionCallService {

    private static final Logger log = LoggerFactory.getLogger(FunctionCallService.class);
    private static final Pattern PARAM_PATTERN = Pattern.compile(":([A-Za-z_][A-Za-z0-9_]*)");
    private static final int ERROR_MESSAGE_MAX = 4000;

    public static final String SOURCE_SYSTEM = "SYSTEM";
    public static final String SOURCE_MANUAL = "MANUAL";

    /** 已渲染待执行语句及其归属编码；组合语句（统计+分页外壳、分支 UNION）编码为参与码 + 连接。 */
    public record Statement(String sqlCode, String sql) {
        public static Statement of(String sqlCode, String sql) {
            return new Statement(sqlCode, sql);
        }
    }

    /** 模板存取抽象：生产为 DB 存取，测试可用种子内存实现。 */
    public interface TemplateStore {
        FuncCallSqlEntity byCode(String sqlCode);

        List<FuncCallSqlEntity> byScenario(String scenarioCode);
    }

    /** DB 模板存取：每次读取前确保表与种子就绪（首次后为空操作）。 */
    @Service
    static class DbTemplateStore implements TemplateStore {
        private final FuncCallSqlMapper mapper;
        private final FuncCallSchemaInitializer schemaInitializer;

        DbTemplateStore(FuncCallSqlMapper mapper, FuncCallSchemaInitializer schemaInitializer) {
            this.mapper = mapper;
            this.schemaInitializer = schemaInitializer;
        }

        @Override
        public FuncCallSqlEntity byCode(String sqlCode) {
            schemaInitializer.ensureInitialized();
            return mapper.findByCode(sqlCode);
        }

        @Override
        public List<FuncCallSqlEntity> byScenario(String scenarioCode) {
            schemaInitializer.ensureInitialized();
            return mapper.findByScenario(scenarioCode);
        }
    }

    private final TemplateStore templateStore;
    private final XxlJobDatabaseQueryClient queryClient;
    private final FuncCallAuditMapper auditMapper;

    @Autowired
    public FunctionCallService(TemplateStore templateStore, XxlJobDatabaseQueryClient queryClient,
                               FuncCallAuditMapper auditMapper) {
        this.templateStore = templateStore;
        this.queryClient = queryClient;
        this.auditMapper = auditMapper;
    }

    /** 测试/无审计构造：auditMapper 为空时跳过审计写入。 */
    public FunctionCallService(TemplateStore templateStore, XxlJobDatabaseQueryClient queryClient) {
        this(templateStore, queryClient, null);
    }

    /** 按 SQL 编码渲染单条模板；模板未登记、已停用或引用了未传参数时报错。 */
    public String render(String sqlCode, Map<String, String> params) {
        FuncCallSqlEntity entity = templateStore.byCode(requireCode(sqlCode));
        if (entity == null) {
            throw new IllegalArgumentException("SQL 编码未登记：" + sqlCode);
        }
        if (Boolean.FALSE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("SQL 编码已停用：" + sqlCode);
        }
        return renderTemplate(entity, params);
    }

    /** 按场景编码渲染全部启用分支（按 branch_order 升序）；无启用分支时报错。 */
    public List<String> renderScenario(String scenarioCode, Map<String, String> params) {
        List<FuncCallSqlEntity> entities = templateStore.byScenario(requireCode(scenarioCode));
        List<FuncCallSqlEntity> enabled = entities.stream().filter(e -> !Boolean.FALSE.equals(e.getEnabled())).toList();
        if (enabled.isEmpty()) {
            throw new IllegalArgumentException("场景编码未登记或已全部停用：" + scenarioCode);
        }
        List<String> rendered = new ArrayList<>();
        for (FuncCallSqlEntity entity : enabled) {
            rendered.add(renderTemplate(entity, params));
        }
        return rendered;
    }

    /** 按 SQL 编码渲染并执行（页面试运行通道）：来源 MANUAL，操作人为当前登录用户。 */
    public List<Map<String, Object>> run(String sqlCode, Map<String, String> params,
                                         String businessLineCode, String environmentCode) {
        return executeStatements(businessLineCode, environmentCode, SOURCE_MANUAL, currentOperator(),
                List.of(Statement.of(sqlCode, render(sqlCode, params)))).getFirst();
    }

    /** 代码/巡检执行通道：runRowsBatch + 逐条审计（来源 SYSTEM）。 */
    public List<List<Map<String, Object>>> executeStatements(String businessLineCode, String environmentCode,
                                                             List<Statement> statements) {
        return executeStatements(businessLineCode, environmentCode, SOURCE_SYSTEM, "SYSTEM", statements);
    }

    public List<List<Map<String, Object>>> executeStatements(String businessLineCode, String environmentCode,
                                                             String source, String operator,
                                                             List<Statement> statements) {
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("执行语句不能为空");
        }
        List<String> sqls = statements.stream().map(Statement::sql).toList();
        LocalDateTime startedAt = LocalDateTime.now().withNano(0);
        String error = null;
        List<List<Map<String, Object>>> results = null;
        try {
            results = queryClient.runRowsBatch(businessLineCode, environmentCode, sqls);
            return results;
        } catch (Exception ex) {
            error = messageOf(ex);
            throw ex;
        } finally {
            auditAll(statements, sqls, businessLineCode, environmentCode, source, operator,
                    startedAt, error, results);
        }
    }

    /** 代码/巡检验证执行通道：runVerifiedBatch（保留 targetHash/起止时间证据链）+ 逐条审计。 */
    public XxlJobDatabaseQueryClient.VerifiedBatch executeVerified(String businessLineCode, String environmentCode,
                                                                   List<Statement> statements) {
        if (statements == null || statements.isEmpty()) {
            throw new IllegalArgumentException("执行语句不能为空");
        }
        List<String> sqls = statements.stream().map(Statement::sql).toList();
        LocalDateTime startedAt = LocalDateTime.now().withNano(0);
        String error = null;
        XxlJobDatabaseQueryClient.VerifiedBatch batch = null;
        try {
            batch = queryClient.runVerifiedBatch(businessLineCode, environmentCode, sqls);
            return batch;
        } catch (Exception ex) {
            error = messageOf(ex);
            throw ex;
        } finally {
            auditAll(statements, sqls, businessLineCode, environmentCode, SOURCE_SYSTEM, "SYSTEM",
                    startedAt, error, batch == null ? null : batch.rows());
        }
    }

    /** 每条语句一行审计；审计写入失败只记日志，不阻断业务结果。 */
    private void auditAll(List<Statement> statements, List<String> sqls, String businessLineCode,
                          String environmentCode, String source, String operator,
                          LocalDateTime startedAt, String error, List<List<Map<String, Object>>> results) {
        if (auditMapper == null) {
            return;
        }
        LocalDateTime finishedAt = LocalDateTime.now().withNano(0);
        long durationMs = ChronoUnit.MILLIS.between(startedAt, finishedAt);
        for (int i = 0; i < statements.size(); i++) {
            Statement statement = statements.get(i);
            FuncCallAuditEntity audit = new FuncCallAuditEntity();
            audit.setSqlCode(statement.sqlCode());
            audit.setScenarioCode(scenarioOf(statement.sqlCode()));
            audit.setSource(source);
            audit.setOperator(operator);
            audit.setBusinessLineCode(businessLineCode);
            audit.setEnvironmentCode(environmentCode);
            audit.setRenderedSql(sqls.get(i));
            audit.setStatus(error == null ? "SUCCESS" : "ERROR");
            audit.setRowCount(error == null && results != null && i < results.size() ? results.get(i).size() : null);
            audit.setDurationMs(durationMs);
            audit.setErrorMessage(truncate(error));
            audit.setStartedAt(startedAt);
            audit.setFinishedAt(finishedAt);
            try {
                auditMapper.insert(audit);
            } catch (Exception ex) {
                log.error("Function call audit write failed. sqlCode={}, message={}", statement.sqlCode(), ex.getMessage());
            }
        }
    }

    /** 单编码（非组合）反查场景编码；组合码或未登记编码返回 null。 */
    private String scenarioOf(String sqlCode) {
        if (sqlCode == null || sqlCode.contains("+")) {
            return null;
        }
        try {
            FuncCallSqlEntity entity = templateStore.byCode(sqlCode);
            return entity == null ? null : entity.getScenarioCode();
        } catch (Exception ex) {
            return null;
        }
    }

    private String currentOperator() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
                return authentication.getName();
            }
        } catch (Exception ex) {
            log.debug("Resolve audit operator failed: {}", ex.getMessage());
        }
        return "UNKNOWN";
    }

    private String renderTemplate(FuncCallSqlEntity entity, Map<String, String> params) {
        Map<String, String> safeParams = params == null ? Map.of() : params;
        List<String> unused = new ArrayList<>();
        for (String key : safeParams.keySet()) {
            if (!entity.getSqlTemplate().contains(":" + key)) {
                unused.add(key);
            }
        }
        if (!unused.isEmpty()) {
            log.warn("Function call {} received unused params {} (模板未引用，已忽略)", entity.getSqlCode(), unused);
        }
        Matcher matcher = PARAM_PATTERN.matcher(entity.getSqlTemplate());
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = safeParams.get(name);
            if (value == null) {
                throw new IllegalArgumentException("SQL 模板 " + entity.getSqlCode() + " 引用的参数未传值：" + name);
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("SQL 编码或场景编码不能为空");
        }
        return code.trim();
    }

    private static String messageOf(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }

    private static String truncate(String value) {
        if (value == null || value.length() <= ERROR_MESSAGE_MAX) {
            return value;
        }
        return value.substring(0, ERROR_MESSAGE_MAX);
    }

    /** 调用方构建参数的便捷入口（保持插入顺序；值为已校验字面量）。 */
    public static Map<String, String> params(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数必须成对出现");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return map;
    }
}
