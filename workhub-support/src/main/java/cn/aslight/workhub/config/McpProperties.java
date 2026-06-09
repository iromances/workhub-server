package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 运维通道配置属性。
 */
@ConfigurationProperties(prefix = "workhub.mcp")
public class McpProperties {

    private String masterKey = "workhub-mcp-dev-master-key";

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }
}
