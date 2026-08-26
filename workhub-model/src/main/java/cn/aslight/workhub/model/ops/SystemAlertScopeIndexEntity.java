package cn.aslight.workhub.model.ops;

public record SystemAlertScopeIndexEntity(Long id,
                                          Long scopeId,
                                          String indexPattern,
                                          Integer sortOrder) {
}
