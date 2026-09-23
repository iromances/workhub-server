package cn.aslight.workhub.model.intake;

import java.util.Map;

/** 原始快照用于冲突检测；changes 缺省字段保持原值，显式 null 清空。 */
public record IntakeProcessInfoUpdateRequest(IntakeProcessInfoResponse original, Map<String, String> changes) {
}
