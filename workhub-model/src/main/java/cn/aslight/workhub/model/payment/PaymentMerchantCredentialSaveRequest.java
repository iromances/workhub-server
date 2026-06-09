package cn.aslight.workhub.model.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * 商户敏感凭据保存请求。
 */
public class PaymentMerchantCredentialSaveRequest {

    @NotBlank(message = "不能为空")
    private String credentialKey;

    @NotBlank(message = "不能为空")
    private String credentialName;

    @NotBlank(message = "不能为空")
    private String credentialType;

    @NotBlank(message = "不能为空")
    private String credentialValue;

    @NotBlank(message = "不能为空")
    private String status;

    private Boolean plainStorage;
    private String remark;

    public String getCredentialKey() {
        return credentialKey;
    }

    public void setCredentialKey(String credentialKey) {
        this.credentialKey = credentialKey;
    }

    public String getCredentialName() {
        return credentialName;
    }

    public void setCredentialName(String credentialName) {
        this.credentialName = credentialName;
    }

    public String getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(String credentialType) {
        this.credentialType = credentialType;
    }

    public String getCredentialValue() {
        return credentialValue;
    }

    public void setCredentialValue(String credentialValue) {
        this.credentialValue = credentialValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getPlainStorage() {
        return plainStorage;
    }

    public void setPlainStorage(Boolean plainStorage) {
        this.plainStorage = plainStorage;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
