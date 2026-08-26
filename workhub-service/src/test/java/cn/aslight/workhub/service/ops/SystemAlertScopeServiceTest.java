package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.ops.SystemAlertScopeMapper;
import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeSaveRequest;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceRequest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SystemAlertScopeServiceTest {

    @Test
    void allModeShouldAllowEmptyServiceList() {
        FakeMapper mapper = new FakeMapper();
        SystemAlertScopeService service = new SystemAlertScopeService(mapper);
        SystemAlertScopeSaveRequest request = request("ALL");

        var response = service.create(request);

        assertEquals("ALL", response.watchMode());
        assertEquals(List.of(), response.services());
        assertEquals(List.of("amp-*", "amp-history-*"), response.indexPatterns());
    }

    @Test
    void selectedModeShouldRequireEnabledService() {
        SystemAlertScopeService service = new SystemAlertScopeService(new FakeMapper());
        SystemAlertScopeSaveRequest request = request("SELECTED");
        SystemAlertScopeServiceRequest disabled = new SystemAlertScopeServiceRequest();
        disabled.setSubsystemName("资产支付");
        disabled.setServiceName("asset-payment");
        disabled.setEnabled(false);
        request.setServices(List.of(disabled));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(request));

        assertEquals("指定子系统模式下至少配置一个启用子系统", error.getMessage());
    }

    @Test
    void updateShouldReplaceChildrenAndResetCursor() {
        FakeMapper mapper = new FakeMapper();
        SystemAlertScopeService service = new SystemAlertScopeService(mapper);
        service.create(request("ALL"));
        SystemAlertScopeSaveRequest update = request("SELECTED");
        SystemAlertScopeServiceRequest selected = new SystemAlertScopeServiceRequest();
        selected.setSubsystemName("资产支付");
        selected.setServiceName("asset-payment");
        selected.setEnabled(true);
        update.setServices(List.of(selected));

        var response = service.update(7L, update);

        assertEquals("SELECTED", response.watchMode());
        assertEquals(List.of("asset-payment"), response.services().stream()
                .map(item -> item.serviceName()).toList());
        assertEquals(1, mapper.syncStateDeleteCount);
    }

    private SystemAlertScopeSaveRequest request(String mode) {
        SystemAlertScopeSaveRequest request = new SystemAlertScopeSaveRequest();
        request.setBusinessLineCode("BL000001");
        request.setEnvironmentCode("prod");
        request.setWatchMode(mode);
        request.setIndexPatterns(List.of("amp-*", "amp-history-*", "amp-*"));
        request.setEnabled(true);
        return request;
    }

    private static class FakeMapper implements SystemAlertScopeMapper {
        private SystemAlertScopeEntity stored;
        private final List<SystemAlertScopeServiceEntity> services = new ArrayList<>();
        private final List<SystemAlertScopeIndexEntity> indexes = new ArrayList<>();
        private int syncStateDeleteCount;

        @Override public List<SystemAlertScopeEntity> findScopes(String businessLineCode, String environmentCode,
                                                                 boolean enabledOnly) {
            return stored == null ? List.of() : List.of(stored);
        }
        @Override public SystemAlertScopeEntity findById(Long id) {
            return stored != null && id.equals(stored.getId()) ? stored : null;
        }
        @Override public SystemAlertScopeEntity findByIdentity(String businessLineCode, String environmentCode) {
            if (stored == null) return null;
            return stored.getBusinessLineCode().equals(businessLineCode)
                    && stored.getEnvironmentCode().equals(environmentCode) ? stored : null;
        }
        @Override public List<SystemAlertScopeServiceEntity> findServices(List<Long> scopeIds) {
            return services.stream().filter(item -> scopeIds.contains(item.scopeId())).toList();
        }
        @Override public List<SystemAlertScopeIndexEntity> findIndexPatterns(List<Long> scopeIds) {
            return indexes.stream().filter(item -> scopeIds.contains(item.scopeId())).toList();
        }
        @Override public int insertScope(SystemAlertScopeEntity entity) {
            entity.setId(7L);
            stored = entity;
            return 1;
        }
        @Override public int updateScope(SystemAlertScopeEntity entity) {
            stored = entity;
            return 1;
        }
        @Override public int insertService(SystemAlertScopeServiceEntity entity) {
            services.add(new SystemAlertScopeServiceEntity(
                    (long) services.size() + 1, entity.scopeId(), entity.subsystemName(),
                    entity.serviceName(), entity.enabled(), entity.sortOrder()));
            return 1;
        }
        @Override public int insertIndexPattern(SystemAlertScopeIndexEntity entity) {
            indexes.add(new SystemAlertScopeIndexEntity(
                    (long) indexes.size() + 1, entity.scopeId(), entity.indexPattern(), entity.sortOrder()));
            return 1;
        }
        @Override public int deleteServices(Long scopeId) {
            services.removeIf(item -> scopeId.equals(item.scopeId()));
            return 1;
        }
        @Override public int deleteIndexPatterns(Long scopeId) {
            indexes.removeIf(item -> scopeId.equals(item.scopeId()));
            return 1;
        }
        @Override public int deleteSyncState(Long scopeId) {
            syncStateDeleteCount++;
            return 1;
        }
        @Override public int deleteScope(Long id) {
            stored = null;
            return 1;
        }
    }
}
