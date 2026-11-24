package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardSummaryResponse;
import cn.aslight.workhub.model.ops.AccountingScenarioDimensionResponse;
import cn.aslight.workhub.model.ops.AccountingScenarioTypeResponse;
import cn.aslight.workhub.service.ops.AccountingMonitorService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountingMonitorControllerTest {

    @Test
    void dashboardShouldExposeAccountingSummary() throws Exception {
        AccountingMonitorService service = mock(AccountingMonitorService.class);
        when(service.dashboard("BL000003")).thenReturn(new AccountingDashboardResponse(
                new AccountingDashboardSummaryResponse(8, 1, 5, 1, 1, 12),
                List.of()
        ));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccountingMonitorController(service)).build();

        mockMvc.perform(get("/api/ops/accounting/dashboard")
                        .param("businessLineCode", "BL000003")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.monitorCount").value(8))
                .andExpect(jsonPath("$.data.summary.anomalyCount").value(12));
    }

    @Test
    void scenariosShouldExposeTypeCatalogWithDimensions() throws Exception {
        AccountingMonitorService service = mock(AccountingMonitorService.class);
        when(service.scenarioTypes()).thenReturn(List.of(new AccountingScenarioTypeResponse(
                "BATCH_WITHHOLD", "批量代扣", "每日定时批量发起账单扣款",
                List.of(new AccountingScenarioDimensionResponse(
                        "EXPECTED_VS_ACTUAL", "应发起 vs 实际", "应发起笔数与实际发起笔数差异超出容差时告警"))
        )));
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccountingMonitorController(service)).build();

        mockMvc.perform(get("/api/ops/accounting/scenarios").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("BATCH_WITHHOLD"))
                .andExpect(jsonPath("$.data[0].name").value("批量代扣"))
                .andExpect(jsonPath("$.data[0].dimensions[0].dimensionCode").value("EXPECTED_VS_ACTUAL"));
    }

    @Test
    void manualRunAllowsOptionalDateForServiceToValidateByScenario() throws Exception {
        AccountingMonitorService service = mock(AccountingMonitorService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccountingMonitorController(service)).build();

        mockMvc.perform(post("/api/ops/accounting/runs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 10,
                                  "requestKey": "request-1"
                                }
                                """))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(service).submitManual(
                org.mockito.ArgumentMatchers.argThat(request -> request.checkDate() == null && request.configId() == 10L),
                org.mockito.ArgumentMatchers.any());
    }
}
