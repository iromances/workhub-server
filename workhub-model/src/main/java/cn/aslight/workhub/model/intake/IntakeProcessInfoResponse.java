package cn.aslight.workhub.model.intake;

import java.util.Map;

/** 需求过程信息及预估工时来源。values 中的 null 表示未填写。 */
public record IntakeProcessInfoResponse(Map<String, String> values, boolean estimatedEffortOverridden) {
}
