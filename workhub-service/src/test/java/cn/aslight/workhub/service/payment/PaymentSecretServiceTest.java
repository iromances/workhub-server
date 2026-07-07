package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentSecretMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentSecretEntity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentSecretServiceTest {

    @Test
    void downloadFile_shouldRestoreBinaryUploadContent() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentSecretMapper secretMapper = mock(PaymentSecretMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentSecretService service = new PaymentSecretService(merchantMapper, secretMapper, cryptoService, auditService);

        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(13L);
        when(merchantMapper.findEntityById(13L)).thenReturn(merchantEntity);
        byte[] bytes = new byte[]{0x01, 0x02, 0x03, 0x04};
        String base64Value = Base64.getEncoder().encodeToString(bytes);
        PaymentSecretEntity secretEntity = new PaymentSecretEntity();
        secretEntity.setId(31L);
        secretEntity.setMerchantId(13L);
        secretEntity.setEncryptedValue("cipher-binary");
        secretEntity.setSourceType("FILE");
        secretEntity.setFileName("merchant.p12");
        secretEntity.setFileContentType("application/x-pkcs12");
        secretEntity.setFileValueType("BINARY");
        when(secretMapper.findEntityById(31L)).thenReturn(secretEntity);
        when(cryptoService.decrypt("cipher-binary")).thenReturn(base64Value);

        var response = service.downloadFile(13L, 31L);

        assertEquals("merchant.p12", response.fileName());
        assertEquals("application/x-pkcs12", response.contentType());
        assertArrayEquals(bytes, response.content());
    }

    @Test
    void downloadFile_shouldFallbackToTextWhenLegacyBinaryValueIsNotBase64() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentSecretMapper secretMapper = mock(PaymentSecretMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentSecretService service = new PaymentSecretService(merchantMapper, secretMapper, cryptoService, auditService);

        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(13L);
        when(merchantMapper.findEntityById(13L)).thenReturn(merchantEntity);
        PaymentSecretEntity secretEntity = new PaymentSecretEntity();
        secretEntity.setId(32L);
        secretEntity.setMerchantId(13L);
        secretEntity.setEncryptedValue("cipher-legacy");
        secretEntity.setSourceType("FILE");
        secretEntity.setSecretName("legacy-cert");
        secretEntity.setFileValueType("BINARY");
        when(secretMapper.findEntityById(32L)).thenReturn(secretEntity);
        when(cryptoService.decrypt("cipher-legacy")).thenReturn("-----BEGIN CERTIFICATE-----");

        var response = service.downloadFile(13L, 32L);

        assertEquals("legacy-cert", response.fileName());
        assertEquals("application/octet-stream", response.contentType());
        assertArrayEquals("-----BEGIN CERTIFICATE-----".getBytes(StandardCharsets.UTF_8), response.content());
    }
}
