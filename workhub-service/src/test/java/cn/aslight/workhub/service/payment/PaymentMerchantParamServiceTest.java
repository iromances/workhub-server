package cn.aslight.workhub.service.payment;

import cn.aslight.workhub.dao.payment.PaymentMerchantMapper;
import cn.aslight.workhub.dao.payment.PaymentMerchantParamMapper;
import cn.aslight.workhub.model.payment.PaymentMerchantEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamEntity;
import cn.aslight.workhub.model.payment.PaymentMerchantParamFileUploadRequest;
import cn.aslight.workhub.model.payment.PaymentMerchantParamSaveRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentMerchantParamServiceTest {

    @Test
    void delete_shouldRemoveParamAndRecordAudit() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantParamMapper paramMapper = mock(PaymentMerchantParamMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantParamService service = new PaymentMerchantParamService(
                merchantMapper,
                paramMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantParamEntity existing = new PaymentMerchantParamEntity();
        existing.setId(11L);
        existing.setMerchantId(8L);
        existing.setParamKey("notifyUrl");
        when(paramMapper.findEntityById(11L)).thenReturn(existing);
        when(paramMapper.deleteByIdAndMerchantId(11L, 8L)).thenReturn(1);
        when(paramMapper.findByMerchantId(8L)).thenReturn(List.of());

        service.delete(8L, 11L, "admin");

        verify(paramMapper).deleteByIdAndMerchantId(11L, 8L);
        verify(auditService).record(
                "MERCHANT_PARAM",
                11L,
                "DELETE",
                "删除商户参数",
                "merchantId=8,paramKey=notifyUrl",
                "admin"
        );
    }

    @Test
    void update_shouldKeepExistingValueWhenParamValueIsBlank() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantParamMapper paramMapper = mock(PaymentMerchantParamMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantParamService service = new PaymentMerchantParamService(
                merchantMapper,
                paramMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantParamEntity existing = new PaymentMerchantParamEntity();
        existing.setId(11L);
        existing.setMerchantId(8L);
        existing.setParamKey("notifyUrl");
        existing.setValueType("URL");
        existing.setSensitiveFlag(false);
        existing.setPlainValue("https://old/callback");
        existing.setMaskedValue("https://old/callback");
        when(paramMapper.findEntityById(11L)).thenReturn(existing);
        when(paramMapper.findByMerchantId(8L)).thenReturn(List.of());

        PaymentMerchantParamSaveRequest request = new PaymentMerchantParamSaveRequest();
        request.setValueType("TEXT");
        request.setSensitive(true);
        request.setParamValue(" ");
        request.setRemark("新备注");

        service.update(8L, 11L, request, "admin");

        ArgumentCaptor<PaymentMerchantParamEntity> captor = ArgumentCaptor.forClass(PaymentMerchantParamEntity.class);
        verify(paramMapper).update(captor.capture());
        PaymentMerchantParamEntity saved = captor.getValue();
        assertEquals("TEXT", saved.getValueType());
        assertFalse(saved.getSensitiveFlag());
        assertEquals("https://old/callback", saved.getPlainValue());
        assertEquals("https://old/callback", saved.getMaskedValue());
        assertEquals("新备注", saved.getRemark());
        verify(cryptoService, never()).encrypt(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void update_shouldAllowChangingParamKey() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantParamMapper paramMapper = mock(PaymentMerchantParamMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantParamService service = new PaymentMerchantParamService(
                merchantMapper,
                paramMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantParamEntity existing = new PaymentMerchantParamEntity();
        existing.setId(11L);
        existing.setMerchantId(8L);
        existing.setParamKey("notifyUrl");
        existing.setValueType("TEXT");
        existing.setSensitiveFlag(false);
        existing.setPlainValue("https://old/callback");
        existing.setMaskedValue("https://old/callback");
        when(paramMapper.findEntityById(11L)).thenReturn(existing);
        when(paramMapper.findByMerchantId(8L)).thenReturn(List.of());

        PaymentMerchantParamSaveRequest request = new PaymentMerchantParamSaveRequest();
        request.setParamKey("callbackUrl");
        request.setValueType("TEXT");
        request.setParamValue(" ");

        service.update(8L, 11L, request, "admin");

        ArgumentCaptor<PaymentMerchantParamEntity> captor = ArgumentCaptor.forClass(PaymentMerchantParamEntity.class);
        verify(paramMapper).update(captor.capture());
        assertEquals("callbackUrl", captor.getValue().getParamKey());
        verify(auditService).record(
                "MERCHANT_PARAM",
                11L,
                "UPDATE",
                "更新商户参数",
                "merchantId=8,paramKey=notifyUrl,newParamKey=callbackUrl",
                "admin"
        );
    }

    @Test
    void update_shouldRequireValueWhenChangingFileParamToText() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantParamMapper paramMapper = mock(PaymentMerchantParamMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantParamService service = new PaymentMerchantParamService(
                merchantMapper,
                paramMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantParamEntity existing = new PaymentMerchantParamEntity();
        existing.setId(11L);
        existing.setMerchantId(8L);
        existing.setParamKey("merchantPrivateKey");
        existing.setValueType("FILE");
        existing.setSourceType("FILE");
        existing.setSensitiveFlag(true);
        when(paramMapper.findEntityById(11L)).thenReturn(existing);

        PaymentMerchantParamSaveRequest request = new PaymentMerchantParamSaveRequest();
        request.setParamKey("merchantPrivateKey");
        request.setValueType("TEXT");
        request.setParamValue(" ");

        assertThrows(IllegalArgumentException.class, () -> service.update(8L, 11L, request, "admin"));
        verify(paramMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateFromFile_shouldRequireFileWhenChangingTextParamToFile() {
        PaymentMerchantMapper merchantMapper = mock(PaymentMerchantMapper.class);
        PaymentMerchantParamMapper paramMapper = mock(PaymentMerchantParamMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        PaymentAuditService auditService = mock(PaymentAuditService.class);
        PaymentMerchantParamService service = new PaymentMerchantParamService(
                merchantMapper,
                paramMapper,
                cryptoService,
                auditService
        );

        PaymentMerchantEntity merchant = new PaymentMerchantEntity();
        merchant.setId(8L);
        when(merchantMapper.findEntityById(8L)).thenReturn(merchant);

        PaymentMerchantParamEntity existing = new PaymentMerchantParamEntity();
        existing.setId(11L);
        existing.setMerchantId(8L);
        existing.setParamKey("notifyUrl");
        existing.setValueType("TEXT");
        existing.setSourceType("TEXT");
        existing.setSensitiveFlag(false);
        when(paramMapper.findEntityById(11L)).thenReturn(existing);

        PaymentMerchantParamFileUploadRequest request = new PaymentMerchantParamFileUploadRequest();
        request.setParamKey("notifyUrl");
        request.setValueType("FILE");

        assertThrows(IllegalArgumentException.class, () -> service.updateFromFile(8L, 11L, request, null, "admin"));
        verify(paramMapper, never()).update(org.mockito.ArgumentMatchers.any());
    }
}
