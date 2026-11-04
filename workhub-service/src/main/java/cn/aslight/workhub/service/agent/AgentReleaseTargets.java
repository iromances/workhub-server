package cn.aslight.workhub.service.agent;

import cn.aslight.workhub.model.agent.*;
import java.util.*;
import java.util.stream.Collectors;

/** 冻结的任务完成目标决定发布阶段；访问范围只约束权限，不扩展发布目标。 */
final class AgentReleaseTargets {
    static void requireSpecification(AgentTaskSpecification specification) {
        specification.validateCompletionTargets();
        if (!specification.requiresCompletionTargets())
            throw new IllegalArgumentException("本任务未要求发布或效果验收；需要发布时请明确目标环境后重新分派");
    }

    /** 生产完成目标保留同业务线测试前置，但调用方仍须校验 TEST 访问授权。 */
    static Set<AgentScope> scopes(AgentTaskSpecification specification, String stage) {
        requireSpecification(specification);
        String environment = environment(stage);
        var result = new LinkedHashSet<AgentScope>();
        for (var target : specification.completionTargets()) {
            if (environment.equals(target.environmentCode()) || "TEST_DEPLOY".equals(stage) && "PROD".equals(target.environmentCode()))
                result.add(new AgentScope(target.businessLineCode(), environment));
        }
        return Collections.unmodifiableSet(result);
    }

    static boolean allows(AgentTaskSpecification specification, AgentJenkinsPolicy.JobBinding binding) {
        requireSpecification(specification);
        if (!Set.of("TEST_DEPLOY", "PROD_DEPLOY").contains(binding.stage())) return false;
        return environment(binding.stage()).equals(binding.scope().environmentCode())
                && scopes(specification, binding.stage()).contains(binding.scope());
    }

    static void requireAllowed(AgentTaskSpecification specification, AgentJenkinsPolicy.JobBinding binding) {
        if (!allows(specification, binding))
            throw new IllegalArgumentException("发布 Job 超出本次明确选择的目标环境及必要测试前置范围");
    }

    static Set<String> repositories(AgentTaskSpecification specification, AgentChangeManifest manifest, String stage) {
        var targets = scopes(specification, stage);
        if (targets.isEmpty()) throw new IllegalArgumentException("本次任务没有选择该发布阶段的完成目标");
        var result = new HashSet<String>();
        for (var target : targets) {
            var repositories = manifest.repositories().stream()
                    .filter(repository -> target.businessLineCode().equals(repository.scope().businessLineCode()))
                    .map(AgentRepositoryChange::repositoryId).collect(Collectors.toSet());
            if (repositories.isEmpty()) throw new IllegalArgumentException("完成目标尚无候选仓库：" + target.businessLineCode());
            result.addAll(repositories);
        }
        for (var edge : manifest.plan().dependencies()) {
            if (result.contains(edge.repositoryId()) && !result.contains(edge.dependsOn()))
                throw new IllegalArgumentException("当前发布环境未覆盖仓库依赖：" + edge.repositoryId() + " -> " + edge.dependsOn() + "；请调整任务目标或候选，不能跨环境省略依赖");
        }
        return Set.copyOf(result);
    }

    static void requireCoverage(AgentTaskSpecification specification, AgentChangeManifest manifest, String stage,
                                List<AgentReleasePlanSnapshot.Step> steps) {
        var expected = repositories(specification, manifest, stage);
        var actual = new HashSet<String>();
        for (var step : steps) {
            var binding = step.binding().binding();
            requireAllowed(specification, binding);
            if (!stage.equals(binding.stage())) throw new IllegalArgumentException("整组发布必须属于同一阶段");
            for (String repositoryId : binding.commitParameters().keySet()) {
                var repository = manifest.repositories().stream().filter(value -> repositoryId.equals(value.repositoryId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("发布 Job 引用了候选清单之外的仓库"));
                if (!repository.scope().businessLineCode().equals(binding.scope().businessLineCode()))
                    throw new IllegalArgumentException("发布仓库不属于目标业务线");
                actual.add(repositoryId);
            }
        }
        if (!expected.equals(actual)) throw new IllegalArgumentException("发布计划须完整覆盖本阶段目标业务线的全部候选仓库，不能遗漏或扩大范围");
    }

    private static String environment(String stage) {
        return switch (stage) {
            case "TEST_DEPLOY" -> "TEST";
            case "PROD_DEPLOY" -> "PROD";
            default -> throw new IllegalArgumentException("发布阶段须为 TEST_DEPLOY 或 PROD_DEPLOY");
        };
    }
    private AgentReleaseTargets() { }
}
