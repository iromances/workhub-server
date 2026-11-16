package cn.aslight.workhub.model.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** 工单审核：通过触发合并+TEST 部署；拒绝回开放（意见必填）。 */
public record AgentIssueReviewRequest(@PositiveOrZero Long expectedVersion,
                                      @NotNull @Pattern(regexp = "APPROVE|REJECT") String decision,
                                      @Size(max = 2000) String note) {
}
