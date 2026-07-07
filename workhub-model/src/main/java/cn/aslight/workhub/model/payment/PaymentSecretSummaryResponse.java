package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 秘钥摘要响应。
 */
public record PaymentSecretSummaryResponse(Long id,
                                           String secretName,
                                           String secretType,
                                           Integer versionNo,
                                           String maskedValue,
                                           String fingerprint,
                                           String algorithm,
                                           String sourceType,
                                           String fileName,
                                           String fileContentType,
                                           String fileValueType,
                                           String status,
                                           LocalDateTime validFrom,
                                           LocalDateTime validTo,
                                           String remark,
                                           LocalDateTime createdAt) {

    public PaymentSecretSummaryResponse(Long id,
                                        String secretName,
                                        String secretType,
                                        Integer versionNo,
                                        String maskedValue,
                                        String fingerprint,
                                        String algorithm,
                                        String status,
                                        LocalDateTime validFrom,
                                        LocalDateTime validTo,
                                        String remark,
                                        LocalDateTime createdAt) {
        this(id,
                secretName,
                secretType,
                versionNo,
                maskedValue,
                fingerprint,
                algorithm,
                null,
                null,
                null,
                null,
                status,
                validFrom,
                validTo,
                remark,
                createdAt);
    }
}
