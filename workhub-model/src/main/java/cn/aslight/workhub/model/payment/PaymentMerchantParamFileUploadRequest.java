package cn.aslight.workhub.model.payment;

/**
 * 商户文件型参数保存请求。
 */
public class PaymentMerchantParamFileUploadRequest {

    private String paramKey;
    private String valueType;
    private String fileValueType;
    private String remark;

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

    public String getFileValueType() {
        return fileValueType;
    }

    public void setFileValueType(String fileValueType) {
        this.fileValueType = fileValueType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
