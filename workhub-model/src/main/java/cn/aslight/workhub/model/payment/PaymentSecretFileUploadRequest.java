package cn.aslight.workhub.model.payment;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDateTime;

/**
 * 秘钥文件上传请求。
 */
public class PaymentSecretFileUploadRequest {

    @NotBlank(message = "不能为空")
    private String secretName;

    @NotBlank(message = "不能为空")
    private String secretType;

    @NotBlank(message = "不能为空")
    private String fileValueType;

    private Boolean activateNow;
    private LocalDateTime validFrom;
    private LocalDateTime validTo;
    private String remark;

    public String getSecretName() {
        return secretName;
    }

    public void setSecretName(String secretName) {
        this.secretName = secretName;
    }

    public String getSecretType() {
        return secretType;
    }

    public void setSecretType(String secretType) {
        this.secretType = secretType;
    }

    public String getFileValueType() {
        return fileValueType;
    }

    public void setFileValueType(String fileValueType) {
        this.fileValueType = fileValueType;
    }

    public Boolean getActivateNow() {
        return activateNow;
    }

    public void setActivateNow(Boolean activateNow) {
        this.activateNow = activateNow;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
