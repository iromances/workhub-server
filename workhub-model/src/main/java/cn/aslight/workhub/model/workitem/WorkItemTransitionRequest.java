package cn.aslight.workhub.model.workitem;

import jakarta.validation.constraints.NotBlank;

/**
 * WorkItemTransition 请求模型。
 */
public class WorkItemTransitionRequest {

    @NotBlank(message = "不能为空")
    private String toStatus;

    private String reason;

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
