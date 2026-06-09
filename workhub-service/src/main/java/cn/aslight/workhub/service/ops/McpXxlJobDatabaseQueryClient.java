package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.mcp.audit.McpAuditLogger;
import cn.aslight.workhub.mcp.config.McpResourceCatalog;
import cn.aslight.workhub.mcp.db.DatabaseDiagnosticService;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.mcp.security.SqlPolicyGuard;
import cn.aslight.workhub.service.mcp.McpResourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class McpXxlJobDatabaseQueryClient implements XxlJobDatabaseQueryClient {

    private final McpResourceService mcpResourceService;
    private final ObjectMapper objectMapper;

    public McpXxlJobDatabaseQueryClient(McpResourceService mcpResourceService) {
        this.mcpResourceService = mcpResourceService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> runRows(String businessLineCode, String environmentCode, String sql) {
        McpResourceCatalog catalog = objectMapper.convertValue(mcpResourceService.catalog(), McpResourceCatalog.class);
        McpResourceCatalog.DatabaseTarget target = resolveDatabaseTarget(catalog, businessLineCode, environmentCode);
        String profileKey = target.profiles().isEmpty() ? "readonly" : target.profiles().getFirst().key();
        DatabaseDiagnosticService databaseService = new DatabaseDiagnosticService(
                catalog,
                new SqlPolicyGuard(),
                new SecretResolver(),
                McpAuditLogger.defaultLogger(objectMapper)
        );
        Map<String, Object> queryResult = databaseService.runReadonlyQuery(target.key(), profileKey, sql);
        Object result = queryResult.get("result");
        if (!(result instanceof Map<?, ?> resultMap)) {
            return List.of();
        }
        Object rows = resultMap.get("rows");
        return rows instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private McpResourceCatalog.DatabaseTarget resolveDatabaseTarget(McpResourceCatalog catalog,
                                                                    String businessLineCode,
                                                                    String environmentCode) {
        List<McpResourceCatalog.DatabaseTarget> matches = catalog.databaseTargets().stream()
                .filter(target -> target.businessLineCodes().contains(businessLineCode))
                .filter(target -> environmentCode.equals(target.environmentCode()))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("未配置业务线环境对应的 MCP 数据库目标：" + businessLineCode + "." + environmentCode);
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("业务线环境存在多个 MCP 数据库目标，无法自动定位 DBServer：" + businessLineCode + "." + environmentCode);
        }
        return matches.getFirst();
    }
}
