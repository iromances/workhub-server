package cn.aslight.workhub.model.intake;

import cn.aslight.workhub.model.workitem.WorkItemDetailResponse;

/**
 * IntakeConvert 响应模型。
 */
public record IntakeConvertResponse(IntakeDetailResponse intakeRecord, WorkItemDetailResponse workItem) {
}
