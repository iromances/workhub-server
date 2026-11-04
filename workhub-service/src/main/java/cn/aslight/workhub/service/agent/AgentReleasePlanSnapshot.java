package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import java.util.*;

/** 公共证据仅保存一份，每个 Job 仍绑定独立出站标识和准确参数。 */
public record AgentReleasePlanSnapshot(int schemaVersion, AgentChangeView change, List<AgentArtifactEntity> evidence,
        List<String> blockers, List<Step> steps, AgentReleaseVerificationPlan verificationPlan) {
    public record Step(String requestId, String approvalId, AgentJenkinsPolicy.BindingSnapshot binding,
                       Map<String, String> parameters, List<String> dependsOn) {
        public Step { parameters = Collections.unmodifiableMap(new TreeMap<>(parameters)); }
        public String bindingId() { return binding.binding().id(); }
    }
    public Map<String, Object> material(Step step) {
        return Map.of("schemaVersion", 1, "change", change, "evidence", evidence, "binding", step.binding(), "parameters", step.parameters());
    }
}
