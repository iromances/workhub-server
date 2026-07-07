package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantCredentialMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantCredentialSaveRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMerchantCredentialServiceTest {

    @Test
    void update_shouldKeepExistingSecretValueWhenCredentialValueIsBlank() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantCredentialMapper credentialMapper = mock(PaymentMerchantCredentialMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantCredentialService service = new PaymentMerchantCredentialService(
                merchantMapper,
                credentialMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantCredentialEntity existing = new PaymentMerchantCredentialEntity();
        existing.setId(31L);
        existing.setMerchantId(8L);
        existing.setCredentialKey("tradePassword");
        existing.setCredentialName("交易密码");
        existing.setCredentialType("TRADE_PASSWORD");
        existing.setEncryptedValue("old-cipher");
        existing.setMaskedValue("********1234");
        existing.setFingerprint("old-fingerprint");
        existing.setStatus("ACTIVE");
        when(credentialMapper.findEntityById(31L)).thenReturn(existing);
        when(credentialMapper.findByMerchantId(8L)).thenReturn(List.of());

        PaymentMerchantCredentialSaveRequest request = new PaymentMerchantCredentialSaveRequest();
        request.setCredentialName("交易密码2");
        request.setCredentialType("PASSWORD");
        request.setCredentialValue("");
        request.setStatus("INACTIVE");
        request.setRemark("停用");

        service.update(8L, 31L, request, "admin");

        ArgumentCaptor<PaymentMerchantCredentialEntity> captor = ArgumentCaptor.forClass(PaymentMerchantCredentialEntity.class);
        verify(credentialMapper).update(captor.capture());
        PaymentMerchantCredentialEntity saved = captor.getValue();
        assertEquals("交易密码2", saved.getCredentialName());
        assertEquals("PASSWORD", saved.getCredentialType());
        assertEquals("old-cipher", saved.getEncryptedValue());
        assertEquals("********1234", saved.getMaskedValue());
        assertEquals("old-fingerprint", saved.getFingerprint());
        assertEquals("INACTIVE", saved.getStatus());
        assertEquals("停用", saved.getRemark());
        verify(cryptoService, never()).encrypt(org.mockito.ArgumentMatchers.anyString());
    }
}
