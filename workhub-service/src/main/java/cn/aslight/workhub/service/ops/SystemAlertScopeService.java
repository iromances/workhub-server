package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertScopeMapper;
import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeResponse;
import cn.aslight.workhub.model.ops.SystemAlertScopeSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceRequest;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceResponse;
import cn.aslight.workhub.model.ops.SystemAlertWatchMode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SystemAlertScopeService {

    private static final Pattern INDEX_PATTERN = Pattern.compile("[A-Za-z0-9*._-]+");
    private final SystemAlertScopeMapper mapper;

    public SystemAlertScopeService(SystemAlertScopeMapper mapper) {
        this.mapper = mapper;
    }

    public List<SystemAlertScopeResponse> list(String businessLineCode, String environmentCode, boolean enabledOnly) {
        return attachChildren(mapper.findScopes(trimToNull(businessLineCode), trimToNull(environmentCode), enabledOnly))
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public SystemAlertScopeResponse create(SystemAlertScopeSaveRequest request) {
        SystemAlertScopeEntity entity = toEntity(request);
        if (mapper.findByIdentity(entity.getBusinessLineCode(), entity.getEnvironmentCode()) != null) {
            throw new IllegalArgumentException("业务线关注范围已存在");
        }
        mapper.insertScope(entity);
        replaceChildren(entity);
        return toResponse(requireScope(entity.getId()));
    }

    @Transactional
    public SystemAlertScopeResponse update(Long id, SystemAlertScopeSaveRequest request) {
        requireScope(id);
        SystemAlertScopeEntity entity = toEntity(request);
        SystemAlertScopeEntity duplicate = mapper.findByIdentity(entity.getBusinessLineCode(), entity.getEnvironmentCode());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("业务线关注范围已存在");
        }
        entity.setId(id);
        mapper.updateScope(entity);
        replaceChildren(entity);
        mapper.deleteSyncState(id);
        return toResponse(requireScope(id));
    }

    @Transactional
    public void delete(Long id) {
        requireScope(id);
        mapper.deleteSyncState(id);
        mapper.deleteServices(id);
        mapper.deleteIndexPatterns(id);
        if (mapper.deleteScope(id) == 0) {
            throw new IllegalArgumentException("业务线关注范围不存在");
        }
    }

    private SystemAlertScopeEntity toEntity(SystemAlertScopeSaveRequest request) {
        SystemAlertScopeEntity entity = new SystemAlertScopeEntity();
        entity.setBusinessLineCode(requireValue(request.getBusinessLineCode(), "业务线不能为空"));
        entity.setEnvironmentCode(requireValue(request.getEnvironmentCode(), "环境不能为空"));
        SystemAlertWatchMode mode = SystemAlertWatchMode.parse(request.getWatchMode());
        entity.setWatchMode(mode.name());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setServices(normalizeServices(request.getServices()));
        entity.setIndexPatterns(normalizeIndexPatterns(request.getIndexPatterns()));
        if (mode == SystemAlertWatchMode.SELECTED
                && entity.getServices().stream().noneMatch(item -> Boolean.TRUE.equals(item.enabled()))) {
            throw new IllegalArgumentException("指定子系统模式下至少配置一个启用子系统");
        }
        return entity;
    }

    private List<SystemAlertScopeServiceEntity> normalizeServices(List<SystemAlertScopeServiceRequest> requests) {
        LinkedHashMap<String, SystemAlertScopeServiceEntity> normalized = new LinkedHashMap<>();
        if (requests == null) {
            return List.of();
        }
        int order = 0;
        for (SystemAlertScopeServiceRequest request : requests) {
            if (request == null) {
                throw new IllegalArgumentException("关注子系统不能为空");
            }
            String serviceName = requireValue(request.getServiceName(), "服务名不能为空");
            String identity = serviceName.toLowerCase(Locale.ROOT);
            if (normalized.containsKey(identity)) {
                throw new IllegalArgumentException("关注子系统服务名重复：" + serviceName);
            }
            normalized.put(identity, new SystemAlertScopeServiceEntity(
                    null, null,
                    requireValue(request.getSubsystemName(), "子系统名称不能为空"),
                    serviceName, request.getEnabled() == null || request.getEnabled(), order++));
        }
        return List.copyOf(normalized.values());
    }

    private List<String> normalizeIndexPatterns(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("至少配置一个日志索引模式");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String pattern = requireValue(value, "日志索引模式不能为空");
            if (pattern.length() > 255 || !INDEX_PATTERN.matcher(pattern).matches()) {
                throw new IllegalArgumentException("日志索引模式非法：" + pattern);
            }
            normalized.add(pattern);
        }
        if (normalized.size() > 20) {
            throw new IllegalArgumentException("日志索引模式最多配置20个");
        }
        return List.copyOf(normalized);
    }

    private void replaceChildren(SystemAlertScopeEntity entity) {
        Long scopeId = entity.getId();
        mapper.deleteServices(scopeId);
        for (SystemAlertScopeServiceEntity service : entity.getServices()) {
            mapper.insertService(new SystemAlertScopeServiceEntity(
                    null, scopeId, service.subsystemName(), service.serviceName(), service.enabled(), service.sortOrder()));
        }
        mapper.deleteIndexPatterns(scopeId);
        for (int index = 0; index < entity.getIndexPatterns().size(); index++) {
            mapper.insertIndexPattern(new SystemAlertScopeIndexEntity(
                    null, scopeId, entity.getIndexPatterns().get(index), index));
        }
    }

    private SystemAlertScopeEntity requireScope(Long id) {
        SystemAlertScopeEntity scope = mapper.findById(id);
        if (scope == null) {
            throw new IllegalArgumentException("业务线关注范围不存在");
        }
        return attachChildren(List.of(scope)).getFirst();
    }

    private List<SystemAlertScopeEntity> attachChildren(List<SystemAlertScopeEntity> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            return List.of();
        }
        List<Long> ids = scopes.stream().map(SystemAlertScopeEntity::getId).toList();
        Map<Long, List<SystemAlertScopeServiceEntity>> services = mapper.findServices(ids).stream()
                .collect(Collectors.groupingBy(SystemAlertScopeServiceEntity::scopeId));
        Map<Long, List<String>> patterns = mapper.findIndexPatterns(ids).stream()
                .collect(Collectors.groupingBy(SystemAlertScopeIndexEntity::scopeId,
                        Collectors.mapping(SystemAlertScopeIndexEntity::indexPattern, Collectors.toList())));
        for (SystemAlertScopeEntity scope : scopes) {
            scope.setServices(services.getOrDefault(scope.getId(), List.of()));
            scope.setIndexPatterns(patterns.getOrDefault(scope.getId(), List.of()));
            scope.setIndexPatternExpression(String.join(",", scope.getIndexPatterns()));
        }
        return scopes;
    }

    private SystemAlertScopeResponse toResponse(SystemAlertScopeEntity entity) {
        return new SystemAlertScopeResponse(
                entity.getId(), entity.getBusinessLineCode(), entity.getEnvironmentCode(), entity.getWatchMode(),
                entity.getServices().stream().map(item -> new SystemAlertScopeServiceResponse(
                        item.id(), item.subsystemName(), item.serviceName(), item.enabled())).toList(),
                entity.getIndexPatterns(), entity.getEnabled(), entity.getRemark(), entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
