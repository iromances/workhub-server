package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 商户详情基础视图。
 */
public record PaymentMerchantDetailView(Long id,
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
                                        LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
}
