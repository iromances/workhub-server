package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public record AgentTaskSpecification(
        @Size(max = 12000) String objective,
        @Size(max = 24000) String context,
        @Size(max = 8000) String acceptanceCriteria,
        @NotEmpty @Size(max = 5) List<@Pattern(regexp = "REPORT|CHANGE_SET|TEST_REPORT|DEPLOYMENT|VERIFICATION") String> expectedArtifacts,
        @NotNull @Size(max = 100) List<@Valid AgentScope> scopes,
        @Size(max = 100) List<@NotNull @Valid AgentScope> completionTargets,
        @Pattern(regexp = "FULL|INCREMENTAL") String analysisScope,
        /** 企微机器人预警地址（任务级）；存储为 enc: 密文，明文仅在提交/回显/发送时出现。 */
        @Size(max = 512) String webhookUrl,
        @Pattern(regexp = "OBSERVE|EXECUTE_CONFIGURED") String monitorMode,
        @Size(max = 20) List<@NotNull @Positive Long> monitorConfigIds,
        java.time.LocalDate monitorCheckDate) {
    public AgentTaskSpecification {
        monitorMode = monitorMode == null ? "OBSERVE" : monitorMode;
        monitorConfigIds = monitorConfigIds == null ? List.of() : List.copyOf(monitorConfigIds);
        if (new HashSet<>(monitorConfigIds).size() != monitorConfigIds.size())
            throw new IllegalArgumentException("监测场景不能重复");
        if ("EXECUTE_CONFIGURED".equals(monitorMode) && monitorConfigIds.isEmpty())
            throw new IllegalArgumentException("执行业务监测须明确选择场景");
        if (!"EXECUTE_CONFIGURED".equals(monitorMode) && (!monitorConfigIds.isEmpty() || monitorCheckDate != null))
            throw new IllegalArgumentException("观察模式不能指定执行场景或日期");
        // 目标与验收可留空（按职责执行）；归一化为空串，删除审计 Map.of 与通知标题等下游不做判空。
        objective = objective == null || objective.isBlank() ? "" : objective;
        acceptanceCriteria = acceptanceCriteria == null || acceptanceCriteria.isBlank() ? "" : acceptanceCriteria;
        expectedArtifacts = expectedArtifacts == null ? null : List.copyOf(expectedArtifacts);
        scopes = scopes == null ? null : List.copyOf(scopes);
        completionTargets = completionTargets == null ? List.of() : List.copyOf(completionTargets);
        analysisScope = analysisScope == null ? "FULL" : analysisScope;
        webhookUrl = webhookUrl == null || webhookUrl.isBlank() ? null : webhookUrl.trim();
    }

    public AgentTaskSpecification(String objective, String context, String acceptanceCriteria, List<String> expectedArtifacts,
                                  List<AgentScope> scopes, List<AgentScope> completionTargets, String analysisScope, String webhookUrl) {
        this(objective, context, acceptanceCriteria, expectedArtifacts, scopes, completionTargets, analysisScope, webhookUrl,
                "OBSERVE", List.of(), null);
    }

    /** 旧任务读取及调用方兼容，不从授权范围推断完成目标；旧规格默认全量分析、无预警地址。 */
    public AgentTaskSpecification(String objective, String context, String acceptanceCriteria, List<String> expectedArtifacts,
                                  List<AgentScope> scopes, List<AgentScope> completionTargets) {
        this(objective, context, acceptanceCriteria, expectedArtifacts, scopes, completionTargets, "FULL", null);
    }
    public AgentTaskSpecification(String objective, String context, String acceptanceCriteria, List<String> expectedArtifacts,
                                  List<AgentScope> scopes) {
        this(objective, context, acceptanceCriteria, expectedArtifacts, scopes, List.of(), "FULL", null);
    }
    public AgentTaskSpecification(String objective, String context, String acceptanceCriteria, List<String> expectedArtifacts,
                                  List<AgentScope> scopes, List<AgentScope> completionTargets, String analysisScope) {
        this(objective, context, acceptanceCriteria, expectedArtifacts, scopes, completionTargets, analysisScope, null);
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
