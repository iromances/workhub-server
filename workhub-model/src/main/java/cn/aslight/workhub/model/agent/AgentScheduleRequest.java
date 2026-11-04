package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.*;

/** cron 采用 Spring 六字段表达式；错过的触发点最多补一次，不追赶整个停机期间。 */
public record AgentScheduleRequest(
        @PositiveOrZero Long expectedVersion,
        @NotBlank @Pattern(regexp = "MANUAL|CRON") String mode,
        @Size(max = 128) String cronExpression,
        @NotBlank @Size(max = 64) String zoneId,
        @NotBlank @Pattern(regexp = "SKIP|RUN_ONCE") String misfirePolicy,
        boolean enabled) {
}
