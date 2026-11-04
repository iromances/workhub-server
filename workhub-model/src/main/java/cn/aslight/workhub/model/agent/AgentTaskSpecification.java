package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record AgentTaskSpecification(
        @NotBlank @Size(max = 12000) String objective,
        @Size(max = 24000) String context,
        @NotBlank @Size(max = 8000) String acceptanceCriteria,
        @NotEmpty @Size(max = 5) List<@Pattern(regexp = "REPORT|CHANGE_SET|TEST_REPORT|DEPLOYMENT|VERIFICATION") String> expectedArtifacts,
        @NotNull @Size(max = 100) List<@Valid AgentScope> scopes,
        @NotNull @Size(max = 64) List<@NotBlank @Size(max = 64) String> toolCodes) {
    public AgentTaskSpecification {
        expectedArtifacts = expectedArtifacts == null ? null : List.copyOf(expectedArtifacts);
        scopes = scopes == null ? null : List.copyOf(scopes);
        toolCodes = toolCodes == null ? null : List.copyOf(toolCodes);
    }
}
