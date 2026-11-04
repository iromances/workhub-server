package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.SqlPolicyGuard;
import cn.aslight.workhub.model.ops.AccountingMonitorConfigEntity;
import cn.aslight.workhub.service.mcp.McpResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpAccountingDatabaseQueryClient implements AccountingDatabaseQueryClient {

    private final McpResourceService mcpResourceService;
    private final ObjectMapper objectMapper;

    @Autowired
    public McpAccountingDatabaseQueryClient(McpResourceService mcpResourceService) {
        this(mcpResourceService, new ObjectMapper());
    }

    McpAccountingDatabaseQueryClient(McpResourceService mcpResourceService,
                                     ObjectMapper objectMapper) {
        this.mcpResourceService = mcpResourceService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Session open(AccountingMonitorConfigEntity config) {
        String targetKey = trimToNull(config.databaseTargetKey());
        if (targetKey == null) {
            throw new IllegalArgumentException("未配置生产数据库目标");
        }
        McpResourceCatalog catalog = objectMapper.convertValue(
                mcpResourceService.catalog(), McpResourceCatalog.class);
        McpResourceCatalog.DatabaseTarget target = catalog.databaseTargets().stream()
                .filter(item -> targetKey.equals(item.key()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据库目标不存在：" + targetKey));
        if (!target.businessLineCodes().contains(config.businessLineCode())) {
            throw new IllegalArgumentException("数据库目标未绑定当前业务线：" + config.businessLineCode());
        }
        if (!config.environmentCode().equals(target.environmentCode())) {
            throw new IllegalArgumentException("数据库目标环境与监测配置不一致");
        }
        String profileKey = target.profiles().stream()
                .map(McpResourceCatalog.DatabaseTarget.DatabaseProfile::key)
                .filter("readonly"::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("数据库目标未配置 readonly profile"));
        DatabaseDiagnosticService service = new DatabaseDiagnosticService(
                catalog,
                new SqlPolicyGuard(),
                new SecretResolver(),
                McpAuditLogger.defaultLogger(objectMapper)
        );
        final String bindingHash;
        try { bindingHash = AccountingExecutionEvidenceService.hash(objectMapper.writeValueAsString(target)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("数据库目标无法固定", ex); }
        return new Session() {
            @Override public String targetHash() { return bindingHash; }
            @Override public List<Map<String, Object>> runRows(String sql) {
                return rows(service.runReadonlyQuery(target.key(), profileKey, sql));
            }
        };
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rows(Map<String, Object> response) {
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)
                || !Boolean.FALSE.equals(result.get("truncatedByBytes")) || !(result.get("rows") instanceof List<?> rows)
                || rows.stream().anyMatch(row -> !(row instanceof Map<?, ?> cells) || cells.keySet().stream().anyMatch(key -> !(key instanceof String)))) {
            throw new IllegalArgumentException("数据库查询回执缺失、格式不完整或已截断");
        }
        return (List<Map<String, Object>>) rows;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
