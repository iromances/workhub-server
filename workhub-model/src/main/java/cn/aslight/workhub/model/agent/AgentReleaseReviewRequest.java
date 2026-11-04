package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.*;

public record AgentReleaseReviewRequest(@NotBlank @Size(max = 64) String requestKey,
        @Min(0) long expectedVersion, @NotNull @Pattern(regexp = "[a-f0-9]{64}") String snapshotHash,
        @NotNull @Pattern(regexp = "APPROVED|REJECTED") String decision, @NotBlank @Size(max = 4000) String comment) { }
