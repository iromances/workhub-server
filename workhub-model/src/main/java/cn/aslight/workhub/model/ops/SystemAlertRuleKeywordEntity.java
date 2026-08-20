package cn.aslight.workhub.model.ops;

public record SystemAlertRuleKeywordEntity(Long id,
                                           Long ruleId,
                                           String keyword,
                                           Integer sortOrder) {
}
