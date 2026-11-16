package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.agent.AgentConfiguration;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * REQ-172f8541：数字员工巡检通知。运行 COMPLETED 且报告含 P0/P1 分级标记时，
 * 向任务范围各业务线成员发站内信（每轮至多一次，dedupe=运行 ID）；通知失败仅记日志，不影响运行结果。
 */
@Service
public class AgentRunNotifier {
    private static final Logger log = LoggerFactory.getLogger(AgentRunNotifier.class);
    private final AgentMapper mapper;
    private final UserMapper users;
    private final NotificationService notifications;
    private final ObjectMapper json;

    public AgentRunNotifier(AgentMapper mapper, UserMapper users, NotificationService notifications, ObjectMapper json) {
        this.mapper = mapper;
        this.users = users;
        this.notifications = notifications;
        this.json = json;
    }

    public void notifyCompletion(String runId) {
        // 巡检刚结束时连接池可能被 CLI 的密集查询瞬时占满，短退避重试避免瞬态拿不到连接。
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                if (attempt > 1) Thread.sleep(2000L * attempt);
                if (notifyOnce(runId)) return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException ex) {
                if (attempt == 3) {
                    String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    log.warn("Agent run notification failed. runId={}, reason={}", runId, reason);
                    auditQuietly(runId, reason);
                }
            }
        }
    }

    /** @return true=已通知或无需通知；false=可重试的瞬时失败（由调用方重试）。 */
    private boolean notifyOnce(String runId) {
        var run = mapper.findRun(runId);
        if (run == null || !"COMPLETED".equals(run.status()) || run.resultText() == null) return true;
        String result = run.resultText();
        if (!result.contains("P0") && !result.contains("P1")) return true;
        var task = mapper.findTask(run.taskId());
        var agent = mapper.findAgent(run.agentId());
        if (task == null || agent == null) return true;
        var spec = json.readValue(task.specificationJson(), cn.aslight.workhub.model.agent.AgentTaskSpecification.class);
        var config = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        String title = "[数字员工巡检] " + config.name() + "·" + abbreviate(spec.objective(), 30);
        String content = abbreviate(result, 1000);
        int sent = 0;
        for (var scope : spec.scopes()) {
            for (String recipient : recipients(scope.businessLineCode())) {
                notifications.createSystemNotification(recipient, "AGENT_PATROL", title, content,
                        scope.businessLineCode(), scope.environmentCode(), "AGENT_RUN:" + runId);
                sent++;
            }
        }
        if (sent > 0) {
            mapper.insertAudit(java.util.UUID.randomUUID().toString(), "RUN", runId, "NOTIFIED", "agent-runtime",
                    json.writeValueAsString(java.util.Map.of("recipients", sent, "title", title)), notifiedAt());
        }
        return true;
    }

    private void auditQuietly(String runId, String reason) {
        try {
            mapper.insertAudit(java.util.UUID.randomUUID().toString(), "RUN", runId, "NOTIFY_FAILED", "agent-runtime",
                    json.writeValueAsString(java.util.Map.of("reason", abbreviate(reason, 1500))), notifiedAt());
        } catch (RuntimeException ignored) {
            // 审计写入失败时仅保留日志。
        }
    }

    private static java.time.LocalDateTime notifiedAt() {
        return java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
    }

    private List<String> recipients(String businessLineCode) {
        List<String> members = users.findBusinessLineMembers(businessLineCode).stream()
                .map(UserOptionResponse::userName)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        return members.isEmpty() ? List.of("admin") : members;
    }

    private static String abbreviate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
