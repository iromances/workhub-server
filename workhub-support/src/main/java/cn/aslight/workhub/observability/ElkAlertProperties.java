package cn.aslight.workhub.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.ops.elk-alert")
public class ElkAlertProperties {

    private boolean enabled;
    private String baseUrl;
    private String indexPattern = "workhub-logs-*";
    private String apiKey;
    private String username;
    private String password;
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 30;
    private int pageSize = 200;
    private int initialLookbackMinutes = 5;
    private String pitKeepAlive = "1m";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getIndexPattern() { return indexPattern; }
    public void setIndexPattern(String indexPattern) { this.indexPattern = indexPattern; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getRequestTimeoutSeconds() { return requestTimeoutSeconds; }
    public void setRequestTimeoutSeconds(int requestTimeoutSeconds) { this.requestTimeoutSeconds = requestTimeoutSeconds; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getInitialLookbackMinutes() { return initialLookbackMinutes; }
    public void setInitialLookbackMinutes(int initialLookbackMinutes) { this.initialLookbackMinutes = initialLookbackMinutes; }
    public String getPitKeepAlive() { return pitKeepAlive; }
    public void setPitKeepAlive(String pitKeepAlive) { this.pitKeepAlive = pitKeepAlive; }
}
