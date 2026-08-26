package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventCategory;
import cn.aslight.workhub.model.ops.SystemAlertEventBatchDeleteResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventNotificationReference;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertNotificationCandidate;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SystemAlertService {

    private static final Pattern INDEX_PATTERN = Pattern.compile("[A-Za-z0-9*._-]+");
    private static final int DELETE_BATCH_SIZE = 500;
    private final SystemAlertMapper systemAlertMapper;
    private final SystemAuditService auditService;

    public SystemAlertService(SystemAlertMapper systemAlertMapper,
                              SystemAuditService auditService) {
        this.systemAlertMapper = systemAlertMapper;
        this.auditService = auditService;
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
                                                  String messageKeyword,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime,
                                                  int page,
                                                  int pageSize) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 1_000);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String normalizedBusinessLineCode = trimToNull(businessLineCode);
        String normalizedEnvironmentCode = trimToNull(environmentCode);
        String normalizedServiceName = trimToNull(serviceName);
        String normalizedLevel = normalizeLevel(level);
        String normalizedMessageKeyword = trimToNull(messageKeyword);
        if (normalizedMessageKeyword != null && normalizedMessageKeyword.length() > 200) {
            throw new IllegalArgumentException("错误消息关键词不能超过200个字符");
        }
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
                normalizedMessageKeyword,
                startTime,
                endTime);
        List<SystemAlertSubsystemSummaryResponse> summaries = systemAlertMapper.summarizeEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedEventCategoryValue,
                normalizedMessageKeyword,
                startTime,
                endTime);
        List<SystemAlertEventResponse> events = systemAlertMapper.findEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedEventCategoryValue,
                normalizedMessageKeyword,
                startTime,
                endTime,
                normalizedPageSize,
                offset);
        return new SystemAlertDashboardResponse(totalCount, normalizedPage, normalizedPageSize, subsystems, summaries, events);
    }

    @Transactional
    public SystemAlertEventBatchDeleteResponse deleteEvents(List<Long> ids, String operator, String ip) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("请选择需要删除的系统预警事件");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("事件ID必须大于0");
        }
        List<Long> normalizedIds = ids.stream().distinct().toList();
        List<SystemAlertEventNotificationReference> references =
                findEventNotificationReferences(normalizedIds);
        List<Long> notificationIds = findRelatedNotificationIds(normalizedIds, references);
        int deletedNotificationCount = deleteNotificationsByIds(notificationIds);
        int deletedCount = deleteEventsByIds(normalizedIds);
        auditService.operation(
                operator,
                "ops:system-alert:delete",
                "BATCH_DELETE",
                "OPS_SYSTEM_ALERT_EVENT",
                normalizedIds.size() == 1 ? String.valueOf(normalizedIds.getFirst()) : "batch:" + normalizedIds.size(),
                deleteAuditSnapshot(normalizedIds),
                "物理删除系统预警事件" + deletedCount + "条，关联站内通知" + deletedNotificationCount + "条",
                "SUCCESS",
                null,
                ip
        );
        return new SystemAlertEventBatchDeleteResponse(deletedCount, deletedNotificationCount);
    }

    private List<SystemAlertEventNotificationReference> findEventNotificationReferences(
            List<Long> eventIds) {
        ArrayList<SystemAlertEventNotificationReference> references = new ArrayList<>();
        for (int start = 0; start < eventIds.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, eventIds.size());
            references.addAll(systemAlertMapper.findEventNotificationReferences(
                    eventIds.subList(start, end)));
        }
        return references;
    }

    private List<Long> findRelatedNotificationIds(
            List<Long> eventIds,
            List<SystemAlertEventNotificationReference> references) {
        if (references.isEmpty()) {
            return List.of();
        }
        Map<NotificationScope, Set<String>> sourceEventIdsByScope = references.stream()
                .collect(Collectors.groupingBy(
                        reference -> new NotificationScope(
                                reference.businessLineCode(), reference.environmentCode()),
                        Collectors.mapping(
                                SystemAlertEventNotificationReference::sourceEventId,
                                Collectors.toSet())));
        ArrayList<SystemAlertNotificationCandidate> candidates = new ArrayList<>();
        for (int start = 0; start < eventIds.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, eventIds.size());
            candidates.addAll(systemAlertMapper.findNotificationCandidatesByEventIds(
                    eventIds.subList(start, end)));
        }
        return candidates.stream()
                .filter(candidate -> isRelatedNotification(candidate, sourceEventIdsByScope))
                .map(SystemAlertNotificationCandidate::id)
                .distinct()
                .toList();
    }

    private boolean isRelatedNotification(
            SystemAlertNotificationCandidate candidate,
            Map<NotificationScope, Set<String>> sourceEventIdsByScope) {
        Set<String> sourceEventIds = sourceEventIdsByScope.get(
                new NotificationScope(candidate.businessLineCode(), candidate.environmentCode()));
        if (sourceEventIds == null || candidate.dedupeKey() == null) {
            return false;
        }
        int separatorIndex = candidate.dedupeKey().lastIndexOf(':');
        return separatorIndex >= 0
                && sourceEventIds.contains(candidate.dedupeKey().substring(separatorIndex + 1));
    }

    private int deleteNotificationsByIds(List<Long> notificationIds) {
        int deletedCount = 0;
        for (int start = 0; start < notificationIds.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, notificationIds.size());
            deletedCount += systemAlertMapper.deleteNotificationsByIds(
                    notificationIds.subList(start, end));
        }
        return deletedCount;
    }

    private int deleteEventsByIds(List<Long> eventIds) {
        int deletedCount = 0;
        for (int start = 0; start < eventIds.size(); start += DELETE_BATCH_SIZE) {
            int end = Math.min(start + DELETE_BATCH_SIZE, eventIds.size());
            deletedCount += systemAlertMapper.deleteEventsByIds(eventIds.subList(start, end));
        }
        return deletedCount;
    }

    private record NotificationScope(String businessLineCode, String environmentCode) {
    }

    private String deleteAuditSnapshot(List<Long> ids) {
        int sampleSize = Math.min(ids.size(), 20);
        String suffix = ids.size() > sampleSize ? "，其余ID省略" : "";
        return "选中事件数：" + ids.size() + "，事件ID样例：" + ids.subList(0, sampleSize) + suffix;
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
