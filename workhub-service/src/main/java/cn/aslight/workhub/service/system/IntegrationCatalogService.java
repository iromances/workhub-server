package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.IntegrationCapabilityMapper;
import cn.aslight.workhub.dao.system.IntegrationSystemMapper;
import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.IntegrationCapabilityEntity;
import cn.aslight.workhub.model.system.IntegrationCapabilityItem;
import cn.aslight.workhub.model.system.IntegrationCapabilityResponse;
import cn.aslight.workhub.model.system.IntegrationSystemDetailResponse;
import cn.aslight.workhub.model.system.IntegrationSystemDetailResponse.ConfigGroupStatus;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.model.system.IntegrationSystemResponse;
import cn.aslight.workhub.model.system.IntegrationSystemSaveRequest;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统集成台账服务：集成系统与接入能力目录的查询和维护；配置状态只统计不回值。
 */
@Service
public class IntegrationCatalogService {

    public static final Set<String> INTEGRATION_METHODS = Set.of("REST_API", "REST_AND_LOCAL_GIT");
    public static final Set<String> AUTH_TYPES = Set.of("ACCOUNT_PASSWORD", "BASIC_API_TOKEN", "PAT");
    public static final Set<String> CAPABILITY_TYPES = Set.of("READ", "WRITE");
    public static final Set<String> IMPL_STATUSES = Set.of("ACTIVE", "PLANNED");

    private final IntegrationSystemMapper systemMapper;
    private final IntegrationCapabilityMapper capabilityMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SystemAuditService auditService;

    public IntegrationCatalogService(IntegrationSystemMapper systemMapper,
                                     IntegrationCapabilityMapper capabilityMapper,
                                     SysConfigMapper sysConfigMapper,
                                     SystemAuditService auditService) {
        this.systemMapper = systemMapper;
        this.capabilityMapper = capabilityMapper;
        this.sysConfigMapper = sysConfigMapper;
        this.auditService = auditService;
    }

    public List<IntegrationSystemResponse> list() {
        return systemMapper.findAll().stream()
                .map(entity -> {
                    IntegrationSystemResponse response = toResponse(entity);
                    response.setCapabilityCount(capabilityMapper.countBySystemId(entity.getId()));
                    return response;
                })
                .toList();
    }

    public IntegrationSystemDetailResponse detail(Long id) {
        IntegrationSystemEntity entity = requireExisting(id);
        IntegrationSystemDetailResponse response = new IntegrationSystemDetailResponse();
        applyEntity(response, entity);
        response.setConfigGroups(splitGroups(entity.getConfigGroups()));
        response.setCapabilities(capabilityMapper.findBySystemId(id).stream()
                .map(this::toCapabilityResponse)
                .toList());
        response.setConfigGroupStatuses(buildConfigGroupStatuses(splitGroups(entity.getConfigGroups())));
        return response;
    }

    @Transactional
    public IntegrationSystemDetailResponse create(IntegrationSystemSaveRequest request, String operator, String ip) {
        String systemCode = normalizeSystemCode(request.getSystemCode());
        if (systemMapper.findByCode(systemCode) != null) {
            throw new IllegalArgumentException("系统编码已存在：" + systemCode);
        }
        List<IntegrationCapabilityEntity> capabilities = buildCapabilities(request.getCapabilities());
        IntegrationSystemEntity entity = new IntegrationSystemEntity();
        applyRequest(entity, request, systemCode);
        entity.setCreatedBy(operator);
        entity.setUpdatedBy(operator);
        systemMapper.insert(entity);
        saveCapabilities(entity.getId(), capabilities);
        audit("CREATE", null, systemMapper.findById(entity.getId()), operator, ip);
        return detail(entity.getId());
    }

    @Transactional
    public IntegrationSystemDetailResponse update(Long id, IntegrationSystemSaveRequest request, String operator, String ip) {
        IntegrationSystemEntity existing = requireExisting(id);
        String beforeSnapshot = auditSnapshot(existing);
        // 全部入参先校验，再落库；system_code 是内置探针的分派键，创建后不可修改。
        List<IntegrationCapabilityEntity> capabilities = buildCapabilities(request.getCapabilities());
        applyRequest(existing, request, existing.getSystemCode());
        existing.setUpdatedBy(operator);
        systemMapper.update(existing);
        capabilityMapper.deleteBySystemId(id);
        saveCapabilities(id, capabilities);
        audit("UPDATE", beforeSnapshot, systemMapper.findById(id), operator, ip);
        return detail(id);
    }

    public IntegrationSystemEntity requireExisting(Long id) {
        IntegrationSystemEntity entity = systemMapper.findById(id);
        if (entity == null) {
            throw new IllegalArgumentException("集成系统不存在");
        }
        return entity;
    }

    private void applyRequest(IntegrationSystemEntity entity, IntegrationSystemSaveRequest request, String systemCode) {
        entity.setSystemCode(systemCode);
        entity.setSystemName(requireValue(request.getSystemName(), "系统名称不能为空"));
        entity.setIntegrationMethod(normalizeEnum(request.getIntegrationMethod(), INTEGRATION_METHODS, "集成方式只支持 REST_API 或 REST_AND_LOCAL_GIT"));
        entity.setAuthType(normalizeEnum(request.getAuthType(), AUTH_TYPES, "认证方式只支持 ACCOUNT_PASSWORD、BASIC_API_TOKEN 或 PAT"));
        entity.setConfigGroups(joinGroups(request.getConfigGroups()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        entity.setRemark(trimToNull(request.getRemark()));
    }

    private List<IntegrationCapabilityEntity> buildCapabilities(List<IntegrationCapabilityItem> items) {
        if (items == null) {
            return List.of();
        }
        Set<String> codes = new HashSet<>();
        List<IntegrationCapabilityEntity> entities = new ArrayList<>();
        for (IntegrationCapabilityItem item : items) {
            String code = normalizeCapabilityCode(item.getCapabilityCode());
            if (!codes.add(code)) {
                throw new IllegalArgumentException("能力编码重复：" + code);
            }
            IntegrationCapabilityEntity entity = new IntegrationCapabilityEntity();
            entity.setCapabilityCode(code);
            entity.setCapabilityName(requireValue(item.getCapabilityName(), "能力名称不能为空"));
            entity.setCapabilityType(normalizeEnum(item.getCapabilityType(), CAPABILITY_TYPES, "能力类型只支持 READ 或 WRITE"));
            entity.setImplStatus(normalizeEnum(item.getImplStatus(), IMPL_STATUSES, "实现状态只支持 ACTIVE 或 PLANNED"));
            entity.setDescription(trimToNull(item.getDescription()));
            entity.setEnabled(item.getEnabled() == null || item.getEnabled());
            entity.setSortOrder(item.getSortOrder() == null ? 0 : item.getSortOrder());
            entity.setRemark(trimToNull(item.getRemark()));
            entities.add(entity);
        }
        return entities;
    }

    private void saveCapabilities(Long systemId, List<IntegrationCapabilityEntity> capabilities) {
        for (IntegrationCapabilityEntity entity : capabilities) {
            entity.setSystemId(systemId);
            capabilityMapper.insert(entity);
        }
    }

    private List<ConfigGroupStatus> buildConfigGroupStatuses(List<String> groups) {
        List<SysConfigItemEntity> all = sysConfigMapper.findAll(null, null);
        List<ConfigGroupStatus> statuses = new ArrayList<>();
        for (String group : groups) {
            boolean prefix = group.endsWith(".");
            List<SysConfigItemEntity> items = all.stream()
                    .filter(item -> prefix ? item.getConfigGroup().startsWith(group) : item.getConfigGroup().equals(group))
                    .toList();
            statuses.add(new ConfigGroupStatus(
                    group,
                    items.size(),
                    (int) items.stream().filter(item -> Boolean.TRUE.equals(item.getEnabled())).count(),
                    items.stream().anyMatch(item -> SysConfigService.VALUE_TYPE_SECRET.equals(item.getValueType())
                            && item.getEncryptedValue() != null && !item.getEncryptedValue().isBlank()),
                    prefix
            ));
        }
        return statuses;
    }

    private IntegrationSystemResponse toResponse(IntegrationSystemEntity entity) {
        IntegrationSystemResponse response = new IntegrationSystemResponse();
        applyEntity(response, entity);
        response.setConfigGroups(splitGroups(entity.getConfigGroups()));
        return response;
    }

    private IntegrationCapabilityResponse toCapabilityResponse(IntegrationCapabilityEntity entity) {
        IntegrationCapabilityResponse response = new IntegrationCapabilityResponse();
        response.setId(entity.getId());
        response.setCapabilityCode(entity.getCapabilityCode());
        response.setCapabilityName(entity.getCapabilityName());
        response.setCapabilityType(entity.getCapabilityType());
        response.setImplStatus(entity.getImplStatus());
        response.setDescription(entity.getDescription());
        response.setEnabled(entity.getEnabled());
        response.setSortOrder(entity.getSortOrder());
        response.setRemark(entity.getRemark());
        return response;
    }

    private void applyEntity(IntegrationSystemResponse response, IntegrationSystemEntity entity) {
        response.setId(entity.getId());
        response.setSystemCode(entity.getSystemCode());
        response.setSystemName(entity.getSystemName());
        response.setIntegrationMethod(entity.getIntegrationMethod());
        response.setAuthType(entity.getAuthType());
        response.setEnabled(entity.getEnabled());
        response.setSortOrder(entity.getSortOrder());
        response.setRemark(entity.getRemark());
        response.setUpdatedAt(entity.getUpdatedAt());
    }

    private String normalizeSystemCode(String systemCode) {
        String normalized = requireValue(systemCode, "系统编码不能为空").toUpperCase();
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("系统编码只能为大写字母、数字或下划线，且以字母开头（2-64 位）");
        }
        return normalized;
    }

    private String normalizeCapabilityCode(String capabilityCode) {
        String normalized = requireValue(capabilityCode, "能力编码不能为空").toLowerCase();
        if (!normalized.matches("[a-z][a-z0-9.-]{1,63}")) {
            throw new IllegalArgumentException("能力编码只能为小写字母、数字、点或连字符，且以字母开头（2-64 位）");
        }
        return normalized;
    }

    private String normalizeEnum(String value, Set<String> allowed, String message) {
        String normalized = requireValue(value, message).toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private String joinGroups(List<String> groups) {
        if (groups == null || groups.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String group : groups) {
            String value = requireValue(group, "配置组不能为空白");
            if (value.length() > 128 || !value.matches("[a-zA-Z0-9.-]+")) {
                throw new IllegalArgumentException("配置组格式不正确（仅字母、数字、点、连字符，最长128）：" + value);
            }
            normalized.add(value);
        }
        return String.join(",", normalized);
    }

    private List<String> splitGroups(String configGroups) {
        if (configGroups == null || configGroups.isBlank()) {
            return List.of();
        }
        return List.of(configGroups.split(","));
    }

    private void audit(String actionType, String beforeSnapshot, IntegrationSystemEntity after, String operator, String ip) {
        auditService.operation(
                operator,
                "system:integration:update",
                actionType,
                "SYS_INTEGRATION_SYSTEM",
                after == null ? null : String.valueOf(after.getId()),
                beforeSnapshot,
                auditSnapshot(after),
                "SUCCESS",
                null,
                ip
        );
    }

    private String auditSnapshot(IntegrationSystemEntity entity) {
        if (entity == null) {
            return null;
        }
        return "{systemCode=%s, systemName=%s, integrationMethod=%s, authType=%s, enabled=%s}"
                .formatted(entity.getSystemCode(), entity.getSystemName(),
                        entity.getIntegrationMethod(), entity.getAuthType(), entity.getEnabled());
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
