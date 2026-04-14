package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 渠道详情响应。
 */
public record PaymentChannelDetailResponse(Long id,
                                           String code,
                                           String name,
                                           String vendorName,
                                           String status,
                                           String description,
                                           LocalDateTime createdAt,
                                           LocalDateTime updatedAt) {
}
