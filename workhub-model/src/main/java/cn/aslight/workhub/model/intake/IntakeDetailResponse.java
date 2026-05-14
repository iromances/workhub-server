package cn.aslight.workhub.model.intake;

import cn.aslight.workhub.model.attachment.AttachmentResponse;

import java.time.LocalDateTime;
import java.util.List;

/**
 * IntakeDetail 响应模型。
 */
public record IntakeDetailResponse(Long id,
                                   String sourceType,
                                   String sourceChannel,
                                   String externalMessageId,
                                   String senderName,
                                   String developmentOwnerUserName,
                                   LocalDateTime receivedAt,
                                   String demandStatus,
                                   String rawContent,
                                   IntakeStructuredData structuredData,
                                   List<IntakeHistoryResponse> histories,
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
