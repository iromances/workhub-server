package cn.aslight.workhub.model.payment;

/**
 * 支付绑定关联商户关系响应。
 */
public record PaymentBindingRelationResponse(Long id,
                                             Long bindingId,
                                             Long merchantId,
                                             String merchantCode,
                                             String merchantName,
                                             String relationRole,
                                             String relationName,
                                             Integer priority,
                                             String remark) {
}
