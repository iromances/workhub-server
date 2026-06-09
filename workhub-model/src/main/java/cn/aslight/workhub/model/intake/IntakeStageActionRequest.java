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
     * 预估完成时间。
     */
    private String plannedDueDate;
    /**
     * 预估开发日期。
     */
    private String plannedDevelopmentStartDate;
    /**
     * 预估提测日期。
     */
    private String plannedTestingStartDate;
    /**
     * 预估上线日期。
     */
    private String plannedReleaseDate;
    /**
     * 实际开发工时。
     */
    private String actualEffort;
    /**
     * 实际开发完成日期。
     */
    private String actualCompletedTime;
    /**
     * 预约验收日期。
     */
    private String scheduledAcceptanceDate;
    /**
     * 实际测试工时。
     */
    private String actualTestingEffort;
    /**
     * 实际测试完成日期。
     */
    private String actualTestingCompletedDate;
    /**
     * 实际验收日期。
     */
    private String acceptanceTime;
    /**
     * 动作发生时间。
     */
    private String occurredAt;
    /**
     * 是否启用 AI 辅助澄清。仅开始澄清动作使用，未传时默认启用。
     */
    private Boolean aiClarificationEnabled;
    /**
     * 需求关闭原因。
     */
    private String closeReason;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getPlannedDueDate() {
        return plannedDueDate;
    }

    public void setPlannedDueDate(String plannedDueDate) {
        this.plannedDueDate = plannedDueDate;
    }

    public String getPlannedDevelopmentStartDate() {
        return plannedDevelopmentStartDate;
    }

    public void setPlannedDevelopmentStartDate(String plannedDevelopmentStartDate) {
        this.plannedDevelopmentStartDate = plannedDevelopmentStartDate;
    }

    public String getPlannedTestingStartDate() {
        return plannedTestingStartDate;
    }

    public void setPlannedTestingStartDate(String plannedTestingStartDate) {
        this.plannedTestingStartDate = plannedTestingStartDate;
    }

    public String getPlannedReleaseDate() {
        return plannedReleaseDate;
    }

    public void setPlannedReleaseDate(String plannedReleaseDate) {
        this.plannedReleaseDate = plannedReleaseDate;
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

    public String getScheduledAcceptanceDate() {
        return scheduledAcceptanceDate;
    }

    public void setScheduledAcceptanceDate(String scheduledAcceptanceDate) {
        this.scheduledAcceptanceDate = scheduledAcceptanceDate;
    }

    public String getActualTestingEffort() {
        return actualTestingEffort;
    }

    public void setActualTestingEffort(String actualTestingEffort) {
        this.actualTestingEffort = actualTestingEffort;
    }

    public String getActualTestingCompletedDate() {
        return actualTestingCompletedDate;
    }

    public void setActualTestingCompletedDate(String actualTestingCompletedDate) {
        this.actualTestingCompletedDate = actualTestingCompletedDate;
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

    public Boolean getAiClarificationEnabled() {
        return aiClarificationEnabled;
    }

    public void setAiClarificationEnabled(Boolean aiClarificationEnabled) {
        this.aiClarificationEnabled = aiClarificationEnabled;
    }

    public String getCloseReason() {
        return closeReason;
    }

    public void setCloseReason(String closeReason) {
        this.closeReason = closeReason;
    }
}
