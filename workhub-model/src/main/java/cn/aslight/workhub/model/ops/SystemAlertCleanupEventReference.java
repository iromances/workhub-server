package cn.aslight.workhub.model.ops;

public record SystemAlertCleanupEventReference(
        Long id,
        String businessLineCode,
        String environmentCode,
        String sourceEventId) {
}
