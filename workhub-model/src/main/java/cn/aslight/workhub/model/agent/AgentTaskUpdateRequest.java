package cn.aslight.workhub.model.agent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 修改任务规格；已有运行保留原快照，后续新运行使用新规格。schedule 可选，提供时一并调整执行方式。 */
public record AgentTaskUpdateRequest(@PositiveOrZero Long expectedVersion,
                                     @NotNull @Valid AgentTaskSpecification specification,
                                     @Valid AgentScheduleRequest schedule) {
}
