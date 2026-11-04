package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentMapper;
import cn.aslight.workhub.model.agent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** 人工核对与实际回执分别留痕；短事务内释放已确认结束的旧调用，不能假造平台产物。 */
@Service
public class AgentRunReconciliationService {
    private final AgentMapper mapper;
    private final AgentAccessService access;
    private final AgentRunStore store;
    private final AgentToolCatalog tools;
    private final AgentToolData data;
    private final AgentCliProcessProbe processes;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public AgentRunReconciliationService(AgentMapper mapper, AgentAccessService access, AgentRunStore store,
            AgentToolCatalog tools, AgentToolData data, AgentCliProcessProbe processes, ObjectMapper json) {
        this(mapper, access, store, tools, data, processes, json, Clock.systemUTC());
    }

    AgentRunReconciliationService(AgentMapper mapper, AgentAccessService access, AgentRunStore store,
            AgentToolCatalog tools, AgentToolData data, AgentCliProcessProbe processes, ObjectMapper json, Clock clock) {
        this.mapper = mapper; this.access = access; this.store = store; this.tools = tools;
        this.data = data; this.processes = processes; this.json = json; this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentRunReconciliationView preview(String taskId, String runId, String user) {
        var actor = access.actor(user);
        return preview(authorizedRun(taskId, runId, actor), actor);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AgentRunReconciliationResult reconcile(String taskId, String runId,
            AgentRunReconciliationRequest request, String user) {
        var actor = access.actor(user);
        actor.require("agent:manage");
        actor.require("agent:task:operate");
        var run = authorizedRun(taskId, runId, actor);
        String eventKey = "reconcile:" + request.requestKey();
        String requestHash = digest(json.writeValueAsString(request));
        var prior = mapper.findEventByKey(runId, eventKey);
        if (prior != null) {
            var saved = json.readTree(prior.payloadJson());
            if (!"MANUAL_RECONCILIATION".equals(prior.kind()) || !actor.name().equals(prior.actor())
                    || !requestHash.equals(saved.path("requestHash").asText())) {
                throw new IllegalArgumentException("核对请求标识已使用，请勿以同一标识提交不同内容或人员");
            }
            return json.treeToValue(saved.path("result"), AgentRunReconciliationResult.class);
        }
        var view = preview(run, actor);
        if (request.expectedVersion() != run.version() || !view.snapshotHash().equals(request.snapshotHash())) {
            throw new IllegalStateException("运行或回执已变化，请重新读取核对材料");
        }
        if (!view.canReconcile()) throw new IllegalStateException(String.join("；", view.blockedReasons()));
        if (run.cancelRequested() && request.nextAction() != AgentRunReconciliationRequest.NextAction.CANCEL) {
            throw new IllegalStateException("运行已请求取消，只能核对后结束本次运行");
        }
        if (request.nextAction() == AgentRunReconciliationRequest.NextAction.RESUME) requireResume(run);
        validateDecisions(view, request.decisions());
        String status = switch (request.nextAction()) {
            case RESUME -> "QUEUED";
            case WAIT_INPUT -> "WAITING_INPUT";
            case CANCEL -> "CANCELLED";
        };
        var result = new AgentRunReconciliationResult(runId, status, run.version() + 1, eventKey);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("requestHash", requestHash);
        payload.put("snapshotHash", view.snapshotHash());
        payload.put("calls", view.calls().stream().map(call -> Map.of("kind", call.kind(), "key", call.key(),
                "receiptSource", Objects.toString(call.receiptSource(), "NONE"),
                "receiptKey", Objects.toString(call.receiptKey(), ""))).toList());
        payload.put("decisions", request.decisions());
        payload.put("nextAction", request.nextAction());
        payload.put("result", result);
        payload.put("message", "人已核对旧调用。人工结论不等于平台实际回执，不能证明测试或发布成功；继续时应依据现状重新规划，不重放旧行动。");
        String safePayload = safeJson(payload);
        append(runId, eventKey, "MANUAL_RECONCILIATION", safePayload, actor.name());
        // 显式跳过旧 API 决策及 CLI 结果；不补造机器回执，原意图与原回执仍保留。
        if (run.stepCount() > 0) handled(runId, "step:" + run.stepCount() + ":handled", eventKey, actor.name());
        for (var call : view.calls()) {
            if ("CLI".equals(call.kind())) handled(runId, call.key() + ":handled", eventKey, actor.name());
        }
        append(runId, eventKey + ":input", "HUMAN_INPUT", safePayload, actor.name());
        String message = "旧调用经人工核对已解除占用；" + switch (request.nextAction()) {
            case RESUME -> "等待 Agent 依据核对记录重新规划";
            case WAIT_INPUT -> "等待人工补充指令";
            case CANCEL -> "本次运行已结束，业务交付与发布结果仍以各自实际证据为准";
        };
        if (mapper.reconcileRun(runId, run.version(), status,
                request.nextAction() == AgentRunReconciliationRequest.NextAction.CANCEL, message, now()) != 1) {
            throw new IllegalStateException("运行状态已改变，未解除占用");
        }
        mapper.insertAudit(UUID.randomUUID().toString(), "RUN", runId, "MANUAL_RECONCILIATION", actor.name(), safePayload, now());
        return result;
    }

    private AgentRunEntity authorizedRun(String taskId, String runId, AgentAccessService.Actor actor) {
        actor.require("agent:task:view");
        var initial = mapper.findRun(runId);
        if (initial == null || !taskId.equals(initial.taskId())) throw new IllegalArgumentException("任务运行不存在");
        access.requireAgent(actor, mapper.lockAgent(initial.agentId()));
        return mapper.lockRun(runId);
    }

    private AgentRunReconciliationView preview(AgentRunEntity run, AgentAccessService.Actor actor) {
        var blocked = new ArrayList<String>();
        if (!actor.has("agent:manage") || !actor.has("agent:task:operate")) blocked.add("提交核对需要 Agent 管理与任务操作权限");
        if (run.ownerToken() == null || run.externalHandle() == null
                || !Set.of("RUNNING", "CANCELLING", "WAITING_RESOURCE").contains(run.status())) blocked.add("运行没有待核对的外部调用");
        if (run.leaseUntil() == null || run.leaseUntil().isAfter(now())) blocked.add("执行租约尚未过期，不能解除当前执行占用");
        var calls = new ArrayList<AgentRunReconciliationView.Call>();
        var proofs = new ArrayList<String>();
        if (run.externalHandle() != null) {
            var handle = json.readTree(run.externalHandle());
            addCall(run, handle, calls, proofs, blocked);
            if ("CLI".equals(handle.path("kind").asText()) && handle.hasNonNull("activeTool")) {
                addCall(run, handle.path("activeTool"), calls, proofs, blocked);
            }
        }
        if (!calls.isEmpty() && calls.stream().noneMatch(AgentRunReconciliationView.Call::needsConfirmation)) {
            blocked.add("已取得全部实际回执，等待执行器自动恢复，无需人工替代回执");
        }
        var resumeBlocked = new ArrayList<String>();
        try { requireResume(run); }
        catch (AccessDeniedException | IllegalArgumentException | IllegalStateException ex) {
            resumeBlocked.add(data.sanitize(ex.getMessage()).asText());
        }
        // 迟到回执不会增加 run.version，因此快照还必须绑定实际回执与已提交工具事务。
        String hash = digest(json.writeValueAsString(Arrays.asList(run.version(), run.ownerToken(), run.leaseUntil(), run.externalHandle(), proofs)));
        return new AgentRunReconciliationView(run.id(), run.version(), hash, run.leaseUntil(), blocked.isEmpty(),
                List.copyOf(blocked), List.copyOf(resumeBlocked), run.cancelRequested(), List.copyOf(calls));
    }

    private void addCall(AgentRunEntity run, JsonNode handle, List<AgentRunReconciliationView.Call> calls,
            List<String> proofs, List<String> blocked) {
        String kind = handle.path("kind").asText(), key = handle.path("key").asText();
        if (!Set.of("API", "TOOL", "CLI").contains(kind) || key.isBlank()) {
            blocked.add("调用句柄格式不受支持，保留占用并由维护人员核对原记录");
            return;
        }
        String receiptKey = key + ("API".equals(kind) ? ":response" : ":result");
        var receipt = mapper.findEventByKey(run.id(), receiptKey);
        String expectedKind = switch (kind) { case "API" -> "MODEL_RESPONSE"; case "CLI" -> "CLI_RESULT"; default -> "TOOL_RESULT"; };
        boolean actualReceipt = receipt != null && expectedKind.equals(receipt.kind())
                && (!"CLI".equals(kind) || json.readTree(receipt.payloadJson()).path("processTreeStopped").asBoolean());
        String receiptSource = actualReceipt ? expectedKind : null;
        proofs.add(receipt == null ? "NONE:" + receiptKey : receiptKey + ":" + receipt.kind() + ":" + digest(receipt.payloadJson()));
        String toolCode = text(handle, "tool");
        var intent = mapper.findEventByKey(run.id(), key + ":request");
        boolean inspection = Set.of("inspection.next", "inspection.ack").contains(Objects.toString(toolCode, ""));
        boolean buildTest = AgentBuildTestStore.START.equals(toolCode);
        if (!actualReceipt && "TOOL".equals(kind) && (inspection || buildTest || AgentVerificationService.MUTATIONS.contains(Objects.toString(toolCode, "")))
                && intent != null && "TOOL_REQUEST".equals(intent.kind())
                && toolCode.equals(json.readTree(intent.payloadJson()).path("tool").asText())) {
            String committed = inspection ? mapper.findInspectionCallResult(run.id(), key) : buildTest ? mapper.findBuildTestCallResult(run.id(),key,toolCode) : mapper.findVerificationCallResult(run.id(), key, toolCode);
            if (committed != null) {
                if (!json.readTree(committed).isObject()) throw new IllegalStateException("已提交工具结果格式无效，请核对原记录");
                receiptSource = inspection ? "COMMITTED_INSPECTION_TRANSACTION" : buildTest ? "COMMITTED_BUILD_TEST_TRANSACTION" : "COMMITTED_VERIFICATION_TRANSACTION";
                proofs.add(key + ":committed:" + digest(committed));
            }
        }
        String processState = null;
        Long pid = handle.hasNonNull("pid") ? handle.path("pid").asLong() : null;
        if ("CLI".equals(kind)) {
            processState = processes.observe(text(handle, "host"), pid, text(handle, "startedAt"));
            if (Set.of("RUNNING", "LIVE_IDENTITY_UNAVAILABLE").contains(processState)) blocked.add("本机 CLI 进程仍存活，需先核实并停止原进程树，不能解除占用");
        }
        calls.add(new AgentRunReconciliationView.Call(kind, key, toolCode,
                handle.hasNonNull("readOnly") ? handle.path("readOnly").asBoolean() : null,
                text(handle, "host"), pid, text(handle, "startedAt"), text(handle, "phase"), processState,
                receiptSource, receiptSource == null ? null : actualReceipt ? receiptKey : key,
                receiptSource == null, intent == null ? null : json.writeValueAsString(data.sanitize(json.readTree(intent.payloadJson())))));
    }

    private void validateDecisions(AgentRunReconciliationView view, List<AgentRunReconciliationRequest.Decision> decisions) {
        var pending = view.calls().stream().filter(AgentRunReconciliationView.Call::needsConfirmation).toList();
        if (decisions == null || decisions.size() != pending.size()
                || decisions.stream().map(AgentRunReconciliationRequest.Decision::key).distinct().count() != decisions.size()) {
            throw new IllegalArgumentException("必须逐项核对全部未知调用，不能重复或遗漏");
        }
        for (var decision : decisions) {
            var call = pending.stream().filter(item -> item.key().equals(decision.key())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("核对项不属于当前未知调用"));
            if (decision.outcome() == null || decision.evidence() == null || decision.evidence().isBlank() || decision.evidence().length() > 3000) {
                throw new IllegalArgumentException("每项核对必须填写明确结论及不超过 3000 字的依据");
            }
            if ("CLI".equals(call.kind()) && call.pid() != null && decision.outcome() == AgentRunReconciliationRequest.Outcome.NOT_STARTED) {
                throw new IllegalArgumentException("CLI 已记录启动，不能确认未发出；请核实进程树结束情况");
            }
        }
    }

    private void requireResume(AgentRunEntity run) {
        if (run.cancelRequested()) throw new IllegalStateException("运行已请求取消");
        var context = store.context(run);
        access.requireRunAuthorization(context);
        store.requireProvider(run);
        for (var code : context.specification().toolCodes()) access.requireExecution(context, tools.require(code));
        for (var scope : context.specification().scopes()) access.requireScope(context, scope);
        if (run.stepCount() >= context.configuration().maxSteps()
                || run.elapsedMillis() >= context.configuration().maxRunSeconds() * 1000L) {
            throw new IllegalStateException("本次运行预算已耗尽，核对后可等待补充或结束，不能重置预算继续");
        }
    }

    private void handled(String runId, String key, String reconciliationKey, String actor) {
        if (mapper.findEventByKey(runId, key) == null) {
            append(runId, key, "MANUAL_RECONCILIATION_HANDLED", safeJson(Map.of("reconciliationKey", reconciliationKey)), actor);
        }
    }

    private void append(String runId, String key, String kind, String payload, String actor) {
        mapper.insertEvent(new AgentEventEntity(UUID.randomUUID().toString(), runId, key, mapper.nextEventOrdinal(runId), kind, payload, actor, now()));
    }

    private String safeJson(Object payload) {
        JsonNode safe = data.sanitize(payload);
        if (safe.path("truncated").asBoolean()) throw new IllegalArgumentException("核对材料过长，请缩小内容并引用具体证据位置");
        return json.writeValueAsString(safe);
    }

    private static String text(JsonNode node, String key) { return node.hasNonNull(key) ? node.path(key).asText() : null; }
    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
