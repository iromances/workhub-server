package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class SystemAlertServiceTest {

    @Test
    void createSubsystem_shouldPersistMultipleUniqueIndexPatternsInOrder() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper);
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
        SystemAlertService service = new SystemAlertService(mapper);
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
                startTime, endTime, 1, 20);

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
        assertEquals(20, mapper.lastLimit);
        assertEquals(0, mapper.lastOffset);
    }

    @Test
    void dashboard_shouldRejectUnknownEventCategory() {
        SystemAlertService service = new SystemAlertService(new FakeSystemAlertMapper());

        assertThrows(IllegalArgumentException.class, () -> service.dashboard(
                null, null, null, "ERROR", "UNKNOWN", null, null, 1, 20));
    }

    @Test
    void dashboard_shouldKeepNullCategoryForOlderClients() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper);

        service.dashboard(null, null, null, "ERROR", null, null, null, 1, 20);

        assertNull(mapper.lastEventCategory);
    }

    @Test
    void dashboard_shouldNotApplyDefaultTimeRangeWhenTimeIsEmpty() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        SystemAlertService service = new SystemAlertService(mapper);

        service.dashboard(null, null, null, "ERROR", "SYSTEM_ERROR", null, null, 1, 20);

        assertNull(mapper.lastStartTime);
        assertNull(mapper.lastEndTime);
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
        private LocalDateTime lastStartTime;
        private LocalDateTime lastEndTime;
        private int lastLimit;
        private int lastOffset;
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
                               String eventCategory, LocalDateTime startTime, LocalDateTime endTime) {
            lastServiceName = serviceName;
            lastLevel = level;
            lastEventCategory = eventCategory;
            lastStartTime = startTime;
            lastEndTime = endTime;
            return totalCount;
        }

        @Override
        public List<SystemAlertSubsystemSummaryResponse> summarizeEvents(String businessLineCode, String environmentCode,
                                                                         String serviceName, String level,
                                                                         String eventCategory,
                                                                         LocalDateTime startTime, LocalDateTime endTime) {
            return summaries;
        }

        @Override
        public List<SystemAlertEventResponse> findEvents(String businessLineCode, String environmentCode,
                                                         String serviceName, String level, String eventCategory,
                                                         LocalDateTime startTime, LocalDateTime endTime,
                                                         int limit, int offset) {
            lastLimit = limit;
            lastOffset = offset;
            return events;
        }
    }
}
