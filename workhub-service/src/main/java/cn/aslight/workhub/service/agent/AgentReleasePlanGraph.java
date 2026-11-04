package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import java.util.*;

/** Job 依赖包含清单仓库依赖；同一部署资源必须明确先后，不能隐式并发。 */
final class AgentReleasePlanGraph {
    static List<AgentReleasePlanSnapshot.Step> order(AgentChangeView change, List<AgentReleasePlanSnapshot.Step> steps,
                                                    List<AgentReleasePlanRequest.Dependency> explicit, Set<String> requiredRepositories) {
        var byId = new TreeMap<String, AgentReleasePlanSnapshot.Step>();
        var dependencies = new TreeMap<String, Set<String>>();
        var repositories = new HashSet<String>();
        for (var step : steps) {
            if (byId.put(step.bindingId(), step) != null) throw new IllegalArgumentException("发布 Job 绑定重复");
            dependencies.put(step.bindingId(), new TreeSet<>());
            repositories.addAll(step.binding().binding().commitParameters().keySet());
        }
        if (!repositories.equals(requiredRepositories)) {
            throw new IllegalArgumentException("发布计划必须覆盖本阶段目标的全部候选仓库");
        }
        for (var edge : explicit) {
            if (!byId.containsKey(edge.bindingId()) || !byId.containsKey(edge.dependsOn()) || edge.bindingId().equals(edge.dependsOn())) throw new IllegalArgumentException("Job 依赖引用不存在或引用自身");
            dependencies.get(edge.bindingId()).add(edge.dependsOn());
        }
        for (var edge : change.manifest().plan().dependencies()) for (var next : steps) for (var prior : steps) {
            if (!next.bindingId().equals(prior.bindingId()) && next.binding().binding().commitParameters().containsKey(edge.repositoryId())
                    && !next.binding().binding().commitParameters().containsKey(edge.dependsOn())
                    && prior.binding().binding().commitParameters().containsKey(edge.dependsOn())) dependencies.get(next.bindingId()).add(prior.bindingId());
        }
        var ordered = new ArrayList<String>();
        while (ordered.size() < steps.size()) {
            var ready = dependencies.keySet().stream().filter(id -> !ordered.contains(id) && ordered.containsAll(dependencies.get(id))).toList();
            if (ready.isEmpty()) throw new IllegalArgumentException("Job 与仓库发布依赖存在循环");
            ordered.addAll(ready);
        }
        for (int left = 0; left < steps.size(); left++) for (int right = left + 1; right < steps.size(); right++) {
            var a = steps.get(left); var b = steps.get(right);
            if (!Collections.disjoint(AgentDeploymentStore.resources(a.binding()), AgentDeploymentStore.resources(b.binding()))
                    && !reaches(a.bindingId(), b.bindingId(), dependencies) && !reaches(b.bindingId(), a.bindingId(), dependencies)) {
                throw new IllegalArgumentException("共享 Job、系统环境或仓库的步骤必须声明先后顺序：" + a.bindingId() + " / " + b.bindingId());
            }
        }
        return ordered.stream().map(id -> {
            var step = byId.get(id);
            return new AgentReleasePlanSnapshot.Step(step.requestId(), step.approvalId(), step.binding(), step.parameters(), List.copyOf(dependencies.get(id)));
        }).toList();
    }
    private static boolean reaches(String from, String to, Map<String, Set<String>> edges) {
        var seen = new HashSet<String>(); var pending = new ArrayDeque<>(edges.get(from));
        while (!pending.isEmpty()) { String id = pending.removeFirst(); if (id.equals(to)) return true; if (seen.add(id)) pending.addAll(edges.get(id)); }
        return false;
    }
    private AgentReleasePlanGraph() { }
}
