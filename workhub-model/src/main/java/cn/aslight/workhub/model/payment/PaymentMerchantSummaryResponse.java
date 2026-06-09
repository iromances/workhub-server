package cn.aslight.workhub.model.payment;

import java.util.List;

/**
 * 商户列表响应。
 */
public record PaymentMerchantSummaryResponse(Long id,
                                             Long channelId,
                                             String channelCode,
                                             String channelName,
                                             String merchantCode,
                                             String merchantName,
                                             String environment,
                                             String appId,
                                             List<String> purposeCodes,
                                             String status) {
}
