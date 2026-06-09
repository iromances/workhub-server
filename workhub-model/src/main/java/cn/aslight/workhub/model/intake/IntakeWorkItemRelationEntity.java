package cn.aslight.workhub.model.intake;

import java.time.LocalDateTime;

/**
 * 需求与正式工作项关联实体。
 */
public class IntakeWorkItemRelationEntity {

    private Long id;
    private Long intakeId;
    private Long workItemId;
    private Integer draftIndex;
    private String relationType;
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

    public Long getWorkItemId() {
        return workItemId;
    }

    public void setWorkItemId(Long workItemId) {
        this.workItemId = workItemId;
    }

    public Integer getDraftIndex() {
        return draftIndex;
    }

    public void setDraftIndex(Integer draftIndex) {
        this.draftIndex = draftIndex;
    }

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
