package cn.aslight.workhub.model.ai;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 接入配置保存请求。
 */
public class AiProviderConfigRequest {

    private Long id;

    @NotBlank(message = "接入配置编码不能为空")
    private String providerCode;

    @NotBlank(message = "接入配置名称不能为空")
    private String providerName;

    @NotBlank(message = "调用通道不能为空")
    private String channelType;

    private String vendor;

    @NotBlank(message = "模型厂商不能为空")
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
    public void setDefaultModel(String value) { this.defaultModel = value; }
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
}
