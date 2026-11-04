package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.*;

public record AgentMessageRequest(@NotBlank @Size(max = 36) String runId,
                                  @NotBlank @Size(max = 64) String requestKey,
                                  @NotNull @Min(0) Long expectedVersion,
                                  @NotBlank @Size(max = 12000) String message) {
}
