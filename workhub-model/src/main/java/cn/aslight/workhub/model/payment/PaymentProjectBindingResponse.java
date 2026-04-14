package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;

/**
 * 项目商户绑定响应。
 */
public record PaymentProjectBindingResponse(Long id,
                                            Long projectId,
                                            String projectCode,
                                            String projectName,
                                            Long merchantId,
                                            String merchantCode,
                                            String merchantName,
                                            Long channelId,
                                            String channelCode,
                                            String channelName,
                                            String environment,
                                            String purposeCode,
                                            Integer priority,
                                            Boolean defaultBinding,
                                            String status,
                                            String remark,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
