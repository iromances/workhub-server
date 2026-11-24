package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 工作台 Agent 的配置；凭据始终留在既有 AI 接入配置中。AI 能力由职责描述引导、经平台 MCP 自行选择。 */
public record AgentConfiguration(
        @NotBlank @Size(max = 128) String name,
        /** 岗位字典 agentPosition 的 enum_code；存量配置无值，保存时必填并校验存在。 */
        @NotBlank @Size(max = 64) String position,
        @NotBlank @Size(max = 12000) String instructions,
        @NotNull @Positive Long providerConfigId,
        @Size(max = 128) String model,
        @NotNull @Size(max = 100) List<@Valid AgentScope> scopes,
        @Min(1) @Max(200) int maxSteps,
        @Min(30) @Max(14400) int maxRunSeconds,
        /** 兼容历史配置，调度不再使用此字段限制并发。 */
        int maxConcurrentRuns,
        /** 巡检预警级别：报告含 ≥ 该级别才触发预警；存量缺省 P0。 */
        @Pattern(regexp = "P0|P1|P2") String alertLevel,
        /** 预警方式：INBOX 站内信 / WECOM 企微机器人；存量缺省仅站内信。 */
        @Size(max = 4) List<@Pattern(regexp = "INBOX|WECOM") String> alertChannels) {
    public AgentConfiguration {
        scopes = scopes == null ? null : List.copyOf(scopes);
        alertLevel = alertLevel == null || alertLevel.isBlank() ? "P0" : alertLevel;
        alertChannels = alertChannels == null || alertChannels.isEmpty()
                ? List.of("INBOX") : alertChannels.stream().distinct().toList();
    }

    public boolean allowsScope(AgentScope scope) {
        return scopes != null && (scopes.isEmpty() || scopes.contains(scope));
    }

    /** 兼容构造器：存量调用点不携带预警字段，归一化为默认 P0+站内信。 */
    public AgentConfiguration(String name, String position, String instructions, Long providerConfigId, String model,
                              List<AgentScope> scopes, int maxSteps, int maxRunSeconds, int maxConcurrentRuns) {
        this(name, position, instructions, providerConfigId, model, scopes, maxSteps, maxRunSeconds, maxConcurrentRuns, null, null);
    }
}
