package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AgentToolSaveRequest(@NotNull @PositiveOrZero Long expectedVersion,
                                   @NotBlank @Size(max = 64) String name,
                                   @NotBlank @Size(max = 256) String description) {
}
