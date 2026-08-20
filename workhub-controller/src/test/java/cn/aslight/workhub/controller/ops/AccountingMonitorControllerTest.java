package cn.aslight.workhub.controller.ops;

import cn.aslight.workhub.model.ops.AccountingDashboardResponse;
import cn.aslight.workhub.model.ops.AccountingDashboardSummaryResponse;
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
    void manualRunShouldRejectMissingTimeWindow() throws Exception {
        AccountingMonitorService service = mock(AccountingMonitorService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AccountingMonitorController(service)).build();

        mockMvc.perform(post("/api/ops/accounting/runs/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configId": 10,
                                  "ruleCodes": ["TX_INVALID_AMOUNT"],
                                  "requestKey": "request-1"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
