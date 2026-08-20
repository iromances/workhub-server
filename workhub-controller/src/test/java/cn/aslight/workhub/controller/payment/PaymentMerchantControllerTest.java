package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.model.payment.PaymentMerchantDetailResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentSecretDownloadResponse;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentMerchantCredentialService;
import cn.aslight.workhub.service.payment.PaymentMerchantParamService;
import cn.aslight.workhub.service.payment.PaymentMerchantService;
import cn.aslight.workhub.service.payment.PaymentSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentMerchantControllerTest {

    @Test
    void detail_shouldExposeMaskedParamsAndSecrets() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(merchantService.detail(8L)).thenReturn(new PaymentMerchantDetailResponse(
                8L,
                1L,
                "YEEPAY",
                "易宝支付",
                "M0001",
                "易宝主商户",
                "PROD",
                "某某保理",
                "ACTIVE",
                "主代收商户",
                List.of("WITHHOLD"),
                List.of(new PaymentMerchantParamResponse(
                        11L,
                        "notifyUrl",
                        "URL",
                        "TEXT",
                        null,
                        null,
                        null,
                        false,
                        "https://demo/callback",
                        null,
                        LocalDateTime.of(2026, 4, 3, 12, 0),
                        LocalDateTime.of(2026, 4, 3, 12, 0)
                )),
                List.of(new PaymentSecretSummaryResponse(
                        21L,
                        "merchant-private-key",
                        "PRIVATE_KEY",
                        3,
                        "********ABCD",
                        "fingerprint",
                        "AES/GCM/NoPadding",
                        "ACTIVE",
                        LocalDateTime.of(2026, 4, 3, 0, 0),
                        null,
                        null,
                        LocalDateTime.of(2026, 4, 3, 12, 0)
                )),
                List.of(new PaymentMerchantCredentialResponse(
                        31L,
                        "tradePassword",
                        "交易密码",
                        "TRADE_PASSWORD",
                        "********1234",
                        "fingerprint",
                        "ACTIVE",
                        null,
                        LocalDateTime.of(2026, 4, 3, 12, 0),
                        LocalDateTime.of(2026, 4, 3, 12, 0)
                )),
                LocalDateTime.of(2026, 4, 3, 12, 0),
                LocalDateTime.of(2026, 4, 3, 12, 30)
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();

        mockMvc.perform(get("/api/payment/merchants/8").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.channelCode").value("YEEPAY"))
                .andExpect(jsonPath("$.data.parameters[0].paramKey").value("notifyUrl"))
                .andExpect(jsonPath("$.data.credentials[0].credentialType").value("TRADE_PASSWORD"))
                .andExpect(jsonPath("$.data.secrets[0].maskedValue").value("********ABCD"))
                .andExpect(jsonPath("$.data.secrets[0].versionNo").value(3));
    }

    @Test
    void list_shouldSupportProjectBusinessLineAndPurposeFilter() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(merchantService.list(eq("ACTIVE"), eq(1L), eq(10L), eq("BL000001"), eq("WITHHOLD"), eq("M0001"))).thenReturn(List.of(
                new PaymentMerchantSummaryResponse(8L, 1L, "YEEPAY", "易宝支付", "M0001", "易宝主商户", "PROD", List.of("WITHHOLD"), "ACTIVE", "生产主商户")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();

        mockMvc.perform(get("/api/payment/merchants")
                        .param("status", "ACTIVE")
                        .param("channelId", "1")
                        .param("projectId", "10")
                        .param("businessLine", "BL000001")
                        .param("purposeCode", "WITHHOLD")
                        .param("keyword", "M0001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].merchantCode").value("M0001"))
                .andExpect(jsonPath("$.data.items[0].purposeCodes[0]").value("WITHHOLD"))
                .andExpect(jsonPath("$.data.items[0].environment").value("PROD"))
                .andExpect(jsonPath("$.data.items[0].remark").value("生产主商户"));
    }

    @Test
    void deleteParam_shouldCallService() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(paramService.delete(8L, 11L, "admin")).thenReturn(List.of());

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();

        mockMvc.perform(delete("/api/payment/merchants/8/params/11")
                        .principal(() -> "admin"))
                .andExpect(status().isOk());

        verify(paramService).delete(8L, 11L, "admin");
    }

    @Test
    void downloadSecretFile_shouldReturnAttachment() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(secretService.downloadFile(8L, 21L)).thenReturn(new PaymentSecretDownloadResponse(
                "merchant.p12",
                "application/x-pkcs12",
                new byte[]{0x01, 0x02}
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();

        mockMvc.perform(get("/api/payment/merchants/8/secrets/21/file"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename*=UTF-8''merchant.p12"))
                .andExpect(header().string("Content-Type", "application/x-pkcs12"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{0x01, 0x02}));
    }
}
