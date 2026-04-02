package cn.aslight.workhub.model.attachment;

import java.time.LocalDateTime;

/**
 * Attachment 响应模型。
 */
public record AttachmentResponse(Long id,
                                 String category,
                                 String fileName,
                                 String contentType,
                                 String downloadUrl,
                                 boolean previewable,
                                 LocalDateTime createdAt) {
}
