package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商户详情响应。
 */
public record PaymentMerchantDetailResponse(Long id,
                                            Long channelId,
                                            String channelCode,
                                            String channelName,
                                            String merchantCode,
                                            String merchantName,
                                            String environment,
                                            String appId,
                                            String settlementSubject,
                                            String status,
                                            String remark,
                                            List<PaymentMerchantParamResponse> parameters,
                                            List<PaymentSecretSummaryResponse> secrets,
                                            List<PaymentMerchantCredentialResponse> credentials,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
