package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.dao.notification.NotificationMapper;
import cn.aslight.workhub.dao.ops.SystemAlertScopeIngestionMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeSyncStateEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.service.notification.NotificationSchemaInitializer;
import cn.aslight.workhub.service.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ElkSystemAlertCollectorTest {

    @Test
    void shouldNotAccessDependenciesWhenDisabled() {
        FakeMapper mapper = new FakeMapper();
        FakeLogSource source = new FakeLogSource(List.of(event("asset-payment", "source-1")));

        collector(new ElkAlertProperties(), source, mapper, new FakeNotificationService()).collect();

        assertEquals(0, source.calls);
        assertEquals(0, mapper.insertCount);
    }

    @Test
    void allModeShouldQueryWithoutServiceFilterAndNotifyByActualService() {
        ElkAlertProperties properties = enabledProperties();
        FakeMapper mapper = new FakeMapper();
        mapper.scopes = List.of(scope(7L, "ALL"));
        FakeLogSource source = new FakeLogSource(List.of(
                event("asset-payment", "source-1"), event("amp-saps", "source-2")));
        FakeNotificationService notifications = new FakeNotificationService();

        collector(properties, source, mapper, notifications).collect();

        assertEquals(List.of(), source.lastServiceNames);
        assertEquals(2, mapper.insertCount);
        assertEquals(List.of("asset-payment", "amp-saps"), mapper.insertedSubsystemNames);
        assertEquals(List.of(
                "admin:ELK_ERROR:ELK:SCOPE:BATCH:7:asset-payment:source-1",
                "admin:ELK_ERROR:ELK:SCOPE:BATCH:7:amp-saps:source-2"), notifications.created);
    }

    @Test
    void selectedModeShouldQueryEnabledServicesAndUseConfiguredSubsystemName() {
        ElkAlertProperties properties = enabledProperties();
        FakeMapper mapper = new FakeMapper();
        mapper.scopes = List.of(scope(7L, "SELECTED"));
        mapper.services = List.of(
                new SystemAlertScopeServiceEntity(1L, 7L, "资产支付", "asset-payment", true, 0),
                new SystemAlertScopeServiceEntity(2L, 7L, "账单管理", "amp-saps", false, 1));
        FakeLogSource source = new FakeLogSource(List.of(event("asset-payment", "source-1")));

        collector(properties, source, mapper, new FakeNotificationService()).collect();

        assertEquals(List.of("asset-payment"), source.lastServiceNames);
        assertEquals(List.of("资产支付"), mapper.insertedSubsystemNames);
        assertEquals("SUCCESS", mapper.status);
        assertEquals(LocalDateTime.of(2026, 7, 21, 2, 3, 4), mapper.cursor);
    }

    @Test
    void shouldPersistSlowSqlWithoutNotificationAndIgnoreDuplicate() {
        ElkAlertProperties properties = enabledProperties();
        FakeMapper mapper = new FakeMapper();
        mapper.scopes = List.of(scope(7L, "ALL"));
        ElkSystemAlertLog slowSql = new ElkSystemAlertLog(
                "source-slow", "asset-payment", "ERROR", "PaymentService",
                "lastPacketReceivedIdleMillis : 119987", "SQLException", "stack",
                "trace-1", "req-1", Instant.parse("2026-07-21T02:03:04Z"));
        FakeNotificationService notifications = new FakeNotificationService();

        collector(properties, new FakeLogSource(List.of(slowSql)), mapper, notifications).collect();

        assertEquals(List.of("SLOW_SQL"), mapper.insertedCategories);
        assertEquals(List.of(), notifications.created);
        assertEquals("处理1条，过滤0条，缺少服务名0条，新增普通异常0条，新增慢SQL1条", mapper.message);

        mapper.insertResult = 0;
        collector(properties, new FakeLogSource(List.of(slowSql)), mapper, notifications).collect();
        assertEquals(List.of(), notifications.created);
    }

    @Test
    void invalidSelectedScopeShouldFailWithoutBlockingNextScope() {
        ElkAlertProperties properties = enabledProperties();
        FakeMapper mapper = new FakeMapper();
        mapper.scopes = List.of(scope(6L, "SELECTED"), scope(7L, "ALL"));

        collector(properties, new FakeLogSource(List.of(event("asset-payment", "source-1"))),
                mapper, new FakeNotificationService()).collect();

        assertEquals(1, mapper.failureCount);
        assertEquals(1, mapper.successCount);
        assertEquals(1, mapper.insertCount);
    }

    private ElkSystemAlertCollector collector(ElkAlertProperties properties, SystemAlertLogSource source,
                                               FakeMapper mapper, FakeNotificationService notifications) {
        return new ElkSystemAlertCollector(properties, source, mapper,
                new FakeUserMapper(List.of()), notifications, ZoneOffset.UTC);
    }

    private ElkAlertProperties enabledProperties() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        return properties;
    }

    private SystemAlertScopeEntity scope(Long id, String mode) {
        SystemAlertScopeEntity scope = new SystemAlertScopeEntity();
        scope.setId(id);
        scope.setBusinessLineCode("BL000004");
        scope.setEnvironmentCode("prod");
        scope.setWatchMode(mode);
        scope.setEnabled(true);
        return scope;
    }

    private ElkSystemAlertLog event(String serviceName, String sourceEventId) {
        return new ElkSystemAlertLog(sourceEventId, serviceName, "ERROR", "PaymentService",
                "支付回调失败", "IllegalStateException", "stack", "trace-1", "req-1",
                Instant.parse("2026-07-21T02:03:04Z"));
    }

    private static SystemAlertRuleEntity slowSqlRule() {
        SystemAlertRuleEntity rule = new SystemAlertRuleEntity();
        rule.setId(1L);
        rule.setRuleName("slow-sql");
        rule.setAction("SLOW_SQL");
        rule.setMatchScope("ALL_TEXT");
        rule.setMatchMode("ANY");
        rule.setPriority(10);
        rule.setEnabled(true);
        rule.setKeywords(List.of("lastPacketReceivedIdleMillis"));
        return rule;
    }

    private static class FakeLogSource implements SystemAlertLogSource {
        private final List<ElkSystemAlertLog> events;
        private int calls;
        private List<String> lastServiceNames = List.of();

        private FakeLogSource(List<ElkSystemAlertLog> events) {
            this.events = events;
        }

        @Override
        public SyncResult readErrors(String indexPattern, List<String> serviceNames, String environmentCode,
                                     Instant fromInclusive, Consumer<ElkSystemAlertLog> consumer) {
            calls++;
            lastServiceNames = List.copyOf(serviceNames);
            events.forEach(consumer);
            Instant latest = events.isEmpty() ? null : events.getLast().occurredAt();
            return new SyncResult(events.size(), latest);
        }
    }

    private static class FakeMapper implements SystemAlertScopeIngestionMapper {
        private List<SystemAlertScopeEntity> scopes = List.of();
        private List<SystemAlertScopeServiceEntity> services = List.of();
        private int insertResult = 1;
        private int insertCount;
        private int successCount;
        private int failureCount;
        private String status;
        private String message;
        private LocalDateTime cursor;
        private final List<String> insertedSubsystemNames = new ArrayList<>();
        private final List<String> insertedCategories = new ArrayList<>();

        @Override public List<SystemAlertScopeEntity> findEnabledScopes() { return scopes; }
        @Override public List<SystemAlertScopeServiceEntity> findServices(List<Long> scopeIds) {
            return services.stream().filter(item -> scopeIds.contains(item.scopeId())).toList();
        }
        @Override public List<SystemAlertScopeIndexEntity> findIndexPatterns(List<Long> scopeIds) {
            return scopeIds.stream().map(id -> new SystemAlertScopeIndexEntity(
                    id * 10, id, "workhub-logs-*", 0)).toList();
        }
        @Override public List<SystemAlertRuleEntity> findEnabledRules() { return List.of(slowSqlRule()); }
        @Override public List<SystemAlertRuleKeywordEntity> findRuleKeywords(List<Long> ruleIds) {
            return List.of(new SystemAlertRuleKeywordEntity(1L, 1L, "lastPacketReceivedIdleMillis", 0));
        }
        @Override public SystemAlertScopeSyncStateEntity findSyncState(Long scopeId) { return null; }
        @Override public int insertElkEvent(SystemAlertScopeEntity scope, String subsystemName,
                                            ElkSystemAlertLog log, LocalDateTime occurredAt,
                                            String eventCategory) {
            if (insertResult > 0) {
                insertCount++;
                insertedSubsystemNames.add(subsystemName);
                insertedCategories.add(eventCategory);
            }
            return insertResult;
        }
        @Override public int saveSuccess(Long scopeId, LocalDateTime lastOccurredAt, String message) {
            status = "SUCCESS";
            cursor = lastOccurredAt;
            this.message = message;
            successCount++;
            return 1;
        }
        @Override public int saveFailure(Long scopeId, String message) {
            status = "ERROR";
            failureCount++;
            return 1;
        }
    }

    private static class FakeUserMapper implements UserMapper {
        private final List<UserOptionResponse> members;
        private FakeUserMapper(List<UserOptionResponse> members) { this.members = members; }
        @Override public List<UserOptionResponse> findActiveUsers() { return members; }
        @Override public List<UserOptionResponse> findBusinessLineMembers(String businessLine) { return members; }
        @Override public void upsertActiveUser(String userName, String displayName) { }
    }

    private static class FakeNotificationService extends NotificationService {
        private final List<String> created = new ArrayList<>();
        private FakeNotificationService() { super((NotificationMapper) null, (NotificationSchemaInitializer) null); }
        @Override
        public void createSystemNotification(String recipientUserName, String notificationType, String title,
                                             String content, String businessLineCode, String environmentCode,
                                             String dedupeKey) {
            created.add(recipientUserName + ":" + notificationType + ":" + dedupeKey);
        }
    }
}
