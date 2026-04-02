package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.wecom.robot")
/**
 * WecomRobot 配置属性。
 */
public class WecomRobotProperties {

    private boolean enabled;
    private String webhook;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getWebhook() {
        return webhook;
    }

    public void setWebhook(String webhook) {
        this.webhook = webhook;
    }
}
