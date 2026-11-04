package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.dao.agent.AgentDeploymentMapper;
import cn.aslight.workhub.model.agent.AgentDeploymentEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;

/** 每次推进一个已批准发布；恢复只查询已发意图。尚未连接自动调度或开放触发 API。 */
@Service
public class AgentDeploymentDispatcher {
    private final AgentDeploymentMapper mapper;
    private final AgentDeploymentStore store;
    private final AgentDeploymentApproval approval;
    private final AgentJenkinsPolicy policies;
    private final AgentJenkinsClient client;
    private final ObjectMapper json;
    private final Clock clock;
    @Autowired public AgentDeploymentDispatcher(AgentDeploymentMapper mapper, AgentDeploymentStore store, AgentDeploymentApproval approval,
            AgentJenkinsPolicy policies, AgentJenkinsClient client, ObjectMapper json) {
        this(mapper, store, approval, policies, client, json, Clock.systemUTC());
    }
    AgentDeploymentDispatcher(AgentDeploymentMapper mapper, AgentDeploymentStore store, AgentDeploymentApproval approval,
            AgentJenkinsPolicy policies, AgentJenkinsClient client, ObjectMapper json, Clock clock) {
        this.mapper = mapper; this.store = store; this.approval = approval; this.policies = policies; this.client = client; this.json = json; this.clock = clock;
    }

    public AgentDeploymentEntity advance(String id) {
        var request = store.claim(id);
        if (request == null) return mapper.find(id);
        try {
            var binding = json.readValue(request.bindingJson(), AgentJenkinsPolicy.BindingSnapshot.class);
            var parameters = parameters(request);
            if ("PREPARED".equals(request.status())) {
                approval.validate(mapper.approval(request.approvalId()));
                var target = policies.target(binding);
                var probe = client.probe(target);
                if (!probe.buildable() || !String.join("/", binding.binding().jobSegments()).equals(probe.jobName())
                        || !probe.parameterNames().containsAll(policies.requiredParameters(binding.binding()))) {
                    return save(request, "BLOCKED", null, null, null, null, "Jenkins Job 身份、可构建状态或必需参数不符合审核绑定");
                }
                // 探针可能耗时，写入前重新取仍启用的凭据，不能继续使用已撤销的缓存值。
                target = policies.target(binding);
                request = store.intent(request);
                try {
                    long queue = client.submit(target, parameters);
                    return save(request, "QUEUED", queue, null, null, null, null);
                } catch (AgentJenkinsClient.SubmissionRejected ex) {
                    return save(request, "REJECTED", null, null, null, null, ex.getMessage());
                } catch (AgentJenkinsClient.SubmissionUnknown ex) {
                    return save(request, "UNKNOWN", null, null, null, null, ex.getMessage());
                }
            }
            // 已发送后即使原任务取消，也只核对原构建；不把任务取消视为 Jenkins 已停止。
            var target = policies.target(binding);
            if (request.buildNumber() != null) return observeBuild(request, binding, target, parameters);
            if (request.queueId() != null) {
                var queue = client.queue(target, request.queueId(), parameters);
                if (!"EXPIRED".equals(queue.state())) {
                    if (!queue.parametersMatch()) return save(request, "UNKNOWN", null, null, null, null, "Jenkins 队列参数与批准内容不一致");
                    return switch (queue.state()) {
                        case "CANCELLED" -> save(request, "ABORTED", null, null, "ABORTED", null, "Jenkins 已确认队列取消，未自动重发");
                        case "STARTED" -> save(request, "BUILDING", null, queue.buildNumber(), null, null, null);
                        case "QUEUED" -> save(request, "QUEUED", null, null, null, null, null);
                        default -> save(request, "UNKNOWN", null, null, null, null, "Jenkins 队列状态未确认");
                    };
                }
            }
            var found = client.find(target, binding.binding().correlationParameter(), request.id(), parameters);
            if (!"FOUND".equals(found.state())) return save(request, "UNKNOWN", null, null, null, null,
                    "Jenkins 原请求核对结果：" + found.state() + "；未重复触发");
            Long queue = found.queueIds().isEmpty() ? null : found.queueIds().getFirst();
            Long build = found.buildNumbers().isEmpty() ? null : found.buildNumbers().getFirst();
            if (request.queueId() != null && queue != null && !request.queueId().equals(queue)) return save(request, "UNKNOWN", null, null, null, null, "查询到不同的队列编号，不能替换原句柄");
            return save(request, build == null ? "QUEUED" : "BUILDING", queue, build, null, null, null);
        } catch (AgentDeploymentStore.LeaseLost ex) { throw ex; }
        catch (AgentJenkinsClient.ClientFailure ex) {
            return save(request, "PREPARED".equals(request.status()) ? "PREPARED" : "UNKNOWN", null, null, request.buildResult(), request.receiptJson(), ex.getMessage());
        } catch (IllegalArgumentException | IllegalStateException | AccessDeniedException ex) {
            return save(request, "PREPARED".equals(request.status()) ? "BLOCKED" : "UNKNOWN", null, null, request.buildResult(), request.receiptJson(),
                    ex instanceof AccessDeniedException ? "发布权限已撤销；未再次触发" : "发布授权、绑定或实际版本回执不符合固定材料；未再次触发");
        }
    }

    private AgentDeploymentEntity observeBuild(AgentDeploymentEntity request, AgentJenkinsPolicy.BindingSnapshot binding,
            AgentJenkinsClient.Target target, Map<String, String> parameters) {
        var build = client.build(target, request.buildNumber(), parameters);
        if (!build.parametersMatch() || request.queueId() != null && build.queueId() != null && !request.queueId().equals(build.queueId())) {
            return save(request, "UNKNOWN", null, null, build.result(), null, "Jenkins 构建参数或队列关联不一致");
        }
        if ("RUNNING".equals(build.state())) return save(request, "BUILDING", null, null, null, null, null);
        if (!"FINISHED".equals(build.state())) return save(request, "UNKNOWN", null, null, build.result(), null, "原构建尚无可确认的终态，未重发");
        if ("ABORTED".equals(build.result())) return save(request, "ABORTED", null, null, build.result(), null, "Jenkins 构建已停止，已执行的部署步骤仍需复查");
        if (Set.of("FAILURE", "UNSTABLE", "NOT_BUILT").contains(build.result())) return save(request, "FAILED", null, null, build.result(), null, "Jenkins 构建未成功，发布影响及回退仍需核实");
        if (!"SUCCESS".equals(build.result())) return save(request, "UNKNOWN", null, null, build.result(), null, "Jenkins 构建结果无法识别");
        var receipt = client.receipt(target, build.buildNumber(), binding.binding().receiptPath());
        if (receipt == null) return save(request, "DEPLOYING", null, null, build.result(), null, "构建成功，但尚未取得实际部署版本回执");
        var verified = AgentDeploymentReceipt.verify(receipt, request, binding, build.buildNumber(), parameters, clock.instant());
        boolean passed = "SUCCEEDED".equals(verified.deploymentStatus());
        return save(request, passed ? "SUCCEEDED" : "FAILED", null, null, build.result(), json.writeValueAsString(verified),
                passed ? null : "Jenkins 回执表明部署未全部完成，后续发布必须停止并核实现场");
    }
    @SuppressWarnings("unchecked") private Map<String, String> parameters(AgentDeploymentEntity request) {
        Map<String, String> values = new TreeMap<>(json.readValue(request.parametersJson(), Map.class));
        AgentJenkinsClient.validateParameters(values);
        if (!AgentCodePolicy.hash(json.writeValueAsString(values)).equals(request.parametersHash())) throw new IllegalArgumentException("发布参数记录摘要不一致");
        return values;
    }
    private AgentDeploymentEntity save(AgentDeploymentEntity request, String state, Long queue, Long build, String result, String receipt, String error) {
        return store.finish(request, state, queue, build, result, receipt, error);
    }
    static Map<String, Object> view(AgentDeploymentEntity value) {
        var result = new LinkedHashMap<String, Object>();
        result.put("id", value.id()); result.put("approvalId", value.approvalId()); result.put("changeSetId", value.changeSetId());
        result.put("manifestHash", value.manifestHash()); result.put("status", value.status()); result.put("queueId", value.queueId());
        result.put("buildNumber", value.buildNumber()); result.put("buildResult", value.buildResult()); result.put("errorMessage", value.errorMessage());
        result.put("version", value.version()); result.put("updatedAt", value.updatedAt()); result.put("businessVerified", false);
        return result;
    }
}
