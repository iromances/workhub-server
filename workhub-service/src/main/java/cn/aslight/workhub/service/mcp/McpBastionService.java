package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.dao.mcp.McpBastionMapper;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestRequest;
import cn.aslight.workhub.model.mcp.McpBastionConnectionTestResponse;
import cn.aslight.workhub.model.mcp.McpBastionEntity;
import cn.aslight.workhub.model.mcp.McpBastionResponse;
import cn.aslight.workhub.model.mcp.McpBastionSaveRequest;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpBastionService {

    private final McpBastionMapper mcpBastionMapper;
    private final McpCryptoService mcpCryptoService;
    private final SystemAuditService auditService;
    private final McpBastionConnectionTester connectionTester;

    public McpBastionService(McpBastionMapper mcpBastionMapper,
                             McpCryptoService mcpCryptoService) {
        this(mcpBastionMapper, mcpCryptoService, null, new McpBastionConnectionTester());
    }

    @Autowired
    public McpBastionService(McpBastionMapper mcpBastionMapper,
                             McpCryptoService mcpCryptoService,
                             SystemAuditService auditService,
                             McpBastionConnectionTester connectionTester) {
        this.mcpBastionMapper = mcpBastionMapper;
        this.mcpCryptoService = mcpCryptoService;
        this.auditService = auditService;
        this.connectionTester = connectionTester;
    }

    public List<McpBastionResponse> list(String keyword, boolean enabledOnly) {
        return mcpBastionMapper.findAll(trimToNull(keyword), enabledOnly)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public McpBastionResponse create(McpBastionSaveRequest request, String operator, String ip) {
        ensureNameAvailable(request.getName(), null);
        McpBastionEntity entity = toEntity(new McpBastionEntity(), request);
        validateCredential(entity);
        mcpBastionMapper.insert(entity);
        McpBastionEntity saved = requireExisting(entity.getId());
        audit("CREATE", null, snapshot(saved), saved.getId(), operator, ip);
        return toResponse(saved);
    }

    @Transactional
    public McpBastionResponse update(Long id,
                                     McpBastionSaveRequest request,
                                     String operator,
                                     String ip) {
        McpBastionEntity existing = requireExisting(id);
        String beforeSnapshot = snapshot(existing);
        ensureNameAvailable(request.getName(), id);
        if (Boolean.TRUE.equals(existing.getEnabled())
                && Boolean.FALSE.equals(request.getEnabled())
                && mcpBastionMapper.countDatabaseReferences(id) > 0) {
            throw new IllegalArgumentException("堡垒机已被数据库目标引用，解除引用后才能停用");
        }
        McpBastionEntity entity = toEntity(existing, request);
        entity.setId(id);
        validateCredential(entity);
        if (mcpBastionMapper.update(entity) == 0) {
            throw new IllegalArgumentException("堡垒机不存在");
        }
        McpBastionEntity saved = requireExisting(id);
        audit("UPDATE", beforeSnapshot, snapshot(saved), saved.getId(), operator, ip);
        return toResponse(saved);
    }

    public McpBastionConnectionTestResponse testConnection(McpBastionConnectionTestRequest request,
                                                            String operator,
                                                            String ip) {
        if (request == null) {
            throw new IllegalArgumentException("堡垒机连接测试参数不能为空");
        }
        String host = requireValue(request.getHost(), "堡垒机主机不能为空");
        String username = requireValue(request.getUsername(), "堡垒机用户不能为空");
        if (request.getPort() == null || request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("堡垒机端口必须在 1-65535 之间");
        }

        McpBastionEntity existing = request.getId() == null ? null : requireExisting(request.getId());
        String password = trimToNull(request.getPassword());
        if (password == null && existing != null && trimToNull(existing.getPasswordEncrypted()) != null) {
            password = decryptPassword(existing);
        }
        String identityFile = trimToNull(request.getIdentityFile());
        if (password == null && identityFile == null) {
            throw new IllegalArgumentException("堡垒机密码或私钥文件不能为空");
        }

        McpBastionConnectionTester.Result result = connectionTester.test(
                host,
                request.getPort(),
                username,
                password,
                identityFile
        );
        auditConnectionTest(
                request.getId(),
                host,
                request.getPort(),
                username,
                password == null ? "IDENTITY_FILE" : "PASSWORD",
                result,
                operator,
                ip
        );
        return new McpBastionConnectionTestResponse(
                result.success(),
                result.durationMs(),
                result.message(),
                LocalDateTime.now()
        );
    }

    public McpBastionEntity requireExisting(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("堡垒机不能为空");
        }
        McpBastionEntity entity = mcpBastionMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("堡垒机不存在");
        }
        return entity;
    }

    public McpBastionEntity requireEnabled(Long id) {
        McpBastionEntity entity = requireExisting(id);
        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("所选堡垒机已停用");
        }
        return entity;
    }

    public String decryptPassword(McpBastionEntity entity) {
        return mcpCryptoService.decrypt(entity.getPasswordEncrypted());
    }

    private McpBastionEntity toEntity(McpBastionEntity entity, McpBastionSaveRequest request) {
        entity.setName(requireValue(request.getName(), "堡垒机名称不能为空"));
        entity.setHost(requireValue(request.getHost(), "堡垒机主机不能为空"));
        if (request.getPort() == null || request.getPort() <= 0 || request.getPort() > 65535) {
            throw new IllegalArgumentException("堡垒机端口必须在 1-65535 之间");
        }
        entity.setPort(request.getPort());
        entity.setUsername(requireValue(request.getUsername(), "堡垒机用户不能为空"));
        String password = trimToNull(request.getPassword());
        if (password != null) {
            entity.setPasswordEncrypted(mcpCryptoService.encrypt(password));
        }
        entity.setIdentityFile(trimToNull(request.getIdentityFile()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private void validateCredential(McpBastionEntity entity) {
        if (trimToNull(entity.getPasswordEncrypted()) == null
                && trimToNull(entity.getIdentityFile()) == null) {
            throw new IllegalArgumentException("堡垒机密码或私钥文件不能为空");
        }
    }

    private void ensureNameAvailable(String name, Long currentId) {
        String normalized = requireValue(name, "堡垒机名称不能为空");
        McpBastionEntity duplicate = mcpBastionMapper.findByName(normalized);
        if (duplicate != null && !duplicate.getId().equals(currentId)) {
            throw new IllegalArgumentException("堡垒机名称已存在");
        }
    }

    private McpBastionResponse toResponse(McpBastionEntity entity) {
        return new McpBastionResponse(
                entity.getId(),
                entity.getName(),
                entity.getHost(),
                entity.getPort(),
                entity.getUsername(),
                trimToNull(entity.getPasswordEncrypted()) != null,
                entity.getIdentityFile(),
                Boolean.TRUE.equals(entity.getEnabled()),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private void audit(String action,
                       String beforeSnapshot,
                       String afterSnapshot,
                       Long targetId,
                       String operator,
                       String ip) {
        if (auditService == null) {
            return;
        }
        auditService.operation(
                operator,
                "ops:mcp-resource:" + ("CREATE".equals(action) ? "create" : "update"),
                action,
                "MCP_BASTION",
                targetId == null ? null : String.valueOf(targetId),
                beforeSnapshot,
                afterSnapshot,
                "SUCCESS",
                null,
                ip
        );
    }

    private void auditConnectionTest(Long bastionId,
                                     String host,
                                     int port,
                                     String username,
                                     String credentialType,
                                     McpBastionConnectionTester.Result result,
                                     String operator,
                                     String ip) {
        if (auditService == null) {
            return;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("host", host);
        snapshot.put("port", port);
        snapshot.put("username", username);
        snapshot.put("credentialType", credentialType);
        snapshot.put("durationMs", result.durationMs());
        auditService.operation(
                operator,
                bastionId == null ? "ops:mcp-resource:create" : "ops:mcp-resource:update",
                "TEST_CONNECTION",
                "MCP_BASTION",
                bastionId == null ? null : String.valueOf(bastionId),
                null,
                snapshot.toString(),
                result.success() ? "SUCCESS" : "FAILURE",
                result.success() ? null : result.message(),
                ip
        );
    }

    private String snapshot(McpBastionEntity entity) {
        if (entity == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("name", entity.getName());
        snapshot.put("host", entity.getHost());
        snapshot.put("port", entity.getPort());
        snapshot.put("username", entity.getUsername());
        snapshot.put("passwordConfigured", trimToNull(entity.getPasswordEncrypted()) != null);
        snapshot.put("identityFileConfigured", trimToNull(entity.getIdentityFile()) != null);
        snapshot.put("enabled", entity.getEnabled());
        snapshot.put("remark", entity.getRemark());
        return snapshot.toString();
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
}
