package cn.aslight.workhub.domain.attachment.dto;

import java.time.LocalDateTime;

public record AttachmentResponse(Long id,
                                 String category,
                                 String fileName,
                                 String contentType,
                                 String downloadUrl,
                                 boolean previewable,
                                 LocalDateTime createdAt) {
}
