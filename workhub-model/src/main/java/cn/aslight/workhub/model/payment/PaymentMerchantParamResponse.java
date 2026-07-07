package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 商户参数响应。
 */
public record PaymentMerchantParamResponse(Long id,
                                           String paramKey,
                                           String valueType,
                                           String sourceType,
                                           String fileName,
                                           String fileContentType,
                                           String fileValueType,
                                           Boolean sensitive,
                                           String displayValue,
                                           String remark,
                                           LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
