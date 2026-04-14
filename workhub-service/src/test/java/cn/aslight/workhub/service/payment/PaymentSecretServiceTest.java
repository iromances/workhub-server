package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentSecretMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentSecretEntity;
import cn.aslight.workhub.model.payment.PaymentSecretSaveRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSecretServiceTest {

    @Test
    void create_shouldRotateVersionAndDeactivateOldSecretWhenActivateNow() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentSecretMapper secretMapper = mock(PaymentSecretMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentSecretService service = new PaymentSecretService(merchantMapper, secretMapper, cryptoService, auditService);

        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(9L);
        when(merchantMapper.findEntityById(9L)).thenReturn(merchantEntity);
        when(secretMapper.findMaxVersion(9L, "merchant-private-key")).thenReturn(2);
        when(cryptoService.encrypt("secret-content")).thenReturn("cipher");
        when(cryptoService.mask("secret-content")).thenReturn("******tent");
        when(cryptoService.fingerprint("secret-content")).thenReturn("fingerprint");
        when(cryptoService.algorithm()).thenReturn("AES/GCM/NoPadding");

        PaymentSecretSaveRequest request = new PaymentSecretSaveRequest();
        request.setSecretName("merchant-private-key");
        request.setSecretType("PRIVATE_KEY");
        request.setSecretValue("secret-content");

        service.create(9L, request, "admin");

        ArgumentCaptor<PaymentSecretEntity> captor = ArgumentCaptor.forClass(PaymentSecretEntity.class);
        verify(secretMapper).deactivateActiveVersions(9L, "merchant-private-key");
        verify(secretMapper).insert(captor.capture());
        PaymentSecretEntity saved = captor.getValue();
        assertEquals(3, saved.getVersionNo());
        assertEquals("ACTIVE", saved.getStatus());
        assertEquals("cipher", saved.getEncryptedValue());
        assertEquals("fingerprint", saved.getFingerprint());
    }

    @Test
    void create_shouldKeepInactiveWhenActivateNowFalse() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentSecretMapper secretMapper = mock(PaymentSecretMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentSecretService service = new PaymentSecretService(merchantMapper, secretMapper, cryptoService, auditService);

        PaymentMerchantEntity merchantEntity = new PaymentMerchantEntity();
        merchantEntity.setId(11L);
        when(merchantMapper.findEntityById(11L)).thenReturn(merchantEntity);
        when(secretMapper.findMaxVersion(11L, "notify-secret")).thenReturn(0);
        when(cryptoService.encrypt(any())).thenReturn("cipher");
        when(cryptoService.mask(any())).thenReturn("****");
        when(cryptoService.fingerprint(any())).thenReturn("fingerprint");
        when(cryptoService.algorithm()).thenReturn("AES/GCM/NoPadding");

        PaymentSecretSaveRequest request = new PaymentSecretSaveRequest();
        request.setSecretName("notify-secret");
        request.setSecretType("SIGN_SECRET");
        request.setSecretValue("raw");
        request.setActivateNow(false);

        service.create(11L, request, "admin");

        ArgumentCaptor<PaymentSecretEntity> captor = ArgumentCaptor.forClass(PaymentSecretEntity.class);
        verify(secretMapper, never()).deactivateActiveVersions(any(), any());
        verify(secretMapper).insert(captor.capture());
        assertEquals("INACTIVE", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getVersionNo());
    }
}
