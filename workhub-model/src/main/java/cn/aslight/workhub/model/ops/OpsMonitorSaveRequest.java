package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotBlank;

public class OpsMonitorSaveRequest {

    @NotBlank(message = "监测类型不能为空")
    private String monitorType;

    private String monitorKey;

    @NotBlank(message = "业务线不能为空")
    private String businessLineCode;

    @NotBlank(message = "环境不能为空")
    private String environmentCode;

    private String name;

    private String adminBaseUrl;
    private String username;
    private String password;
    private String xxlJobDatabaseName;
    private String executorAppName;
    private String jobHandler;
    private String jobDesc;
    private String mqTopic;
    private String mqConsumerGroup;
    private Integer mqLagThreshold;
    private Boolean enabled;
    private String remark;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
