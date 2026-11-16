package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgentWorkbenchService {
    private static final Set<String> TERMINAL = Set.of("COMPLETED", "FAILED", "CANCELLED");
    private final AgentMapper mapper;
    private final AgentAccessService access;
    private final AiProviderConfigService providers;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AgentWorkbenchService(AgentMapper mapper, AgentAccessService access, AiProviderConfigService providers,
                                 ObjectMapper json) {
        this(mapper, access, providers, json, Clock.systemUTC());
    }

    AgentWorkbenchService(AgentMapper mapper, AgentAccessService access, AiProviderConfigService providers,
                           ObjectMapper json, Clock clock) {
        this.mapper = mapper;
        this.access = access;
        this.providers = providers;
        this.json = json;
        this.clock = clock;
    }

    public AgentPage<AgentDefinitionResponse> list(String user, int page, int pageSize) {
        var actor = access.actor(user);
        actor.require("agent:view");
        String owner = actor.has("agent:manage") ? null : actor.name();
        int size = pageSize(pageSize);
        return new AgentPage<>(mapper.countAgents(owner),
                mapper.listAgents(owner, offset(page, size), size).stream().map(this::response).toList());
    }

    public AgentDefinitionResponse detail(String id, String user) {
        var actor = access.actor(user);
        actor.require("agent:view");
        var agent = mapper.findAgent(id);
        access.requireAgent(actor, agent);
        return response(agent);
    }

    @Transactional
    public AgentDefinitionResponse create(AgentSaveRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        validateConfiguration(request.configuration(), actor);
        LocalDateTime now = now();
        var agent = new AgentDefinitionEntity(id(), request.configuration().name(), encode(request.configuration()),
                false, actor.name(), 0, now, now);
        mapper.insertAgent(agent);
        audit("AGENT", agent.id(), "CREATE", actor.name(), request.configuration());
        return response(agent);
    }

    @Transactional
    public AgentDefinitionResponse update(String id, AgentSaveRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        var agent = mapper.lockAgent(id);
        access.requireAgent(actor, agent);
        requireVersion(agent.version(), request.expectedVersion());
        validateConfiguration(request.configuration(), actor);
        checkChanged(mapper.updateAgent(new AgentDefinitionEntity(id, request.configuration().name(),
                encode(request.configuration()), agent.enabled(), agent.ownerName(), agent.version(), agent.createdAt(), now())));
        audit("AGENT", id, "UPDATE", actor.name(), request);
        return response(mapper.findAgent(id));
    }

    @Transactional
    public AgentDefinitionResponse setEnabled(String id, long version, boolean enabled, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        var agent = mapper.lockAgent(id);
        access.requireAgent(actor, agent);
        requireVersion(agent.version(), version);
        if (enabled) {
            validateConfiguration(configuration(agent.configurationJson()), actor);
            requireProviderEnabled(configuration(agent.configurationJson()));
        }
        checkChanged(mapper.setEnabled(id, version, enabled, now()));
        if (!enabled) {
            for (String runId : mapper.findActiveRunIds(id)) {
                AgentRunEntity run = mapper.lockRun(runId);
                if (!TERMINAL.contains(run.status()) && !run.cancelRequested()) {
                    checkChanged(mapper.requestCancel(runId, run.version(), now()));
                    event(runId, "disabled:" + (version + 1), "CANCEL_REQUESTED", Map.of("reason", "Agent 已停用"), actor.name());
                }
            }
        }
        audit("AGENT", id, enabled ? "ENABLE" : "DISABLE", actor.name(), Map.of("version", version));
        return response(mapper.findAgent(id));
    }

    @Transactional
    public AgentTaskResponse createTask(String agentId, AgentTaskCreateRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:assign");
        var agent = mapper.lockAgent(agentId);
        access.requireAgent(actor, agent);
        String hash = hash(encode(request));
        AgentTaskEntity existing = mapper.findTaskByKey(agentId, request.requestKey());
        if (existing != null) {
            if (!existing.requestedBy().equals(actor.name()) || !hash.equals(existing.requestHash())) {
                throw new IllegalArgumentException("此请求标识已经用于另一份任务内容");
            }
            return response(existing);
        }
        validateTask(request.specification(), configuration(agent.configurationJson()), actor);
        AgentSchedule.validate(request.schedule());
        var schedule = request.schedule();
        var task = new AgentTaskEntity(id(), agentId, request.requestKey(), hash, encode(request.specification()),
                actor.name(), schedule.mode(), schedule.cronExpression(), schedule.zoneId(), schedule.misfirePolicy(),
                schedule.enabled(), AgentSchedule.next(schedule, clock.instant()), 0, now(), now());
        mapper.insertTask(task);
        audit("TASK", task.id(), "CREATE", actor.name(), request);
        if (request.runImmediately()) enqueue(task, agent, "initial:" + request.requestKey(), actor.name());
        return response(task);
    }

    public AgentPage<AgentTaskResponse> tasks(String agentId, String user, int page, int pageSize) {
        var actor = access.actor(user);
        actor.require("agent:task:view");
        access.requireAgent(actor, mapper.findAgent(agentId));
        int size = pageSize(pageSize);
        return new AgentPage<>(mapper.countTasks(agentId),
                mapper.listTasks(agentId, offset(page, size), size).stream().map(this::response).toList());
    }

    public AgentTaskResponse task(String taskId, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:view");
        var task = requireTask(taskId);
        access.requireAgent(actor, mapper.findAgent(task.agentId()));
        return response(task);
    }

    @Transactional
    public AgentTaskResponse schedule(String taskId, AgentScheduleRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var task = mapper.lockTask(taskId);
        requireVersion(task.version(), request.expectedVersion());
        AgentSchedule.validate(request);
        checkChanged(mapper.updateSchedule(new AgentTaskEntity(task.id(), task.agentId(), task.requestKey(), task.requestHash(),
                task.specificationJson(), task.requestedBy(), request.mode(), request.cronExpression(), request.zoneId(),
                request.misfirePolicy(), request.enabled(), AgentSchedule.next(request, clock.instant()), task.version(), task.createdAt(), now())));
        audit("TASK", taskId, "SCHEDULE", actor.name(), request);
        return response(mapper.findTask(taskId));
    }

    /** 修改任务规格（可选一并调整执行方式）；已有运行保留原规格快照，后续新运行按新规格重新校验并执行。 */
    @Transactional
    public AgentTaskResponse updateTask(String taskId, AgentTaskUpdateRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var task = mapper.lockTask(taskId);
        requireVersion(task.version(), request.expectedVersion());
        validateTask(request.specification(), configuration(agent.configurationJson()), actor);
        if (request.schedule() != null) AgentSchedule.validate(request.schedule());
        var nextRunAt = request.schedule() == null ? null : AgentSchedule.next(request.schedule(), clock.instant());
        checkChanged(mapper.updateTaskSpecification(task.id(), task.version(), encode(request.specification()),
                request.schedule(), nextRunAt, now()));
        audit("TASK", taskId, "UPDATE_SPEC", actor.name(),
                request.schedule() == null ? request.specification() : Map.of("specification", request.specification(), "schedule", request.schedule()));
        return response(mapper.findTask(taskId));
    }

    /** 物理删除任务及其运行/事件/产物；过程数据保留证据，存在即拒绝，删除本身先写审计。 */
    @Transactional
    public void deleteTask(String taskId, long expectedVersion, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var task = mapper.lockTask(taskId);
        requireVersion(task.version(), expectedVersion);
        if (task.scheduleEnabled()) throw new IllegalArgumentException("周期任务须先暂停调度后再删除");
        if (mapper.countUnfinishedRuns(taskId) > 0)
            throw new IllegalArgumentException("任务存在未结束的运行（排队、执行中或待核对），请先取消并等待结束");
        var counts = mapper.taskRemovalCounts(taskId);
        if (counts.hasProcessData())
            throw new IllegalArgumentException("任务运行已产生" + counts.processCategories() + "等过程数据，为保留证据不能删除；可停用任务替代");
        var spec = specification(task.specificationJson());
        audit("TASK", taskId, "DELETE", actor.name(), Map.of("objective", spec.objective(), "requestKey", task.requestKey(),
                "deletedRuns", mapper.countRuns(taskId), "deletedEvents", counts.events(), "deletedArtifacts", counts.artifacts()));
        mapper.deleteArtifactsByTask(taskId);
        mapper.deleteEventsByTask(taskId);
        mapper.deleteRunsByTask(taskId);
        checkChanged(mapper.deleteTaskById(taskId, task.version()));
    }

    /** 物理删除 Agent 及其任务/运行/事件/产物；须已停用且无周期、未结束运行与过程数据，删除本身先写审计。 */
    @Transactional
    public void deleteAgent(String id, long expectedVersion, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        var agent = mapper.lockAgent(id);
        access.requireAgent(actor, agent);
        requireVersion(agent.version(), expectedVersion);
        if (agent.enabled()) throw new IllegalArgumentException("Agent 尚未停用，请先停用后再删除");
        if (mapper.countEnabledSchedulesByAgent(id) > 0)
            throw new IllegalArgumentException("Agent 存在启用中的周期任务，请先暂停后再删除");
        if (mapper.countUnfinishedRunsByAgent(id) > 0)
            throw new IllegalArgumentException("Agent 存在未结束的运行（排队、执行中或待核对），请先取消并等待结束");
        var counts = mapper.agentRemovalCounts(id);
        if (counts.hasProcessData())
            throw new IllegalArgumentException("Agent 运行已产生" + counts.processCategories() + "等过程数据，为保留证据不能删除；可停用 Agent 替代");
        audit("AGENT", id, "DELETE", actor.name(), Map.of("name", agent.name(),
                "deletedTasks", mapper.countTasks(id), "deletedRuns", mapper.countRunsByAgent(id),
                "deletedEvents", counts.events(), "deletedArtifacts", counts.artifacts()));
        mapper.deleteArtifactsByAgent(id);
        mapper.deleteEventsByAgent(id);
        mapper.deleteRunsByAgent(id);
        mapper.deleteTasksByAgent(id);
        checkChanged(mapper.deleteAgentById(id, agent.version()));
    }

    @Transactional
    public AgentRunResponse run(String taskId, AgentRunRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var task = mapper.lockTask(taskId);
        String triggerKey = "manual:" + actor.name() + ":" + request.requestKey();
        if (triggerKey.length() > 128) throw new IllegalArgumentException("请求标识过长");
        var existing = mapper.findRunByKey(taskId, triggerKey);
        if (existing != null) return response(existing);
        requireVersion(task.version(), request.expectedVersion());
        return response(enqueue(task, agent, triggerKey, actor.name()));
    }

    /** 调度器每个到期任务单独调用事务，最多补一次，运行不依赖请求线程。 */
    @Transactional
    public void dispatchScheduledTask(String taskId) {
        AgentTaskEntity initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        var task = mapper.lockTask(taskId);
        LocalDateTime now = now();
        if (!agent.enabled() || !task.scheduleEnabled() || task.nextRunAt() == null || task.nextRunAt().isAfter(now)) return;
        AgentScheduleRequest schedule = schedule(task);
        LocalDateTime following = AgentSchedule.next(schedule, task.nextRunAt().toInstant(ZoneOffset.UTC));
        boolean missedMultiple = !following.isAfter(now);
        if (!missedMultiple || "RUN_ONCE".equals(task.misfirePolicy())) {
            enqueue(task, agent, "schedule:" + task.nextRunAt(), "scheduler");
        } else {
            audit("TASK", taskId, "MISFIRE_SKIPPED", "scheduler", Map.of("scheduledFor", task.nextRunAt()));
        }
        checkChanged(mapper.advanceSchedule(taskId, task.version(), AgentSchedule.next(schedule, clock.instant()), now));
    }

    public AgentPage<AgentRunResponse> runs(String taskId, String user, int page, int pageSize) {
        task(taskId, user);
        int size = pageSize(pageSize);
        return new AgentPage<>(mapper.countRuns(taskId),
                mapper.listRuns(taskId, offset(page, size), size).stream().map(this::response).toList());
    }

    public AgentRunDetailResponse runDetail(String taskId, String runId, String user) {
        task(taskId, user);
        var run = requireRun(taskId, runId, false);
        return new AgentRunDetailResponse(response(run), configuration(run.configurationJson()),
                specification(run.specificationJson()), json.readValue(run.providerBindingJson(), AgentProviderBinding.class), mapper.listEvents(runId), mapper.listArtifacts(runId));
    }

    @Transactional
    public AgentRunResponse cancel(String taskId, String runId, long version, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var task = requireTask(taskId);
        access.requireAgent(actor, mapper.lockAgent(task.agentId()));
        var run = requireRun(taskId, runId, true);
        if (TERMINAL.contains(run.status()) || run.cancelRequested()) return response(run);
        requireVersion(run.version(), version);
        checkChanged(mapper.requestCancel(runId, version, now()));
        event(runId, "cancel:" + version, "CANCEL_REQUESTED", Map.of("version", version), actor.name());
        return response(mapper.findRun(runId));
    }

    /** 重试创建新运行并保留原证据；外部执行尚未对账时不能以重试绕过占位。 */
    @Transactional
    public AgentRunResponse retry(String taskId, String runId, AgentRunRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var task = mapper.lockTask(taskId);
        var previous = requireRun(taskId, runId, true);
        String key = "retry:" + runId + ":" + request.requestKey();
        var existing = mapper.findRunByKey(taskId, key);
        if (existing != null) return response(existing);
        requireVersion(previous.version(), request.expectedVersion());
        if (previous.externalHandle() != null || previous.ownerToken() != null) throw new IllegalStateException("原运行仍有未确认结束的外部执行，不能重试");
        if (!Set.of("FAILED", "CANCELLED", "WAITING_RESOURCE").contains(previous.status())) throw new IllegalArgumentException("当前状态不能重试，请补充指令或等待执行结束");
        var next = enqueue(task, agent, key, actor.name());
        event(next.id(), "retry-origin", "RETRY", Map.of("previousRunId", runId), actor.name());
        audit("RUN", runId, "RETRY", actor.name(), Map.of("newRunId", next.id()));
        return response(next);
    }

    @Transactional
    public AgentRunResponse message(String taskId, AgentMessageRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:task:operate");
        var task = requireTask(taskId);
        access.requireAgent(actor, mapper.lockAgent(task.agentId()));
        var run = requireRun(taskId, request.runId(), true);
        String key = "input:" + request.requestKey();
        String payload = encode(Map.of("message", request.message()));
        var previous = mapper.findEventByKey(run.id(), key);
        if (previous != null) {
            if (!previous.actor().equals(actor.name()) || !previous.payloadJson().equals(payload)) {
                throw new IllegalArgumentException("此补充指令标识已使用，内容不一致");
            }
            return response(run);
        }
        requireVersion(run.version(), request.expectedVersion());
        if (mapper.listEvents(run.id()).stream().filter(e -> "HUMAN_INPUT".equals(e.kind())).count() >= 100) {
            throw new IllegalArgumentException("单次运行的补充指令已达上限，请分派后续任务");
        }
        // 外部执行句柄未对账完成时，回复不能隐式恢复或重复外部操作。
        if ("WAITING_INPUT".equals(run.status()) && run.externalHandle() != null) {
            throw new IllegalStateException("外部执行状态尚待核实，不能通过补充指令重新启动");
        }
        checkChanged(mapper.acceptMessage(run.id(), run.version(), now()));
        event(run.id(), key, "HUMAN_INPUT", Map.of("message", request.message()), actor.name());
        return response(mapper.findRun(run.id()));
    }

    private AgentRunEntity enqueue(AgentTaskEntity task, AgentDefinitionEntity agent, String triggerKey, String initiatedBy) {
        var existing = mapper.findRunByKey(task.id(), triggerKey);
        if (existing != null) return existing;
        if (!agent.enabled()) throw new IllegalArgumentException("Agent 已停用，不能开始任务");
        var actor = access.actor(task.requestedBy());
        actor.require("agent:task:assign");
        access.requireAgent(actor, agent);
        var configuration = configuration(agent.configurationJson());
        validateTask(specification(task.specificationJson()), configuration, actor);
        requireProviderEnabled(configuration);
        if (mapper.countPendingRuns(agent.id()) >= 100) throw new IllegalStateException("Agent 待执行任务达到上限");
        var provider = providers.getEnabledById(configuration.providerConfigId());
        String model = configuration.model() == null || configuration.model().isBlank() ? provider.getDefaultModel() : configuration.model();
        var snapshot = new AgentConfiguration(configuration.name(), configuration.instructions(), configuration.providerConfigId(), model,
                configuration.scopes(), configuration.maxSteps(), configuration.maxRunSeconds(), configuration.maxConcurrentRuns());
        var run = new AgentRunEntity(id(), task.id(), agent.id(), triggerKey, task.requestedBy(),
                encode(snapshot), task.specificationJson(), encode(AgentProviderBinding.from(provider)), "QUEUED", 0, 0, false,
                null, null, null, null, null, 0, now(), now());
        mapper.insertRun(run);
        event(run.id(), "created", "QUEUED", Map.of("trigger", triggerKey, "agentVersion", agent.version(), "taskVersion", task.version(), "requestedBy", task.requestedBy()), initiatedBy);
        return run;
    }

    /** 物理删除运行记录及其事件/产物：用于清理待核对僵尸运行释放全局并发额度；活动运行不可删。 */
    @Transactional
    public void deleteRun(String taskId, String runId, long expectedVersion, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        var initial = requireTask(taskId);
        var agent = mapper.lockAgent(initial.agentId());
        access.requireAgent(actor, agent);
        var run = requireRun(taskId, runId, true);
        requireVersion(run.version(), expectedVersion);
        if (Set.of("QUEUED", "RUNNING", "CANCELLING").contains(run.status())) {
            throw new IllegalArgumentException("排队或执行中的运行不能删除，请先取消并等待结束");
        }
        audit("RUN", runId, "DELETE", actor.name(), Map.of("reason",
                run.ownerToken() != null ? "清理待核对运行并释放并发额度" : "删除运行记录",
                "unresolvedHandle", run.externalHandle() != null));
        mapper.deleteArtifactsByRun(runId);
        mapper.deleteEventsByRun(runId);
        checkChanged(mapper.deleteRunById(runId, run.version()));
    }

    private void validateConfiguration(AgentConfiguration configuration, AgentAccessService.Actor actor) {
        providers.requireExisting(configuration.providerConfigId());
        access.validateScopes(configuration.scopes());
    }

    private void validateTask(AgentTaskSpecification task, AgentConfiguration configuration, AgentAccessService.Actor actor) {
        task.validateCompletionTargets();
        if (!configuration.scopes().containsAll(task.scopes())) {
            throw new IllegalArgumentException("任务范围超出 Agent 的授权配置");
        }
        access.validateScopes(task.scopes());
    }

    private void requireProviderEnabled(AgentConfiguration configuration) {
        var provider = providers.getEnabledById(configuration.providerConfigId());
        if ((configuration.model() == null || configuration.model().isBlank())
                && (provider.getDefaultModel() == null || provider.getDefaultModel().isBlank())) {
            throw new IllegalArgumentException("请选择模型或在 AI 接入中配置默认模型");
        }
    }

    private AgentTaskEntity requireTask(String id) {
        var task = mapper.findTask(id);
        if (task == null) throw new IllegalArgumentException("Agent 任务不存在");
        return task;
    }

    private AgentRunEntity requireRun(String taskId, String runId, boolean lock) {
        var run = lock ? mapper.lockRun(runId) : mapper.findRun(runId);
        if (run == null || !run.taskId().equals(taskId)) throw new IllegalArgumentException("运行不属于当前任务");
        return run;
    }

    private void event(String runId, String key, String kind, Object payload, String actor) {
        mapper.insertEvent(new AgentEventEntity(id(), runId, key, mapper.nextEventOrdinal(runId), kind, encode(payload), actor, now()));
    }

    private void audit(String type, String objectId, String action, String actor, Object payload) {
        mapper.insertAudit(id(), type, objectId, action, actor, encode(payload), now());
    }

    private AgentDefinitionResponse response(AgentDefinitionEntity entity) {
        return new AgentDefinitionResponse(entity.id(), configuration(entity.configurationJson()), entity.enabled(),
                entity.ownerName(), entity.version(), entity.createdAt(), entity.updatedAt());
    }

    private AgentTaskResponse response(AgentTaskEntity entity) {
        return new AgentTaskResponse(entity.id(), entity.agentId(), specification(entity.specificationJson()),
                entity.requestedBy(), schedule(entity), entity.nextRunAt(), entity.version(), entity.createdAt(), entity.updatedAt());
    }

    private AgentRunResponse response(AgentRunEntity entity) {
        return new AgentRunResponse(entity.id(), entity.taskId(), entity.agentId(), entity.status(), entity.stepCount(),
                entity.elapsedMillis(), entity.cancelRequested(), entity.resultText(), entity.errorMessage(),
                entity.ownerToken() == null && entity.externalHandle() == null && Set.of("FAILED", "CANCELLED", "WAITING_RESOURCE").contains(entity.status()),
                entity.version(), entity.createdAt(), entity.updatedAt());
    }

    private AgentScheduleRequest schedule(AgentTaskEntity task) {
        return new AgentScheduleRequest(task.version(), task.scheduleMode(), task.cronExpression(), task.zoneId(), task.misfirePolicy(), task.scheduleEnabled());
    }

    private AgentConfiguration configuration(String value) { return json.readValue(value, AgentConfiguration.class); }
    private AgentTaskSpecification specification(String value) { return json.readValue(value, AgentTaskSpecification.class); }
    private String encode(Object value) { return json.writeValueAsString(value); }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static String id() { return UUID.randomUUID().toString(); }
    private static int pageSize(int size) { return Math.clamp(size, 1, 100); }
    private static int offset(int page, int size) { return Math.multiplyExact(Math.max(page, 1) - 1, size); }
    private static void checkChanged(int rows) {
        if (rows != 1) throw new IllegalStateException("状态已变化，请刷新后重试");
    }
    private static void requireVersion(long actual, Long expected) {
        if (expected == null || actual != expected) throw new IllegalStateException("版本已变化，请刷新后重试");
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }
}
