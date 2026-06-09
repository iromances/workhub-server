package cn.aslight.workhub.model.system;

import java.time.LocalDateTime;

/**
 * 研发人员资源响应。
 */
public record DeveloperResourceResponse(Long id,
                                        String userName,
                                        String displayName,
                                        Boolean enabled,
                                        String remark,
                                        LocalDateTime createdAt,
                                        LocalDateTime updatedAt) {
}
