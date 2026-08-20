package cn.aslight.workhub.model.ops;

public record AccountingRuleResponse(String ruleCode,
                                     String ruleName,
                                     String category,
                                     String granularity,
                                     String severity,
                                     String description,
                                     String ruleProfile) {
}
