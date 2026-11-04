package cn.aslight.workhub.model.autonomy;

import jakarta.validation.constraints.*;

/** 这是 AI 的分析建议，平台事实与执行结果另由 Observation/产物证明。 */
public record AutonomyProposal(@NotBlank @Size(max = 4000) String facts,
                                @NotBlank @Size(max = 4000) String hypothesis,
                                @NotBlank @Size(max = 2000) String impact,
                                @NotBlank @Size(max = 6000) String plan,
                                @NotBlank @Size(max = 3000) String acceptance,
                                @NotBlank @Size(max = 2000) String rollback,
                                @NotBlank @Size(max = 2000) String unknowns,
                                @NotBlank @Pattern(regexp = "CODE|CONFIGURATION|DATA|INVESTIGATION") String changeType) { }
