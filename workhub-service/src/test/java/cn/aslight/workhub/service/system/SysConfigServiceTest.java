package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import cn.aslight.workhub.model.system.SysConfigSaveRequest;
import cn.aslight.workhub.service.payment.PaymentCryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SysConfigServiceTest {

    @Test
    void shouldReadEnabledGroupValuesAndDecryptSecretsInMemory() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        SysConfigItemEntity baseUrl = textEntity("baseUrl", "http://elasticsearch:9200", true);
        SysConfigItemEntity password = secretEntity();
        SysConfigItemEntity disabled = textEntity("pageSize", "500", false);
        when(mapper.findAll("elk.alert", null)).thenReturn(List.of(baseUrl, password, disabled));
        when(cryptoService.decrypt("old-cipher")).thenReturn("plain-password");

        Map<String, String> values = new SysConfigService(mapper, cryptoService)
                .findPlainValues("elk.alert");

        assertEquals("http://elasticsearch:9200", values.get("baseUrl"));
        assertEquals("plain-password", values.get("password"));
        assertFalse(values.containsKey("pageSize"));
    }

    @Test
    void shouldAuditConfigChangeWithoutPlainSecret() {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        PaymentCryptoService cryptoService = mock(PaymentCryptoService.class);
        SystemAuditService auditService = mock(SystemAuditService.class);
        SysConfigItemEntity existing = secretEntity();
        when(mapper.findById(7L)).thenReturn(existing);
        when(cryptoService.encrypt("new-password")).thenReturn("new-cipher");
        when(cryptoService.mask("new-password")).thenReturn("******word");

        SysConfigService service = new SysConfigService(mapper, cryptoService, auditService);
        service.update(7L, request("new-password"), "admin", "127.0.0.1");

        ArgumentCaptor<String> before = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> after = ArgumentCaptor.forClass(String.class);
        verify(auditService).operation(
                eq("admin"), eq("system:config:manage"), eq("UPDATE"),
                eq("SYS_CONFIG_ITEM"), eq("7"), before.capture(), after.capture(),
                eq("SUCCESS"), eq(null), eq("127.0.0.1"));
        assertFalse(before.getValue().contains("old-cipher"));
        assertFalse(after.getValue().contains("new-password"));
        assertFalse(after.getValue().contains("new-cipher"));
    }

    private SysConfigItemEntity secretEntity() {
        SysConfigItemEntity entity = new SysConfigItemEntity();
        entity.setId(7L);
        entity.setConfigGroup("elk.alert");
        entity.setConfigKey("password");
        entity.setConfigName("ELK密码");
        entity.setValueType("SECRET");
        entity.setEncryptedValue("old-cipher");
        entity.setMaskedValue("******word");
        entity.setEnabled(true);
        return entity;
    }

    private SysConfigItemEntity textEntity(String key, String value, boolean enabled) {
        SysConfigItemEntity entity = new SysConfigItemEntity();
        entity.setConfigGroup("elk.alert");
        entity.setConfigKey(key);
        entity.setConfigName(key);
        entity.setValueType("TEXT");
        entity.setPlainValue(value);
        entity.setEnabled(enabled);
        return entity;
    }

    private SysConfigSaveRequest request(String value) {
        SysConfigSaveRequest request = new SysConfigSaveRequest();
        request.setConfigGroup("elk.alert");
        request.setConfigKey("password");
        request.setConfigName("ELK密码");
        request.setValueType("SECRET");
        request.setValue(value);
        request.setEnabled(true);
        return request;
    }
}
