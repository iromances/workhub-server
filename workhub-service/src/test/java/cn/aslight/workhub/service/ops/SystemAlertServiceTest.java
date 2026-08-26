package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertCleanupEventReference;
import cn.aslight.workhub.model.ops.SystemAlertEventBatchDeleteResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventNotificationReference;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertNotificationCandidate;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import cn.aslight.workhub.service.system.SystemAuditService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemAlertServiceTest {

    @Test
    void createSubsystem_shouldPersistMultipleUniqueIndexPatternsInOrder() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());
        SystemAlertSubsystemSaveRequest request = new SystemAlertSubsystemSaveRequest();
        request.setBusinessLineCode("BL000001");
        request.setEnvironmentCode("prod");
        request.setSubsystemName("保费系统");
        request.setServiceName("amp-saps");
        request.setIndexPatterns(List.of("amp-saps-*", "amp-saps-history-*", "amp-saps-*"));
        request.setEnabled(true);

        var response = service.createSubsystem(request);

        assertEquals(List.of("amp-saps-*", "amp-saps-history-*"), response.indexPatterns());
        assertEquals(List.of("amp-saps-*", "amp-saps-history-*"),
                mapper.indexRows.stream()
                        .filter(row -> row.subsystemId().equals(7L))
                        .map(SystemAlertSubsystemIndexPatternEntity::indexPattern)
                        .toList());
    }

    @Test
    void dashboard_shouldQueryLocalErrorEventsForStandaloneSubsystems() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 4, 9, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 6, 4, 10, 0);
        mapper.subsystems = List.of(subsystem());
        mapper.totalCount = 2;
        mapper.summaries = List.of(
                new SystemAlertSubsystemSummaryResponse("保费分期", "prod", "资产支付", "asset-payment", 2, LocalDateTime.of(2026, 6, 4, 9, 50))
        );
        mapper.events = List.of(
                new SystemAlertEventResponse(11L, "保费分期", "prod", "资产支付", "asset-payment", "ERROR",
                        "SYSTEM_ERROR", "NullPointerException", "支付回调失败", "java.lang.NullPointerException", "空指针",
                        "trace-1", "req-1", LocalDateTime.of(2026, 6, 4, 9, 50), "LOCAL")
        );

        SystemAlertDashboardResponse response = service.dashboard(
                "保费分期", "prod", "asset-payment", "ERROR", "slow_sql",
                " 回调失败 ", startTime, endTime, 1, 20);

        assertEquals(1, response.subsystems().size());
        assertEquals(2, response.totalCount());
        assertEquals("资产支付", response.subsystems().getFirst().subsystemName());
        assertEquals("支付回调失败", response.events().getFirst().message());
        assertEquals(List.of("asset-payment-*", "asset-payment-history-*"),
                response.subsystems().getFirst().indexPatterns());
        assertEquals("保费分期", mapper.lastBusinessLineCode);
        assertEquals("prod", mapper.lastEnvironmentCode);
        assertEquals("asset-payment", mapper.lastServiceName);
        assertEquals("ERROR", mapper.lastLevel);
        assertEquals("SLOW_SQL", mapper.lastEventCategory);
        assertEquals("回调失败", mapper.lastMessageKeyword);
        assertEquals(20, mapper.lastLimit);
        assertEquals(0, mapper.lastOffset);
    }

    @Test
    void dashboard_shouldRejectUnknownEventCategory() {
        SystemAlertService service = new SystemAlertService(new FakeSystemAlertMapper(), new RecordingAuditService());

        assertThrows(IllegalArgumentException.class, () -> service.dashboard(
                null, null, null, "ERROR", "UNKNOWN", null, null, null, 1, 20));
    }

    @Test
    void dashboard_shouldRejectOverlongMessageKeyword() {
        SystemAlertService service = new SystemAlertService(new FakeSystemAlertMapper(), new RecordingAuditService());

        assertThrows(IllegalArgumentException.class, () -> service.dashboard(
                null, null, null, "ERROR", "SYSTEM_ERROR", "x".repeat(201),
                null, null, 1, 20));
    }

    @Test
    void dashboard_shouldKeepNullCategoryForOlderClients() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());

        service.dashboard(null, null, null, "ERROR", null, null, null, null, 1, 20);

        assertNull(mapper.lastEventCategory);
    }

    @Test
    void dashboard_shouldNotApplyDefaultTimeRangeWhenTimeIsEmpty() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());

        service.dashboard(null, null, null, "ERROR", "SYSTEM_ERROR", null, null, null, 1, 20);

        assertNull(mapper.lastStartTime);
        assertNull(mapper.lastEndTime);
    }

    @Test
    void dashboard_shouldAllowOneThousandRowsPerPage() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());

        service.dashboard(null, null, null, "ERROR", "SYSTEM_ERROR", null, null, null, 1, 1_000);

        assertEquals(1_000, mapper.lastLimit);
    }

    @Test
    void deleteEvents_shouldDeduplicateIdsAndWriteAudit() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        mapper.notificationReferences = List.of(
                notificationReference("BL000001", "prod", "source-11"),
                notificationReference("BL000001", "prod", "source-12"));
        mapper.notificationCandidates = List.of(
                notificationCandidate(101L, "BL000001", "prod", "ELK:BATCH:1:source-11"),
                notificationCandidate(102L, "BL000001", "prod", "ELK:SCOPE:BATCH:2:asset-payment:source-12"),
                notificationCandidate(103L, "BL000001", "prod", "ELK:BATCH:1:other-source"));
        RecordingAuditService auditService = new RecordingAuditService();
        SystemAlertService service = new SystemAlertService(mapper, auditService);

        SystemAlertEventBatchDeleteResponse result = service.deleteEvents(
                List.of(11L, 12L, 11L), "admin", "127.0.0.1");

        assertEquals(2, result.deletedCount());
        assertEquals(2, result.deletedNotificationCount());
        assertEquals(List.of(11L, 12L), mapper.deletedEventIds);
        assertEquals(List.of(101L, 102L), mapper.deletedNotificationIds);
        assertEquals(List.of("notifications", "events"), mapper.deleteOrder);
        assertEquals("admin", auditService.operator);
        assertEquals("ops:system-alert:delete", auditService.permissionCode);
        assertEquals("选中事件数：2，事件ID样例：[11, 12]", auditService.beforeSnapshot);
        assertEquals("物理删除系统预警事件2条，关联站内通知2条", auditService.afterSnapshot);
    }

    @Test
    void deleteEvents_shouldDeleteTwoHundredEventsAndNotificationsByPrimaryKey() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        List<Long> ids = LongStream.rangeClosed(1, 200).boxed().toList();
        mapper.notificationReferences = ids.stream()
                .map(id -> notificationReference("BL000001", "prod", "source-" + id))
                .toList();
        mapper.notificationCandidates = ids.stream()
                .map(id -> notificationCandidate(
                        id + 1_000, "BL000001", "prod", "ELK:BATCH:1:source-" + id))
                .toList();
        RecordingAuditService auditService = new RecordingAuditService();
        SystemAlertService service = new SystemAlertService(mapper, auditService);

        SystemAlertEventBatchDeleteResponse result = service.deleteEvents(ids, "admin", "127.0.0.1");

        assertEquals(200, result.deletedCount());
        assertEquals(200, result.deletedNotificationCount());
        assertEquals(ids, mapper.deletedEventIds);
        assertEquals(ids.stream().map(id -> id + 1_000).toList(), mapper.deletedNotificationIds);
        org.junit.jupiter.api.Assertions.assertTrue(auditService.beforeSnapshot.endsWith("其余ID省略"));
    }

    @Test
    void deleteEvents_shouldKeepNotificationWithSameSourceIdInAnotherEnvironment() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        mapper.notificationReferences = List.of(
                notificationReference("BL000001", "prod", "same-source"));
        mapper.notificationCandidates = List.of(
                notificationCandidate(101L, "BL000001", "prod", "ELK:BATCH:1:same-source"),
                notificationCandidate(102L, "BL000001", "test", "ELK:BATCH:1:same-source"));
        SystemAlertService service = new SystemAlertService(mapper, new RecordingAuditService());

        SystemAlertEventBatchDeleteResponse result = service.deleteEvents(
                List.of(11L), "admin", "127.0.0.1");

        assertEquals(1, result.deletedNotificationCount());
        assertEquals(List.of(101L), mapper.deletedNotificationIds);
    }

    private SystemAlertEventNotificationReference notificationReference(
            String businessLineCode,
            String environmentCode,
            String sourceEventId) {
        return new SystemAlertEventNotificationReference(
                businessLineCode, environmentCode, sourceEventId);
    }

    private SystemAlertNotificationCandidate notificationCandidate(
            Long id,
            String businessLineCode,
            String environmentCode,
            String dedupeKey) {
        return new SystemAlertNotificationCandidate(
                id, businessLineCode, environmentCode, dedupeKey);
    }

    private SystemAlertSubsystemEntity subsystem() {
        SystemAlertSubsystemEntity entity = new SystemAlertSubsystemEntity();
        entity.setId(1L);
        entity.setBusinessLineCode("保费分期");
        entity.setEnvironmentCode("prod");
        entity.setSubsystemName("资产支付");
        entity.setServiceName("asset-payment");
        entity.setEnabled(true);
        return entity;
    }

    private static class FakeSystemAlertMapper implements SystemAlertMapper {
        private List<SystemAlertSubsystemEntity> subsystems = List.of();
        private List<SystemAlertSubsystemSummaryResponse> summaries = List.of();
        private List<SystemAlertEventResponse> events = List.of();
        private int totalCount;
        private String lastBusinessLineCode;
        private String lastEnvironmentCode;
        private String lastServiceName;
        private String lastLevel;
        private String lastEventCategory;
        private String lastMessageKeyword;
        private LocalDateTime lastStartTime;
        private LocalDateTime lastEndTime;
        private int lastLimit;
        private int lastOffset;
        private List<SystemAlertEventNotificationReference> notificationReferences = List.of();
        private List<SystemAlertNotificationCandidate> notificationCandidates = List.of();
        private final List<Long> deletedEventIds = new ArrayList<>();
        private final List<Long> deletedNotificationIds = new ArrayList<>();
        private final List<String> deleteOrder = new ArrayList<>();
        private SystemAlertSubsystemEntity storedSubsystem;
        private final List<SystemAlertSubsystemIndexPatternEntity> indexRows = new ArrayList<>(List.of(
                new SystemAlertSubsystemIndexPatternEntity(1L, 1L, "asset-payment-*", 0),
                new SystemAlertSubsystemIndexPatternEntity(2L, 1L, "asset-payment-history-*", 1)
        ));

        @Override
        public List<SystemAlertSubsystemEntity> findSubsystems(String businessLineCode, String environmentCode, boolean enabledOnly, String keyword) {
            lastBusinessLineCode = businessLineCode;
            lastEnvironmentCode = environmentCode;
            return subsystems;
        }

        @Override
        public SystemAlertSubsystemEntity findSubsystemById(Long id) {
            return storedSubsystem != null && id.equals(storedSubsystem.getId()) ? storedSubsystem : null;
        }

        @Override
        public SystemAlertSubsystemEntity findSubsystemByIdentity(String businessLineCode, String environmentCode, String serviceName) {
            return null;
        }

        @Override
        public List<SystemAlertSubsystemIndexPatternEntity> findIndexPatterns(List<Long> subsystemIds) {
            return indexRows.stream().filter(row -> subsystemIds.contains(row.subsystemId())).toList();
        }

        @Override
        public int insertSubsystem(SystemAlertSubsystemEntity entity) {
            entity.setId(7L);
            storedSubsystem = entity;
            return 1;
        }

        @Override
        public int insertIndexPattern(Long subsystemId, String indexPattern, int sortOrder) {
            indexRows.add(new SystemAlertSubsystemIndexPatternEntity(
                    (long) indexRows.size() + 1, subsystemId, indexPattern, sortOrder));
            return 1;
        }

        @Override
        public int updateSubsystem(SystemAlertSubsystemEntity entity) {
            return 0;
        }

        @Override
        public int deleteIndexPatterns(Long subsystemId) {
            int before = indexRows.size();
            indexRows.removeIf(row -> subsystemId.equals(row.subsystemId()));
            return before - indexRows.size();
        }

        @Override
        public int deleteSubsystemById(Long id) {
            return 0;
        }

        @Override
        public int countEvents(String businessLineCode, String environmentCode, String serviceName, String level,
                               String eventCategory, String messageKeyword,
                               LocalDateTime startTime, LocalDateTime endTime) {
            lastServiceName = serviceName;
            lastLevel = level;
            lastEventCategory = eventCategory;
            lastMessageKeyword = messageKeyword;
            lastStartTime = startTime;
            lastEndTime = endTime;
            return totalCount;
        }

        @Override
        public List<SystemAlertSubsystemSummaryResponse> summarizeEvents(String businessLineCode, String environmentCode,
                                                                         String serviceName, String level,
                                                                         String eventCategory,
                                                                         String messageKeyword,
                                                                         LocalDateTime startTime, LocalDateTime endTime) {
            return summaries;
        }

        @Override
        public List<SystemAlertEventResponse> findEvents(String businessLineCode, String environmentCode,
                                                         String serviceName, String level, String eventCategory,
                                                         String messageKeyword,
                                                         LocalDateTime startTime, LocalDateTime endTime,
                                                         int limit, int offset) {
            lastLimit = limit;
            lastOffset = offset;
            return events;
        }

        @Override
        public long findMaxEventId() {
            return 0;
        }

        @Override
        public List<SystemAlertCleanupEventReference> findCleanupEventBatch(
                String businessLineCode, String environmentCode, String serviceName, String level,
                String eventCategory, String messageKeyword, LocalDateTime startTime, LocalDateTime endTime,
                long processedEventId, long maxEventId, int limit) {
            return List.of();
        }

        @Override
        public int deleteEventsByIds(List<Long> ids) {
            deleteOrder.add("events");
            deletedEventIds.addAll(ids);
            return ids.size();
        }

        @Override
        public List<SystemAlertEventNotificationReference> findEventNotificationReferences(List<Long> ids) {
            return notificationReferences;
        }

        @Override
        public List<SystemAlertNotificationCandidate> findNotificationCandidatesByEventIds(List<Long> ids) {
            return notificationCandidates;
        }

        @Override
        public int deleteNotificationsByIds(List<Long> ids) {
            deleteOrder.add("notifications");
            deletedNotificationIds.addAll(ids);
            return ids.size();
        }
    }

    private static class RecordingAuditService extends SystemAuditService {
        private String operator;
        private String permissionCode;
        private String beforeSnapshot;
        private String afterSnapshot;

        private RecordingAuditService() {
            super(null);
        }

        @Override
        public void operation(String operator,
                              String permissionCode,
                              String actionType,
                              String targetType,
                              String targetId,
                              String beforeSnapshot,
                              String afterSnapshot,
                              String result,
                              String errorMessage,
                              String ip) {
            this.operator = operator;
            this.permissionCode = permissionCode;
            this.beforeSnapshot = beforeSnapshot;
            this.afterSnapshot = afterSnapshot;
        }
    }
}
