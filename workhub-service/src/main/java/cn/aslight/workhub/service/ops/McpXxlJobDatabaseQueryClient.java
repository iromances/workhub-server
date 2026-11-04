package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.SqlPolicyGuard;
import cn.aslight.workhub.service.mcp.McpResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class McpXxlJobDatabaseQueryClient implements XxlJobDatabaseQueryClient {
    private final McpResourceService mcpResourceService;
    private final ObjectMapper json = new ObjectMapper();

    public McpXxlJobDatabaseQueryClient(McpResourceService mcpResourceService) { this.mcpResourceService = mcpResourceService; }

    @Override
    public List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql) {
        return runRowsBatch(businessLineCode, environmentCode, List.of(sql)).getFirst();
    }

    @Override
    public List<List<Map<String, Object>>> runRowsBatch(String businessLineCode, String environmentCode, List<String> sqls) {
        return runVerifiedBatch(businessLineCode, environmentCode, sqls).rows();
    }

    @Override
    public VerifiedBatch runVerifiedBatch(String businessLineCode, String environmentCode, List<String> sqls) {
        if (sqls == null || sqls.isEmpty()) throw new IllegalArgumentException("查询批次不能为空");
        var catalog = json.convertValue(mcpResourceService.catalog(), McpResourceCatalog.class);
        var target = resolveDatabaseTarget(catalog, businessLineCode, environmentCode);
        String profile = target.profiles().stream().map(McpResourceCatalog.DatabaseTarget.DatabaseProfile::key)
                .filter("readonly"::equals).findFirst().orElseThrow(() -> new IllegalArgumentException("数据库目标未配置 readonly profile"));
        final String targetHash;
        try { targetHash = AccountingExecutionEvidenceService.hash(json.writeValueAsString(target)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("无法固定实际数据库目标", ex); }
        var service = new DatabaseDiagnosticService(catalog, new SqlPolicyGuard(), new SecretResolver(), McpAuditLogger.defaultLogger(json));
        Instant startedAt = Instant.now();
        var results = service.runReadonlyQueries(target.key(), profile, List.copyOf(sqls));
        Instant finishedAt = Instant.now();
        if (results == null || results.size() != sqls.size()) throw new IllegalArgumentException("数据库查询回执缺少批次结果");
        return new VerifiedBatch(targetHash, startedAt, finishedAt, results.stream().map(McpXxlJobDatabaseQueryClient::rows).toList());
    }

    /** 批量接口直接返回结果集；与单条接口的 result 包装不同，缺失不能退化为空集合。 */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Map<String, Object> result) {
        if (result == null || !Boolean.FALSE.equals(result.get("truncatedByBytes")) || !(result.get("rows") instanceof List<?> rows)
                || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?> cells) || cells.keySet().stream().anyMatch(key -> !(key instanceof String))))
            throw new IllegalArgumentException("数据库查询回执缺失、格式不完整或已截断");
        return (List<Map<String, Object>>) rows;
    }

    private McpResourceCatalog.DatabaseTarget resolveDatabaseTarget(McpResourceCatalog catalog, String businessLineCode, String environmentCode) {
        var matches = catalog.databaseTargets().stream().filter(target -> target.businessLineCodes().contains(businessLineCode))
                .filter(target -> environmentCode.equals(target.environmentCode())).toList();
        if (matches.isEmpty()) throw new IllegalArgumentException("未配置业务线环境对应的 MCP 数据库目标：" + businessLineCode + "." + environmentCode);
        if (matches.size() > 1) throw new IllegalArgumentException("业务线环境存在多个 MCP 数据库目标，无法自动定位 DBServer：" + businessLineCode + "." + environmentCode);
        return matches.getFirst();
    }
}
