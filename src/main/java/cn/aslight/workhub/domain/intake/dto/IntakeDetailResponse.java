package cn.aslight.workhub.domain.intake.dto;

import cn.aslight.workhub.domain.attachment.dto.AttachmentResponse;

import java.time.LocalDateTime;
import java.util.List;

public record IntakeDetailResponse(Long id,
                                   String sourceType,
                                   String sourceChannel,
                                   String externalMessageId,
                                   String senderName,
                                   LocalDateTime receivedAt,
                                   String rawContent,
                                   IntakeStructuredData structuredData,
                                   String status,
                                   String enrichmentStatus,
                                   String enrichmentErrorSummary,
                                   LocalDateTime enrichmentUpdatedAt,
                                   IntakeAIDraft aiDraft,
                                   List<AttachmentResponse> attachments,
                                   Long convertedWorkItemId,
                                   LocalDateTime createdAt,
                                   LocalDateTime updatedAt) {
}
