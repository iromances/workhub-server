package cn.aslight.workhub.model.ops;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class SystemAlertDeleteByEventRequest {

    @NotNull(message = "预警事件ID不能为空")
    @Positive(message = "预警事件ID必须为正整数")
    private Long eventId;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }
}
