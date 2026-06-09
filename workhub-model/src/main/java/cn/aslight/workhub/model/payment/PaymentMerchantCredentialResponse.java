package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 商户敏感凭据摘要响应。
 */
public record PaymentMerchantCredentialResponse(Long id,
                                                String credentialKey,
                                                String credentialName,
                                                String credentialType,
                                                String maskedValue,
                                                String fingerprint,
                                                String status,
                                                String remark,
                                                LocalDateTime createdAt,
                                                LocalDateTime updatedAt) {
}
