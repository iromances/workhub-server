package cn.aslight.workhub.model.system;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统集成台账详情响应：系统信息 + 能力清单 + 配置组状态。
 */
public class IntegrationSystemDetailResponse extends IntegrationSystemResponse {

    private List<IntegrationCapabilityResponse> capabilities = new ArrayList<>();
    private List<ConfigGroupStatus> configGroupStatuses = new ArrayList<>();

    public List<IntegrationCapabilityResponse> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<IntegrationCapabilityResponse> capabilities) {
        this.capabilities = capabilities;
    }

    public List<ConfigGroupStatus> getConfigGroupStatuses() {
        return configGroupStatuses;
    }

    public void setConfigGroupStatuses(List<ConfigGroupStatus> configGroupStatuses) {
        this.configGroupStatuses = configGroupStatuses;
    }

    /**
     * 配置组状态；只含统计信息，不回传任何配置值。
     */
    public static class ConfigGroupStatus {

        private String group;
        private int itemCount;
        private int enabledCount;
        private boolean secretConfigured;
        private boolean prefixGroup;

        public ConfigGroupStatus() {
        }

        public ConfigGroupStatus(String group, int itemCount, int enabledCount, boolean secretConfigured, boolean prefixGroup) {
            this.group = group;
            this.itemCount = itemCount;
            this.enabledCount = enabledCount;
            this.secretConfigured = secretConfigured;
            this.prefixGroup = prefixGroup;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public int getItemCount() {
            return itemCount;
        }

        public void setItemCount(int itemCount) {
            this.itemCount = itemCount;
        }

        public int getEnabledCount() {
            return enabledCount;
        }

        public void setEnabledCount(int enabledCount) {
            this.enabledCount = enabledCount;
        }

        public boolean isSecretConfigured() {
            return secretConfigured;
        }

        public void setSecretConfigured(boolean secretConfigured) {
            this.secretConfigured = secretConfigured;
        }

        public boolean isPrefixGroup() {
            return prefixGroup;
        }

        public void setPrefixGroup(boolean prefixGroup) {
            this.prefixGroup = prefixGroup;
        }
    }
}
