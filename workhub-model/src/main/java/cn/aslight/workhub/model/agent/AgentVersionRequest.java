package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AgentVersionRequest(@NotNull @Min(0) Long expectedVersion) {
}
