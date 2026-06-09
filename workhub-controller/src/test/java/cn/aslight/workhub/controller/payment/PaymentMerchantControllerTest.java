package cn.aslight.workhub.controller.payment;

import cn.aslight.workhub.model.payment.PaymentMerchantDetailResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantParamResponse;
import cn.aslight.workhub.model.payment.PaymentSecretFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentSecretSummaryResponse;
import cn.aslight.workhub.model.payment.PaymentMerchantSummaryResponse;
import cn.aslight.workhub.service.payment.PaymentMerchantCredentialService;
import cn.aslight.workhub.service.payment.PaymentMerchantParamService;
import cn.aslight.workhub.service.payment.PaymentMerchantService;
import cn.aslight.workhub.service.payment.PaymentSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
                "app-prod",
                "某某保理",
                "ACTIVE",
                "主代收商户",
                List.of("WITHHOLD"),
                List.of(new PaymentMerchantParamResponse(
                        11L,
                        "notifyUrl",
                        "URL",
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
    void list_shouldSupportProjectAndPurposeFilter() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(merchantService.list(eq("ACTIVE"), eq(1L), eq(10L), eq("WITHHOLD"), eq("M0001"))).thenReturn(List.of(
                new PaymentMerchantSummaryResponse(8L, 1L, "YEEPAY", "易宝支付", "M0001", "易宝主商户", "PROD", "app-prod", List.of("WITHHOLD"), "ACTIVE")
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();

        mockMvc.perform(get("/api/payment/merchants")
                        .param("status", "ACTIVE")
                        .param("channelId", "1")
                        .param("projectId", "10")
                        .param("purposeCode", "WITHHOLD")
                        .param("keyword", "M0001")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].merchantCode").value("M0001"))
                .andExpect(jsonPath("$.data.items[0].purposeCodes[0]").value("WITHHOLD"))
                .andExpect(jsonPath("$.data.items[0].environment").value("PROD"));
    }

    @Test
    void createSecretFromFile_shouldAcceptMultipartFileAndMetadata() throws Exception {
        PaymentMerchantService merchantService = mock(PaymentMerchantService.class);
        PaymentMerchantParamService paramService = mock(PaymentMerchantParamService.class);
        PaymentMerchantCredentialService credentialService = mock(PaymentMerchantCredentialService.class);
        PaymentSecretService secretService = mock(PaymentSecretService.class);
        when(secretService.createFromFile(eq(8L), any(PaymentSecretFileUploadRequest.class), any(), eq("system"))).thenReturn(List.of(
                new PaymentSecretSummaryResponse(
                        21L,
                        "merchant-cert",
                        "CERTIFICATE",
                        2,
                        "********",
                        "fingerprint",
                        "AES/GCM/NoPadding",
                        "ACTIVE",
                        null,
                        null,
                        "生产证书",
                        LocalDateTime.of(2026, 4, 3, 12, 0)
                )
        ));

        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new PaymentMerchantController(merchantService, paramService, credentialService, secretService)
        ).build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "merchant.p12",
                "application/x-pkcs12",
                new byte[]{0x01, 0x02}
        );

        mockMvc.perform(multipart("/api/payment/merchants/8/secrets/file")
                        .file(file)
                        .param("secretName", "merchant-cert")
                        .param("secretType", "CERTIFICATE")
                        .param("fileValueType", "BINARY")
                        .param("activateNow", "true")
                        .param("remark", "生产证书")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].secretName").value("merchant-cert"))
                .andExpect(jsonPath("$.data[0].versionNo").value(2));

        verify(secretService).createFromFile(eq(8L), any(PaymentSecretFileUploadRequest.class), any(), eq("system"));
    }
}
