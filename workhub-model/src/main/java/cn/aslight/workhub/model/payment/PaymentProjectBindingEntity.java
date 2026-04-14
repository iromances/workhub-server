package cn.aslight.workhub.model.payment;

/**
 * 项目商户绑定实体。
 */
public class PaymentProjectBindingEntity {

    private Long id;
    private Long projectId;
    private Long merchantId;
    private String purposeCode;
    private Integer priority;
    private Boolean defaultBinding;
    private String bindingStatus;
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getBindingStatus() {
        return bindingStatus;
    }

    public void setBindingStatus(String bindingStatus) {
        this.bindingStatus = bindingStatus;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
