package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public class SystemAlertCleanupTaskEntity {

    private Long id;
    private String taskNo;
    private String messageKeyword;
    private String businessLineCode;
    private String environmentCode;
    private String serviceName;
    private String logLevel;
    private String eventCategory;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private Long ruleId;
    private Long maxEventId;
    private Long processedEventId;
    private Long deletedEventCount;
    private Long deletedNotificationCount;
    private String operatorUserName;
    private String requestIp;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getMessageKeyword() { return messageKeyword; }
    public void setMessageKeyword(String messageKeyword) { this.messageKeyword = messageKeyword; }
    public String getBusinessLineCode() { return businessLineCode; }
    public void setBusinessLineCode(String businessLineCode) { this.businessLineCode = businessLineCode; }
    public String getEnvironmentCode() { return environmentCode; }
    public void setEnvironmentCode(String environmentCode) { this.environmentCode = environmentCode; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public String getLogLevel() { return logLevel; }
    public void setLogLevel(String logLevel) { this.logLevel = logLevel; }
    public String getEventCategory() { return eventCategory; }
    public void setEventCategory(String eventCategory) { this.eventCategory = eventCategory; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getMaxEventId() { return maxEventId; }
    public void setMaxEventId(Long maxEventId) { this.maxEventId = maxEventId; }
    public Long getProcessedEventId() { return processedEventId; }
    public void setProcessedEventId(Long processedEventId) { this.processedEventId = processedEventId; }
    public Long getDeletedEventCount() { return deletedEventCount; }
    public void setDeletedEventCount(Long deletedEventCount) { this.deletedEventCount = deletedEventCount; }
    public Long getDeletedNotificationCount() { return deletedNotificationCount; }
    public void setDeletedNotificationCount(Long deletedNotificationCount) { this.deletedNotificationCount = deletedNotificationCount; }
    public String getOperatorUserName() { return operatorUserName; }
    public void setOperatorUserName(String operatorUserName) { this.operatorUserName = operatorUserName; }
    public String getRequestIp() { return requestIp; }
    public void setRequestIp(String requestIp) { this.requestIp = requestIp; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
