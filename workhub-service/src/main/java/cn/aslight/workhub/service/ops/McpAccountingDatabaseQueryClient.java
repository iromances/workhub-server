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
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runRows(AccountingMonitorConfigEntity config, String sql) {
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
        Map<String, Object> queryResult = service.runReadonlyQuery(target.key(), profileKey, sql);
        Object result = queryResult.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            return List.of();
        }
        Object rows = resultMap.get("rows");
        return rows instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
