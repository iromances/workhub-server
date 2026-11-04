package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AgentRunReconciliationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String requestKey,
        @NotNull @Min(0) Long expectedVersion,
        @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String snapshotHash,
        @NotNull NextAction nextAction,
        @NotNull @Size(min = 1, max = 2) List<@NotNull @Valid Decision> decisions) {
    public enum NextAction { RESUME, WAIT_INPUT, CANCEL }
    public enum Outcome { NOT_STARTED, ENDED }
    public record Decision(@NotBlank @Size(max = 160) String key, @NotNull Outcome outcome,
                           @NotBlank @Size(max = 3000) String evidence) { }
}
