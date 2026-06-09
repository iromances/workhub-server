package cn.aslight.workhub.model.ops;

import java.time.LocalDateTime;

public class OpsMonitorEntity {

    private Long id;
    private String monitorType;
    private String monitorKey;
    private String businessLineCode;
    private String environmentCode;
    private String name;
    private String adminBaseUrl;
    private String username;
    private String passwordEncrypted;
    private String xxlJobDatabaseName;
    private String executorAppName;
    private String jobHandler;
    private String jobDesc;
    private String mqTopic;
    private String mqConsumerGroup;
    private Integer mqLagThreshold;
    private Boolean enabled;
    private String lastStatus;
    private String lastMessage;
    private LocalDateTime lastCheckedAt;
    private String remark;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getMonitorType() {
        return monitorType;
    }

    public void setMonitorType(String monitorType) {
        this.monitorType = monitorType;
    }

    public String getMonitorKey() {
        return monitorKey;
    }

    public void setMonitorKey(String monitorKey) {
        this.monitorKey = monitorKey;
    }

    public String getBusinessLineCode() {
        return businessLineCode;
    }

    public void setBusinessLineCode(String businessLineCode) {
        this.businessLineCode = businessLineCode;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAdminBaseUrl() {
        return adminBaseUrl;
    }

    public void setAdminBaseUrl(String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordEncrypted() {
        return passwordEncrypted;
    }

    public void setPasswordEncrypted(String passwordEncrypted) {
        this.passwordEncrypted = passwordEncrypted;
    }

    public String getXxlJobDatabaseName() {
        return xxlJobDatabaseName;
    }

    public void setXxlJobDatabaseName(String xxlJobDatabaseName) {
        this.xxlJobDatabaseName = xxlJobDatabaseName;
    }

    public String getExecutorAppName() {
        return executorAppName;
    }

    public void setExecutorAppName(String executorAppName) {
        this.executorAppName = executorAppName;
    }

    public String getJobHandler() {
        return jobHandler;
    }

    public void setJobHandler(String jobHandler) {
        this.jobHandler = jobHandler;
    }

    public String getJobDesc() {
        return jobDesc;
    }

    public void setJobDesc(String jobDesc) {
        this.jobDesc = jobDesc;
    }

    public String getMqTopic() {
        return mqTopic;
    }

    public void setMqTopic(String mqTopic) {
        this.mqTopic = mqTopic;
    }

    public String getMqConsumerGroup() {
        return mqConsumerGroup;
    }

    public void setMqConsumerGroup(String mqConsumerGroup) {
        this.mqConsumerGroup = mqConsumerGroup;
    }

    public Integer getMqLagThreshold() {
        return mqLagThreshold;
    }

    public void setMqLagThreshold(Integer mqLagThreshold) {
        this.mqLagThreshold = mqLagThreshold;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getLastStatus() {
        return lastStatus;
    }

    public void setLastStatus(String lastStatus) {
        this.lastStatus = lastStatus;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public LocalDateTime getLastCheckedAt() {
        return lastCheckedAt;
    }

    public void setLastCheckedAt(LocalDateTime lastCheckedAt) {
        this.lastCheckedAt = lastCheckedAt;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
