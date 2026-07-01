package cn.aslight.workhub.model.project;

import java.time.LocalDateTime;

/**
 * 研发涉及系统清单响应模型。
 */
public record ProjectInvolvedSystemResponse(Long id,
                                            String systemScope,
                                            String businessLineCode,
                                            String businessLine,
                                            String systemName,
                                            String description,
                                            Boolean enabled,
                                            Integer sortOrder,
                                            LocalDateTime createdAt,
                                            LocalDateTime updatedAt) {
}
