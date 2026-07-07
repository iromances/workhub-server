package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 商户秘钥文件保存请求。
 */
public class PaymentSecretFileUploadRequest {

    private String secretName;
    private String secretType;
    private String fileValueType;
    private String status;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
