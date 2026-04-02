package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 需求管理历史记录实体。
 */
public class IntakeHistoryEntity {

    /**
     * 历史记录主键 ID。
     */
    private Long id;
    /**
     * 关联的需求记录 ID。
     */
    private Long intakeId;
    /**
     * 动作类型，例如 VIEW、UPDATE。
     */
    private String actionType;
    /**
     * 动作摘要。
     */
    private String actionSummary;
    /**
     * 动作详情文本。
     */
    private String detailText;
    /**
     * 操作人用户名。
     */
    private String operatorUserName;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getIntakeId() {
        return intakeId;
    }

    public void setIntakeId(Long intakeId) {
        this.intakeId = intakeId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionSummary() {
        return actionSummary;
    }

    public void setActionSummary(String actionSummary) {
        this.actionSummary = actionSummary;
    }

    public String getDetailText() {
        return detailText;
    }

    public void setDetailText(String detailText) {
        this.detailText = detailText;
    }

    public String getOperatorUserName() {
        return operatorUserName;
    }

    public void setOperatorUserName(String operatorUserName) {
        this.operatorUserName = operatorUserName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
