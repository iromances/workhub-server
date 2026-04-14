package cn.aslight.workhub.model.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 商户保存请求。
 */
public class PaymentMerchantSaveRequest {

    @NotNull(message = "不能为空")
    private Long channelId;

    @NotBlank(message = "不能为空")
    private String merchantCode;

    @NotBlank(message = "不能为空")
    private String merchantName;

    @NotBlank(message = "不能为空")
    private String environment;

    @NotBlank(message = "不能为空")
    private String status;

    private String appId;
    private String settlementSubject;
    private String remark;

    public Long getChannelId() {
        return channelId;
    }

    public void setChannelId(Long channelId) {
        this.channelId = channelId;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public void setMerchantCode(String merchantCode) {
        this.merchantCode = merchantCode;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getSettlementSubject() {
        return settlementSubject;
    }

    public void setSettlementSubject(String settlementSubject) {
        this.settlementSubject = settlementSubject;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
