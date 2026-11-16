package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 巡检问题修复请求：单/多选工单触发修复管线。 */
public record AgentIssueFixRequest(@NotNull @Size(min = 1, max = 10) List<@NotBlank String> issueIds) {
}
