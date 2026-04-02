package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "workhub.storage")
/**
 * Storage 配置属性。
 */
public class StorageProperties {

    private String localPath = "data/uploads";

    public String getLocalPath() {
        return localPath;
    }

    public void setLocalPath(String localPath) {
        this.localPath = localPath;
    }
}
