package cn.aslight.workhub.model.ops;

public record SystemAlertNotificationCandidate(Long id,
                                               String businessLineCode,
                                               String environmentCode,
                                               String dedupeKey) {
}
