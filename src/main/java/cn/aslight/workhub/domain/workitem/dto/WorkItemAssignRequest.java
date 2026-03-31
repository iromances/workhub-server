package cn.aslight.workhub.domain.workitem.dto;

import jakarta.validation.constraints.NotBlank;

public class WorkItemAssignRequest {

    @NotBlank(message = "不能为空")
    private String ownerUserName;

    @NotBlank(message = "不能为空")
    private String followerUserName;

    private String reason;

    public String getOwnerUserName() {
        return ownerUserName;
    }

    public void setOwnerUserName(String ownerUserName) {
        this.ownerUserName = ownerUserName;
    }

    public String getFollowerUserName() {
        return followerUserName;
    }

    public void setFollowerUserName(String followerUserName) {
        this.followerUserName = followerUserName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
