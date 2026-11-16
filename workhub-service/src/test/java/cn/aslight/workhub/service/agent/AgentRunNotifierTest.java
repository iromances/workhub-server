package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.dao.system.UserMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.system.UserOptionResponse;
import cn.aslight.workhub.service.notification.NotificationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** REQ-172f8541：巡检完成通知——仅 COMPLETED 且含 P0/P1 时按任务范围业务线成员发站内信。 */
class AgentRunNotifierTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentMapper mapper = mock(AgentMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AgentRunNotifier notifier = new AgentRunNotifier(mapper, users, notifications, json);

    @Test
    void completedRunWithP0NotifiesScopeLineMembers() {
        var scope = new AgentScope("BL000001", "PROD");
        stubRun("COMPLETED", "【巡检报告】1)【P0】修复采集；其余正常");
        stubTaskAndAgent(scope);
        when(users.findBusinessLineMembers("BL000001")).thenReturn(List.of(new UserOptionResponse("alice", "Alice", "保费分期")));

        notifier.notifyCompletion("run-1");

        verify(notifications).createSystemNotification(eq("alice"), eq("AGENT_PATROL"), contains("[数字员工巡检] 巡检助手"),
                contains("【P0】"), eq("BL000001"), eq("PROD"), eq("AGENT_RUN:run-1"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void completedRunWithoutPriorityMarksOrNonCompletedRunDoesNotNotify() {
        stubRun("COMPLETED", "本轮无异常");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"));
        notifier.notifyCompletion("run-1");

        stubRun("FAILED", "1)【P0】失败原因");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"));
        notifier.notifyCompletion("run-2");

        verifyNoInteractions(notifications);
    }

    @Test
    void notificationFailureDoesNotPropagate() {
        stubRun("COMPLETED", "【P1】发现差异");
        stubTaskAndAgent(new AgentScope("BL000001", "PROD"));
        when(users.findBusinessLineMembers("BL000001")).thenThrow(new IllegalStateException("用户服务不可用"));

        assertDoesNotThrow(() -> notifier.notifyCompletion("run-1"));
        verifyNoInteractions(notifications);
    }

    private void stubRun(String status, String result) {
        var created = LocalDateTime.of(2026, 11, 12, 8, 0);
        when(mapper.findRun(anyString())).thenReturn(new AgentRunEntity("run-1", "task-1", "agent-1", "trigger", "admin",
                "{}", "{}", "{}", status, 1, 0, false, null, null, null, result, null, 0, created, created));
    }

    private void stubTaskAndAgent(AgentScope scope) {
        var created = LocalDateTime.of(2026, 11, 12, 8, 0);
        var spec = new AgentTaskSpecification("巡检保费分期的监测", null, "输出报告", List.of("REPORT"), List.of(scope));
        when(mapper.findTask("task-1")).thenReturn(new AgentTaskEntity("task-1", "agent-1", "key", "hash",
                json.writeValueAsString(spec), "admin", "MANUAL", null, "Asia/Shanghai", "RUN_ONCE", false, null, 0,
                created, created));
        var config = new AgentConfiguration("巡检助手", "职责", 1L, null, List.of(scope), 30, 1800, 1);
        when(mapper.findAgent("agent-1")).thenReturn(new AgentDefinitionEntity("agent-1", "巡检助手",
                json.writeValueAsString(config), true, "admin", 0, created, created));
    }
}
