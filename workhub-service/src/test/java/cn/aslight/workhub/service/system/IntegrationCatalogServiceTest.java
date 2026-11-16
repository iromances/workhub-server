package cn.aslight.workhub.service.system;

import cn.aslight.workhub.dao.system.IntegrationCapabilityMapper;
import cn.aslight.workhub.dao.system.IntegrationSystemMapper;
import cn.aslight.workhub.dao.system.SysConfigMapper;
import cn.aslight.workhub.model.system.IntegrationCapabilityItem;
import cn.aslight.workhub.model.system.IntegrationSystemDetailResponse;
import cn.aslight.workhub.model.system.IntegrationSystemEntity;
import cn.aslight.workhub.model.system.IntegrationSystemSaveRequest;
import cn.aslight.workhub.model.system.SysConfigItemEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntegrationCatalogServiceTest {

    @Test
    void createRejectsDuplicateSystemCode() {
        var mapper = mock(IntegrationSystemMapper.class);
        when(mapper.findByCode("SONARQUBE")).thenReturn(new IntegrationSystemEntity());
        var service = newService(mapper, mock(IntegrationCapabilityMapper.class), mock(SysConfigMapper.class));
        assertThrows(IllegalArgumentException.class, () -> service.create(request("SONARQUBE"), "sh", null));
    }

    @Test
    void createRejectsInvalidEnumAndSystemCode() {
        var service = newService();
        var badMethod = request("SONARQUBE");
        badMethod.setIntegrationMethod("WEBHOOK");
        assertThrows(IllegalArgumentException.class, () -> service.create(badMethod, "sh", null));

        var badCode = request("sonar");
        assertThrows(IllegalArgumentException.class, () -> service.create(badCode, "sh", null));
    }

    @Test
    void createSavesSystemAndCapabilitiesWithAudit() {
        var mapper = mock(IntegrationSystemMapper.class);
        var capabilityMapper = mock(IntegrationCapabilityMapper.class);
        var sysConfigMapper = mock(SysConfigMapper.class);
        var audit = mock(SystemAuditService.class);
        var entity = entity(1L, "SONARQUBE");
        when(mapper.insert(any(IntegrationSystemEntity.class))).thenAnswer(invocation -> {
            invocation.<IntegrationSystemEntity>getArgument(0).setId(1L);
            return 1;
        });
        when(mapper.findById(1L)).thenReturn(entity);
        when(capabilityMapper.findBySystemId(1L)).thenReturn(List.of());
        when(sysConfigMapper.findAll(null, null)).thenReturn(List.of());
        var service = new IntegrationCatalogService(mapper, capabilityMapper, sysConfigMapper, audit);

        service.create(request("SONARQUBE"), "sh", "127.0.0.1");

        var systemCaptor = ArgumentCaptor.forClass(IntegrationSystemEntity.class);
        verify(mapper).insert(systemCaptor.capture());
        assertEquals("SONARQUBE", systemCaptor.getValue().getSystemCode());
        assertEquals("sonar.global", systemCaptor.getValue().getConfigGroups());
        var capabilityCaptor = ArgumentCaptor.forClass(cn.aslight.workhub.model.system.IntegrationCapabilityEntity.class);
        verify(capabilityMapper, org.mockito.Mockito.times(2)).insert(capabilityCaptor.capture());
        assertEquals(List.of("scan-project", "query-issue"),
                capabilityCaptor.getAllValues().stream().map(cn.aslight.workhub.model.system.IntegrationCapabilityEntity::getCapabilityCode).toList());
        verify(audit).operation(anyString(), anyString(), anyString(), anyString(), anyString(), any(), anyString(), anyString(), any(), any());
    }

    @Test
    void updateKeepsSystemCodeAndReplacesCapabilities() {
        var mapper = mock(IntegrationSystemMapper.class);
        var capabilityMapper = mock(IntegrationCapabilityMapper.class);
        var sysConfigMapper = mock(SysConfigMapper.class);
        when(mapper.findById(2L)).thenReturn(entity(2L, "ZENTAO"));
        when(capabilityMapper.findBySystemId(2L)).thenReturn(List.of());
        when(sysConfigMapper.findAll(null, null)).thenReturn(List.of());
        var service = new IntegrationCatalogService(mapper, capabilityMapper, sysConfigMapper, mock(SystemAuditService.class));

        var request = request("CHANGED_CODE");
        request.setCapabilities(List.of(capability("scan-project"), capability("scan-project")));
        assertThrows(IllegalArgumentException.class, () -> service.update(2L, request, "sh", null));

        request.setCapabilities(List.of(capability("scan-project")));
        service.update(2L, request, "sh", null);

        var systemCaptor = ArgumentCaptor.forClass(IntegrationSystemEntity.class);
        verify(mapper).update(systemCaptor.capture());
        assertEquals("ZENTAO", systemCaptor.getValue().getSystemCode());
        verify(capabilityMapper).deleteBySystemId(2L);
        verify(capabilityMapper).insert(any(cn.aslight.workhub.model.system.IntegrationCapabilityEntity.class));
    }

    @Test
    void detailAggregatesConfigGroupStatusesWithoutValues() {
        var mapper = mock(IntegrationSystemMapper.class);
        var capabilityMapper = mock(IntegrationCapabilityMapper.class);
        var sysConfigMapper = mock(SysConfigMapper.class);
        var entity = entity(3L, "GITLAB");
        entity.setConfigGroups("gitlab.global,gitlab.");
        when(mapper.findById(3L)).thenReturn(entity);
        when(capabilityMapper.findBySystemId(3L)).thenReturn(List.of());
        when(sysConfigMapper.findAll(null, null)).thenReturn(List.of(
                configItem("gitlab.global", "webApiUrl", "TEXT", true),
                secretItem("gitlab.global", "accessToken", true),
                secretItem("gitlab.global", "sshPassword", false),
                configItem("gitlab.BL000001", "repoUrl", "TEXT", true)
        ));
        var service = new IntegrationCatalogService(mapper, capabilityMapper, sysConfigMapper, mock(SystemAuditService.class));

        IntegrationSystemDetailResponse detail = service.detail(3L);

        assertEquals(List.of("gitlab.global", "gitlab."), detail.getConfigGroups());
        var statuses = detail.getConfigGroupStatuses();
        assertEquals(2, statuses.size());
        var global = statuses.get(0);
        assertEquals("gitlab.global", global.getGroup());
        assertEquals(3, global.getItemCount());
        assertEquals(2, global.getEnabledCount());
        assertTrue(global.isSecretConfigured());
        assertFalse(global.isPrefixGroup());
        var prefix = statuses.get(1);
        assertEquals("gitlab.", prefix.getGroup());
        assertEquals(4, prefix.getItemCount());
        assertTrue(prefix.isPrefixGroup());
        assertTrue(prefix.isSecretConfigured());
    }

    @Test
    void detailRejectsMissingSystem() {
        var service = newService();
        assertThrows(IllegalArgumentException.class, () -> service.detail(404L));
    }

    private static IntegrationCatalogService newService() {
        return newService(mock(IntegrationSystemMapper.class), mock(IntegrationCapabilityMapper.class), mock(SysConfigMapper.class));
    }

    private static IntegrationCatalogService newService(IntegrationSystemMapper mapper,
                                                        IntegrationCapabilityMapper capabilityMapper,
                                                        SysConfigMapper sysConfigMapper) {
        return new IntegrationCatalogService(mapper, capabilityMapper, sysConfigMapper, mock(SystemAuditService.class));
    }

    private static IntegrationSystemSaveRequest request(String systemCode) {
        var request = new IntegrationSystemSaveRequest();
        request.setSystemCode(systemCode);
        request.setSystemName("SonarQube");
        request.setIntegrationMethod("REST_API");
        request.setAuthType("PAT");
        request.setConfigGroups(List.of("sonar.global"));
        request.setCapabilities(List.of(capability("scan-project"), capability("query-issue")));
        return request;
    }

    private static IntegrationCapabilityItem capability(String code) {
        var item = new IntegrationCapabilityItem();
        item.setCapabilityCode(code);
        item.setCapabilityName("能力 " + code);
        item.setCapabilityType("READ");
        item.setImplStatus("ACTIVE");
        return item;
    }

    private static IntegrationSystemEntity entity(Long id, String code) {
        var entity = new IntegrationSystemEntity();
        entity.setId(id);
        entity.setSystemCode(code);
        entity.setSystemName(code);
        entity.setIntegrationMethod("REST_API");
        entity.setAuthType("PAT");
        entity.setConfigGroups("sonar.global");
        entity.setEnabled(true);
        entity.setSortOrder(0);
        return entity;
    }

    private static SysConfigItemEntity configItem(String group, String key, String type, boolean enabled) {
        var entity = new SysConfigItemEntity();
        entity.setConfigGroup(group);
        entity.setConfigKey(key);
        entity.setValueType(type);
        entity.setEnabled(enabled);
        return entity;
    }

    private static SysConfigItemEntity secretItem(String group, String key, boolean enabled) {
        var entity = configItem(group, key, "SECRET", enabled);
        entity.setEncryptedValue("cipher");
        return entity;
    }
}
