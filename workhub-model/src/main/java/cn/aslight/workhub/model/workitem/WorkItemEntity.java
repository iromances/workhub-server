package cn.aslight.workhub.model.workitem;

import java.time.LocalDateTime;

/**
 * 工作项实体。
 */
public class WorkItemEntity {

    /**
     * 工作项主键 ID。
     */
    private Long id;
    /**
     * 工作项编号。
     */
    private String workItemNo;
    /**
     * 所属项目 ID。
     */
    private Long projectId;
    /**
     * 所属迭代 ID。
     */
    private Long sprintId;
    /**
     * 所属版本 ID。
     */
    private Long releaseId;
    /**
     * 工作项类型。
     */
    private String workItemType;
    /**
     * 工作项标题。
     */
    private String title;
    /**
     * 工作项描述。
     */
    private String description;
    /**
     * 来源类型。
     */
    private String sourceType;
    /**
     * 来源渠道。
     */
    private String sourceChannel;
    /**
     * 优先级。
     */
    private String priority;
    /**
     * 紧急程度。
     */
    private String urgency;
    /**
     * 当前状态。
     */
    private String status;
    /**
     * 创建人用户名。
     */
    private String creatorUserName;
    /**
     * 负责人用户名。
     */
    private String ownerUserName;
    /**
     * 跟进人用户名。
     */
    private String followerUserName;
    /**
     * 提出人姓名。
     */
    private String proposerName;
    /**
     * 验收标准。
     */
    private String acceptanceCriteria;
    /**
     * 计划开始时间。
     */
    private LocalDateTime plannedStartAt;
    /**
     * 计划结束时间。
     */
    private LocalDateTime plannedEndAt;
    /**
     * 实际完成时间。
     */
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getWorkItemNo() {
        return workItemNo;
    }

    public void setWorkItemNo(String workItemNo) {
        this.workItemNo = workItemNo;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getSprintId() {
        return sprintId;
    }

    public void setSprintId(Long sprintId) {
        this.sprintId = sprintId;
    }

    public Long getReleaseId() {
        return releaseId;
    }

    public void setReleaseId(Long releaseId) {
        this.releaseId = releaseId;
    }

    public String getWorkItemType() {
        return workItemType;
    }

    public void setWorkItemType(String workItemType) {
        this.workItemType = workItemType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatorUserName() {
        return creatorUserName;
    }

    public void setCreatorUserName(String creatorUserName) {
        this.creatorUserName = creatorUserName;
    }

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

    public String getProposerName() {
        return proposerName;
    }

    public void setProposerName(String proposerName) {
        this.proposerName = proposerName;
    }

    public String getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(String acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public LocalDateTime getPlannedStartAt() {
        return plannedStartAt;
    }

    public void setPlannedStartAt(LocalDateTime plannedStartAt) {
        this.plannedStartAt = plannedStartAt;
    }

    public LocalDateTime getPlannedEndAt() {
        return plannedEndAt;
    }

    public void setPlannedEndAt(LocalDateTime plannedEndAt) {
        this.plannedEndAt = plannedEndAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }
}
