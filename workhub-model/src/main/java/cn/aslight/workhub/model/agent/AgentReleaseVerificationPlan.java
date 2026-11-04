package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** Agent 提出的业务复查标准；随整组发布审核冻结，本身不是验证结果。 */
public record AgentReleaseVerificationPlan(
        @NotNull @Size(min = 1, max = 20) List<@NotNull @Valid Check> checks,
        @NotBlank @Size(max = 4000) String historicalDataHandling,
        @NotBlank @Size(max = 4000) String failureHandling) {
    public static final List<String> TOOL_CODES = List.of("monitor.business.status", "monitor.business.detail",
            "monitor.accounting.status", "monitor.accounting.detail", "monitor.accounting.runs", "monitor.system.alerts", "workhub.logs");
    public AgentReleaseVerificationPlan { if (checks != null) checks = List.copyOf(checks); }

    public record Check(@NotBlank @Size(max = 64) String id, @NotNull @Valid AgentScope scope,
            @NotBlank @Size(max = 64) String toolCode, @NotBlank @Size(max = 2000) String monitoringItem,
            @NotBlank @Size(max = 4000) String passCondition,
            @Min(1) @Max(2592000) long observationSeconds,
            @Min(1) @Max(86400) long sampleIntervalSeconds,
            @Min(1) @Max(2592000) long maxDataAgeSeconds,
            @Min(1) @Max(10000) long minimumSamples,
            @NotBlank @Size(max = 4000) String evidenceRequirements) { }
}
