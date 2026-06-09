package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.model.payment.PaymentProjectBindingResponse;
import cn.aslight.workhub.model.payment.PaymentPurposeOptionResponse;
import cn.aslight.workhub.service.payment.PaymentCatalogService;
import cn.aslight.workhub.service.payment.PaymentProjectBindingService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentBindingControllerTest {

    @Test
    void resolve_shouldReturnDefaultBinding() throws Exception {
        PaymentProjectBindingService bindingService = mock(PaymentProjectBindingService.class);
        PaymentCatalogService catalogService = mock(PaymentCatalogService.class);
        when(bindingService.resolve(10L, "WITHHOLD")).thenReturn(new PaymentProjectBindingResponse(
                100L,
                10L,
                "资产业务",
                "WOCHENG",
                "沃橙项目",
                8L,
                "M0001",
                "易宝主商户",
                1L,
                "YEEPAY",
                "易宝支付",
                "PROD",
                "WITHHOLD",
                List.of("WITHHOLD"),
                1,
                true,
                "ACTIVE",
                null,
                List.of(),
                LocalDateTime.of(2026, 4, 3, 12, 0),
                LocalDateTime.of(2026, 4, 3, 12, 30)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentBindingController(bindingService, catalogService)
        ).build();

        mockMvc.perform(get("/api/payment/projects/10/bindings/resolve")
                        .param("purposeCode", "WITHHOLD")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCode").value("WOCHENG"))
                .andExpect(jsonPath("$.data.merchantCode").value("M0001"))
                .andExpect(jsonPath("$.data.defaultBinding").value(true));
    }

    @Test
    void listPurposes_shouldExposePurposeCatalog() throws Exception {
        PaymentProjectBindingService bindingService = mock(PaymentProjectBindingService.class);
        PaymentCatalogService catalogService = mock(PaymentCatalogService.class);
        when(catalogService.listPurposes()).thenReturn(List.of(
                new PaymentPurposeOptionResponse("BIND_CARD", "绑卡", "银行卡签约"),
                new PaymentPurposeOptionResponse("WITHHOLD", "代收", "委托扣款")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentBindingController(bindingService, catalogService)
        ).build();

        mockMvc.perform(get("/api/payment/purposes").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("BIND_CARD"))
                .andExpect(jsonPath("$.data[1].name").value("代收"));
    }
}
