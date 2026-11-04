package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentDeploymentMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Set;
import java.util.concurrent.*;

/** 只消费已经人工批准且落盘的出站请求；DB 租约决定跨实例所有权。 */
@Service
public class AgentReleaseCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AgentReleaseCoordinator.class);
    private final AgentDeploymentMapper deployments;
    private final AgentDeploymentDispatcher dispatcher;
    private final ExecutorService workers = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("agent-release-", 0).factory());
    private final Set<String> local = ConcurrentHashMap.newKeySet();
    public AgentReleaseCoordinator(AgentDeploymentMapper deployments, AgentDeploymentDispatcher dispatcher) { this.deployments = deployments; this.dispatcher = dispatcher; }
    public synchronized void tick() {
        if (workers.isShutdown()) return;
        for (String id : deployments.due(LocalDateTime.now(ZoneOffset.UTC))) {
            if (local.size() >= 4) break;
            if (!local.add(id)) continue;
            try {
                workers.submit(() -> {
                    try { dispatcher.advance(id); }
                    catch (AgentDeploymentStore.LeaseLost ex) { log.info("Agent release lease lost. requestId={}", id); }
                    catch (RuntimeException ex) { log.warn("Agent release check failed. requestId={}, errorType={}", id, ex.getClass().getSimpleName()); }
                    finally { local.remove(id); }
                });
            } catch (RejectedExecutionException ex) { local.remove(id); }
        }
    }
    @PreDestroy public synchronized void close() { workers.shutdownNow(); }
}
