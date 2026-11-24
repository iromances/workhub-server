package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.AgentRunEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AgentExecutionCoordinatorTest {
    @Test
    void drainsMoreThanOldGlobalMaximumInOneTick() {
        var store = mock(AgentRunStore.class);
        var sequence = new AtomicInteger();
        when(store.claim()).thenAnswer(invocation -> {
            int index = sequence.getAndIncrement();
            if (index >= 40) return null;
            var run = mock(AgentRunEntity.class);
            when(run.id()).thenReturn("run-" + index);
            when(run.ownerToken()).thenReturn("owner-" + index);
            return run;
        });
        // 不调用模型；只观察本轮调度是否把所有已领取任务交给执行器。
        doThrow(new AgentRunStore.LeaseLost("test stop")).when(store).requireActive(anyString(), anyString());
        var coordinator = new AgentExecutionCoordinator(mock(AgentMapper.class), mock(AgentWorkbenchService.class), store,
                mock(AiProviderConfigService.class), mock(AgentRunNotifier.class), mock(AgentIssueService.class), List.of());
        try {
            coordinator.tick();
            verify(store, times(41)).claim();
            verify(store, timeout(5000).times(40)).requireActive(anyString(), anyString());
        } finally { coordinator.close(); }
    }
}
