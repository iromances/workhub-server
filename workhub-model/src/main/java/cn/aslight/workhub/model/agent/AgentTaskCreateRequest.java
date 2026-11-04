package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AgentTaskCreateRequest(
        @NotBlank @Size(max = 64) String requestKey,
        @NotNull @Valid AgentTaskSpecification specification,
        @NotNull @Valid AgentScheduleRequest schedule,
        boolean runImmediately) {
}
