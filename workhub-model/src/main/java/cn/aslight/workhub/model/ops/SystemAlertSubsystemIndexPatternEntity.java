package cn.aslight.workhub.model.ops;

public record SystemAlertSubsystemIndexPatternEntity(Long id,
                                                     Long subsystemId,
                                                     String indexPattern,
                                                     Integer sortOrder) {
}
