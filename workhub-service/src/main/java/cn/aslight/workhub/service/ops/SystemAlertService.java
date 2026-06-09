package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SystemAlertService {

    private final SystemAlertMapper systemAlertMapper;
    private final SystemAlertSchemaInitializer schemaInitializer;

    public SystemAlertService(SystemAlertMapper systemAlertMapper,
                              SystemAlertSchemaInitializer schemaInitializer) {
        this.systemAlertMapper = systemAlertMapper;
        this.schemaInitializer = schemaInitializer;
    }

    public List<SystemAlertSubsystemResponse> listSubsystems(String businessLineCode,
                                                             String environmentCode,
                                                             boolean enabledOnly,
                                                             String keyword) {
        schemaInitializer.ensureInitialized();
        return systemAlertMapper.findSubsystems(
                        trimToNull(businessLineCode),
                        trimToNull(environmentCode),
                        enabledOnly,
                        trimToNull(keyword))
                .stream()
                .map(this::toSubsystemResponse)
                .toList();
    }

    @Transactional
    public SystemAlertSubsystemResponse createSubsystem(SystemAlertSubsystemSaveRequest request) {
        schemaInitializer.ensureInitialized();
        SystemAlertSubsystemEntity entity = toSubsystemEntity(new SystemAlertSubsystemEntity(), request);
        SystemAlertSubsystemEntity duplicate = systemAlertMapper.findSubsystemByIdentity(
                entity.getBusinessLineCode(),
                entity.getEnvironmentCode(),
                entity.getServiceName());
        if (duplicate != null) {
            throw new IllegalArgumentException("关注子系统已存在");
        }
        systemAlertMapper.insertSubsystem(entity);
        return toSubsystemResponse(requireSubsystem(entity.getId()));
    }

    @Transactional
    public SystemAlertSubsystemResponse updateSubsystem(Long id, SystemAlertSubsystemSaveRequest request) {
        schemaInitializer.ensureInitialized();
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
        return toSubsystemResponse(requireSubsystem(id));
    }

    @Transactional
    public void deleteSubsystem(Long id) {
        schemaInitializer.ensureInitialized();
        if (systemAlertMapper.deleteSubsystemById(id) == 0) {
            throw new IllegalArgumentException("关注子系统不存在");
        }
    }

    public SystemAlertDashboardResponse dashboard(String businessLineCode,
                                                  String environmentCode,
                                                  String serviceName,
                                                  String level,
                                                  LocalDateTime startTime,
                                                  LocalDateTime endTime,
                                                  int page,
                                                  int pageSize) {
        schemaInitializer.ensureInitialized();
        LocalDateTime normalizedEndTime = endTime == null ? LocalDateTime.now() : endTime;
        LocalDateTime normalizedStartTime = startTime == null ? normalizedEndTime.minusHours(1) : startTime;
        if (normalizedStartTime.isAfter(normalizedEndTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        int normalizedPage = Math.max(page, 1);
        int normalizedPageSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (normalizedPage - 1) * normalizedPageSize;
        String normalizedBusinessLineCode = trimToNull(businessLineCode);
        String normalizedEnvironmentCode = trimToNull(environmentCode);
        String normalizedServiceName = trimToNull(serviceName);
        String normalizedLevel = normalizeLevel(level);
        List<SystemAlertSubsystemResponse> subsystems = systemAlertMapper.findSubsystems(
                        normalizedBusinessLineCode,
                        normalizedEnvironmentCode,
                        true,
                        null)
                .stream()
                .map(this::toSubsystemResponse)
                .toList();
        int totalCount = systemAlertMapper.countEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedStartTime,
                normalizedEndTime);
        List<SystemAlertSubsystemSummaryResponse> summaries = systemAlertMapper.summarizeEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedStartTime,
                normalizedEndTime);
        List<SystemAlertEventResponse> events = systemAlertMapper.findEvents(
                normalizedBusinessLineCode,
                normalizedEnvironmentCode,
                normalizedServiceName,
                normalizedLevel,
                normalizedStartTime,
                normalizedEndTime,
                normalizedPageSize,
                offset);
        return new SystemAlertDashboardResponse(totalCount, normalizedPage, normalizedPageSize, subsystems, summaries, events);
    }

    private SystemAlertSubsystemEntity requireSubsystem(Long id) {
        SystemAlertSubsystemEntity entity = systemAlertMapper.findSubsystemById(id);
        if (entity == null) {
            throw new IllegalArgumentException("关注子系统不存在");
        }
        return entity;
    }

    private SystemAlertSubsystemEntity toSubsystemEntity(SystemAlertSubsystemEntity entity, SystemAlertSubsystemSaveRequest request) {
        entity.setBusinessLineCode(requireValue(request.getBusinessLineCode(), "业务线不能为空"));
        entity.setEnvironmentCode(requireValue(request.getEnvironmentCode(), "环境不能为空"));
        entity.setSubsystemName(requireValue(request.getSubsystemName(), "子系统名称不能为空"));
        entity.setServiceName(requireValue(request.getServiceName(), "服务名不能为空"));
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
                entity.getEnabled(),
                entity.getRemark(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
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
