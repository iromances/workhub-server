package cn.aslight.workhub.model.ops;

public record SystemAlertEventBatchDeleteResponse(Integer deletedCount,
                                                  Integer deletedNotificationCount) {
}
