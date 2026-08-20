package cn.aslight.workhub.model.payment;

/**
 * 渠道列表响应。
 */
public record PaymentChannelSummaryResponse(Long id,
                                            String code,
                                            String name,
                                            String vendorName,
                                            String status,
                                            String description) {
}
