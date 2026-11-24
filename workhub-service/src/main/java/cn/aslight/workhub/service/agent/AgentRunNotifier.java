package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotMessageSender;
import cn.aslight.workhub.integration.wecom.WecomRobotSendResult;
import cn.aslight.workhub.model.agent.AgentConfiguration;
import cn.aslight.workhub.model.agent.AgentTaskSpecification;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import cn.aslight.workhub.service.payment.PaymentCryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Set;

/**
 * REQ-172f8541 数字员工巡检通知；REQ-2020a468 升级为按 Agent 配置驱动：
 * 运行 COMPLETED 且报告含 ≥ 预警级别（默认 P0）的分级标记时，按预警方式分发——
 * INBOX 站内信（任务范围各业务线成员，每轮至多一次，dedupe=运行 ID）与 WECOM 企微机器人
 * （任务级 webhook 地址，AES 密文存储、发送前解密）。渠道间独立成败；通知失败仅记日志与审计，不影响运行结果。
 */
@Service
public class AgentRunNotifier {
    private static final Logger log = LoggerFactory.getLogger(AgentRunNotifier.class);
    private final AgentMapper mapper;
    private final UserMapper users;
    private final NotificationService notifications;
    private final WecomRobotMessageSender wecom;
    private final PaymentCryptoService crypto;
    private final ObjectMapper json;

    public AgentRunNotifier(AgentMapper mapper, UserMapper users, NotificationService notifications,
                            WecomRobotMessageSender wecom, PaymentCryptoService crypto, ObjectMapper json) {
        this.mapper = mapper;
        this.users = users;
        this.notifications = notifications;
        this.wecom = wecom;
        this.crypto = crypto;
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
                    auditQuietly(runId, "NOTIFY_FAILED", java.util.Map.of("reason", abbreviate(reason, 1500)));
                }
            }
        }
    }

    /** @return true=已通知或无需通知；false=可重试的瞬时失败（由调用方重试）。 */
    private boolean notifyOnce(String runId) {
        var run = mapper.findRun(runId);
        if (run == null || !"COMPLETED".equals(run.status()) || run.resultText() == null) return true;
        String result = run.resultText();
        var task = mapper.findTask(run.taskId());
        var agent = mapper.findAgent(run.agentId());
        if (task == null || agent == null) return true;
        var spec = json.readValue(task.specificationJson(), AgentTaskSpecification.class);
        var config = json.readValue(agent.configurationJson(), AgentConfiguration.class);
        boolean alert = levelHit(result, config.alertLevel());
        for (var event : mapper.listEvents(runId).reversed()) {
            if (!Set.of("CLI_RESULT", "STRUCTURED_RESULT").contains(event.kind())) continue;
            var outcome = json.readTree(event.payloadJson()).path("result");
            if (!AgentReportContract.isV2(outcome)) continue;
            alert = false;
            for (var finding : outcome.path("report").path("findings")) {
                if (!"RESOLVED".equals(finding.path("disposition").asText())
                        && "VERIFIED".equals(finding.path("verificationStatus").asText())
                        && levelHit(finding.path("severity").asText(), config.alertLevel())) alert = true;
            }
            break;
        }
        if (!alert) return true;
        String title = "[数字员工巡检] " + config.name() + "·" + abbreviate(spec.objective(), 30);
        if (config.alertChannels().contains("INBOX")) {
            sendInbox(runId, spec, title, result);
        }
        if (config.alertChannels().contains("WECOM")) {
            sendWecom(runId, spec, title, result);
        }
        return true;
    }

    /** 报告文本是否含 ≥ 配置级别的分级标记：P0 仅 P0；P1 含 P0/P1；P2 全部。 */
    static boolean levelHit(String result, String alertLevel) {
        if (result.contains("P0")) return true;
        if ("P1".equals(alertLevel) || "P2".equals(alertLevel)) {
            if (result.contains("P1")) return true;
            return "P2".equals(alertLevel) && result.contains("P2");
        }
        return false;
    }

    private void sendInbox(String runId, AgentTaskSpecification spec, String title, String result) {
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
    }

    /** 企微机器人：任务级地址密文存储，发送前解密；无地址跳过、失败记审计，均不重试、不影响其他渠道。 */
    private void sendWecom(String runId, AgentTaskSpecification spec, String title, String result) {
        String stored = spec.webhookUrl();
        if (stored == null) {
            auditQuietly(runId, "WEBHOOK_SKIPPED", java.util.Map.of("reason", "任务未配置预警 webhook 地址"));
            return;
        }
        String webhookUrl = stored.startsWith(AgentWorkbenchService.WEBHOOK_CIPHER_PREFIX)
                ? crypto.decrypt(stored.substring(AgentWorkbenchService.WEBHOOK_CIPHER_PREFIX.length())) : stored;
        String content = "## " + title + "\n" + abbreviate(result, 1000);
        try {
            WecomRobotSendResult outcome = wecom.sendTo(webhookUrl, content);
            if (outcome.sent()) {
                auditQuietly(runId, "WEBHOOK_SENT", java.util.Map.of("errcode", outcome.errcode(),
                        "contentLength", content.length()));
            } else {
                auditQuietly(runId, "WEBHOOK_FAILED", java.util.Map.of("errcode", outcome.errcode(),
                        "reason", abbreviate(outcome.errmsg(), 500)));
            }
        } catch (RuntimeException ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            auditQuietly(runId, "WEBHOOK_FAILED", java.util.Map.of("reason", abbreviate(reason, 500)));
        }
    }

    private void auditQuietly(String runId, String action, java.util.Map<String, Object> payload) {
        try {
            mapper.insertAudit(java.util.UUID.randomUUID().toString(), "RUN", runId, action, "agent-runtime",
                    json.writeValueAsString(payload), notifiedAt());
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
