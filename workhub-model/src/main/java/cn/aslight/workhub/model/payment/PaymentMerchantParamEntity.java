package cn.aslight.workhub.model.payment;

/**
 * 商户参数实体。
 */
public class PaymentMerchantParamEntity {

    private Long id;
    private Long merchantId;
    private String paramKey;
    private String valueType;
    private Boolean sensitiveFlag;
    private String plainValue;
    private String encryptedValue;
    private String maskedValue;
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Boolean getSensitiveFlag() {
        return sensitiveFlag;
    }

    public void setSensitiveFlag(Boolean sensitiveFlag) {
        this.sensitiveFlag = sensitiveFlag;
    }

    public String getPlainValue() {
        return plainValue;
    }

    public void setPlainValue(String plainValue) {
        this.plainValue = plainValue;
    }

    public String getEncryptedValue() {
        return encryptedValue;
    }

    public void setEncryptedValue(String encryptedValue) {
        this.encryptedValue = encryptedValue;
    }

    public String getMaskedValue() {
        return maskedValue;
    }

    public void setMaskedValue(String maskedValue) {
        this.maskedValue = maskedValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
