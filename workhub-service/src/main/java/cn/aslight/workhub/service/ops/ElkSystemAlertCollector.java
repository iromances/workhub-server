package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.dao.ops.SystemAlertScopeIngestionMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertEventCategory;
import cn.aslight.workhub.model.ops.SystemAlertRuleAction;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeIndexEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeServiceEntity;
import cn.aslight.workhub.model.ops.SystemAlertScopeSyncStateEntity;
import cn.aslight.workhub.model.ops.SystemAlertWatchMode;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ElkSystemAlertCollector {

    private static final Logger log = LoggerFactory.getLogger(ElkSystemAlertCollector.class);
    private final AtomicBoolean running = new AtomicBoolean();
    private final ElkAlertRuntimeConfigService configService;
    private final SystemAlertLogSource logSource;
    private final SystemAlertScopeIngestionMapper ingestionMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ZoneId zoneId;

    @Autowired
    public ElkSystemAlertCollector(ElkAlertRuntimeConfigService configService,
                                   SystemAlertLogSource logSource,
                                   SystemAlertScopeIngestionMapper ingestionMapper,
                                   UserMapper userMapper,
                                   NotificationService notificationService) {
        this(configService, logSource, ingestionMapper, userMapper, notificationService, ZoneId.systemDefault());
    }

    ElkSystemAlertCollector(ElkAlertProperties properties,
                            SystemAlertLogSource logSource,
                            SystemAlertScopeIngestionMapper ingestionMapper,
                            UserMapper userMapper,
                            NotificationService notificationService,
                            ZoneId zoneId) {
        this(new ElkAlertRuntimeConfigService(properties), logSource, ingestionMapper,
                userMapper, notificationService, zoneId);
    }

    ElkSystemAlertCollector(ElkAlertRuntimeConfigService configService,
                            SystemAlertLogSource logSource,
                            SystemAlertScopeIngestionMapper ingestionMapper,
                            UserMapper userMapper,
                            NotificationService notificationService,
                            ZoneId zoneId) {
        this.configService = configService;
        this.logSource = logSource;
        this.ingestionMapper = ingestionMapper;
        this.userMapper = userMapper;
        this.notificationService = notificationService;
        this.zoneId = zoneId;
    }

    public void collect() {
        ElkAlertRuntimeConfig config;
        try {
            config = configService.current(null);
        } catch (Exception ex) {
            log.warn("ELK系统预警配置解析失败，原因={}", safeError(ex));
            return;
        }
        if (!config.enabled()) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            log.info("ELK系统预警采集任务仍在运行，本轮跳过");
            return;
        }
        try {
            List<SystemAlertRuleEntity> rules = ingestionMapper.findEnabledRules();
            attachRuleKeywords(rules);
            List<SystemAlertScopeEntity> scopes = ingestionMapper.findEnabledScopes();
            attachScopeChildren(scopes);
            int succeeded = 0;
            for (SystemAlertScopeEntity scope : scopes) {
                if (collectScope(scope, config.initialLookbackMinutes(), rules)) {
                    succeeded++;
                }
            }
            log.info("ELK系统预警采集完成，业务线范围数={}，成功数={}，失败数={}",
                    scopes.size(), succeeded, scopes.size() - succeeded);
        } finally {
            running.set(false);
        }
    }

    private boolean collectScope(SystemAlertScopeEntity scope,
                                 int initialLookbackMinutes,
                                 List<SystemAlertRuleEntity> rules) {
        try {
            SystemAlertScopeSyncStateEntity state = ingestionMapper.findSyncState(scope.getId());
            Instant from = resolveFrom(state, initialLookbackMinutes);
            Map<String, SystemAlertScopeServiceEntity> servicesByName = scope.getServices().stream()
                    .collect(Collectors.toMap(SystemAlertScopeServiceEntity::serviceName, item -> item,
                            (left, right) -> left, LinkedHashMap::new));
            List<String> selectedServices = SystemAlertWatchMode.SELECTED.name().equals(scope.getWatchMode())
                    ? scope.getServices().stream()
                    .filter(item -> Boolean.TRUE.equals(item.enabled()))
                    .map(SystemAlertScopeServiceEntity::serviceName)
                    .toList()
                    : List.of();
            if (SystemAlertWatchMode.SELECTED.name().equals(scope.getWatchMode()) && selectedServices.isEmpty()) {
                throw new IllegalStateException("指定子系统模式下没有启用的子系统");
            }
            Map<String, NotificationBatch> batches = new LinkedHashMap<>();
            int[] filteredCount = {0};
            int[] missingServiceCount = {0};
            SystemAlertLogSource.SyncResult result = logSource.readErrors(
                    scope.getIndexPatternExpression(), selectedServices,
                    scope.getEnvironmentCode(), from,
                    event -> {
                        if (event.serviceName() == null || event.serviceName().isBlank()) {
                            missingServiceCount[0]++;
                            return;
                        }
                        SystemAlertRuleAction action = SystemAlertEventClassifier.classify(event, rules);
                        if (action == SystemAlertRuleAction.IGNORE) {
                            filteredCount[0]++;
                            return;
                        }
                        SystemAlertEventCategory eventCategory = action == SystemAlertRuleAction.SLOW_SQL
                                ? SystemAlertEventCategory.SLOW_SQL
                                : SystemAlertEventCategory.SYSTEM_ERROR;
                        LocalDateTime occurredAt = LocalDateTime.ofInstant(event.occurredAt(), zoneId);
                        SystemAlertScopeServiceEntity configuredService = servicesByName.get(event.serviceName());
                        String subsystemName = configuredService == null
                                ? event.serviceName() : configuredService.subsystemName();
                        if (ingestionMapper.insertElkEvent(scope, subsystemName, event, occurredAt,
                                eventCategory.name()) > 0) {
                            batches.computeIfAbsent(event.serviceName(), ignored -> new NotificationBatch())
                                    .record(event, eventCategory);
                        }
                    });
            List<String> recipients = null;
            for (Map.Entry<String, NotificationBatch> entry : batches.entrySet()) {
                if (entry.getValue().count() > 0) {
                    if (recipients == null) {
                        recipients = recipients(scope.getBusinessLineCode());
                    }
                    SystemAlertScopeServiceEntity configuredService = servicesByName.get(entry.getKey());
                    String subsystemName = configuredService == null
                            ? entry.getKey() : configuredService.subsystemName();
                    notifyRecipients(scope, subsystemName, entry.getValue(), recipients);
                }
            }
            LocalDateTime cursor = result.latestOccurredAt() == null
                    ? state == null ? null : state.lastOccurredAt()
                    : LocalDateTime.ofInstant(result.latestOccurredAt(), zoneId);
            int systemErrorCount = batches.values().stream().mapToInt(NotificationBatch::count).sum();
            int slowSqlCount = batches.values().stream().mapToInt(NotificationBatch::slowSqlCount).sum();
            ingestionMapper.saveSuccess(scope.getId(), cursor,
                    abbreviate("处理" + result.fetchedCount() + "条，过滤" + filteredCount[0]
                            + "条，缺少服务名" + missingServiceCount[0]
                            + "条，新增普通异常" + systemErrorCount
                            + "条，新增慢SQL" + slowSqlCount + "条", 1_000));
            return true;
        } catch (Exception ex) {
            String message = safeError(ex);
            try {
                ingestionMapper.saveFailure(scope.getId(), message);
            } catch (Exception stateEx) {
                log.warn("保存ELK系统预警失败状态失败，scopeId={}，原因={}",
                        scope.getId(), stateEx.getClass().getSimpleName());
            }
            log.warn("ELK系统预警采集失败，scopeId={}，businessLineCode={}，原因={}",
                    scope.getId(), scope.getBusinessLineCode(), message);
            return false;
        }
    }

    private void attachScopeChildren(List<SystemAlertScopeEntity> scopes) {
        if (scopes.isEmpty()) {
            return;
        }
        List<Long> ids = scopes.stream().map(SystemAlertScopeEntity::getId).toList();
        Map<Long, List<SystemAlertScopeServiceEntity>> servicesByScope = ingestionMapper.findServices(ids).stream()
                .collect(Collectors.groupingBy(SystemAlertScopeServiceEntity::scopeId));
        Map<Long, List<String>> patternsByScope = ingestionMapper.findIndexPatterns(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertScopeIndexEntity::scopeId,
                        Collectors.mapping(SystemAlertScopeIndexEntity::indexPattern, Collectors.toList())
                ));
        for (SystemAlertScopeEntity scope : scopes) {
            scope.setServices(servicesByScope.getOrDefault(scope.getId(), List.of()));
            List<String> patterns = patternsByScope.getOrDefault(scope.getId(), List.of());
            scope.setIndexPatterns(patterns);
            scope.setIndexPatternExpression(patterns.isEmpty() ? null : String.join(",", patterns));
        }
    }

    private void attachRuleKeywords(List<SystemAlertRuleEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        List<Long> ids = rules.stream().map(SystemAlertRuleEntity::getId).toList();
        Map<Long, List<String>> keywordsByRule = ingestionMapper.findRuleKeywords(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertRuleKeywordEntity::ruleId,
                        Collectors.mapping(SystemAlertRuleKeywordEntity::keyword, Collectors.toList())
                ));
        for (SystemAlertRuleEntity rule : rules) {
            rule.setKeywords(keywordsByRule.getOrDefault(rule.getId(), List.of()));
        }
    }

    private Instant resolveFrom(SystemAlertScopeSyncStateEntity state, int initialLookbackMinutes) {
        if (state == null || state.lastOccurredAt() == null) {
            return Instant.now().minus(Math.max(1, initialLookbackMinutes), ChronoUnit.MINUTES);
        }
        return state.lastOccurredAt().atZone(zoneId).toInstant();
    }

    private List<String> recipients(String businessLineCode) {
        List<String> users = userMapper.findBusinessLineMembers(businessLineCode).stream()
                .map(UserOptionResponse::userName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        return users.isEmpty() ? List.of("admin") : users;
    }

    private void notifyRecipients(SystemAlertScopeEntity scope, String subsystemName, NotificationBatch batch,
                                  List<String> recipients) {
        if (batch.count() == 0 || batch.latest() == null) {
            return;
        }
        ElkSystemAlertLog latest = batch.latest();
        String title = subsystemName + " 出现 " + batch.count() + " 条错误";
        String content = """
                业务线：%s
                环境：%s
                子系统：%s
                服务：%s
                本轮新增：%s 条
                时间范围：%s 至 %s
                异常类型：%s
                Trace ID：%s
                最近错误摘要：%s
                """.formatted(
                scope.getBusinessLineCode(), scope.getEnvironmentCode(),
                subsystemName, latest.serviceName(), batch.count(),
                LocalDateTime.ofInstant(batch.firstOccurredAt(), zoneId),
                LocalDateTime.ofInstant(batch.lastOccurredAt(), zoneId),
                display(latest.errorType()), display(latest.traceId()),
                display(abbreviate(latest.message(), 2_000)));
        String dedupeKey = "ELK:SCOPE:BATCH:" + scope.getId() + ":" + latest.serviceName()
                + ":" + latest.sourceEventId();
        for (String recipient : recipients) {
            notificationService.createSystemNotification(recipient, "ELK_ERROR", title, content,
                    scope.getBusinessLineCode(), scope.getEnvironmentCode(), dedupeKey);
        }
    }

    private static final class NotificationBatch {
        private int count;
        private int slowSqlCount;
        private Instant firstOccurredAt;
        private Instant lastOccurredAt;
        private ElkSystemAlertLog latest;

        void record(ElkSystemAlertLog event, SystemAlertEventCategory eventCategory) {
            if (eventCategory == SystemAlertEventCategory.SLOW_SQL) {
                slowSqlCount++;
                return;
            }
            count++;
            if (firstOccurredAt == null || event.occurredAt().isBefore(firstOccurredAt)) {
                firstOccurredAt = event.occurredAt();
            }
            if (lastOccurredAt == null || event.occurredAt().isAfter(lastOccurredAt)) {
                lastOccurredAt = event.occurredAt();
                latest = event;
            }
        }

        int count() {
            return count;
        }

        int slowSqlCount() {
            return slowSqlCount;
        }

        Instant firstOccurredAt() {
            return firstOccurredAt;
        }

        Instant lastOccurredAt() {
            return lastOccurredAt;
        }

        ElkSystemAlertLog latest() {
            return latest;
        }
    }

    private String display(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String safeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            message = ex.getClass().getSimpleName();
        }
        return abbreviate(message.replaceAll("(?i)(apikey|authorization|password|token)\\s*[:=]\\s*\\S+", "$1=***"), 1_000);
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value;
        return value.substring(0, maxLength);
    }
}
