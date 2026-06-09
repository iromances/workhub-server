package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertMapper;
import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SystemAlertServiceTest {

    @Test
    void dashboard_shouldQueryLocalErrorEventsForStandaloneSubsystems() {
        FakeSystemAlertMapper mapper = new FakeSystemAlertMapper();
        FakeSystemAlertSchemaInitializer schemaInitializer = new FakeSystemAlertSchemaInitializer();
        SystemAlertService service = new SystemAlertService(mapper, schemaInitializer);
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 4, 9, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 6, 4, 10, 0);
        mapper.subsystems = List.of(subsystem());
        mapper.totalCount = 2;
        mapper.summaries = List.of(
                new SystemAlertSubsystemSummaryResponse("保费分期", "prod", "资产支付", "asset-payment", 2, LocalDateTime.of(2026, 6, 4, 9, 50))
        );
        mapper.events = List.of(
                new SystemAlertEventResponse(11L, "保费分期", "prod", "资产支付", "asset-payment", "ERROR",
                        "NullPointerException", "支付回调失败", "java.lang.NullPointerException", "空指针",
                        "trace-1", "req-1", LocalDateTime.of(2026, 6, 4, 9, 50), "LOCAL")
        );

        SystemAlertDashboardResponse response = service.dashboard("保费分期", "prod", "asset-payment", "ERROR", startTime, endTime, 1, 20);

        assertEquals(1, response.subsystems().size());
        assertEquals(2, response.totalCount());
        assertEquals("资产支付", response.subsystems().getFirst().subsystemName());
        assertEquals("支付回调失败", response.events().getFirst().message());
        assertEquals(1, schemaInitializer.ensureCount);
        assertEquals("保费分期", mapper.lastBusinessLineCode);
        assertEquals("prod", mapper.lastEnvironmentCode);
        assertEquals("asset-payment", mapper.lastServiceName);
        assertEquals("ERROR", mapper.lastLevel);
        assertEquals(20, mapper.lastLimit);
        assertEquals(0, mapper.lastOffset);
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

    private static class FakeSystemAlertSchemaInitializer extends SystemAlertSchemaInitializer {
        private int ensureCount;

        private FakeSystemAlertSchemaInitializer() {
            super(null);
        }

        @Override
        public void ensureInitialized() {
            ensureCount++;
        }
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
        private int lastLimit;
        private int lastOffset;

        @Override
        public List<SystemAlertSubsystemEntity> findSubsystems(String businessLineCode, String environmentCode, boolean enabledOnly, String keyword) {
            lastBusinessLineCode = businessLineCode;
            lastEnvironmentCode = environmentCode;
            return subsystems;
        }

        @Override
        public SystemAlertSubsystemEntity findSubsystemById(Long id) {
            return null;
        }

        @Override
        public SystemAlertSubsystemEntity findSubsystemByIdentity(String businessLineCode, String environmentCode, String serviceName) {
            return null;
        }

        @Override
        public int insertSubsystem(SystemAlertSubsystemEntity entity) {
            return 0;
        }

        @Override
        public int updateSubsystem(SystemAlertSubsystemEntity entity) {
            return 0;
        }

        @Override
        public int deleteSubsystemById(Long id) {
            return 0;
        }

        @Override
        public int countEvents(String businessLineCode, String environmentCode, String serviceName, String level, LocalDateTime startTime, LocalDateTime endTime) {
            lastServiceName = serviceName;
            lastLevel = level;
            return totalCount;
        }

        @Override
        public List<SystemAlertSubsystemSummaryResponse> summarizeEvents(String businessLineCode, String environmentCode, String serviceName, String level, LocalDateTime startTime, LocalDateTime endTime) {
            return summaries;
        }

        @Override
        public List<SystemAlertEventResponse> findEvents(String businessLineCode, String environmentCode, String serviceName, String level, LocalDateTime startTime, LocalDateTime endTime, int limit, int offset) {
            lastLimit = limit;
            lastOffset = offset;
            return events;
        }
    }
}
