package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.AgentDeploymentEntity;
import cn.aslight.workhub.model.agent.AgentScope;
import tools.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;

/** 只验证从已固定 Jenkins build 的 artifact 读取到的实际部署回执，不接受模型产物。 */
final class AgentDeploymentReceipt {
    static Verified verify(JsonNode value, AgentDeploymentEntity request, AgentJenkinsPolicy.BindingSnapshot target,
                           long build, Map<String, String> parameters, Instant now) {
        if (value == null || !value.isObject() || !value.path("schemaVersion").isIntegralNumber() || value.path("schemaVersion").asInt() != 1
                || !"WORKHUB_DEPLOYMENT".equals(text(value, "type")) || !request.id().equals(text(value, "requestId"))
                || !request.manifestHash().equals(text(value, "manifestHash")) || !target.binding().stage().equals(text(value, "stage"))
                || !String.join("/", target.binding().jobSegments()).equals(text(value, "jobFullName"))
                || !value.path("buildNumber").isIntegralNumber() || value.path("buildNumber").asLong() != build) throw invalid();
        var scope = value.path("scope"); var expectedScope = target.binding().scope();
        if (!expectedScope.businessLineCode().equals(text(scope, "businessLineCode")) || !expectedScope.environmentCode().equals(text(scope, "environmentCode"))) throw invalid();
        String status = text(value, "deploymentStatus");
        if (!Set.of("SUCCEEDED", "FAILED", "PARTIAL").contains(status) || !value.path("deployedCommits").isObject()) throw invalid();
        var actual = new TreeMap<String, String>();
        for (var entry : value.path("deployedCommits").properties()) {
            if (!target.binding().commitParameters().containsKey(entry.getKey()) || !entry.getValue().isString()) throw invalid();
            actual.put(entry.getKey(), AgentCodeGit.sha(entry.getValue().asText()));
        }
        if ("SUCCEEDED".equals(status)) {
            if (!actual.keySet().equals(target.binding().commitParameters().keySet())) throw invalid();
            target.binding().commitParameters().forEach((repo, parameter) -> { if (!actual.get(repo).equals(parameters.get(parameter))) throw invalid(); });
        }
        Instant started; Instant finished;
        try { started = Instant.parse(text(value, "startedAt")); finished = Instant.parse(text(value, "finishedAt")); }
        catch (DateTimeException ex) { throw invalid(); }
        if (finished.isBefore(started) || started.isBefore(request.createdAt().toInstant(ZoneOffset.UTC).minusSeconds(120)) || finished.isAfter(now.plusSeconds(120))) throw invalid();
        return new Verified(1, "WORKHUB_DEPLOYMENT", request.id(), request.manifestHash(), target.binding().stage(), expectedScope,
                String.join("/", target.binding().jobSegments()), build, status, Collections.unmodifiableMap(actual), started.toString(), finished.toString());
    }
    private static String text(JsonNode node, String key) { if (!node.path(key).isString()) throw invalid(); return node.path(key).asText(); }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Jenkins 实际部署回执缺失或与批准的请求、版本、环境不一致"); }
    record Verified(int schemaVersion, String type, String requestId, String manifestHash, String stage, AgentScope scope,
                    String jobFullName, long buildNumber, String deploymentStatus, Map<String, String> deployedCommits, String startedAt, String finishedAt) { }
}
