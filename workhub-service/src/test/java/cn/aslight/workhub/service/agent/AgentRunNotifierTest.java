package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.config.PaymentProperties;
import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.integration.wecom.WecomRobotMessageSender;
import cn.aslight.workhub.integration.wecom.WecomRobotSendResult;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import cn.aslight.workhub.service.payment.PaymentCryptoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REQ-172f8541 站内信巡检通知；REQ-2020a468 升级：按 Agent 预警级别（默认 P0）与方式（INBOX/WECOM）分发。 */
class AgentRunNotifierTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentMapper mapper = mock(AgentMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final WecomRobotMessageSender wecom = mock(WecomRobotMessageSender.class);
    private final PaymentProperties cryptoProperties = new PaymentProperties();
    private final PaymentCryptoService crypto;

    {
        cryptoProperties.setMasterKey("test-master-key");
        crypto = new PaymentCryptoService(cryptoProperties);
    }
    private final AgentRunNotifier notifier = new AgentRunNotifier(mapper, users, notifications, wecom, crypto, json);

    @Test
    void completedRunWithP0NotifiesScopeLineMembersAtDefaultLevel() {
        stubRun("COMPLETED", "【巡检报告】1)【P0】修复采集；其余正常");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"), defaultConfig(new AgentScope("BL000001", "PROD")), null);
        when(users.findBusinessLineMembers("BL000001")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "保费分期")));

        notifier.notifyCompletion("run-1");

        verify(notifications).createSystemNotification(eq("alice"), eq("AGENT_PATROL"), contains("[数字员工巡检] 巡检助手"),
                contains("【P0】"), eq("BL000001"), eq("PROD"), eq("AGENT_RUN:run-1"));
        verifyNoMoreInteractions(notifications);
        verifyNoInteractions(wecom);
    }

    @Test
    void defaultP0LevelIgnoresP1OnlyReportWhileP1LevelNotifies() {
        stubRun("COMPLETED", "【巡检报告】1)【P1】账务差异待确认");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, defaultConfig(scope), null);
        when(users.findBusinessLineMembers("BL000001")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "保费分期")));
        notifier.notifyCompletion("run-1");
        verifyNoInteractions(notifications); // 默认 P0：仅 P1 不触发

        stubRun("COMPLETED", "【巡检报告】1)【P1】账务差异待确认");
        stubTaskAndAgent(scope, withAlerts(scope, "P1", List.of("INBOX")), null);
        notifier.notifyCompletion("run-2");
        verify(notifications).createSystemNotification(eq("alice"), eq("AGENT_PATROL"), anyString(), contains("【P1】"),
                eq("BL000001"), eq("PROD"), eq("AGENT_RUN:run-2"));
    }

    @Test
    void p2LevelNotifiesOnAnyPriorityMark() {
        stubRun("COMPLETED", "【巡检报告】1)【P2】低优先提示");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, withAlerts(scope, "P2", List.of("INBOX")), null);
        when(users.findBusinessLineMembers("BL000001")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "保费分期")));

        notifier.notifyCompletion("run-1");

        verify(notifications).createSystemNotification(anyString(), eq("AGENT_PATROL"), anyString(), contains("【P2】"),
                eq("BL000001"), eq("PROD"), eq("AGENT_RUN:run-1"));
    }

    @Test
    void nonCompletedRunOrNoMarksDoNotNotify() {
        stubRun("COMPLETED", "本轮无异常");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"), defaultConfig(new AgentScope("BL000001", "PROD")), null);
        notifier.notifyCompletion("run-1");

        stubRun("FAILED", "1)【P0】失败原因");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"), defaultConfig(new AgentScope("BL000001", "PROD")), null);
        notifier.notifyCompletion("run-1");

        verifyNoInteractions(notifications, wecom);
    }

    @Test
    void wecomChannelDecryptsTaskWebhookAndSendsMarkdown() {
        String webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=task-token";
        stubRun("COMPLETED", "【巡检报告】1)【P0】修复采集");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, withAlerts(scope, "P0", List.of("WECOM")), encryptedWebhook(webhookUrl));
        when(wecom.sendTo(eq(webhookUrl), anyString())).thenReturn(new WecomRobotSendResult(true, 0, "ok"));

        notifier.notifyCompletion("run-1");

        var content = ArgumentCaptor.forClass(String.class);
        verify(wecom).sendTo(eq(webhookUrl), content.capture());
        assertTrue(content.getValue().startsWith("## [数字员工巡检]"));
        assertTrue(content.getValue().contains("【P0】"));
        verifyNoInteractions(notifications); // 方式仅企微，不发站内信
        verify(mapper).insertAudit(anyString(), eq("RUN"), eq("run-1"), eq("WEBHOOK_SENT"), eq("agent-runtime"), anyString(), any());
    }

    @Test
    void wecomWithoutAddressSkipsWithAudit() {
        stubRun("COMPLETED", "【巡检报告】1)【P0】修复采集");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, withAlerts(scope, "P0", List.of("WECOM")), null);

        notifier.notifyCompletion("run-1");

        verifyNoInteractions(wecom, notifications);
        verify(mapper).insertAudit(anyString(), eq("RUN"), eq("run-1"), eq("WEBHOOK_SKIPPED"), eq("agent-runtime"), anyString(), any());
    }

    @Test
    void wecomFailureDoesNotAffectInboxChannel() {
        String webhookUrl = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=task-token";
        stubRun("COMPLETED", "【巡检报告】1)【P0】修复采集");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, withAlerts(scope, "P0", List.of("INBOX", "WECOM")), encryptedWebhook(webhookUrl));
        when(wecom.sendTo(eq(webhookUrl), anyString())).thenThrow(new IllegalArgumentException("消息内容超出企微 markdown 上限 4096 字节"));
        when(users.findBusinessLineMembers("BL000001")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "保费分期")));

        assertDoesNotThrow(() -> notifier.notifyCompletion("run-1"));

        verify(notifications).createSystemNotification(eq("alice"), eq("AGENT_PATROL"), anyString(), contains("【P0】"),
                eq("BL000001"), eq("PROD"), eq("AGENT_RUN:run-1"));
        verify(mapper).insertAudit(anyString(), eq("RUN"), eq("run-1"), eq("WEBHOOK_FAILED"), eq("agent-runtime"), anyString(), any());
    }

    @Test
    void inboxFailureDoesNotPropagate() {
        stubRun("COMPLETED", "1)【P0】采集失败");
        var scope = new AgentScope("BL000001", "PROD");
        stubTaskAndAgent(scope, defaultConfig(scope), null);
        when(users.findBusinessLineMembers("BL000001")).thenThrow(new IllegalStateException("用户服务不可用"));

        assertDoesNotThrow(() -> notifier.notifyCompletion("run-1"));
        verifyNoInteractions(notifications);
    }

    /** 兼容构造器（无预警字段）等价 P0+站内信，代表存量 Agent。 */
    private AgentConfiguration defaultConfig(AgentScope scope) {
        return new AgentConfiguration("巡检助手", "OPS", "职责", 1L, null, List.of(scope), 30, 1800, 1);
    }

    private AgentConfiguration withAlerts(AgentScope scope, String level, List<String> channels) {
        return new AgentConfiguration("巡检助手", "OPS", "职责", 1L, null, List.of(scope), 30, 1800, 1, level, channels);
    }

    private String encryptedWebhook(String webhookUrl) {
        return AgentWorkbenchService.WEBHOOK_CIPHER_PREFIX + crypto.encrypt(webhookUrl);
    }

    private void stubRun(String status, String result) {
        var created = LocalDateTime.of(2026, 11, 12, 8, 0);
        when(mapper.findRun(anyString())).thenReturn(new AgentRunEntity("run-1", "task-1", "agent-1", "trigger", "admin",
                "{}", "{}", "{}", status, 1, 0, false, null, null, null, result, null, 0, created, created));
    }

    private void stubTaskAndAgent(AgentScope scope, AgentConfiguration config, String storedWebhook) {
        var created = LocalDateTime.of(2026, 11, 12, 8, 0);
        var spec = new AgentTaskSpecification("巡检保费分期的监测", null, "输出报告", List.of("REPORT"),
                List.of(scope), List.of(), "FULL", storedWebhook);
        when(mapper.findTask("task-1")).thenReturn(new AgentTaskEntity("task-1", "agent-1", "key", "hash",
                json.writeValueAsString(spec), "admin", "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false, null, 0,
                created, created));
        when(mapper.findAgent("agent-1")).thenReturn(new AgentDefinitionEntity("agent-1", "巡检助手",
                json.writeValueAsString(config), true, "admin", 0, created, created));
    }
}
