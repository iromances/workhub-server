package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.model.payment.PaymentChannelDetailResponse;
import cn.aslight.workhub.model.payment.PaymentChannelSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentChannelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentChannelControllerTest {

    @Test
    void list_shouldExposeChannelItems() throws Exception {
        PaymentChannelService paymentChannelService = mock(PaymentChannelService.class);
        when(paymentChannelService.list(eq(1L), eq("ACTIVE"))).thenReturn(List.of(
                new PaymentChannelSummaryResponse(1L, "YEEPAY", "易宝支付", "易宝", "ACTIVE")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentChannelController(paymentChannelService)).build();

        mockMvc.perform(get("/api/payment/channels")
                        .param("channelId", "1")
                        .param("status", "ACTIVE")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].code").value("YEEPAY"))
                .andExpect(jsonPath("$.data.items[0].name").value("易宝支付"));
    }

    @Test
    void create_shouldReturnCreatedChannel() throws Exception {
        PaymentChannelService paymentChannelService = mock(PaymentChannelService.class);
        when(paymentChannelService.create(any(), eq("admin"))).thenReturn(new PaymentChannelDetailResponse(
                2L,
                "WECHAT_PAY",
                "微信支付",
                "腾讯",
                "ACTIVE",
                "主支付渠道",
                LocalDateTime.of(2026, 4, 3, 12, 0),
                LocalDateTime.of(2026, 4, 3, 12, 0)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new PaymentChannelController(paymentChannelService)).build();

        mockMvc.perform(post("/api/payment/channels")
                        .principal(new UsernamePasswordAuthenticationToken("admin", "N/A"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"WECHAT_PAY",
                                  "name":"微信支付",
                                  "vendorName":"腾讯",
                                  "status":"ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("WECHAT_PAY"))
                .andExpect(jsonPath("$.data.vendorName").value("腾讯"));
    }
}
