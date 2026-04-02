package cn.aslight.workhub.model.intake;

/**
 * 需求阶段动作请求。
 */
public class IntakeStageActionRequest {

    /**
     * 阶段动作编码。
     */
    private String action;
    /**
     * 预估工时。
     */
    private String estimatedEffort;
    /**
     * 预估完成时间。
     */
    private String plannedDueDate;
    /**
     * 实际工时。
     */
    private String actualEffort;
    /**
     * 实际完成时间。
     */
    private String actualCompletedTime;
    /**
     * 验收时间。
     */
    private String acceptanceTime;
    /**
     * 动作发生时间。
     */
    private String occurredAt;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEstimatedEffort() {
        return estimatedEffort;
    }

    public void setEstimatedEffort(String estimatedEffort) {
        this.estimatedEffort = estimatedEffort;
    }

    public String getPlannedDueDate() {
        return plannedDueDate;
    }

    public void setPlannedDueDate(String plannedDueDate) {
        this.plannedDueDate = plannedDueDate;
    }

    public String getActualEffort() {
        return actualEffort;
    }

    public void setActualEffort(String actualEffort) {
        this.actualEffort = actualEffort;
    }

    public String getActualCompletedTime() {
        return actualCompletedTime;
    }

    public void setActualCompletedTime(String actualCompletedTime) {
        this.actualCompletedTime = actualCompletedTime;
    }

    public String getAcceptanceTime() {
        return acceptanceTime;
    }

    public void setAcceptanceTime(String acceptanceTime) {
        this.acceptanceTime = acceptanceTime;
    }

    public String getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt) {
        this.occurredAt = occurredAt;
    }
}
