package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.AgentRunEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 工作线程只承载本机执行，数据库才是任务真相；重启或多实例不依赖内存去重。 */
@Service
public class AgentExecutionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentExecutionCoordinator.class);
    private final AgentMapper mapper;
    private final AgentWorkbenchService workbench;
    private final AgentRunStore store;
    private final AiProviderConfigService providers;
    private final Map<String, AgentExecutionAdapter> adapters;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService pulses = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("agent-heartbeat").factory());
    private final ConcurrentMap<String, Execution> local = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();

    public AgentExecutionCoordinator(AgentMapper mapper, AgentWorkbenchService workbench, AgentRunStore store,
                                     AiProviderConfigService providers, List<AgentExecutionAdapter> adapters) {
        this.mapper = mapper;
        this.workbench = workbench;
        this.store = store;
        this.providers = providers;
        Map<String, AgentExecutionAdapter> registered = new HashMap<>();
        adapters.forEach(adapter -> {
            if (registered.putIfAbsent(adapter.channelType(), adapter) != null) throw new IllegalArgumentException("执行通道重复: " + adapter.channelType());
        });
        this.adapters = Map.copyOf(registered);
        pulses.scheduleWithFixedDelay(this::heartbeat, 1, 5, TimeUnit.SECONDS);
    }

    public void tick(int globalLimit) {
        if (closed.get()) return;
        var now = LocalDateTime.now(ZoneOffset.UTC);
        for (String task : mapper.findDueTaskIds(now)) {
            try { workbench.dispatchScheduledTask(task); }
            catch (RuntimeException ex) { log.warn("Agent scheduled task was not enqueued. taskId={}, errorType={}", task, ex.getClass().getSimpleName()); }
        }
        for (String run : mapper.findExpiredRunIds(now)) {
            try { store.recoverExpired(run); }
            catch (RuntimeException ex) { log.warn("Agent run recovery failed. runId={}, errorType={}", run, ex.getClass().getSimpleName()); }
        }
        for (int i = 0; i < globalLimit && !closed.get(); i++) {
            AgentRunEntity claimed = store.claim(globalLimit);
            if (claimed == null) break;
            var execution = new Execution(claimed);
            local.put(claimed.id(), execution);
            try {
                workers.submit(() -> execute(execution));
            } catch (RejectedExecutionException ex) {
                local.remove(claimed.id());
                store.finish(claimed.id(), claimed.ownerToken(), "WAITING_RESOURCE", null, "执行器正在停止，请稍后重试");
            }
        }
    }

    private void execute(Execution execution) {
        execution.thread = Thread.currentThread();
        var claim = execution.claim;
        try {
            store.requireActive(claim.id(), claim.ownerToken());
            var provider = providers.getEnabledById(store.context(claim).configuration().providerConfigId());
            var adapter = adapters.get(provider.getChannelType());
            if (adapter == null) {
                store.finish(claim.id(), claim.ownerToken(), "WAITING_RESOURCE", null, "所选 AI 通道缺少可用执行适配器: " + provider.getChannelType());
                return;
            }
            adapter.execute(claim.id(), claim.ownerToken());
        } catch (AgentRunStore.LeaseLost ex) {
            // 失租约执行器不能完成或复活任务，交由持久化恢复流程处理。
            log.info("Agent worker lost ownership. runId={}", claim.id());
        } catch (RuntimeException ex) {
            Thread.interrupted();
            try {
                var current = store.get(claim.id());
                if (current.externalHandle() == null) {
                    String state = ex instanceof AgentRunStore.BudgetExceeded ? "WAITING_INPUT" : "FAILED";
                    store.finish(claim.id(), claim.ownerToken(), state, null, ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                }
                // 有外部句柄时不推断调用已停止。心跳停止后，由恢复流程显式标记待对账。
            } catch (RuntimeException failure) {
                log.warn("Agent execution stopped with unresolved state. runId={}, errorType={}", claim.id(), failure.getClass().getSimpleName());
            }
        } finally {
            local.remove(claim.id(), execution);
        }
    }

    private void heartbeat() {
        for (var execution : local.values()) {
            try {
                var run = store.heartbeat(execution.claim.id(), execution.claim.ownerToken(), execution.elapsed());
                if (run.cancelRequested() || execution.elapsed() >= store.context(run).configuration().maxRunSeconds() * 1000L) execution.interrupt();
            } catch (RuntimeException ex) {
                // 数据库或租约不可用时立即停止本机继续行动，不能自发续租或转交另一个模型。
                execution.interrupt();
            }
        }
    }

    @PreDestroy
    public void close() {
        closed.set(true);
        pulses.shutdownNow();
        local.values().forEach(Execution::interrupt);
        workers.shutdownNow();
    }

    private static final class Execution {
        private final AgentRunEntity claim;
        private final long started = System.nanoTime();
        private volatile Thread thread;
        private Execution(AgentRunEntity claim) { this.claim = claim; }
        private long elapsed() { return claim.elapsedMillis() + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }
        private void interrupt() { if (thread != null) thread.interrupt(); }
    }
}
