package cn.aslight.workhub.model.ops;

public record SystemAlertEventNotificationReference(String businessLineCode,
                                                    String environmentCode,
                                                    String sourceEventId) {
}
