package cn.aslight.workhub.model.ai;

import java.time.LocalDateTime;

/**
 * AI 接入配置实体。
 */
public class AiProviderConfigEntity {

    private Long id;
    private String providerCode;
    private String providerName;
    private String channelType;
    private String vendor;
    private String modelProvider;
    private String defaultModel;
    private String defaultReasoningLevel;
    private String defaultSpeedMode;
    private String apiProtocol;
    private String apiBaseUrl;
    private String apiKey;
    private String cliCommand;
    private String cliWorkingDirectory;
    private Integer connectTimeoutSeconds;
    private Integer readTimeoutSeconds;
    private Integer callTimeoutSeconds;
    private String siteUrl;
    private String appName;
    private Boolean enabled;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime modifyTime;
    private String modifyBy;
    private LocalDateTime deleteTime;
    private String deleteBy;
    private Boolean deleted;
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public String getModelProvider() {
        return modelProvider;
    }

    public void setModelProvider(String modelProvider) {
        this.modelProvider = modelProvider;
    }

    public String getDefaultModel() { return defaultModel; }
    public void setDefaultModel(String defaultModel) { this.defaultModel = defaultModel; }
    public String getDefaultReasoningLevel() { return defaultReasoningLevel; }
    public void setDefaultReasoningLevel(String value) { this.defaultReasoningLevel = value; }
    public String getDefaultSpeedMode() { return defaultSpeedMode; }
    public void setDefaultSpeedMode(String value) { this.defaultSpeedMode = value; }

    public String getApiProtocol() {
        return apiProtocol;
    }

    public void setApiProtocol(String apiProtocol) {
        this.apiProtocol = apiProtocol;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getCliCommand() {
        return cliCommand;
    }

    public void setCliCommand(String cliCommand) {
        this.cliCommand = cliCommand;
    }

    public String getCliWorkingDirectory() {
        return cliWorkingDirectory;
    }

    public void setCliWorkingDirectory(String cliWorkingDirectory) {
        this.cliWorkingDirectory = cliWorkingDirectory;
    }

    public Integer getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(Integer connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public Integer getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(Integer readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public Integer getCallTimeoutSeconds() {
        return callTimeoutSeconds;
    }

    public void setCallTimeoutSeconds(Integer callTimeoutSeconds) {
        this.callTimeoutSeconds = callTimeoutSeconds;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
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

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime value) { this.createTime = value; }
    public String getCreateBy() { return createBy; }
    public void setCreateBy(String value) { this.createBy = value; }
    public LocalDateTime getModifyTime() { return modifyTime; }
    public void setModifyTime(LocalDateTime value) { this.modifyTime = value; }
    public String getModifyBy() { return modifyBy; }
    public void setModifyBy(String value) { this.modifyBy = value; }
    public LocalDateTime getDeleteTime() { return deleteTime; }
    public void setDeleteTime(LocalDateTime value) { this.deleteTime = value; }
    public String getDeleteBy() { return deleteBy; }
    public void setDeleteBy(String value) { this.deleteBy = value; }
    public Boolean getDeleted() { return deleted; }
    public void setDeleted(Boolean value) { this.deleted = value; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer value) { this.version = value; }
}
