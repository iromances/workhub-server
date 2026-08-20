package cn.aslight.workhub.service.ops;

import cn.aslight.workhub.observability.ElkAlertProperties;
import cn.aslight.workhub.dao.ops.SystemAlertIngestionMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.ops.ElkSystemAlertLog;
import cn.aslight.workhub.model.ops.SystemAlertEventCategory;
import cn.aslight.workhub.model.ops.SystemAlertRuleAction;
import cn.aslight.workhub.model.ops.SystemAlertRuleEntity;
import cn.aslight.workhub.model.ops.SystemAlertRuleKeywordEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemEntity;
import cn.aslight.workhub.model.ops.SystemAlertSubsystemIndexPatternEntity;
import cn.aslight.workhub.model.ops.SystemAlertSyncStateEntity;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class ElkSystemAlertCollector {

    private static final Logger log = LoggerFactory.getLogger(ElkSystemAlertCollector.class);
    private final AtomicBoolean running = new AtomicBoolean();
    private final ElkAlertRuntimeConfigService configService;
    private final SystemAlertLogSource logSource;
    private final SystemAlertIngestionMapper ingestionMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;
    private final ZoneId zoneId;

    @Autowired
    public ElkSystemAlertCollector(ElkAlertRuntimeConfigService configService,
                                   SystemAlertLogSource logSource,
                                   SystemAlertIngestionMapper ingestionMapper,
                                   UserMapper userMapper,
                                   NotificationService notificationService) {
        this(configService, logSource, ingestionMapper, userMapper, notificationService, ZoneId.systemDefault());
    }

    ElkSystemAlertCollector(ElkAlertProperties properties,
                            SystemAlertLogSource logSource,
                            SystemAlertIngestionMapper ingestionMapper,
                            UserMapper userMapper,
                            NotificationService notificationService,
                            ZoneId zoneId) {
        this(new ElkAlertRuntimeConfigService(properties), logSource, ingestionMapper,
                userMapper, notificationService, zoneId);
    }

    ElkSystemAlertCollector(ElkAlertRuntimeConfigService configService,
                            SystemAlertLogSource logSource,
                            SystemAlertIngestionMapper ingestionMapper,
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
            List<SystemAlertSubsystemEntity> subsystems = ingestionMapper.findEnabledSubsystems();
            attachIndexPatterns(subsystems);
            int succeeded = 0;
            for (SystemAlertSubsystemEntity subsystem : subsystems) {
                if (collectSubsystem(subsystem, config.initialLookbackMinutes(), rules)) {
                    succeeded++;
                }
            }
            log.info("ELK系统预警采集完成，子系统数={}，成功数={}，失败数={}",
                    subsystems.size(), succeeded, subsystems.size() - succeeded);
        } finally {
            running.set(false);
        }
    }

    private boolean collectSubsystem(SystemAlertSubsystemEntity subsystem,
                                     int initialLookbackMinutes,
                                     List<SystemAlertRuleEntity> rules) {
        try {
            SystemAlertSyncStateEntity state = ingestionMapper.findSyncState(subsystem.getId());
            Instant from = resolveFrom(state, initialLookbackMinutes);
            NotificationBatch notificationBatch = new NotificationBatch();
            SystemAlertLogSource.SyncResult result = logSource.readErrors(
                    subsystem.getIndexPatternExpression(), subsystem.getServiceName(),
                    subsystem.getEnvironmentCode(), from,
                    event -> {
                        SystemAlertRuleAction action = SystemAlertEventClassifier.classify(event, rules);
                        if (action == SystemAlertRuleAction.IGNORE) {
                            notificationBatch.recordFiltered();
                            return;
                        }
                        SystemAlertEventCategory eventCategory = action == SystemAlertRuleAction.SLOW_SQL
                                ? SystemAlertEventCategory.SLOW_SQL
                                : SystemAlertEventCategory.SYSTEM_ERROR;
                        LocalDateTime occurredAt = LocalDateTime.ofInstant(event.occurredAt(), zoneId);
                        if (ingestionMapper.insertElkEvent(
                                subsystem, event, occurredAt, eventCategory.name()) > 0) {
                            notificationBatch.record(event, eventCategory);
                        }
                    });
            if (notificationBatch.count() > 0) {
                notifyRecipients(subsystem, notificationBatch, recipients(subsystem.getBusinessLineCode()));
            }
            LocalDateTime cursor = result.latestOccurredAt() == null
                    ? state == null ? null : state.getLastOccurredAt()
                    : LocalDateTime.ofInstant(result.latestOccurredAt(), zoneId);
            ingestionMapper.saveSuccess(subsystem.getId(), cursor,
                    abbreviate("处理" + result.fetchedCount() + "条，过滤" + notificationBatch.filteredCount()
                            + "条，新增普通异常" + notificationBatch.count()
                            + "条，新增慢SQL" + notificationBatch.slowSqlCount() + "条", 1_000));
            return true;
        } catch (Exception ex) {
            String message = safeError(ex);
            try {
                ingestionMapper.saveFailure(subsystem.getId(), message);
            } catch (Exception stateEx) {
                log.warn("保存ELK系统预警失败状态失败，subsystemId={}，原因={}",
                        subsystem.getId(), stateEx.getClass().getSimpleName());
            }
            log.warn("ELK系统预警采集失败，subsystemId={}，serviceName={}，原因={}",
                    subsystem.getId(), subsystem.getServiceName(), message);
            return false;
        }
    }

    private void attachIndexPatterns(List<SystemAlertSubsystemEntity> subsystems) {
        if (subsystems.isEmpty()) {
            return;
        }
        List<Long> ids = subsystems.stream().map(SystemAlertSubsystemEntity::getId).toList();
        Map<Long, List<String>> patternsBySubsystem = ingestionMapper.findIndexPatterns(ids).stream()
                .collect(Collectors.groupingBy(
                        SystemAlertSubsystemIndexPatternEntity::subsystemId,
                        Collectors.mapping(SystemAlertSubsystemIndexPatternEntity::indexPattern, Collectors.toList())
                ));
        for (SystemAlertSubsystemEntity subsystem : subsystems) {
            List<String> patterns = patternsBySubsystem.getOrDefault(subsystem.getId(), List.of());
            subsystem.setIndexPatterns(patterns);
            subsystem.setIndexPatternExpression(patterns.isEmpty() ? null : String.join(",", patterns));
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

    private Instant resolveFrom(SystemAlertSyncStateEntity state, int initialLookbackMinutes) {
        if (state == null || state.getLastOccurredAt() == null) {
            return Instant.now().minus(Math.max(1, initialLookbackMinutes), ChronoUnit.MINUTES);
        }
        return state.getLastOccurredAt().atZone(zoneId).toInstant();
    }

    private List<String> recipients(String businessLineCode) {
        List<String> users = userMapper.findBusinessLineMembers(businessLineCode).stream()
                .map(UserOptionResponse::userName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        return users.isEmpty() ? List.of("admin") : users;
    }

    private void notifyRecipients(SystemAlertSubsystemEntity subsystem, NotificationBatch batch,
                                  List<String> recipients) {
        if (batch.count() == 0 || batch.latest() == null) {
            return;
        }
        ElkSystemAlertLog latest = batch.latest();
        String title = subsystem.getSubsystemName() + " 出现 " + batch.count() + " 条错误";
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
                subsystem.getBusinessLineCode(), subsystem.getEnvironmentCode(),
                subsystem.getSubsystemName(), latest.serviceName(), batch.count(),
                LocalDateTime.ofInstant(batch.firstOccurredAt(), zoneId),
                LocalDateTime.ofInstant(batch.lastOccurredAt(), zoneId),
                display(latest.errorType()), display(latest.traceId()),
                display(abbreviate(latest.message(), 2_000)));
        String dedupeKey = "ELK:BATCH:" + subsystem.getId() + ":" + latest.sourceEventId();
        for (String recipient : recipients) {
            notificationService.createSystemNotification(recipient, "ELK_ERROR", title, content,
                    subsystem.getBusinessLineCode(), subsystem.getEnvironmentCode(), dedupeKey);
        }
    }

    private static final class NotificationBatch {
        private int count;
        private int slowSqlCount;
        private int filteredCount;
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

        void recordFiltered() {
            filteredCount++;
        }

        int count() {
            return count;
        }

        int filteredCount() {
            return filteredCount;
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
