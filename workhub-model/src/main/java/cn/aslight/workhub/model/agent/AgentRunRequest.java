package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.*;

public record AgentRunRequest(@NotBlank @Size(max = 64) String requestKey,
                              @NotNull @Min(0) Long expectedVersion) {
}
