package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.model.ops.SystemAlertDashboardResponse;
import cn.aslight.workhub.model.ops.SystemAlertEventResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemResponse;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemSummaryResponse;
import cn.aslight.workhub.service.ops.SystemAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                        List.of(new SystemAlertSubsystemResponse(1L, "保费分期", "prod", "资产支付", "asset-payment", true, null,
                                LocalDateTime.of(2026, 6, 4, 8, 0), LocalDateTime.of(2026, 6, 4, 8, 0))),
                        List.of(new SystemAlertSubsystemSummaryResponse("保费分期", "prod", "资产支付", "asset-payment", 2,
                                LocalDateTime.of(2026, 6, 4, 9, 50))),
                        List.of(new SystemAlertEventResponse(11L, "保费分期", "prod", "资产支付", "asset-payment", "ERROR",
                                "NullPointerException", "支付回调失败", "java.lang.NullPointerException", "空指针",
                                "trace-1", "req-1", LocalDateTime.of(2026, 6, 4, 9, 50), "LOCAL"))
                ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SystemAlertController(systemAlertService)).build();

        mockMvc.perform(get("/api/ops/system-alerts")
                        .param("businessLineCode", "保费分期")
                        .param("environmentCode", "prod")
                        .param("serviceName", "asset-payment")
                        .param("level", "ERROR")
                        .param("startTime", "2026-06-04T09:00:00")
                        .param("endTime", "2026-06-04T10:00:00")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.subsystems[0].subsystemName").value("资产支付"))
                .andExpect(jsonPath("$.data.summaries[0].errorCount").value(2))
                .andExpect(jsonPath("$.data.events[0].message").value("支付回调失败"));
    }

    private static class StubSystemAlertService extends SystemAlertService {
        private final SystemAlertDashboardResponse response;

        private StubSystemAlertService(SystemAlertDashboardResponse response) {
            super(null, null);
            this.response = response;
        }

        @Override
        public SystemAlertDashboardResponse dashboard(String businessLineCode,
                                                      String environmentCode,
                                                      String serviceName,
                                                      String level,
                                                      LocalDateTime startTime,
                                                      LocalDateTime endTime,
                                                      int page,
                                                      int pageSize) {
            return response;
        }
    }
}
