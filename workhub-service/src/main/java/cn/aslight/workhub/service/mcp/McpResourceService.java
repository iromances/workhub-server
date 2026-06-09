package cn.aslight.workhub.service.mcp;

import cn.aslight.workhub.dao.mcp.McpResourceMapper;
import cn.aslight.workhub.dao.project.BusinessLineMapper;
import cn.aslight.workhub.dao.project.ProjectInvolvedSystemMapper;
import cn.aslight.workhub.model.mcp.McpAuditEntryResponse;
import cn.aslight.workhub.model.mcp.McpCatalogResponse;
import cn.aslight.workhub.model.mcp.McpResourceEntity;
import cn.aslight.workhub.model.mcp.McpResourceProfile;
import cn.aslight.workhub.model.mcp.McpResourceResponse;
import cn.aslight.workhub.model.mcp.McpResourceSaveRequest;
import cn.aslight.workhub.model.project.BusinessLineEntity;
import cn.aslight.workhub.model.project.ProjectInvolvedSystemEntity;
import cn.aslight.workhub.service.intake.ProjectKnowledgeBaseService;
import cn.aslight.workhub.service.system.SysConfigService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class McpResourceService {

    private static final String DATABASE = "DATABASE";
    private static final String SERVER = "SERVER";

    private final McpResourceMapper mcpResourceMapper;
    private final McpCryptoService mcpCryptoService;
    private final McpResourceSchemaInitializer schemaInitializer;
    private final BusinessLineMapper businessLineMapper;
    private final ProjectInvolvedSystemMapper involvedSystemMapper;
    private final SysConfigService sysConfigService;
    private final ObjectMapper objectMapper;

    public McpResourceService(McpResourceMapper mcpResourceMapper,
                              McpCryptoService mcpCryptoService,
                              McpResourceSchemaInitializer schemaInitializer,
                              BusinessLineMapper businessLineMapper,
                              ProjectInvolvedSystemMapper involvedSystemMapper,
                              SysConfigService sysConfigService) {
        this.mcpResourceMapper = mcpResourceMapper;
        this.mcpCryptoService = mcpCryptoService;
        this.schemaInitializer = schemaInitializer;
        this.businessLineMapper = businessLineMapper;
        this.involvedSystemMapper = involvedSystemMapper;
        this.sysConfigService = sysConfigService;
        this.objectMapper = new ObjectMapper();
    }

    public List<McpResourceResponse> list(String resourceType,
                                          String businessLineCode,
                                          String environmentCode,
                                          String keyword,
                                          boolean enabledOnly) {
        schemaInitializer.ensureInitialized();
        return mcpResourceMapper.findAll(normalizeResourceTypeOrNull(resourceType),
                        trimToNull(businessLineCode),
                        trimToNull(environmentCode),
                        trimToNull(keyword),
                        enabledOnly)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public McpResourceResponse create(McpResourceSaveRequest request) {
        schemaInitializer.ensureInitialized();
        McpResourceEntity duplicate = mcpResourceMapper.findByTargetKey(requireValue(request.getTargetKey(), "目标 key 不能为空"));
        if (duplicate != null) {
            throw new IllegalArgumentException("目标 key 已存在");
        }
        McpResourceEntity entity = toEntity(new McpResourceEntity(), request);
        mcpResourceMapper.insert(entity);
        replaceBusinessLineBindings(entity.getId(), normalizeBusinessLineCodes(request));
        return toResponse(requireExisting(entity.getId()));
    }

    @Transactional
    public McpResourceResponse update(Long id, McpResourceSaveRequest request) {
        schemaInitializer.ensureInitialized();
        McpResourceEntity existing = requireExisting(id);
        String targetKey = requireValue(request.getTargetKey(), "目标 key 不能为空");
        McpResourceEntity duplicate = mcpResourceMapper.findByTargetKey(targetKey);
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("目标 key 已存在");
        }
        McpResourceEntity entity = toEntity(existing, request);
        entity.setId(id);
        mcpResourceMapper.update(entity);
        replaceBusinessLineBindings(id, normalizeBusinessLineCodes(request));
        return toResponse(requireExisting(id));
    }

    @Transactional
    public void delete(Long id) {
        schemaInitializer.ensureInitialized();
        mcpResourceMapper.deleteBusinessLineBindings(id);
        if (mcpResourceMapper.deleteById(id) == 0) {
            throw new IllegalArgumentException("MCP 资源不存在");
        }
    }

    public McpCatalogResponse catalog() {
        List<McpResourceResponse> resources = list(null, null, null, null, true);
        Set<String> businessLineCodes = new LinkedHashSet<>();
        Set<String> environmentCodes = new LinkedHashSet<>();
        resources.forEach(resource -> {
            businessLineCodes.addAll(resource.businessLineCodes());
            environmentCodes.add(resource.environmentCode());
        });
        List<Map<String, Object>> businessLines = businessLineCatalog(businessLineCodes);
        List<Map<String, Object>> environments = environmentCodes.stream()
                .map(code -> Map.<String, Object>of(
                        "code", code,
                        "name", code,
                        "production", "prod".equalsIgnoreCase(code),
                        "enabled", true
                ))
                .toList();
        List<Map<String, Object>> databaseTargets = resources.stream()
                .filter(resource -> DATABASE.equals(resource.resourceType()))
                .map(this::databaseCatalogTarget)
                .toList();
        List<Map<String, Object>> serverTargets = resources.stream()
                .filter(resource -> SERVER.equals(resource.resourceType()))
                .map(this::serverCatalogTarget)
                .toList();
        return new McpCatalogResponse(
                businessLines,
                environments,
                databaseTargets,
                serverTargets,
                knowledgeCatalog(),
                gitlabCatalog()
        );
    }

    public List<McpAuditEntryResponse> auditEntries(int limit) {
        int size = Math.min(Math.max(limit, 1), 500);
        Path path = Path.of(System.getenv().getOrDefault("WORKHUB_MCP_AUDIT_LOG", "logs/mcp-audit.jsonl"));
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path);
            int from = Math.max(lines.size() - size, 0);
            List<McpAuditEntryResponse> entries = new ArrayList<>();
            for (int i = lines.size() - 1; i >= from; i--) {
                String line = lines.get(i);
                if (line == null || line.isBlank()) {
                    continue;
                }
                Map<String, Object> fields = objectMapper.readValue(line, new TypeReference<>() {
                });
                entries.add(new McpAuditEntryResponse(fields));
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalArgumentException("读取 MCP 审计日志失败：" + ex.getMessage(), ex);
        }
    }

    private Map<String, Object> databaseCatalogTarget(McpResourceResponse resource) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("key", resource.targetKey());
        target.put("businessLineCode", resource.businessLineCode());
        target.put("businessLineCodes", resource.businessLineCodes());
        target.put("environmentCode", resource.environmentCode());
        target.put("name", resource.name());
        target.put("host", resource.host());
        target.put("port", resource.port());
        target.put("schema", resource.databaseSchema());
        target.put("username", resource.username());
        target.put("password", decryptDatabasePassword(resource));
        Map<String, Object> tunnel = sshTunnel(resource);
        if (!tunnel.isEmpty()) {
            target.put("sshTunnel", tunnel);
        }
        target.put("profiles", resource.profiles().stream()
                .map(profile -> Map.of(
                        "key", profile.key(),
                        "maxRows", defaultInt(profile.maxRows(), 100),
                        "queryTimeoutSeconds", defaultInt(profile.queryTimeoutSeconds(), 10),
                        "maxResultBytes", defaultInt(profile.maxResultBytes(), 65536)
                ))
                .toList());
        return target;
    }

    private List<Map<String, Object>> businessLineCatalog(Set<String> resourceBusinessLineCodes) {
        Map<String, BusinessLineEntity> configuredLines = new LinkedHashMap<>();
        List<BusinessLineEntity> businessLineEntities = businessLineMapper.findAll(null);
        if (businessLineEntities == null) {
            businessLineEntities = List.of();
        }
        for (BusinessLineEntity entity : businessLineEntities) {
            String name = trimToNull(entity.getBusinessLineName());
            if (name != null) {
                configuredLines.put(name, entity);
            }
        }
        List<ProjectInvolvedSystemEntity> foundSystems = involvedSystemMapper.findAll(null, null, true, null);
        List<ProjectInvolvedSystemEntity> systems = foundSystems == null ? List.of() : foundSystems;
        Set<String> allCodes = new LinkedHashSet<>(configuredLines.keySet());
        allCodes.addAll(resourceBusinessLineCodes);
        return allCodes.stream()
                .map(code -> businessLineCatalogItem(code, configuredLines.get(code), systems))
                .toList();
    }

    private Map<String, Object> businessLineCatalogItem(String code,
                                                        BusinessLineEntity entity,
                                                        List<ProjectInvolvedSystemEntity> systems) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("name", entity == null ? code : entity.getBusinessLineName());
        item.put("gitlabGroupName", entity == null ? null : trimToNull(entity.getGitlabGroupName()));
        item.put("involvedSystems", systemsByScope(systems, "BUSINESS_LINE", code));
        item.put("globalSystems", systemsByScope(systems, "MIDDLE_PLATFORM", ""));
        item.put("enabled", entity == null || !Boolean.FALSE.equals(entity.getEnabled()));
        return item;
    }

    private List<String> systemsByScope(List<ProjectInvolvedSystemEntity> systems, String scope, String businessLine) {
        return systems.stream()
                .filter(item -> scope.equals(item.getSystemScope()))
                .filter(item -> businessLine == null || businessLine.equals(nullToEmpty(item.getBusinessLine())))
                .map(ProjectInvolvedSystemEntity::getSystemName)
                .map(this::trimToNull)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private Map<String, Object> knowledgeCatalog() {
        Map<String, Object> knowledge = new LinkedHashMap<>();
        knowledge.put("projectVaultPath", sysConfigService.findPlainValue(
                ProjectKnowledgeBaseService.CONFIG_GROUP,
                ProjectKnowledgeBaseService.CONFIG_KEY_VAULT_PATH
        ));
        knowledge.put("requirementRawPathPattern", "raw/requirements/{业务线}/{项目名称}/{年份}/{审批编号}/source.md");
        knowledge.put("requirementWikiPathPattern", "wiki/projects/{业务线}/需求迭代/{项目名称}/{年份}/{审批编号}-{需求名称}.md");
        return knowledge;
    }

    private Map<String, Object> gitlabCatalog() {
        Map<String, Object> gitlab = new LinkedHashMap<>();
        gitlab.put("webApiUrl", sysConfigService.findPlainValue("gitlab.global", "webApiUrl"));
        gitlab.put("sshHost", sysConfigService.findPlainValue("gitlab.global", "sshHost"));
        gitlab.put("accessTokenConfigured", trimToNull(sysConfigService.findPlainValue("gitlab.global", "accessToken")) != null);
        return gitlab;
    }

    private Map<String, Object> serverCatalogTarget(McpResourceResponse resource) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("key", resource.targetKey());
        target.put("businessLineCode", resource.businessLineCode());
        target.put("businessLineCodes", resource.businessLineCodes());
        target.put("environmentCode", resource.environmentCode());
        target.put("name", resource.name());
        target.put("systemName", resource.systemName());
        target.put("systemNames", resource.systemNames());
        target.put("host", resource.host());
        target.put("port", resource.port());
        target.put("username", resource.username());
        target.put("password", decryptSshPassword(resource));
        target.put("identityFile", resource.sshIdentityFile());
        Map<String, Object> tunnel = sshTunnel(resource);
        if (!tunnel.isEmpty()) {
            target.put("sshTunnel", tunnel);
        }
        target.put("allowedServices", resource.allowedServices());
        target.put("allowedLogPaths", resource.allowedLogPaths());
        target.put("profiles", resource.profiles().stream()
                .map(profile -> Map.of(
                        "key", profile.key(),
                        "maxOutputLines", defaultInt(profile.maxOutputLines(), 500),
                        "timeoutSeconds", defaultInt(profile.timeoutSeconds(), 10)
                ))
                .toList());
        return target;
    }

    private Map<String, Object> sshTunnel(McpResourceResponse resource) {
        Map<String, Object> tunnel = new LinkedHashMap<>();
        if (Boolean.TRUE.equals(resource.sshBastionEnabled()) && resource.sshBastionHost() != null) {
            tunnel.put("bastionHost", resource.sshBastionHost());
            tunnel.put("bastionPort", defaultInt(resource.sshBastionPort(), 22));
            tunnel.put("bastionUser", resource.sshBastionUser());
            tunnel.put("password", decryptSshBastionPassword(resource));
            tunnel.put("identityFile", resource.sshIdentityFile());
        }
        return tunnel;
    }

    private McpResourceEntity requireExisting(Long id) {
        McpResourceEntity entity = mcpResourceMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("MCP 资源不存在");
        }
        return entity;
    }

    private McpResourceEntity toEntity(McpResourceEntity entity, McpResourceSaveRequest request) {
        String resourceType = normalizeResourceType(request.getResourceType());
        validateByResourceType(resourceType, entity, request);
        entity.setResourceType(resourceType);
        entity.setTargetKey(requireValue(request.getTargetKey(), "目标 key 不能为空"));
        entity.setBusinessLineCode(normalizeBusinessLineCodes(request).getFirst());
        entity.setEnvironmentCode(requireValue(request.getEnvironmentCode(), "环境不能为空"));
        entity.setName(requireValue(request.getName(), "名称不能为空"));
        entity.setSystemName(SERVER.equals(resourceType) ? writeJson(normalizeSystemNames(request)) : null);
        entity.setHost(requireValue(request.getHost(), "目标主机不能为空"));
        entity.setPort(request.getPort());
        entity.setDatabaseSchema(trimToNull(request.getDatabaseSchema()));
        entity.setUsername(trimToNull(request.getUsername()));
        entity.setPasswordEncrypted(encryptOrKeep(firstNonBlank(request.getPassword(), request.getSecretRef()), entity.getPasswordEncrypted()));
        if (trimToNull(firstNonBlank(request.getPassword(), request.getSecretRef())) != null) {
            entity.setSecretRef(null);
        }
        entity.setSshPasswordEncrypted(encryptOrKeep(request.getSshPassword(), entity.getSshPasswordEncrypted()));
        entity.setSshBastionEnabled(Boolean.TRUE.equals(request.getSshBastionEnabled()));
        entity.setSshBastionHost(trimToNull(request.getSshBastionHost()));
        entity.setSshBastionPort(request.getSshBastionPort());
        entity.setSshBastionUser(trimToNull(request.getSshBastionUser()));
        entity.setSshBastionPasswordEncrypted(encryptOrKeep(request.getSshBastionPassword(), entity.getSshBastionPasswordEncrypted()));
        entity.setSshIdentityFile(trimToNull(request.getSshIdentityFile()));
        entity.setAllowedServicesJson(writeJson(request.getAllowedServices() == null ? List.of() : request.getAllowedServices()));
        entity.setAllowedLogPathsJson(writeJson(request.getAllowedLogPaths() == null ? List.of() : request.getAllowedLogPaths()));
        entity.setProfilesJson(writeJson(normalizeProfiles(request.getProfiles(), resourceType)));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private void validateByResourceType(String resourceType, McpResourceEntity existing, McpResourceSaveRequest request) {
        if (request.getPort() == null || request.getPort() <= 0) {
            throw new IllegalArgumentException("端口必须大于 0");
        }
        if (DATABASE.equals(resourceType)) {
            requireValue(request.getUsername(), "数据库用户名不能为空");
            if (!hasAnySecret(request.getPassword(), request.getSecretRef(), existing.getPasswordEncrypted(), existing.getSecretRef())) {
                throw new IllegalArgumentException("数据库密码不能为空");
            }
            validateSshCredential(existing, request);
            return;
        }
        if (SERVER.equals(resourceType)) {
            requireValue(request.getUsername(), "服务器 SSH 用户不能为空");
            if (!hasAnySecret(request.getSshPassword(), existing.getSshPasswordEncrypted(), request.getSshIdentityFile(), existing.getSshIdentityFile())) {
                throw new IllegalArgumentException("服务器 SSH 密码或私钥文件不能为空");
            }
        }
    }

    private List<McpResourceProfile> normalizeProfiles(List<McpResourceProfile> profiles, String resourceType) {
        List<McpResourceProfile> normalized = profiles == null || profiles.isEmpty()
                ? List.of(defaultProfile(resourceType))
                : profiles;
        normalized.forEach(profile -> requireValue(profile.key(), "profile key 不能为空"));
        return normalized;
    }

    private List<String> normalizeBusinessLineCodes(McpResourceSaveRequest request) {
        List<String> values = new ArrayList<>();
        if (request.getBusinessLineCodes() != null) {
            request.getBusinessLineCodes().forEach(value -> {
                String normalized = trimToNull(value);
                if (normalized != null && !values.contains(normalized)) {
                    values.add(normalized);
                }
            });
        }
        String legacy = trimToNull(request.getBusinessLineCode());
        if (legacy != null && !values.contains(legacy)) {
            values.add(legacy);
        }
        if (values.isEmpty()) {
            throw new IllegalArgumentException("业务线不能为空");
        }
        return values;
    }

    private List<String> normalizeSystemNames(McpResourceSaveRequest request) {
        List<String> values = new ArrayList<>();
        if (request.getSystemNames() != null) {
            request.getSystemNames().forEach(value -> addUnique(values, value));
        }
        addUnique(values, request.getSystemName());
        if (values.isEmpty()) {
            throw new IllegalArgumentException("服务器所属系统不能为空");
        }
        return values;
    }

    private void addUnique(List<String> values, String value) {
        String normalized = trimToNull(value);
        if (normalized != null && !values.contains(normalized)) {
            values.add(normalized);
        }
    }

    private void replaceBusinessLineBindings(Long resourceId, List<String> businessLineCodes) {
        mcpResourceMapper.deleteBusinessLineBindings(resourceId);
        businessLineCodes.forEach(code -> mcpResourceMapper.insertBusinessLineBinding(resourceId, code));
    }

    private McpResourceProfile defaultProfile(String resourceType) {
        if (DATABASE.equals(resourceType)) {
            return new McpResourceProfile("readonly", 100, 10, 65536, null, null);
        }
        return new McpResourceProfile("diagnostic", null, null, null, 500, 10);
    }

    private McpResourceResponse toResponse(McpResourceEntity entity) {
        List<String> systemNames = readSystemNames(entity.getSystemName());
        return new McpResourceResponse(
                entity.getId(),
                entity.getResourceType(),
                entity.getTargetKey(),
                entity.getBusinessLineCode(),
                businessLineCodes(entity),
                entity.getEnvironmentCode(),
                entity.getName(),
                systemNames.isEmpty() ? null : systemNames.getFirst(),
                systemNames,
                entity.getHost(),
                entity.getPort(),
                entity.getDatabaseSchema(),
                entity.getUsername(),
                hasAnySecret(entity.getPasswordEncrypted(), entity.getSecretRef()),
                hasAnySecret(entity.getSshPasswordEncrypted()),
                Boolean.TRUE.equals(entity.getSshBastionEnabled()),
                entity.getSshBastionHost(),
                entity.getSshBastionPort(),
                entity.getSshBastionUser(),
                hasAnySecret(entity.getSshBastionPasswordEncrypted()),
                entity.getSshIdentityFile(),
                readStringList(entity.getAllowedServicesJson()),
                readStringList(entity.getAllowedLogPathsJson()),
                readProfiles(entity.getProfilesJson()),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<String> businessLineCodes(McpResourceEntity entity) {
        if (entity.getId() != null) {
            List<String> codes = mcpResourceMapper.findBusinessLineCodes(entity.getId());
            if (codes != null && !codes.isEmpty()) {
                return codes;
            }
        }
        String legacy = trimToNull(entity.getBusinessLineCode());
        return legacy == null ? List.of() : List.of(legacy);
    }

    private String normalizeResourceType(String resourceType) {
        String normalized = requireValue(resourceType, "资源类型不能为空").toUpperCase();
        if (!DATABASE.equals(normalized) && !SERVER.equals(normalized)) {
            throw new IllegalArgumentException("资源类型只支持 DATABASE 或 SERVER");
        }
        return normalized;
    }

    private String normalizeResourceTypeOrNull(String resourceType) {
        return resourceType == null || resourceType.isBlank() ? null : normalizeResourceType(resourceType);
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP 白名单 JSON 格式错误", ex);
        }
    }

    private List<String> readSystemNames(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return List.of();
        }
        if (!normalized.startsWith("[")) {
            return List.of(normalized);
        }
        try {
            List<String> parsed = objectMapper.readValue(normalized, new TypeReference<>() {
            });
            List<String> values = new ArrayList<>();
            parsed.forEach(item -> addUnique(values, item));
            return values;
        } catch (IOException ex) {
            return List.of(normalized);
        }
    }

    private List<McpResourceProfile> readProfiles(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP profile JSON 格式错误", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalArgumentException("MCP 配置无法序列化", ex);
        }
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null || value <= 0 ? defaultValue : value;
    }

    private String decryptDatabasePassword(McpResourceResponse resource) {
        McpResourceEntity entity = requireExisting(resource.id());
        if (trimToNull(entity.getPasswordEncrypted()) != null) {
            return mcpCryptoService.decrypt(entity.getPasswordEncrypted());
        }
        return entity.getSecretRef();
    }

    private String decryptSshPassword(McpResourceResponse resource) {
        McpResourceEntity entity = requireExisting(resource.id());
        return mcpCryptoService.decrypt(entity.getSshPasswordEncrypted());
    }

    private String decryptSshBastionPassword(McpResourceResponse resource) {
        McpResourceEntity entity = requireExisting(resource.id());
        return mcpCryptoService.decrypt(entity.getSshBastionPasswordEncrypted());
    }

    private void validateSshCredential(McpResourceEntity existing, McpResourceSaveRequest request) {
        if (!Boolean.TRUE.equals(request.getSshBastionEnabled())) {
            return;
        }
        requireValue(request.getSshBastionHost(), "堡垒机主机不能为空");
        requireValue(request.getSshBastionUser(), "堡垒机用户不能为空");
        if (!hasAnySecret(request.getSshBastionPassword(), existing.getSshBastionPasswordEncrypted(), request.getSshIdentityFile(), existing.getSshIdentityFile())) {
            throw new IllegalArgumentException("堡垒机密码或私钥文件不能为空");
        }
    }

    private String encryptOrKeep(String plainText, String existingCipherText) {
        String normalized = trimToNull(plainText);
        if (normalized == null) {
            return existingCipherText;
        }
        return mcpCryptoService.encrypt(normalized);
    }

    private boolean hasAnySecret(String... values) {
        for (String value : values) {
            if (trimToNull(value) != null) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String first, String second) {
        String normalizedFirst = trimToNull(first);
        return normalizedFirst == null ? trimToNull(second) : normalizedFirst;
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

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
