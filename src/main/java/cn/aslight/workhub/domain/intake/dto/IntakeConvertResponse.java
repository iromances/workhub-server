package cn.aslight.workhub.domain.intake.dto;

import cn.aslight.workhub.domain.workitem.dto.WorkItemDetailResponse;

public record IntakeConvertResponse(IntakeDetailResponse intakeRecord, WorkItemDetailResponse workItem) {
}
