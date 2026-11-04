package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.*;
import cn.aslight.workhub.model.ai.AiProviderConfigEntity;
import cn.aslight.workhub.service.ai.AiProviderConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 短事务持久化执行状态。数据库锁从不跨越 AI、CLI 或企业工具调用。 */
@Service
public class AgentRunStore {
    public static final int LEASE_SECONDS = 60;
    private static final Set<String> END_STATES = Set.of("COMPLETED", "FAILED", "CANCELLED", "WAITING_INPUT", "WAITING_RESOURCE");
    private final AgentMapper mapper;
    private final ObjectMapper json;
    private final AgentAccessService access;
    private final AgentToolData data;
    private final AiProviderConfigService providers;
    private final Clock clock;

    @Autowired
    public AgentRunStore(AgentMapper mapper, ObjectMapper json, AgentAccessService access, AgentToolData data, AiProviderConfigService providers) {
        this(mapper, json, access, data, providers, Clock.systemUTC());
    }

    AgentRunStore(AgentMapper mapper, ObjectMapper json, AgentAccessService access, AgentToolData data, AiProviderConfigService providers, Clock clock) {
        this.mapper = mapper;
        this.json = json;
        this.access = access;
        this.data = data;
        this.providers = providers;
        this.clock = clock;
    }

    @Transactional
    public AgentRunEntity claim(int globalLimit) {
        mapper.lockDispatcher();
        if (globalLimit < 1 || globalLimit > 32) throw new IllegalArgumentException("运行并发上限应为 1 到 32");
        if (mapper.countOwnedRuns(null) >= globalLimit) return null;
        for (String id : mapper.findQueuedRunIds()) {
            var initial = mapper.findRun(id);
            var agent = mapper.lockAgent(initial.agentId());
            var run = mapper.lockRun(id);
            if (agent == null || !agent.enabled() || !"QUEUED".equals(run.status()) || run.ownerToken() != null || run.externalHandle() != null) continue;
            var current = json.readValue(agent.configurationJson(), AgentConfiguration.class);
            int limit = Math.min(current.maxConcurrentRuns(), context(run).configuration().maxConcurrentRuns());
            if (mapper.countOwnedRuns(agent.id()) >= limit) continue;
            String token = UUID.randomUUID().toString();
            changed(mapper.claimRun(id, token, now().plusSeconds(LEASE_SECONDS), now()));
            append(id, "claim:" + token, "RUNNING", Map.of("recoveredStepCount", run.stepCount(), "elapsedMillis", run.elapsedMillis()));
            return mapper.findRun(id);
        }
        return null;
    }

    public AgentRunEntity get(String runId) {
        var run = mapper.findRun(runId);
        if (run == null) throw new IllegalArgumentException("Agent 运行不存在");
        return run;
    }

    public AgentToolContext context(AgentRunEntity run) {
        return new AgentToolContext(run.id(), run.taskId(), run.agentId(), run.requestedBy(), run.ownerToken(),
                json.readValue(run.configurationJson(), AgentConfiguration.class),
                json.readValue(run.specificationJson(), AgentTaskSpecification.class));
    }

    public List<AgentEventEntity> events(String runId) { return mapper.listEvents(runId); }

    public AgentToolContext contextForOwner(String runId, String owner) {
        return context(owned(get(runId), owner, false));
    }

    public AgentEventEntity event(String runId, String key) { return mapper.findEventByKey(runId, key); }

    public void requireActive(String runId, String owner) {
        var run = owned(get(runId), owner, false);
        requireBudget(run);
        access.requireRunAuthorization(context(run));
        requireProvider(run);
    }

    public AiProviderConfigEntity requireProvider(AgentRunEntity run) {
        var binding = json.readValue(run.providerBindingJson(), AgentProviderBinding.class);
        var provider = providers.getEnabledById(binding.providerConfigId());
        if (!binding.equals(AgentProviderBinding.from(provider))) throw new IllegalStateException("AI 接入配置已变化，请按新配置重新运行；未切换本次任务的 AI");
        return provider;
    }

    @Transactional
    public AgentRunEntity heartbeat(String runId, String owner, long elapsedMillis) {
        var run = owned(mapper.lockRun(runId), owner, true);
        changed(mapper.heartbeat(runId, owner, now().plusSeconds(LEASE_SECONDS), Math.max(elapsedMillis, run.elapsedMillis()), now()));
        return mapper.findRun(runId);
    }

    /** 持久化调用意图后才能发出请求；未知回执不会被重启或重试隐式重发。 */
    @Transactional
    public int beginModel(String runId, String owner, Object providerMetadata) {
        return beginModel(runId, owner, providerMetadata, null);
    }

    @Transactional
    public int beginModel(String runId, String owner, Object providerMetadata, Long inputThroughOrdinal) {
        var run = owned(mapper.lockRun(runId), owner, false);
        if (inputThroughOrdinal != null && mapper.listEvents(runId).stream().anyMatch(event -> "HUMAN_INPUT".equals(event.kind()) && event.ordinal() > inputThroughOrdinal)) {
            throw new InputChanged("准备调用期间收到新指令，请重新构造上下文");
        }
        requireBudget(run);
        access.requireRunAuthorization(context(run));
        requireNoExternal(run);
        requireProvider(run);
        if (run.stepCount() >= context(run).configuration().maxSteps()) throw new BudgetExceeded("Agent 行动步数已达上限");
        changed(mapper.incrementStep(runId, owner, now()));
        int step = run.stepCount() + 1;
        String key = "step:" + step;
        changed(mapper.setExternalHandle(runId, owner, json.writeValueAsString(Map.of("kind", "API", "key", key)), now()));
        append(runId, key + ":request", "MODEL_REQUEST", providerMetadata);
        return step;
    }

    @Transactional
    public void endModel(String runId, String owner, int step, Object response) {
        var run = receiptOwner(mapper.lockRun(runId), owner);
        requireHandle(run, "API", "step:" + step);
        appendReceipt(runId, "step:" + step + ":response", "MODEL_RESPONSE", response);
        if (liveReceipt(run)) changed(mapper.setExternalHandle(runId, owner, null, now()));
    }

    @Transactional
    public void record(String runId, String owner, String key, String kind, Object payload) {
        owned(mapper.lockRun(runId), owner, true);
        append(runId, key, kind, payload);
    }

    @Transactional
    public void beginTool(String runId, String owner, String key, AgentTool tool, JsonNode arguments, boolean consumesStep) {
        var run = owned(mapper.lockRun(runId), owner, false);
        requireBudget(run);
        var nativeHandle = nativeHandleWithoutActiveTool(run);
        if (nativeHandle != null) requireUnchangedInput(runId, nativeHandle.path("key").asText() + ":request");
        access.requireExecution(context(run), tool);
        requireProvider(run);
        if (consumesStep) {
            if (run.stepCount() >= context(run).configuration().maxSteps()) throw new BudgetExceeded("Agent 行动步数已达上限");
            changed(mapper.incrementStep(runId, owner, now()));
        }
        JsonNode handle = json.valueToTree(Map.of("kind", "TOOL", "key", key, "tool", tool.code(), "readOnly", tool.readOnly()));
        if (nativeHandle != null) { nativeHandle.set("activeTool", handle); handle = nativeHandle; }
        changed(mapper.setExternalHandle(runId, owner, json.writeValueAsString(handle), now()));
        append(runId, key + ":request", "TOOL_REQUEST", Map.of("tool", tool.code(), "arguments", arguments));
    }

    @Transactional
    public void beginApiTool(String runId, String owner, String key, AgentTool tool, JsonNode arguments, String modelRequestKey) {
        owned(mapper.lockRun(runId), owner, false);
        requireUnchangedInput(runId, modelRequestKey);
        beginTool(runId, owner, key, tool, arguments, false);
    }

    @Transactional
    public void endTool(String runId, String owner, String key, Object result) {
        var run = receiptOwner(mapper.lockRun(runId), owner);
        var handle = json.readTree(run.externalHandle());
        if ("CLI".equals(handle.path("kind").asText())) {
            var active = handle.path("activeTool");
            if (!"TOOL".equals(active.path("kind").asText()) || !key.equals(active.path("key").asText())) throw new IllegalStateException("CLI 工具回执不符");
            ((tools.jackson.databind.node.ObjectNode) handle).remove("activeTool");
        } else {
            requireHandle(run, "TOOL", key);
            handle = null;
        }
        appendReceipt(runId, key + ":result", "TOOL_RESULT", result);
        if (liveReceipt(run)) changed(mapper.setExternalHandle(runId, owner, handle == null ? null : json.writeValueAsString(handle), now()));
    }

    @Transactional
    public void beginNative(String runId, String owner, String attempt, String host) {
        beginNative(runId, owner, attempt, host, null);
    }

    @Transactional
    public void beginNative(String runId, String owner, String attempt, String host, Long inputThroughOrdinal) {
        var run = owned(mapper.lockRun(runId), owner, false);
        if (inputThroughOrdinal != null && mapper.listEvents(runId).stream().anyMatch(event -> "HUMAN_INPUT".equals(event.kind()) && event.ordinal() > inputThroughOrdinal)) {
            throw new InputChanged("准备 CLI 任务期间收到新指令，请重新构造上下文");
        }
        requireBudget(run);
        requireNoExternal(run);
        access.requireRunAuthorization(context(run));
        requireProvider(run);
        if (run.stepCount() >= context(run).configuration().maxSteps()) throw new BudgetExceeded("Agent 行动步数已达上限");
        changed(mapper.incrementStep(runId, owner, now()));
        changed(mapper.setExternalHandle(runId, owner, json.writeValueAsString(Map.of("kind", "CLI", "key", attempt, "host", host, "phase", "STARTING")), now()));
        append(runId, attempt + ":request", "CLI_REQUEST", Map.of("providerConfigId", context(run).configuration().providerConfigId(), "model", context(run).configuration().model()));
    }

    @Transactional
    public void nativeStarted(String runId, String owner, String attempt, long pid, String startedAt) {
        var run = owned(mapper.lockRun(runId), owner, true);
        requireHandle(run, "CLI", attempt);
        var handle = (tools.jackson.databind.node.ObjectNode) json.readTree(run.externalHandle());
        handle.put("phase", "RUNNING").put("pid", pid).put("startedAt", startedAt);
        changed(mapper.setExternalHandle(runId, owner, json.writeValueAsString(handle), now()));
        append(runId, attempt + ":started", "CLI_STARTED", Map.of("pid", pid, "startedAt", startedAt));
    }

    /** 本机已确认整个进程树停止才登记；活动企业工具需独立回执，不能因进程退出清除。 */
    @Transactional
    public void endNative(String runId, String owner, String attempt, Object receipt) {
        var run = receiptOwner(mapper.lockRun(runId), owner);
        requireHandle(run, "CLI", attempt);
        if (!json.valueToTree(receipt).path("processTreeStopped").asBoolean()) throw new IllegalArgumentException("缺少 CLI 进程树已停止的确认，不能解除外部句柄");
        appendReceipt(runId, attempt + ":result", "CLI_RESULT", receipt);
        if (liveReceipt(run) && !json.readTree(run.externalHandle()).hasNonNull("activeTool")) changed(mapper.setExternalHandle(runId, owner, null, now()));
    }

    /** 回读原生 CLI 已落盘的结果；消费标记与完成状态同事务，重启不重跑完整任务。 */
    @Transactional
    public boolean finishSavedNative(String runId, String owner) {
        owned(mapper.lockRun(runId), owner, true);
        var saved = mapper.listEvents(runId).reversed().stream().filter(event -> "CLI_RESULT".equals(event.kind())
                && event.eventKey().endsWith(":result") && mapper.findEventByKey(runId, event.eventKey().replaceFirst(":result$", ":handled")) == null).findFirst().orElse(null);
        if (saved == null) return false;
        requireNoExternal(get(runId));
        String attempt = saved.eventKey().substring(0, saved.eventKey().length() - ":result".length());
        var payload = json.readTree(saved.payloadJson());
        var result = payload.path("result");
        if (payload.path("replanRequested").asBoolean() && hasNewInputSince(runId, attempt + ":request")) {
            append(runId, attempt + ":handled", "INPUT_CHANGED", Map.of("message", "原 CLI 已停止，将携带新指令和既有工具回执重新规划"));
            return false;
        } else if (!payload.has("exitCode") || payload.path("exitCode").asInt(-1) != 0 || !result.isObject()
                || !Set.of("RESULT", "NEEDS_INPUT").contains(result.path("type").asText()) || result.path("summary").asText().isBlank()) {
            finish(runId, owner, "FAILED", null, "CLI 已确认停止，但没有有效最终结果；已有工具回执保留，请核对后重试");
        } else if (hasNewInputSince(runId, attempt + ":request")) {
            append(runId, attempt + ":handled", "INPUT_CHANGED", Map.of("message", "已收到新指令，将依据已有工具结果继续规划"));
            return false;
        } else {
            finish(runId, owner, "RESULT".equals(result.path("type").asText()) ? "COMPLETED" : "WAITING_INPUT", result.path("summary").asText(), null);
        }
        append(runId, attempt + ":handled", "STEP_COMPLETED", Map.of("source", "CLI_RESULT"));
        return true;
    }

    @Transactional
    public void finishDecision(String runId, String owner, String modelRequestKey, String status, String result) {
        owned(mapper.lockRun(runId), owner, true);
        requireUnchangedInput(runId, modelRequestKey);
        finish(runId, owner, status, result, null);
    }

    /** 新指令在 AI 开始执行后到达时，不能让旧答案直接结束任务。 */
    public boolean hasNewInputSince(String runId, String requestEventKey) {
        var request = mapper.findEventByKey(runId, requestEventKey);
        return request != null && mapper.listEvents(runId).stream().anyMatch(event -> "HUMAN_INPUT".equals(event.kind()) && event.ordinal() > request.ordinal());
    }

    @Transactional
    public void finish(String runId, String owner, String status, String result, String error) {
        if (!END_STATES.contains(status)) throw new IllegalArgumentException("不支持的结束状态");
        var run = owned(mapper.lockRun(runId), owner, true);
        requireNoExternal(run);
        var claim = mapper.findEventByKey(runId, "claim:" + owner);
        if (claim != null) {
            long elapsed = json.readTree(claim.payloadJson()).path("elapsedMillis").asLong()
                    + Math.max(0, java.time.Duration.between(claim.createdAt(), now()).toMillis());
            changed(mapper.heartbeat(runId, owner, now().plusSeconds(LEASE_SECONDS), Math.max(elapsed, run.elapsedMillis()), now()));
        }
        String actual = run.cancelRequested() ? "CANCELLED" : status;
        if ("COMPLETED".equals(actual) && !context(run).specification().expectedArtifacts().stream().allMatch("REPORT"::equals)) {
            actual = "WAITING_INPUT";
            error = "当前只有分析报告，尚未取得所要求的分支、实际测试或发布验证产物，任务未完成";
        }
        String safeResult = result == null ? null : data.sanitize(result).asText();
        String safeError = error == null ? null : data.sanitize(error).asText();
        if ("COMPLETED".equals(status) && !run.cancelRequested()) {
            if (safeResult == null || safeResult.isBlank()) throw new IllegalArgumentException("完成任务必须提供实际产物");
            String artifactKey = "report:" + owner;
            String payload = json.writeValueAsString(Map.of("text", safeResult));
            if (mapper.findArtifact(runId, artifactKey) == null) {
                mapper.insertArtifact(new AgentArtifactEntity(UUID.randomUUID().toString(), runId, artifactKey,
                        "REPORT", "AI_REPORT", payload, digest(payload), now()));
            }
        }
        if (safeError != null && safeError.length() > 2000) safeError = safeError.substring(0, 2000);
        if (run.stepCount() > 0) append(runId, "step:" + run.stepCount() + ":handled", "STEP_COMPLETED", Map.of("status", actual));
        append(runId, "finish:" + owner, actual, Map.of("result", safeResult == null ? "" : safeResult, "reason", safeError == null ? "" : safeError));
        changed(mapper.finishRun(runId, owner, actual, safeResult, safeError, now()));
    }

    /** 只有同一调用的实际回执才能消除未知句柄；无回执则保留所有权与并发占位。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void recoverExpired(String runId) {
        var initial = get(runId);
        mapper.lockAgent(initial.agentId());
        var run = mapper.lockRun(runId);
        if (run.ownerToken() == null || run.leaseUntil() == null || run.leaseUntil().isAfter(now())
                || !Set.of("RUNNING", "CANCELLING", "WAITING_RESOURCE").contains(run.status())) return;
        String remainingHandle = unresolvedHandle(run);
        boolean unresolved = remainingHandle != null;
        String status = unresolved ? "WAITING_RESOURCE" : run.cancelRequested() ? "CANCELLED" : "QUEUED";
        String error = unresolved ? "执行器失去租约，外部调用结果待核实；保留执行句柄，未自动重发或宣告停止" : null;
        if (status.equals(run.status()) && java.util.Objects.equals(remainingHandle, run.externalHandle())) return;
        changed(mapper.recoverRun(runId, run.version(), status, unresolved ? run.ownerToken() : null,
                unresolved ? run.leaseUntil() : null, remainingHandle, error, now()));
        append(runId, "recovery:" + run.ownerToken() + ":" + run.version(), "RECOVERY", Map.of("status", status, "unresolvedExternalCall", unresolved));
    }

    private String unresolvedHandle(AgentRunEntity run) {
        if (run.externalHandle() == null) return null;
        var handle = json.readTree(run.externalHandle());
        String kind = handle.path("kind").asText(), key = handle.path("key").asText();
        if (key.isBlank()) return run.externalHandle();
        if ("API".equals(kind) && hasReceipt(run.id(), key + ":response", "MODEL_RESPONSE")) return null;
        if ("TOOL".equals(kind)) {
            restoreCommittedInspectionResult(run, handle, false);
            if (hasReceipt(run.id(), key + ":result", "TOOL_RESULT")) return null;
        }
        if ("CLI".equals(kind)) {
            var active = handle.path("activeTool");
            if ("TOOL".equals(active.path("kind").asText())) restoreCommittedInspectionResult(run, active, true);
            if (!active.isMissingNode() && hasReceipt(run.id(), active.path("key").asText() + ":result", "TOOL_RESULT")) {
                ((tools.jackson.databind.node.ObjectNode) handle).remove("activeTool");
            }
            var stopped = mapper.findEventByKey(run.id(), key + ":result");
            if (!handle.hasNonNull("activeTool") && stopped != null && "CLI_RESULT".equals(stopped.kind())
                    && json.readTree(stopped.payloadJson()).path("processTreeStopped").asBoolean()) return null;
            return json.writeValueAsString(handle);
        }
        return run.externalHandle();
    }

    private void restoreCommittedInspectionResult(AgentRunEntity run, JsonNode handle, boolean nativeProtocol) {
        String key = handle.path("key").asText(), tool = handle.path("tool").asText();
        if (key.isBlank() || !Set.of("inspection.next", "inspection.ack").contains(tool)
                || mapper.findEventByKey(run.id(), key + ":result") != null) return;
        var intent = mapper.findEventByKey(run.id(), key + ":request");
        if (intent == null || !"TOOL_REQUEST".equals(intent.kind()) || !tool.equals(json.readTree(intent.payloadJson()).path("tool").asText())) return;
        // 生产者已在同一事务验证当前运行/owner/工具键并保存结果；缺失记录不是“没有执行”的证明。
        String committed = mapper.findInspectionCallResult(run.id(), key);
        if (committed == null) return;
        var value = json.readTree(committed);
        if (!value.isObject()) throw new IllegalStateException("已持久化的巡检结果格式无效，保留原调用等待核对");
        Object receipt = nativeProtocol
                ? AgentToolReplies.nativeSuccess(json, data, value, mapper.listEvents(run.id()).stream()
                    .filter(event -> "HUMAN_INPUT".equals(event.kind())).map(event -> json.readTree(event.payloadJson())).toList())
                : AgentToolReplies.apiSuccess(data, value);
        appendReceipt(run.id(), key + ":result", "TOOL_RESULT", receipt);
        append(run.id(), key + ":receipt-recovered", "TOOL_RECEIPT_RECOVERED", Map.of("tool", tool, "source", "COMMITTED_INSPECTION_TRANSACTION"));
    }

    private boolean hasReceipt(String runId, String key, String kind) {
        var event = mapper.findEventByKey(runId, key);
        return event != null && kind.equals(event.kind());
    }

    private AgentRunEntity receiptOwner(AgentRunEntity run, String owner) {
        if (run == null || owner == null || !owner.equals(run.ownerToken()) || run.externalHandle() == null
                || !Set.of("RUNNING", "CANCELLING", "WAITING_RESOURCE").contains(run.status())) {
            throw new LeaseLost("回执不属于仍待核对的原执行者，不能更改新运行");
        }
        return run;
    }

    private boolean liveReceipt(AgentRunEntity run) {
        return run.leaseUntil() != null && run.leaseUntil().isAfter(now()) && Set.of("RUNNING", "CANCELLING").contains(run.status());
    }

    private void requireUnchangedInput(String runId, String requestKey) {
        if (mapper.findEventByKey(runId, requestKey) == null) throw new IllegalArgumentException("缺少本次模型调用的输入版本");
        if (hasNewInputSince(runId, requestKey)) throw new InputChanged("已有新指令，旧行动需要重新规划");
    }

    private void appendReceipt(String runId, String key, String kind, Object payload) {
        var prior = mapper.findEventByKey(runId, key);
        if (prior != null && (!kind.equals(prior.kind()) || !json.readTree(prior.payloadJson()).equals(json.readTree(json.writeValueAsString(data.sanitize(payload)))))) {
            throw new IllegalArgumentException("同一调用的回执内容不一致，原回执不可改写");
        }
        append(runId, key, kind, payload);
    }

    private AgentRunEntity owned(AgentRunEntity run, String owner, boolean stoppingAllowed) {
        if (run == null || owner == null || !owner.equals(run.ownerToken()) || run.leaseUntil() == null || !run.leaseUntil().isAfter(now())) {
            throw new LeaseLost("执行租约失效，旧执行器不能继续更新运行");
        }
        if (!stoppingAllowed && run.cancelRequested()) throw new StopRequested("任务已请求取消");
        if (!("RUNNING".equals(run.status()) || stoppingAllowed && "CANCELLING".equals(run.status()))) throw new LeaseLost("运行已不处于执行状态");
        return run;
    }

    private void requireBudget(AgentRunEntity run) {
        if (run.elapsedMillis() >= context(run).configuration().maxRunSeconds() * 1000L) throw new BudgetExceeded("Agent 运行时间预算已耗尽");
    }

    private static void requireNoExternal(AgentRunEntity run) {
        if (run.externalHandle() != null) throw new IllegalStateException("存在尚未确认结束的外部调用");
    }

    private tools.jackson.databind.node.ObjectNode nativeHandleWithoutActiveTool(AgentRunEntity run) {
        if (run.externalHandle() == null) return null;
        var handle = json.readTree(run.externalHandle());
        if (!handle.isObject() || !"CLI".equals(handle.path("kind").asText()) || handle.hasNonNull("activeTool")) {
            throw new IllegalStateException("已有外部工具调用尚未结束");
        }
        return (tools.jackson.databind.node.ObjectNode) handle;
    }

    private void requireHandle(AgentRunEntity run, String kind, String key) {
        if (run.externalHandle() == null) throw new IllegalStateException("缺少调用意图");
        var handle = json.readTree(run.externalHandle());
        if (!kind.equals(handle.path("kind").asText()) || !key.equals(handle.path("key").asText())) throw new IllegalStateException("调用回执与当前执行句柄不符");
    }

    private void append(String runId, String key, String kind, Object payload) {
        if (mapper.findEventByKey(runId, key) != null) return;
        mapper.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), runId, key, mapper.nextEventOrdinal(runId),
                kind, json.writeValueAsString(data.sanitize(payload)), "agent-runtime", now()));
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static String digest(String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private static void changed(int count) { if (count != 1) throw new LeaseLost("运行状态已改变"); }
    public static class LeaseLost extends IllegalStateException { public LeaseLost(String message) { super(message); } }
    public static class StopRequested extends IllegalStateException { public StopRequested(String message) { super(message); } }
    public static class BudgetExceeded extends IllegalStateException { public BudgetExceeded(String message) { super(message); } }
    public static class InputChanged extends IllegalStateException { public InputChanged(String message) { super(message); } }
}
