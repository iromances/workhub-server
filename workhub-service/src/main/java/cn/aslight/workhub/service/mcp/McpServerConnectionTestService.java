package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpServerConnectionTestResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class McpServerConnectionTestService {

    private final McpResourceMapper resourceMapper;
    private final McpCryptoService cryptoService;
    private final McpServerConnectionTester connectionTester;
    private final SystemAuditService auditService;

    public McpServerConnectionTestService(McpResourceMapper resourceMapper,
                                          McpCryptoService cryptoService,
                                          McpServerConnectionTester connectionTester,
                                          SystemAuditService auditService) {
        this.resourceMapper = resourceMapper;
        this.cryptoService = cryptoService;
        this.connectionTester = connectionTester;
        this.auditService = auditService;
    }

    public McpServerConnectionTestResponse test(McpServerConnectionTestRequest request,
                                                 String operator,
                                                 String ip) {
        if (request == null) {
            throw new IllegalArgumentException("服务器连接测试参数不能为空");
        }
        String host = requireValue(request.getHost(), "服务器主机不能为空");
        String username = requireValue(request.getUsername(), "服务器 SSH 用户不能为空");
        int port = requirePort(request.getPort(), "服务器端口必须在 1-65535 之间");

        McpResourceEntity existing = request.getId() == null ? null : requireServerResource(request.getId());
        String identityFile = trimToNull(request.getSshIdentityFile());
        String targetPassword = passwordOrNull(request.getSshPassword());
        if (targetPassword == null && existing != null) {
            targetPassword = decrypt(existing.getSshPasswordEncrypted());
        }
        if (targetPassword == null && identityFile == null) {
            throw new IllegalArgumentException("服务器 SSH 密码或私钥文件不能为空");
        }

        boolean useBastion = Boolean.TRUE.equals(request.getSshBastionEnabled());
        McpServerConnectionTester.Bastion bastion = null;
        if (useBastion) {
            String bastionHost = requireValue(request.getSshBastionHost(), "堡垒机主机不能为空");
            String bastionUser = requireValue(request.getSshBastionUser(), "堡垒机用户不能为空");
            int bastionPort = requirePort(request.getSshBastionPort(), "堡垒机端口必须在 1-65535 之间");
            String bastionPassword = passwordOrNull(request.getSshBastionPassword());
            if (bastionPassword == null && existing != null
                    && Boolean.TRUE.equals(existing.getSshBastionEnabled())) {
                bastionPassword = decrypt(existing.getSshBastionPasswordEncrypted());
            }
            if (bastionPassword == null && identityFile == null) {
                throw new IllegalArgumentException("堡垒机密码或私钥文件不能为空");
            }
            bastion = new McpServerConnectionTester.Bastion(
                    bastionHost, bastionPort, bastionUser, bastionPassword, identityFile
            );
        }

        McpServerConnectionTester.Result result = connectionTester.test(
                new McpServerConnectionTester.Input(
                        host, port, username, targetPassword, identityFile, bastion
                )
        );
        audit(request.getId(), host, port, username, result, operator, ip);
        return new McpServerConnectionTestResponse(
                result.success(),
                result.connectionMode(),
                result.failureStage(),
                result.durationMs(),
                result.message(),
                LocalDateTime.now()
        );
    }

    private McpResourceEntity requireServerResource(Long id) {
        McpResourceEntity entity = resourceMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("服务器目标不存在");
        }
        if (!"SERVER".equalsIgnoreCase(entity.getResourceType())) {
            throw new IllegalArgumentException("所选资源不是服务器目标");
        }
        return entity;
    }

    private void audit(Long resourceId,
                       String host,
                       int port,
                       String username,
                       McpServerConnectionTester.Result result,
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
        snapshot.put("failureStage", result.failureStage());
        snapshot.put("durationMs", result.durationMs());
        auditService.operation(
                operator,
                resourceId == null ? "ops:mcp-resource:create" : "ops:mcp-resource:update",
                "TEST_SERVER_CONNECTION",
                "MCP_SERVER_RESOURCE",
                resourceId == null ? null : String.valueOf(resourceId),
                null,
                snapshot.toString(),
                result.success() ? "SUCCESS" : "FAILURE",
                result.success() ? null : result.message(),
                ip
        );
    }

    private String decrypt(String encrypted) {
        return trimToNull(encrypted) == null ? null : cryptoService.decrypt(encrypted);
    }

    private int requirePort(Integer port, String message) {
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException(message);
        }
        return port;
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String passwordOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
