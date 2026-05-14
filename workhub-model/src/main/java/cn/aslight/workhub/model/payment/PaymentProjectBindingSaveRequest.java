package cn.aslight.workhub.model.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 项目商户绑定保存请求。
 */
public class PaymentProjectBindingSaveRequest {

    @NotNull(message = "不能为空")
    private Long projectId;

    @NotNull(message = "不能为空")
    private Long merchantId;

    @NotBlank(message = "不能为空")
    private String purposeCode;

    private List<String> purposeCodes;
    private List<PaymentBindingRelationSaveRequest> relations;

    @NotNull(message = "不能为空")
    private Integer priority;

    private Boolean defaultBinding;

    @NotBlank(message = "不能为空")
    private String status;

    private String remark;

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(Long merchantId) {
        this.merchantId = merchantId;
    }

    public String getPurposeCode() {
        return purposeCode;
    }

    public void setPurposeCode(String purposeCode) {
        this.purposeCode = purposeCode;
    }

    public List<String> getPurposeCodes() {
        return purposeCodes;
    }

    public void setPurposeCodes(List<String> purposeCodes) {
        this.purposeCodes = purposeCodes;
    }

    public List<PaymentBindingRelationSaveRequest> getRelations() {
        return relations;
    }

    public void setRelations(List<PaymentBindingRelationSaveRequest> relations) {
        this.relations = relations;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public Boolean getDefaultBinding() {
        return defaultBinding;
    }

    public void setDefaultBinding(Boolean defaultBinding) {
        this.defaultBinding = defaultBinding;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
