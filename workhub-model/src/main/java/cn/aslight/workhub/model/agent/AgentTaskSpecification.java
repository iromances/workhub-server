package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public record AgentTaskSpecification(
        @NotBlank @Size(max = 12000) String objective,
        @Size(max = 24000) String context,
        @NotBlank @Size(max = 8000) String acceptanceCriteria,
        @NotEmpty @Size(max = 5) List<@Pattern(regexp = "REPORT|CHANGE_SET|TEST_REPORT|DEPLOYMENT|VERIFICATION") String> expectedArtifacts,
        @NotNull @Size(max = 100) List<@Valid AgentScope> scopes,
        @NotNull @Size(max = 64) List<@NotBlank @Size(max = 64) String> toolCodes,
        @Size(max = 100) List<@NotNull @Valid AgentScope> completionTargets) {
    public AgentTaskSpecification {
        expectedArtifacts = expectedArtifacts == null ? null : List.copyOf(expectedArtifacts);
        scopes = scopes == null ? null : List.copyOf(scopes);
        toolCodes = toolCodes == null ? null : List.copyOf(toolCodes);
        completionTargets = completionTargets == null ? List.of() : List.copyOf(completionTargets);
    }

    /** 旧任务读取及调用方兼容，不从授权范围推断完成目标。 */
    public AgentTaskSpecification(String objective, String context, String acceptanceCriteria, List<String> expectedArtifacts,
                                  List<AgentScope> scopes, List<String> toolCodes) {
        this(objective, context, acceptanceCriteria, expectedArtifacts, scopes, toolCodes, List.of());
    }
    public boolean requiresCompletionTargets() {
        return expectedArtifacts != null && expectedArtifacts.stream().anyMatch(kind -> Set.of("DEPLOYMENT", "VERIFICATION").contains(kind));
    }
    public void validateCompletionTargets() {
        if (requiresCompletionTargets() && completionTargets.isEmpty())
            throw new IllegalArgumentException("发布或效果验收任务须明确选择本次完成目标环境；旧任务请重新分派");
        if (!requiresCompletionTargets() && !completionTargets.isEmpty())
            throw new IllegalArgumentException("仅发布或效果验收任务可设置完成目标环境");
        if (completionTargets.size() > 100 || new HashSet<>(completionTargets).size() != completionTargets.size()
                || scopes == null || !scopes.containsAll(completionTargets)
                || completionTargets.stream().anyMatch(scope -> !Set.of("TEST", "PROD").contains(scope.environmentCode())))
            throw new IllegalArgumentException("完成目标须为任务范围内不重复的业务线及测试/生产环境");
    }
}
