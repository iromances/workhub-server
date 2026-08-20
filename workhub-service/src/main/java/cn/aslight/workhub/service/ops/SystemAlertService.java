package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventCategory;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SystemAlertService {

    private static final Pattern INDEX_PATTERN = Pattern.compile("[A-Za-z0-9*._-]+");
    private final SystemAlertMapper systemAlertMapper;

    public SystemAlertService(SystemAlertMapper systemAlertMapper) {
        this.systemAlertMapper = systemAlertMapper;
    }

    public List<SystemAlertSubsystemResponse> listSubsystems(String businessLineCode,
                                                             String environmentCode,
                                                             boolean enabledOnly,
                                                             String keyword) {
        return attachIndexPatterns(systemAlertMapper.findSubsystems(
                        trimToNull(businessLineCode),
                        trimToNull(environmentCode),
                        enabledOnly,
                        trimToNull(keyword)))
                .stream()
                .map(this::toSubsystemResponse)
                .toList();
    }

    @Transactional
    public SystemAlertSubsystemResponse createSubsystem(SystemAlertSubsystemSaveRequest request) {
        SystemAlertSubsystemEntity entity = toSubsystemEntity(new SystemAlertSubsystemEntity(), request);
        SystemAlertSubsystemEntity duplicate = systemAlertMapper.findSubsystemByIdentity(
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getServiceName());
        if (duplicate != null) {
            throw new IllegalArgumentException("关注子系统已存在");
        }
        systemAlertMapper.insertSubsystem(entity);
        replaceIndexPatterns(entity.getId(), entity.getIndexPatterns());
        return toSubsystemResponse(requireSubsystem(entity.getId()));
    }

    @Transactional
    public SystemAlertSubsystemResponse updateSubsystem(Long id, SystemAlertSubsystemSaveRequest request) {
        requireSubsystem(id);
        SystemAlertSubsystemEntity entity = toSubsystemEntity(new SystemAlertSubsystemEntity(), request);
        SystemAlertSubsystemEntity duplicate = systemAlertMapper.findSubsystemByIdentity(
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getServiceName());
        if (duplicate != null && !duplicate.getId().equals(id)) {
            throw new IllegalArgumentException("关注子系统已存在");
        }
        entity.setId(id);
        systemAlertMapper.updateSubsystem(entity);
        replaceIndexPatterns(id, entity.getIndexPatterns());
        return toSubsystemResponse(requireSubsystem(id));
    }

    @Transactional
    public void deleteSubsystem(Long id) {
        requireSubsystem(id);
        systemAlertMapper.deleteIndexPatterns(id);
        if (systemAlertMapper.deleteSubsystemById(id) == 0) {
            throw new IllegalArgumentException("关注子系统不存在");
        }
    }

    public SystemAlertDashboardResponse dashboard(String businessLineCode,
                                                  String environmentCode,
                                                  String serviceName,
                                                  String level,
                                                  String eventCategory,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime,
                                                  int page,
                                                  int pageSize) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String normalizedBusinessLineCode = trimToNull(businessLineCode);
        String normalizedEnvironmentCode = trimToNull(environmentCode);
        String normalizedServiceName = trimToNull(serviceName);
        String normalizedLevel = normalizeLevel(level);
        SystemAlertEventCategory normalizedEventCategory = SystemAlertEventCategory.parseNullable(eventCategory);
        String normalizedEventCategoryValue = normalizedEventCategory == null ? null : normalizedEventCategory.name();
        List<SystemAlertSubsystemResponse> subsystems = attachIndexPatterns(systemAlertMapper.findSubsystems(
                        normalizedBusinessLineCode,
                        normalizedEnvironmentCode,
                        true,
                        null))
                .stream()
                .map(this::toSubsystemResponse)
                .toList();
        int totalCount = systemAlertMapper.countEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedEventCategoryValue,
                startTime,
                endTime);
        List<SystemAlertSubsystemSummaryResponse> summaries = systemAlertMapper.summarizeEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedEventCategoryValue,
                startTime,
                endTime);
        List<SystemAlertEventResponse> events = systemAlertMapper.findEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedEventCategoryValue,
                startTime,
                endTime,
                normalizedPageSize,
                offset);
        return new SystemAlertDashboardResponse(totalCount, normalizedPage, normalizedPageSize, subsystems, summaries, events);
    }

    private SystemAlertSubsystemEntity requireSubsystem(Long id) {
        SystemAlertSubsystemEntity entity = systemAlertMapper.findSubsystemById(id);
        if (entity == null) {
            throw new IllegalArgumentException("关注子系统不存在");
        }
        return attachIndexPatterns(List.of(entity)).getFirst();
    }

    private SystemAlertSubsystemEntity toSubsystemEntity(SystemAlertSubsystemEntity entity, SystemAlertSubsystemSaveRequest request) {
        entity.setBusinessLineCode(requireValue(request.getBusinessLineCode(), "业务线不能为空"));
        entity.setEnvironmentCode(requireValue(request.getEnvironmentCode(), "环境不能为空"));
        entity.setSubsystemName(requireValue(request.getSubsystemName(), "子系统名称不能为空"));
        entity.setServiceName(requireValue(request.getServiceName(), "服务名不能为空"));
        entity.setIndexPatterns(normalizeIndexPatterns(request.getIndexPatterns()));
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setRemark(trimToNull(request.getRemark()));
        return entity;
    }

    private SystemAlertSubsystemResponse toSubsystemResponse(SystemAlertSubsystemEntity entity) {
        return new SystemAlertSubsystemResponse(
                entity.getId(),
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getSubsystemName(),
                entity.getServiceName(),
                entity.getIndexPatterns(),
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private List<SystemAlertSubsystemEntity> attachIndexPatterns(List<SystemAlertSubsystemEntity> subsystems) {
        if (subsystems == null || subsystems.isEmpty()) {
            return List.of();
        }
        List<Long> ids = subsystems.stream().map(SystemAlertSubsystemEntity::getId).toList();
        Map<Long, List<String>> patternsBySubsystem = systemAlertMapper.findIndexPatterns(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertSubsystemIndexPatternEntity::subsystemId,
                        Collectors.mapping(SystemAlertSubsystemIndexPatternEntity::indexPattern, Collectors.toList())
                ));
        for (SystemAlertSubsystemEntity subsystem : subsystems) {
            List<String> patterns = patternsBySubsystem.getOrDefault(subsystem.getId(), List.of());
            subsystem.setIndexPatterns(patterns);
            subsystem.setIndexPatternExpression(patterns.isEmpty() ? null : String.join(",", patterns));
        }
        return subsystems;
    }

    private void replaceIndexPatterns(Long subsystemId, List<String> indexPatterns) {
        systemAlertMapper.deleteIndexPatterns(subsystemId);
        for (int index = 0; index < indexPatterns.size(); index++) {
            systemAlertMapper.insertIndexPattern(subsystemId, indexPatterns.get(index), index);
        }
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

    private String normalizeLevel(String level) {
        String normalized = trimToNull(level);
        return normalized == null ? "ERROR" : normalized.toUpperCase();
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
