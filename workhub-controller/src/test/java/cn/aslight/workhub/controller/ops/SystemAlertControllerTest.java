package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertCleanupTaskResponse;
import cn.aslight.workhub.model.ops.SystemAlertDeleteAndFilterRequest;
import cn.aslight.workhub.model.ops.SystemAlertEventBatchDeleteResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import cn.aslight.workhub.service.ops.SystemAlertService;
import cn.aslight.workhub.service.ops.SystemAlertCleanupTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SystemAlertControllerTest {

    @Test
    void dashboard_shouldExposeLocalSystemAlertData() throws Exception {
        LocalDateTime startTime = LocalDateTime.of(2026, 6, 4, 9, 0);
        LocalDateTime endTime = LocalDateTime.of(2026, 6, 4, 10, 0);
        SystemAlertService systemAlertService = new StubSystemAlertService(new SystemAlertDashboardResponse(
                        2,
                        1,
                        20,
                        List.of(new SystemAlertSubsystemResponse(1L, "保费分期", "prod", "资产支付", "asset-payment",
                                List.of("asset-payment-*", "asset-payment-history-*"), true, null,
                                LocalDateTime.of(2026, 6, 4, 8, 0), LocalDateTime.of(2026, 6, 4, 8, 0))),
                        List.of(new SystemAlertSubsystemSummaryResponse("保费分期", "prod", "资产支付", "asset-payment", 2,
                                LocalDateTime.of(2026, 6, 4, 9, 50))),
                        List.of(new SystemAlertEventResponse(11L, "保费分期", "prod", "资产支付", "asset-payment", "ERROR",
                                "SYSTEM_ERROR", "NullPointerException", "支付回调失败", "java.lang.NullPointerException", "空指针",
                                "trace-1", "req-1", LocalDateTime.of(2026, 6, 4, 9, 50), "LOCAL"))
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SystemAlertController(systemAlertService, null, null, null)).build();

        mockMvc.perform(get("/api/ops/system-alerts")
                        .param("businessLineCode", "保费分期")
                        .param("environmentCode", "prod")
                        .param("serviceName", "asset-payment")
                        .param("level", "ERROR")
                        .param("eventCategory", "SYSTEM_ERROR")
                        .param("messageKeyword", "回调失败")
                        .param("startTime", "2026-06-04T09:00:00")
                        .param("endTime", "2026-06-04T10:00:00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.subsystems[0].subsystemName").value("资产支付"))
                .andExpect(jsonPath("$.data.summaries[0].errorCount").value(2))
                .andExpect(jsonPath("$.data.events[0].eventCategory").value("SYSTEM_ERROR"))
                .andExpect(jsonPath("$.data.events[0].message").value("支付回调失败"));
    }

    @Test
    void batchDeleteEvents_shouldReturnDeletedCount() throws Exception {
        StubSystemAlertService service = new StubSystemAlertService(null);
        service.deletedCount = 2;
        service.deletedNotificationCount = 3;
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SystemAlertController(service, null, null, null)).build();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("admin", "N/A", List.of());

        mockMvc.perform(post("/api/ops/system-alerts/events/batch-delete")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[11,12]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deletedCount").value(2))
                .andExpect(jsonPath("$.data.deletedNotificationCount").value(3));

        org.junit.jupiter.api.Assertions.assertEquals(List.of(11L, 12L), service.deletedIds);
        org.junit.jupiter.api.Assertions.assertEquals("admin", service.operator);
    }

    @Test
    void submitDeleteAndFilterTask_shouldReturnIndependentTask() throws Exception {
        StubCleanupTaskService cleanupService = new StubCleanupTaskService();
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new SystemAlertController(null, cleanupService, null, null)).build();
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated("admin", "N/A", List.of());

        mockMvc.perform(post("/api/ops/system-alerts/events/delete-and-filter-tasks")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"messageKeyword":"Insert Person Time","businessLineCode":"BL000004",
                                 "environmentCode":"prod","serviceName":"amp-order","level":"ERROR",
                                 "eventCategory":"SYSTEM_ERROR"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(27))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.messageKeyword").value("Insert Person Time"));

        org.junit.jupiter.api.Assertions.assertEquals("Insert Person Time",
                cleanupService.request.getMessageKeyword());
        org.junit.jupiter.api.Assertions.assertEquals("admin", cleanupService.operator);
    }

    private static class StubSystemAlertService extends SystemAlertService {
        private final SystemAlertDashboardResponse response;
        private int deletedCount;
        private int deletedNotificationCount;
        private List<Long> deletedIds = List.of();
        private String operator;

        private StubSystemAlertService(SystemAlertDashboardResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
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
            return response;
        }

        @Override
        public SystemAlertEventBatchDeleteResponse deleteEvents(List<Long> ids, String operator, String ip) {
            this.deletedIds = ids;
            this.operator = operator;
            return new SystemAlertEventBatchDeleteResponse(deletedCount, deletedNotificationCount);
        }
    }

    private static class StubCleanupTaskService extends SystemAlertCleanupTaskService {
        private SystemAlertDeleteAndFilterRequest request;
        private String operator;

        private StubCleanupTaskService() {
            super(null, null, new SyncTaskExecutor());
        }

        @Override
        public SystemAlertCleanupTaskResponse submit(SystemAlertDeleteAndFilterRequest request,
                                                     String operator,
                                                     String ip) {
            this.request = request;
            this.operator = operator;
            return new SystemAlertCleanupTaskResponse(
                    27L, "SACT-27", request.getMessageKeyword(), request.getBusinessLineCode(),
                    request.getEnvironmentCode(), request.getServiceName(), request.getLevel(),
                    request.getEventCategory(), request.getStartTime(), request.getEndTime(),
                    "PENDING", null, 0L, 0L, 0L, 0L, null,
                    LocalDateTime.of(2026, 8, 25, 0, 0), null, null,
                    LocalDateTime.of(2026, 8, 25, 0, 0));
        }
    }
}
