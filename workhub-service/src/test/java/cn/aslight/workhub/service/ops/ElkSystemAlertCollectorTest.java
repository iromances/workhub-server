package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.dao.notification.NotificationMapper;
import cn.aslight.workhub.dao.ops.SystemAlertIngestionMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSyncStateEntity;
import cn.aslight.workhub.model.system.UserOptionResponse;
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
        ElkAlertProperties properties = new ElkAlertProperties();
        FakeMapper mapper = new FakeMapper();
        FakeLogSource source = new FakeLogSource(List.of(event()));
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties, source, mapper,
                new FakeUserMapper(List.of()), new FakeNotificationService(), ZoneOffset.UTC);

        collector.collect();

        assertEquals(0, source.calls);
        assertEquals(0, mapper.insertCount);
    }

    @Test
    void shouldInsertEventAdvanceCursorAndNotifyBusinessLineMember() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        FakeLogSource source = new FakeLogSource(List.of(event()));
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties, source, mapper,
                new FakeUserMapper(List.of(new UserOptionResponse("zhangsan", "张三", "BL000004"))),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(1, mapper.insertCount);
        assertEquals("SUCCESS", mapper.status);
        assertEquals(LocalDateTime.of(2026, 7, 21, 2, 3, 4), mapper.cursor);
        assertEquals(List.of("zhangsan:ELK_ERROR:ELK:BATCH:7:source-1"), notifications.created);
        assertEquals("workhub-logs-*,workhub-history-*", source.lastIndexPattern);
    }

    @Test
    void shouldNotNotifyAgainWhenEventAlreadyExists() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        mapper.insertResult = 0;
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(event())), mapper, new FakeUserMapper(List.of()),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(0, mapper.insertCount);
        assertEquals(List.of(), notifications.created);
    }

    @Test
    void shouldNotNotifyWhenSlowSqlAlreadyExists() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        mapper.insertResult = 0;
        ElkSystemAlertLog duplicateSlowSql = event("source-slow",
                "lastPacketReceivedIdleMillis : 119987", "2026-07-21T02:03:04Z");
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(duplicateSlowSql)), mapper, new FakeUserMapper(List.of()),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(0, mapper.insertCount);
        assertEquals(List.of(), notifications.created);
        assertEquals("处理1条，过滤0条，新增普通异常0条，新增慢SQL0条", mapper.message);
    }

    @Test
    void shouldAggregateMultipleNewEventsIntoOneNotification() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        ElkSystemAlertLog later = new ElkSystemAlertLog(
                "source-2", "asset-payment", "ERROR", "PaymentService",
                "第二条错误", "IllegalStateException", "stack", "trace-2", "req-2",
                Instant.parse("2026-07-21T02:04:04Z"));
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(event(), later)), mapper, new FakeUserMapper(List.of()),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(2, mapper.insertCount);
        assertEquals(List.of("admin:ELK_ERROR:ELK:BATCH:7:source-2"), notifications.created);
    }

    @Test
    void shouldFilterExpectedBusinessNoiseBeforePersistenceAndNotification() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        ElkSystemAlertLog invalidJwt = event("source-jwt",
                "拦截请求解析 token 异常: JWT无效", "2026-07-21T02:03:04Z");
        ElkSystemAlertLog valid = event("source-valid",
                "支付回调失败", "2026-07-21T02:04:04Z");
        ElkSystemAlertLog insufficientBalance = event("source-balance",
                "渠道代付失败：账户余额不足，请及时充值", "2026-07-21T02:05:04Z");
        ElkSystemAlertLog settledBill = event("source-settled",
                "[900000]LYX026031810458861_3账单已结算", "2026-07-21T02:06:04Z");
        ElkSystemAlertLog inconsistentAmount = event("source-amount",
                "[200007]交易金额和商品明细总额不等", "2026-07-21T02:07:04Z");
        ElkSystemAlertLog duplicateSubmission = event("source-duplicate",
                "[900000]请勿重复提交", "2026-07-21T02:08:04Z");
        ElkSystemAlertLog duplicatePaymentNo = event("source-payment-no",
                "[900000]业务方支付流水号重复", "2026-07-21T02:09:04Z");
        ElkSystemAlertLog outOfOrderSettlement = event("source-settlement-order",
                "[900000]请按顺序结算账单", "2026-07-21T02:10:04Z");
        ElkSystemAlertLog trialAmountMismatch = event("source-trial-amount",
                "[900000]交易金额与试算不等", "2026-07-21T02:11:04Z");
        ElkSystemAlertLog refundPeriodExceeded = event("source-refund-period",
                "[999999]实际天数已超七天，无法执行七天无理由退费", "2026-07-21T02:12:04Z");
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(invalidJwt, valid, insufficientBalance, settledBill,
                        inconsistentAmount, duplicateSubmission, duplicatePaymentNo,
                        outOfOrderSettlement, trialAmountMismatch, refundPeriodExceeded)), mapper,
                new FakeUserMapper(List.of()), notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(List.of("source-valid"), mapper.insertedEventIds);
        assertEquals(LocalDateTime.of(2026, 7, 21, 2, 12, 4), mapper.cursor);
        assertEquals("处理10条，过滤9条，新增普通异常1条，新增慢SQL0条", mapper.message);
        assertEquals(List.of("admin:ELK_ERROR:ELK:BATCH:7:source-valid"), notifications.created);
    }

    @Test
    void shouldPersistSlowSqlWithoutSendingNotification() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        ElkSystemAlertLog slowSql = event("source-slow",
                "discard long time none received connection, lastPacketReceivedIdleMillis : 119987",
                "2026-07-21T02:03:04Z");
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(slowSql)), mapper, new FakeUserMapper(List.of()),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(List.of("source-slow"), mapper.insertedEventIds);
        assertEquals(List.of("SLOW_SQL"), mapper.insertedCategories);
        assertEquals(List.of(), notifications.created);
        assertEquals("处理1条，过滤0条，新增普通异常0条，新增慢SQL1条", mapper.message);
    }

    @Test
    void shouldNotifyOnlySystemErrorsInMixedBatch() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        mapper.subsystems = List.of(subsystem());
        ElkSystemAlertLog systemError = event("source-error", "支付回调失败", "2026-07-21T02:03:04Z");
        ElkSystemAlertLog slowSql = event("source-slow",
                "lastPacketReceivedIdleMillis : 119987", "2026-07-21T02:04:04Z");
        FakeNotificationService notifications = new FakeNotificationService();
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties,
                new FakeLogSource(List.of(systemError, slowSql)), mapper, new FakeUserMapper(List.of()),
                notifications, ZoneOffset.UTC);

        collector.collect();

        assertEquals(List.of("SYSTEM_ERROR", "SLOW_SQL"), mapper.insertedCategories);
        assertEquals(List.of("admin:ELK_ERROR:ELK:BATCH:7:source-error"), notifications.created);
        assertEquals("处理2条，过滤0条，新增普通异常1条，新增慢SQL1条", mapper.message);
    }

    @Test
    void shouldContinueWithNextSubsystemWhenOneSourceFails() {
        ElkAlertProperties properties = new ElkAlertProperties();
        properties.setEnabled(true);
        FakeMapper mapper = new FakeMapper();
        SystemAlertSubsystemEntity failed = subsystem();
        failed.setId(6L);
        failed.setServiceName("failed-service");
        mapper.subsystems = List.of(failed, subsystem());
        SystemAlertLogSource source = (indexPattern, serviceName, environmentCode, fromInclusive, consumer) -> {
            if ("failed-service".equals(serviceName)) throw new IllegalStateException("ELK连接或响应异常");
            consumer.accept(event());
            return new SystemAlertLogSource.SyncResult(1, event().occurredAt());
        };
        ElkSystemAlertCollector collector = new ElkSystemAlertCollector(properties, source, mapper,
                new FakeUserMapper(List.of()), new FakeNotificationService(), ZoneOffset.UTC);

        collector.collect();

        assertEquals(1, mapper.failureCount);
        assertEquals(1, mapper.insertCount);
        assertEquals(1, mapper.successCount);
    }

    private SystemAlertSubsystemEntity subsystem() {
        SystemAlertSubsystemEntity entity = new SystemAlertSubsystemEntity();
        entity.setId(7L);
        entity.setBusinessLineCode("BL000004");
        entity.setEnvironmentCode("prod");
        entity.setSubsystemName("资产支付");
        entity.setServiceName("asset-payment");
        entity.setEnabled(true);
        return entity;
    }

    private ElkSystemAlertLog event() {
        return event("source-1", "支付回调失败", "2026-07-21T02:03:04Z");
    }

    private ElkSystemAlertLog event(String sourceEventId, String message, String occurredAt) {
        return new ElkSystemAlertLog(sourceEventId, "asset-payment", "ERROR", "PaymentService",
                message, "IllegalStateException", "stack", "trace-1", "req-1",
                Instant.parse(occurredAt));
    }

    private static List<SystemAlertRuleEntity> defaultRules() {
        return List.of(
                rule(1L, "IGNORE", "MESSAGE", "ANY", 10,
                        "拦截请求解析token异常：jwt无效", "余额不足",
                        "[200007]交易金额和商品明细总额不等", "[900000]请勿重复提交",
                        "[900000]业务方支付流水号重复", "[900000]请按顺序结算账单",
                        "[900000]交易金额与试算不等", "[999999]实际天数已超七天，无法执行七天无理由退费"),
                rule(2L, "IGNORE", "MESSAGE", "ALL", 20, "[900000]", "账单已结算"),
                rule(3L, "SLOW_SQL", "ALL_TEXT", "ANY", 100,
                        "discard long time none received connection", "lastPacketReceivedIdleMillis")
        );
    }

    private static SystemAlertRuleEntity rule(Long id, String action, String scope, String mode,
                                              int priority, String... keywords) {
        SystemAlertRuleEntity rule = new SystemAlertRuleEntity();
        rule.setId(id);
        rule.setRuleName("rule-" + id);
        rule.setAction(action);
        rule.setMatchScope(scope);
        rule.setMatchMode(mode);
        rule.setPriority(priority);
        rule.setEnabled(true);
        rule.setKeywords(List.of(keywords));
        return rule;
    }

    private static class FakeLogSource implements SystemAlertLogSource {
        private final List<ElkSystemAlertLog> events;
        private int calls;
        private String lastIndexPattern;
        private FakeLogSource(List<ElkSystemAlertLog> events) { this.events = events; }
        @Override
        public SyncResult readErrors(String indexPattern, String serviceName, String environmentCode,
                                     Instant fromInclusive,
                                     Consumer<ElkSystemAlertLog> consumer) {
            calls++;
            lastIndexPattern = indexPattern;
            events.forEach(consumer);
            Instant latest = events.isEmpty() ? null : events.getLast().occurredAt();
            return new SyncResult(events.size(), latest);
        }
    }

    private static class FakeMapper implements SystemAlertIngestionMapper {
        private List<SystemAlertSubsystemEntity> subsystems = List.of();
        private int insertResult = 1;
        private int insertCount;
        private String status;
        private String message;
        private LocalDateTime cursor;
        private int successCount;
        private int failureCount;
        private List<SystemAlertRuleEntity> rules = defaultRules();
        private final List<String> insertedEventIds = new ArrayList<>();
        private final List<String> insertedCategories = new ArrayList<>();
        @Override public List<SystemAlertSubsystemEntity> findEnabledSubsystems() { return subsystems; }
        @Override public List<SystemAlertRuleEntity> findEnabledRules() { return rules; }
        @Override public List<SystemAlertRuleKeywordEntity> findRuleKeywords(List<Long> ruleIds) {
            List<SystemAlertRuleKeywordEntity> result = new ArrayList<>();
            long id = 1L;
            for (SystemAlertRuleEntity rule : rules) {
                if (!ruleIds.contains(rule.getId())) continue;
                for (int index = 0; index < rule.getKeywords().size(); index++) {
                    result.add(new SystemAlertRuleKeywordEntity(
                            id++, rule.getId(), rule.getKeywords().get(index), index));
                }
            }
            return result;
        }
        @Override public List<SystemAlertSubsystemIndexPatternEntity> findIndexPatterns(List<Long> subsystemIds) {
            return subsystemIds.stream()
                    .flatMap(id -> java.util.stream.Stream.of(
                            new SystemAlertSubsystemIndexPatternEntity(id * 10, id, "workhub-logs-*", 0),
                            new SystemAlertSubsystemIndexPatternEntity(id * 10 + 1, id, "workhub-history-*", 1)
                    ))
                    .toList();
        }
        @Override public SystemAlertSyncStateEntity findSyncState(Long subsystemId) { return null; }
        @Override public int insertElkEvent(SystemAlertSubsystemEntity subsystem, ElkSystemAlertLog log,
                                            LocalDateTime occurredAt, String eventCategory) {
            if (insertResult > 0) {
                insertCount++;
                insertedEventIds.add(log.sourceEventId());
                insertedCategories.add(eventCategory);
            }
            return insertResult;
        }
        @Override public int saveSuccess(Long subsystemId, LocalDateTime lastOccurredAt, String message) {
            status = "SUCCESS"; cursor = lastOccurredAt; this.message = message; successCount++; return 1;
        }
        @Override public int saveFailure(Long subsystemId, String message) { status = "ERROR"; failureCount++; return 1; }
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
