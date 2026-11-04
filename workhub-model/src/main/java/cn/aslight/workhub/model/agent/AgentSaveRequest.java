package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record AgentSaveRequest(@PositiveOrZero Long expectedVersion,
                               @NotNull @Valid AgentConfiguration configuration) {
}
