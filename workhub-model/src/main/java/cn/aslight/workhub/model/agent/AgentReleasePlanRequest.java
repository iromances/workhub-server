package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AgentReleasePlanRequest(@NotBlank @Size(max = 64) String requestKey,
        @NotBlank @Size(max = 36) String changeSetId, @Pattern(regexp = "TEST_DEPLOY|PROD_DEPLOY") @NotNull String stage,
        @NotNull @Size(min = 1, max = 20) List<@NotBlank @Size(max = 64) String> bindingIds,
        @NotNull @Size(max = 100) List<@Valid Dependency> dependencies) {
    public record Dependency(@NotBlank @Size(max = 64) String bindingId, @NotBlank @Size(max = 64) String dependsOn) { }
}
