package cn.aslight.workhub.model.payment;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目商户绑定响应。
 */
public record PaymentProjectBindingResponse(Long id,
                                            Long projectId,
                                            String businessLineCode,
                                            String businessLineName,
                                            String projectGroup,
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
                                            List<String> purposeCodes,
                                            Integer priority,
                                            Boolean defaultBinding,
                                            String status,
                                            String remark,
                                            List<PaymentBindingRelationResponse> relations,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
