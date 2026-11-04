package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** 工作台 Agent 的配置；凭据始终留在既有 AI 接入配置中。 */
public record AgentConfiguration(
        @NotBlank @Size(max = 128) String name,
        @NotBlank @Size(max = 12000) String instructions,
        @NotNull @Positive Long providerConfigId,
        @Size(max = 128) String model,
        @NotNull @Size(max = 100) List<@Valid AgentScope> scopes,
        @NotNull @Size(max = 64) List<@NotBlank @Size(max = 64) String> toolCodes,
        @Min(1) @Max(200) int maxSteps,
        @Min(30) @Max(14400) int maxRunSeconds,
        @Min(1) @Max(8) int maxConcurrentRuns) {
    public AgentConfiguration {
        scopes = scopes == null ? null : List.copyOf(scopes);
        toolCodes = toolCodes == null ? null : List.copyOf(toolCodes);
    }
}
