package cn.aslight.workhub.domain.workitem.dto;

import jakarta.validation.constraints.NotBlank;

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
