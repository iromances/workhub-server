package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

/** AI 的交付说明；实际代码与执行结果由平台另行取证。 */
public record AgentChangePlan(@NotBlank @Size(max = 4000) String summary,
                              @NotBlank @Size(max = 4000) String impact,
                              @NotBlank @Size(max = 4000) String acceptance,
                              @NotBlank @Size(max = 4000) String rollback,
                              @NotNull @Size(max = 100) List<@Valid Dependency> dependencies) {
    public record Dependency(@NotBlank @Size(max = 64) String repositoryId,
                             @NotBlank @Size(max = 64) String dependsOn) { }
}
