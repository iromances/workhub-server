package cn.aslight.workhub.model.payment;

import jakarta.validation.constraints.NotBlank;

/**
 * 渠道保存请求。
 */
public class PaymentChannelSaveRequest {

    @NotBlank(message = "不能为空")
    private String code;

    @NotBlank(message = "不能为空")
    private String name;

    @NotBlank(message = "不能为空")
    private String vendorName;

    @NotBlank(message = "不能为空")
    private String status;

    private String description;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVendorName() {
        return vendorName;
    }

    public void setVendorName(String vendorName) {
        this.vendorName = vendorName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
