package cn.aslight.workhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付配置属性。
 */
@ConfigurationProperties(prefix = "workhub.payment")
public class PaymentProperties {

    private String masterKey = "workhub-payment-dev-master-key";
    private int maskVisibleSuffix = 4;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public int getMaskVisibleSuffix() {
        return maskVisibleSuffix;
    }

    public void setMaskVisibleSuffix(int maskVisibleSuffix) {
        this.maskVisibleSuffix = maskVisibleSuffix;
    }
}
