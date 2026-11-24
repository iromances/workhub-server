package cn.aslight.workhub.model.ops;

/**
 * REQ-d227e09d：场景监测维度（场景类型 × 维度后缀拼装结果 rule_code）。
 */
public record AccountingScenarioDimensionResponse(String dimensionCode,
                                                  String name,
                                                  String description) {
}
