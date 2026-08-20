package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.mcp.security.SecretResolver;
import cn.aslight.workhub.model.mcp.McpBastionEntity;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpDatabaseConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class McpDatabaseConnectionTestService {

    private final McpResourceMapper resourceMapper;
    private final McpCryptoService cryptoService;
    private final McpBastionService bastionService;
    private final McpDatabaseConnectionTester connectionTester;
    private final SystemAuditService auditService;

    public McpDatabaseConnectionTestService(McpResourceMapper resourceMapper,
                                            McpCryptoService cryptoService,
                                            McpBastionService bastionService,
                                            McpDatabaseConnectionTester connectionTester,
                                            SystemAuditService auditService) {
        this.resourceMapper = resourceMapper;
        this.cryptoService = cryptoService;
        this.bastionService = bastionService;
        this.connectionTester = connectionTester;
        this.auditService = auditService;
    }

    public McpDatabaseConnectionTestResponse test(McpDatabaseConnectionTestRequest request,
                                                   String operator,
                                                   String ip) {
        if (request == null) {
            throw new IllegalArgumentException("数据库连接测试参数不能为空");
        }
        String host = requireValue(request.getHost(), "数据库主机不能为空");
        String username = requireValue(request.getUsername(), "数据库用户不能为空");
        if (request.getPort() == null || request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("数据库端口必须在 1-65535 之间");
        }

        McpResourceEntity existing = request.getId() == null ? null : requireDatabaseResource(request.getId());
        String databasePassword = passwordOrNull(request.getPassword());
        if (databasePassword == null && existing != null) {
            databasePassword = existingDatabasePassword(existing);
        }
        if (databasePassword == null) {
            throw new IllegalArgumentException("数据库密码不能为空");
        }

        boolean useBastion = Boolean.TRUE.equals(request.getSshBastionEnabled());
        Long bastionId = request.getBastionId();
        if (useBastion && bastionId == null && existing != null
                && Boolean.TRUE.equals(existing.getSshBastionEnabled())) {
            bastionId = existing.getBastionId();
        }
        McpBastionEntity bastion = useBastion ? bastionService.requireEnabled(bastionId) : null;
        McpDatabaseConnectionTester.Bastion testerBastion = bastion == null ? null
                : new McpDatabaseConnectionTester.Bastion(
                        bastion.getHost(),
                        bastion.getPort() == null ? 22 : bastion.getPort(),
                        bastion.getUsername(),
                        bastionService.decryptPassword(bastion),
                        bastion.getIdentityFile()
                );

        McpDatabaseConnectionTester.Result result = connectionTester.test(
                new McpDatabaseConnectionTester.Input(
                        host,
                        request.getPort(),
                        trimToNull(request.getDatabaseSchema()),
                        username,
                        databasePassword,
                        testerBastion
                )
        );
        audit(request.getId(), host, request.getPort(), username, bastion, result, operator, ip);
        String message = result.success() && bastion != null
                ? "经“" + bastion.getName() + "”连接数据库成功"
                : result.message();
        return new McpDatabaseConnectionTestResponse(
                result.success(),
                result.connectionMode(),
                result.failureStage(),
                bastion == null ? null : bastion.getName(),
                result.durationMs(),
                message,
                LocalDateTime.now()
        );
    }

    private McpResourceEntity requireDatabaseResource(Long id) {
        McpResourceEntity entity = resourceMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("数据库目标不存在");
        }
        if (!"DATABASE".equalsIgnoreCase(entity.getResourceType())) {
            throw new IllegalArgumentException("所选资源不是数据库目标");
        }
        return entity;
    }

    private String existingDatabasePassword(McpResourceEntity entity) {
        if (trimToNull(entity.getPasswordEncrypted()) != null) {
            return cryptoService.decrypt(entity.getPasswordEncrypted());
        }
        String secretRef = trimToNull(entity.getSecretRef());
        return secretRef == null ? null : new SecretResolver().resolve(secretRef);
    }

    private void audit(Long resourceId,
                       String host,
                       int port,
                       String username,
                       McpBastionEntity bastion,
                       McpDatabaseConnectionTester.Result result,
                       String operator,
                       String ip) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("host", host);
        snapshot.put("port", port);
        snapshot.put("username", username);
        snapshot.put("connectionMode", result.connectionMode());
        snapshot.put("bastionId", bastion == null ? null : bastion.getId());
        snapshot.put("failureStage", result.failureStage());
        snapshot.put("durationMs", result.durationMs());
        auditService.operation(
                operator,
                resourceId == null ? "ops:mcp-resource:create" : "ops:mcp-resource:update",
                "TEST_DATABASE_CONNECTION",
                "MCP_DATABASE_RESOURCE",
                resourceId == null ? null : String.valueOf(resourceId),
                null,
                snapshot.toString(),
                result.success() ? "SUCCESS" : "FAILURE",
                result.success() ? null : result.message(),
                ip
        );
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String passwordOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
