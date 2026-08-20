package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 运维通道配置属性。
 */
@ConfigurationProperties(prefix = "workhub.mcp")
public class McpProperties {

    private String masterKey = "workhub-mcp-dev-master-key";
    private String accessToken;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
