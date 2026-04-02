package cn.aslight.workhub.model.workitem;

/**
 * 工作项状态流转日志实体。
 */
public class WorkItemTransitionLogEntity {

    /**
     * 流转日志主键 ID。
     */
    private Long id;
    /**
     * 所属工作项 ID。
     */
    private Long workItemId;
    /**
     * 原状态。
     */
    private String fromStatus;
    /**
     * 目标状态。
     */
    private String toStatus;
    /**
     * 流转原因说明。
     */
    private String reason;
    /**
     * 操作人用户名。
     */
    private String operatorUserName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

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

    public String getOperatorUserName() {
        return operatorUserName;
    }

    public void setOperatorUserName(String operatorUserName) {
        this.operatorUserName = operatorUserName;
    }
}
