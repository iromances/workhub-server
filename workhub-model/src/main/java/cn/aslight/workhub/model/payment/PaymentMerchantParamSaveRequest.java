package cn.aslight.workhub.model.payment;

/**
 * 商户参数保存请求。
 */
public class PaymentMerchantParamSaveRequest {

    private String paramKey;

    private String paramValue;

    private String valueType;

    private Boolean sensitive;
    private String remark;

    public String getParamKey() {
        return paramKey;
    }

    public void setParamKey(String paramKey) {
        this.paramKey = paramKey;
    }

    public String getParamValue() {
        return paramValue;
    }

    public void setParamValue(String paramValue) {
        this.paramValue = paramValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Boolean getSensitive() {
        return sensitive;
    }

    public void setSensitive(Boolean sensitive) {
        this.sensitive = sensitive;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
